package com.architect.service;

import com.architect.dto.EdgeDto;
import com.architect.model.Repo;
import com.architect.model.RuntimeWiringFact;
import com.architect.model.User;
import com.architect.repository.RuntimeWiringFactRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns {@link RuntimeWiringFact}s into repo→repo graph edges: gateway routes, UI→gateway proxy, registry clients.
 */
@Service
@RequiredArgsConstructor
public class RuntimeWiringGraphService {

    private final RuntimeWiringFactRepository factRepository;

    public List<EdgeDto> buildWiredEdges(User user, List<Repo> repos) {
        if (repos.isEmpty()) return List.of();

        List<RuntimeWiringFact> facts = factRepository.findAllByUserId(user.getId());
        Map<Long, List<RuntimeWiringFact>> byRepo = facts.stream()
                .collect(Collectors.groupingBy(f -> f.getRepo().getId()));

        Map<String, Long> logicalNameToRepo = new LinkedHashMap<>();
        for (Repo r : repos) {
            String n = r.getName().toLowerCase(Locale.ROOT);
            logicalNameToRepo.putIfAbsent(n, r.getId());
            logicalNameToRepo.putIfAbsent(n.replace("_", "-"), r.getId());
        }
        for (RuntimeWiringFact f : facts) {
            if (!RuntimeWiringFact.APP_NAME.equals(f.getFactType()) || f.getFactValue() == null) continue;
            String v = f.getFactValue().toLowerCase(Locale.ROOT).trim();
            logicalNameToRepo.put(v, f.getRepo().getId());
            logicalNameToRepo.put(v.replace("_", "-"), f.getRepo().getId());
        }

        Long gatewayRepoId = null;
        for (Repo r : repos) {
            List<RuntimeWiringFact> fs = byRepo.getOrDefault(r.getId(), List.of());
            if (fs.stream().anyMatch(x -> RuntimeWiringFact.GATEWAY_ROUTE.equals(x.getFactType()))) {
                gatewayRepoId = r.getId();
                break;
            }
        }
        if (gatewayRepoId == null) {
            for (Repo r : repos) {
                if (r.getName().toLowerCase(Locale.ROOT).contains("gateway")) {
                    gatewayRepoId = r.getId();
                    break;
                }
            }
        }

        List<EdgeDto> edges = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Pattern portP = Pattern.compile(":(\\d{2,5})(?:/|$)");

        // Gateway → backend services (lb://booking-service + Path=/api/bookings/**)
        for (Repo r : repos) {
            for (RuntimeWiringFact f : byRepo.getOrDefault(r.getId(), List.of())) {
                if (!RuntimeWiringFact.GATEWAY_ROUTE.equals(f.getFactType())) continue;
                String svc = f.getFactValue();
                if (svc == null) continue;
                Long targetId = resolveRepoForServiceId(svc, logicalNameToRepo, repos);
                if (targetId == null || targetId.equals(r.getId())) continue;
                String path = f.getFactKey() != null ? f.getFactKey() : "*";
                String eid = "wired-gw-" + r.getId() + "-" + targetId + "-" + path.hashCode();
                if (!seen.add(eid)) continue;
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("wiringKind", "GATEWAY");
                data.put("pathPattern", path);
                data.put("targetService", svc);
                data.put("fromRepoName", repoName(repos, r.getId()));
                data.put("toRepoName", repoName(repos, targetId));
                edges.add(EdgeDto.builder()
                        .id(eid)
                        .source("repo-" + r.getId())
                        .target("repo-" + targetId)
                        .type("WIRED")
                        .label("gateway → " + svc + " · " + shortenPath(path))
                        .data(data)
                        .build());
            }
        }

        // UI (Vite proxy) → gateway / backend by port
        for (Repo r : repos) {
            for (RuntimeWiringFact f : byRepo.getOrDefault(r.getId(), List.of())) {
                if (!RuntimeWiringFact.VITE_PROXY.equals(f.getFactType())) continue;
                String target = f.getFactValue();
                if (target == null) continue;
                Matcher m = portP.matcher(target);
                if (!m.find()) continue;
                int port = Integer.parseInt(m.group(1));
                Long targetRepo = findRepoByPort(port, byRepo, repos, gatewayRepoId);
                if (targetRepo == null || targetRepo.equals(r.getId())) continue;
                String eid = "wired-vite-" + r.getId() + "-" + targetRepo;
                if (!seen.add(eid)) continue;
                String proxyPath = f.getFactKey() != null ? f.getFactKey() : "/api";
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("wiringKind", "UI_PROXY");
                data.put("proxyPath", proxyPath);
                data.put("targetUrl", target);
                data.put("fromRepoName", repoName(repos, r.getId()));
                data.put("toRepoName", repoName(repos, targetRepo));
                edges.add(EdgeDto.builder()
                        .id(eid)
                        .source("repo-" + r.getId())
                        .target("repo-" + targetRepo)
                        .type("WIRED")
                        .label("UI proxy " + proxyPath + " → :" + port)
                        .data(data)
                        .build());
            }
        }

        // Backend → backend: http://user-service:8080, Feign, yml service URLs
        Map<String, BackendAgg> backendAgg = new LinkedHashMap<>();
        for (Repo r : repos) {
            for (RuntimeWiringFact f : byRepo.getOrDefault(r.getId(), List.of())) {
                if (!RuntimeWiringFact.BACKEND_HOST.equals(f.getFactType())) continue;
                String raw = f.getFactValue();
                if (raw == null || raw.isBlank()) continue;
                String logicalHost = toLogicalHost(raw);
                Long targetId = resolveRepoForServiceId(logicalHost, logicalNameToRepo, repos);
                if (targetId == null || targetId.equals(r.getId())) continue;
                String pairKey = r.getId() + "->" + targetId;
                backendAgg.computeIfAbsent(pairKey, k -> new BackendAgg(r.getId(), targetId))
                        .hosts.add(raw);
                if (f.getFactKey() != null) {
                    backendAgg.get(pairKey).kinds.add(f.getFactKey());
                }
            }
        }
        for (BackendAgg agg : backendAgg.values()) {
            String eid = "wired-be-" + agg.fromId + "-" + agg.toId;
            if (!seen.add(eid)) continue;
            String toName = repoName(repos, agg.toId);
            List<String> hostList = new ArrayList<>(agg.hosts);
            Collections.sort(hostList);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("wiringKind", "BACKEND_HTTP");
            data.put("hosts", hostList.subList(0, Math.min(hostList.size(), 12)));
            data.put("sources", new ArrayList<>(agg.kinds));
            data.put("fromRepoName", repoName(repos, agg.fromId));
            data.put("toRepoName", toName);
            String label = repoName(repos, agg.fromId) + " → " + toName + " (backend)";
            edges.add(EdgeDto.builder()
                    .id(eid)
                    .source("repo-" + agg.fromId)
                    .target("repo-" + agg.toId)
                    .type("WIRED")
                    .label(label)
                    .data(data)
                    .build());
        }

        return edges;
    }

    private static String toLogicalHost(String raw) {
        String s = raw.trim();
        if (s.matches("^[a-zA-Z0-9][a-zA-Z0-9.-]*:\\d{1,5}$")) {
            return s.substring(0, s.lastIndexOf(':'));
        }
        return s.split(":")[0];
    }

    private static final class BackendAgg {
        final long fromId;
        final long toId;
        final Set<String> hosts = new LinkedHashSet<>();
        final Set<String> kinds = new LinkedHashSet<>();

        BackendAgg(long fromId, long toId) {
            this.fromId = fromId;
            this.toId = toId;
        }
    }

    private static String shortenPath(String p) {
        if (p == null || p.length() <= 28) return p != null ? p : "";
        return p.substring(0, 25) + "…";
    }

    private static String repoName(List<Repo> repos, long id) {
        return repos.stream().filter(r -> r.getId().equals(id)).map(Repo::getName).findFirst().orElse("?");
    }

    private static Long findRepoByPort(int port, Map<Long, List<RuntimeWiringFact>> byRepo,
                                       List<Repo> repos, Long preferredGatewayId) {
        List<Long> matches = new ArrayList<>();
        for (Repo r : repos) {
            for (RuntimeWiringFact f : byRepo.getOrDefault(r.getId(), List.of())) {
                if (RuntimeWiringFact.SERVER_PORT.equals(f.getFactType())
                        && f.getFactValue() != null
                        && String.valueOf(port).equals(f.getFactValue().trim())) {
                    matches.add(r.getId());
                    break;
                }
            }
        }
        if (matches.isEmpty()) return preferredGatewayId;
        if (preferredGatewayId != null && matches.contains(preferredGatewayId)) {
            return preferredGatewayId;
        }
        return matches.get(0);
    }

    private static Long resolveRepoForServiceId(String serviceId, Map<String, Long> logicalNameToRepo, List<Repo> repos) {
        String s = serviceId.toLowerCase(Locale.ROOT).trim();
        if (logicalNameToRepo.containsKey(s)) return logicalNameToRepo.get(s);
        if (logicalNameToRepo.containsKey(s.replace("_", "-"))) {
            return logicalNameToRepo.get(s.replace("_", "-"));
        }
        String noSuffix = s.replaceAll("-service$", "");
        if (logicalNameToRepo.containsKey(noSuffix)) return logicalNameToRepo.get(noSuffix);
        if (logicalNameToRepo.containsKey(noSuffix + "-service")) {
            return logicalNameToRepo.get(noSuffix + "-service");
        }
        for (Repo r : repos) {
            String rn = r.getName().toLowerCase(Locale.ROOT);
            if (rn.equals(s) || rn.replace("_", "-").equals(s)) return r.getId();
            if (s.contains(rn) || rn.contains(s)) return r.getId();
        }
        return null;
    }
}
