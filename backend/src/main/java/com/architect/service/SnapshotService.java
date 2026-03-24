package com.architect.service;

import com.architect.dto.GraphDto;
import com.architect.model.DependencySnapshot;
import com.architect.repository.DependencySnapshotRepository;
import com.architect.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SnapshotService {

    private final DependencySnapshotRepository snapshotRepository;
    private final GraphBuilderService graphBuilderService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public Map<String, Object> createSnapshot(Long userId, String label) {
        try {
            com.architect.model.User user = userRepository.findById(userId).orElseThrow();
            GraphDto graph = graphBuilderService.buildGraph(user);

            DependencySnapshot snapshot = new DependencySnapshot();
            userRepository.findById(userId).ifPresent(snapshot::setUser);
            snapshot.setSnapshotLabel(label);
            snapshot.setSnapshotData(objectMapper.writeValueAsString(graph));
            snapshot.setNodeCount(graph.getNodes().size());
            snapshot.setEdgeCount(graph.getEdges().size());
            snapshot = snapshotRepository.save(snapshot);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", snapshot.getId());
            result.put("label", snapshot.getSnapshotLabel());
            result.put("nodeCount", snapshot.getNodeCount());
            result.put("edgeCount", snapshot.getEdgeCount());
            result.put("createdAt", snapshot.getCreatedAt());
            return result;
        } catch (Exception e) {
            log.error("Failed to create snapshot", e);
            throw new RuntimeException("Failed to create snapshot: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> listSnapshots(Long userId) {
        return snapshotRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", s.getId());
                    m.put("label", s.getSnapshotLabel());
                    m.put("nodeCount", s.getNodeCount());
                    m.put("edgeCount", s.getEdgeCount());
                    m.put("createdAt", s.getCreatedAt());
                    return m;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> diffSnapshots(Long snapshotId, Long compareId) {
        try {
            DependencySnapshot a = snapshotRepository.findById(snapshotId).orElseThrow();
            DependencySnapshot b = snapshotRepository.findById(compareId).orElseThrow();

            GraphDto graphA = objectMapper.readValue(a.getSnapshotData(), GraphDto.class);
            GraphDto graphB = objectMapper.readValue(b.getSnapshotData(), GraphDto.class);

            Set<String> nodesA = graphA.getNodes().stream()
                    .map(n -> n.getId()).collect(Collectors.toSet());
            Set<String> nodesB = graphB.getNodes().stream()
                    .map(n -> n.getId()).collect(Collectors.toSet());
            Set<String> edgesA = graphA.getEdges().stream()
                    .map(e -> e.getSource() + "->" + e.getTarget()).collect(Collectors.toSet());
            Set<String> edgesB = graphB.getEdges().stream()
                    .map(e -> e.getSource() + "->" + e.getTarget()).collect(Collectors.toSet());

            Set<String> addedNodes = new HashSet<>(nodesB);
            addedNodes.removeAll(nodesA);
            Set<String> removedNodes = new HashSet<>(nodesA);
            removedNodes.removeAll(nodesB);
            Set<String> addedEdges = new HashSet<>(edgesB);
            addedEdges.removeAll(edgesA);
            Set<String> removedEdges = new HashSet<>(edgesA);
            removedEdges.removeAll(edgesB);

            Map<String, Object> diff = new LinkedHashMap<>();
            diff.put("snapshotA", Map.of("id", a.getId(), "label", a.getSnapshotLabel(), "createdAt", a.getCreatedAt()));
            diff.put("snapshotB", Map.of("id", b.getId(), "label", b.getSnapshotLabel(), "createdAt", b.getCreatedAt()));
            diff.put("addedNodes", addedNodes.size());
            diff.put("removedNodes", removedNodes.size());
            diff.put("addedEdges", addedEdges.size());
            diff.put("removedEdges", removedEdges.size());
            diff.put("addedNodeList", addedNodes);
            diff.put("removedNodeList", removedNodes);
            diff.put("addedEdgeList", addedEdges);
            diff.put("removedEdgeList", removedEdges);
            diff.put("netChange", (addedNodes.size() + addedEdges.size()) - (removedNodes.size() + removedEdges.size()));
            return diff;
        } catch (Exception e) {
            throw new RuntimeException("Diff failed: " + e.getMessage());
        }
    }
}
