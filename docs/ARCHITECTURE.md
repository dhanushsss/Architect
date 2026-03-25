# Architect — Architecture Document

**Version:** 1.1.0 · **Updated:** 2026-03-25
**Stack:** Java 21 / Spring Boot 3.2 · React 18 / TypeScript · PostgreSQL 15

---

## Table of Contents

1. [High-Level Design (HLD)](#1-high-level-design)
2. [Low-Level Design (LLD)](#2-low-level-design)
3. [Data Structures & Algorithms](#3-data-structures--algorithms)
4. [Request Lifecycle](#4-request-lifecycle)
5. [API Versioning Strategy](#5-api-versioning-strategy)
6. [Caching Strategy](#6-caching-strategy)

---

## 1. High-Level Design

### System Context

```
Developer Browser
      │
      │  HTTPS
      ▼
┌─────────────────────────────────────────────────────┐
│                    React SPA (Vite)                 │
│   Dashboard · Graph · Risk · AI · Insights          │
│          baseURL: /api/v1  (versioned calls)        │
│          auth:   /api/auth (OAuth — unversioned)    │
└──────────────────────┬──────────────────────────────┘
                       │  Vite proxy (dev) / nginx (prod)
                       ▼
┌─────────────────────────────────────────────────────┐
│             Spring Boot 3.2 Backend                 │
│                                                     │
│  ┌──────────────────┐                               │
│  │  Interceptor     │  RequestTrackingInterceptor   │
│  │  Chain           │  RateLimitInterceptor         │
│  └────────┬─────────┘                               │
│           ▼                                         │
│  ┌──────────────────┐  ┌──────────────────────┐     │
│  │  Controllers     │  │  Service Layer        │     │
│  │  /api/v1/**      │→ │  Scan · Graph · AI    │     │
│  │  /api/auth/**    │  │  Impact · Insights    │     │
│  │  /api/webhooks/**│  └──────────┬───────────┘     │
│  └──────────────────┘             │                  │
│                          ┌────────▼────────┐         │
│                          │  Repositories   │         │
│                          │  (Spring JPA)   │         │
│                          └────────┬────────┘         │
└───────────────────────────────────┼─────────────────┘
                                    ▼
                           PostgreSQL 15
                           (Flyway V1–V6)

External:
  GitHub API       ←→  GitHubService (WebClient)
  GitHub OAuth     ←→  AuthController /api/auth/**
  GitHub Webhooks  ←→  WebhookController /api/webhooks/**
  Anthropic Claude ←→  AiService
  Slack            ←→  WebhookController (outbound)
```

### Core Data Flow — Repo Scan → Graph

```
User connects repo
      │
      ▼
RepoController.connectRepo()
      │ @Async QUICK scan
      ▼
RepoScannerService.scanRepo()
      ├── GitHubService.getRepoTree()     → all file paths
      ├── priority sort (controllers first)
      ├── for each file:
      │     EndpointExtractorService      → API_ENDPOINT rows
      │     FrontendCallDetectorService   → ApiCall rows (FRONTEND)
      │     BackendHttpCallDetectorService→ ApiCall rows (BACKEND)
      │     BackendOutboundHostExtractor  → RuntimeWiringFact rows
      │     ImportTracerService           → ComponentImport rows (DEEP)
      │     RuntimeWiringExtractorService → RuntimeWiringFact rows (DEEP)
      │
      └── buildDependencyEdges()
            PathSegmentIndex  ← build O(N) once over all endpoints
            match calls → endpoints             (this repo)
            retroactive re-link UNRESOLVED      (all other repos)
            @CacheEvict("graph", "riskOverview")

User opens Graph tab
      │
      ▼
GraphController.getGraph()
      │ @Cacheable("graph", key=userId)
      ▼
GraphBuilderService.buildGraph()
      ├── REPO nodes
      ├── API_ENDPOINT nodes + DEFINES edges
      ├── cross-repo CALLS edges (aggregated per repo pair)
      ├── IMPORTS edges (INTERNAL / MONOREPO / EXTERNAL)
      └── WIRED edges (RuntimeWiringGraphService)
```

### PR Webhook Flow

```
GitHub PR opened/updated
      │
      ▼  POST /api/webhooks/github
WebhookController
      ├── verify HMAC-SHA256 signature
      ├── get changed files from PR diff
      ├── ImpactAnalysisService.analyzeChangedFiles()
      │     computeRisk(repos, files, endpoints)
      │     score = repos×2 + files×0.3 + endpoints×0.5
      ├── POST comment to GitHub PR     ← always
      └── POST Slack alert              ← BLOCKED or REVIEW REQUIRED only
```

---

## 2. Low-Level Design

### 2.1 Service Dependency Map

```
RepoScannerService
  ├── EndpointExtractorService
  │     inner parsers: SpringParser, NodeParser, PythonParser, RubyParser, GoParser
  │     SpringParser: extracts @GetMapping/@PostMapping/@RequestMapping etc.
  │     NodeParser:   Express app.get/post/router.get/post
  │     PythonParser: FastAPI @app.get/post, Flask @app.route
  │     (Tree-sitter ready — swap parser without touching dispatch)
  ├── BackendHttpCallDetectorService
  │     Java:   WebClient.uri("…"), RestTemplate.get/postForObject/ForEntity("…"), URI.create("…")
  │     Node.js: axios.get/post, axios({url}), got(), fetch(abs-url), superagent, needle
  │     Python:  requests/httpx/session/client.get/post/…("…") — including f"…" f-strings
  │              e.g. client.post(f"{REGISTRY}/register") is detected
  ├── BackendOutboundHostExtractor
  │     Java:   http://service-name:port patterns (not localhost, not public TLDs)
  │             @FeignClient("user-service") and @FeignClient(name="…")
  │     Config: http://hostname:port patterns in yml/properties/.env files
  ├── ImportTracerService
  │     classifies: INTERNAL (./xx) | MONOREPO (@scope/xx) | EXTERNAL
  ├── RuntimeWiringExtractorService
  │     application.yml/.properties:  APP_NAME, SERVER_PORT, GATEWAY_ROUTE (lb://, http://host:port), EUREKA_REGISTRY
  │     vite.config.ts/.js:           VITE_PROXY (target: url, keyed by proxy path)
  │     .env files:                   REGISTRY_URL, PUBLIC_URL, GATEWAY_URL/VITE_API/API_GATEWAY
  │     server.js / gateway.js / index.js / app.js (Node.js entry files):
  │                                   GATEWAY_ROUTE from routing map objects
  │                                   e.g. { bookings: "booking-service" } → GATEWAY_ROUTE /api/bookings/** → booking-service
  ├── PathSegmentIndex          ← built once per buildDependencyEdges()
  ├── buildDependencyEdges()    ← self-match exclusion: caller's own endpoints are never selected
  ├── @Async                    ← non-blocking, Spring TaskExecutor
  └── @CacheEvict("graph")      ← invalidates cache on scan complete

GraphBuilderService
  ├── @Cacheable("graph", key=userId)
  ├── @Transactional(readOnly=true)
  └── aggregates: repos + endpoints + calls + imports + wiredFacts → GraphDto

ImpactAnalysisService
  └── RiskResult record: score, label (HIGH/MEDIUM/LOW), verdict
        score = min(repos×2, 6.0) + min(files×0.3, 2.5) + min(eps×0.5, 1.5)

RuntimeWiringGraphService
  └── builds WIRED edges:
        GATEWAY_ROUTE  → gateway → backend-service edge
        VITE_PROXY     → frontend → backend edge (port matching)
        BACKEND_HOST   → backend → backend edge (hostname matching)
```

### 2.2 Interceptor Chain

```
Every /api/** request:

  RequestTrackingInterceptor.preHandle()
    → setAttribute("req.startMs", currentTimeMillis())

  [ controller executes ]

  RequestTrackingInterceptor.afterCompletion()
    → elapsed = now - startMs
    → log INFO/WARN/ERROR by HTTP status
    → setHeader("X-Response-Time-Ms", elapsed)
    → setHeader("X-Api-Version", "1")

Additionally for /api/v1/**:

  RateLimitInterceptor.preHandle()
    → key = "user:{id}" or "ip:{addr}"
    → compute bucket (slide window on expiry)
    → count.incrementAndGet()
    → 429 if count > 300
    → set X-RateLimit-{Limit,Remaining,Reset} headers
```

### 2.3 Error Response Contract

All non-2xx responses from `/api/v1/**`:

```json
{
  "status": 404,
  "code": "NOT_FOUND",
  "message": "Repo with id 42 not found",
  "timestamp": "2026-03-24T10:15:30.123Z"
}
```

| `code` | HTTP | Source |
|---|---|---|
| `BAD_REQUEST` | 400 | `IllegalArgumentException` |
| `MISSING_PARAMETER` | 400 | Missing `@RequestParam` |
| `TYPE_MISMATCH` | 400 | Wrong param type |
| `FORBIDDEN` | 403 | `AccessDeniedException` |
| `NOT_FOUND` | 404 | `ResourceNotFoundException` |
| `ENDPOINT_NOT_FOUND` | 404 | Unknown route |
| `RATE_LIMIT_EXCEEDED` | 429 | `RateLimitInterceptor` |
| `INTERNAL_ERROR` | 500 | Uncaught exception |

### 2.4 Database Schema Summary

```
repos               github_id, user_id, name, scan_status, last_scanned_at
api_endpoints       repo_id, path, http_method, file_path, line_number, framework
api_calls           caller_repo_id, endpoint_id (nullable FK), url_pattern,
                    target_kind (INTERNAL | EXTERNAL | UNRESOLVED)
component_imports   source_repo_id, target_repo_id (nullable), import_path,
                    import_type (INTERNAL | MONOREPO | EXTERNAL), resolved_file
runtime_wiring_facts repo_id, fact_type, fact_key, fact_value, source_file
                    fact_type: APP_NAME | SERVER_PORT | GATEWAY_ROUTE |
                               VITE_PROXY | BACKEND_HOST | EUREKA_REGISTRY
dependency_edges    source_id, source_type, target_id, target_type,
                    edge_type (CALLS | IMPORTS | WIRED | DEFINES)
```

Migrations managed by Flyway: `V1__init.sql` → `V9__repos_last_scanned_commit.sql`

| Migration | Contents |
|-----------|----------|
| V1 | Core schema: users, repos, api_endpoints, api_calls, dependency_edges |
| V2 | AI, orgs, snapshots, audit logs, API keys |
| V3 | component_imports: import_type, resolved_file; dependency_edges indexes |
| V4 | runtime_wiring_facts table |
| V5 | pr_analyses, pr_analysis_runs |
| V6 | api_calls: normalized_pattern, target_kind, external_host columns |
| V7 | runtime_wiring_warnings table |
| V8 | github_etag_cache table (ETag-based conditional GitHub fetches) |
| V9 | repos: last_scanned_commit_sha column (incremental scan support) |

---

## 3. Data Structures & Algorithms

### 3.1 PathSegmentIndex — Endpoint Matching

**File:** `com.architect.service.PathSegmentIndex`

#### Problem

Matching HTTP calls to defined endpoints requires normalising both sides (`{id}` → `*`) and comparing segment by segment. Naïve approach: for every unresolved call, scan all N endpoints.

**Complexity:** O(U × N) — for 2 000 calls × 500 endpoints = **1 000 000 comparisons** per re-link.

#### Data structure: two-level HashMap

```
Level 1 — exact paths (no path variables)
  HashMap<String, ApiEndpoint>   — O(1) lookup
  Key: normalised path, e.g. "/api/users"
  Covers ~60% of calls (most frontend calls hit fixed paths)

Level 2 — parametrised paths (contain {id}, {name} etc.)
  HashMap<Integer, List<ApiEndpoint>>   — key = segment depth
  Covers GET /api/users/{id} under key=3, GET /api/orders/{id}/items under key=4
  For a query at depth k: only E_k candidates, not all N
```

#### Algorithm

```
Build (O(N)):
  for ep in endpoints:
    norm = replace {vars} with *; collapse //
    if no * → exactIndex.put(norm, ep)
    else    → wildcardIndex[countSegments(norm)].add(ep)

Query findCandidates(callPath):
  norm = normalise(callPath); strip ?query

  // Leading-wildcard stripping — MUST run before Level 3
  // Handles env-var base URLs: ${REGISTRY}/services → /*/services
  //   Python f-string:         {REGISTRY}/register  → /*/register
  // Strip leading * segments first so /*/services finds /services, not
  // a same-depth endpoint like /gateway/services via Level 3.
  if norm starts with "/*":
    stripped = strip leading wildcard segments (e.g. /*/services → /services)
    if stripped ≠ norm and stripped ≠ "/":
      results = findCandidatesForNorm(stripped)
      if results not empty → return results (early exit)

  return findCandidatesForNorm(norm)

findCandidatesForNorm(norm):
  Level 1: exact = exactIndex.get(norm)       → O(1)
  Level 2: k = countSegments(norm)
           for ep in wildcardIndex.get(k):    → O(E_k)
             if segmentsMatch(norm, ep): add ep
  Level 3: if results empty AND norm contains '*':
             // call has wildcard but endpoint is exact (e.g. BASE_URL + static path)
             for (path, ep) in exactIndex where countSegments(path)==k:
               if segmentsMatch(norm, path): add ep
  return results
```

**Self-match exclusion in `buildDependencyEdges`:**
Both step 1 (this repo's calls) and step 2 (retroactive re-link) skip any match where
`matched.getRepo().getId() == callerRepo.getId()`. This prevents a service from being
wired to its own endpoints when the true target repo is not yet scanned — the call stays
UNRESOLVED and is re-linked once the target is scanned.

#### Improvement

| Scenario | Old | New |
|---|---|---|
| Single match (exact) | O(N) | O(1) |
| Single match (wildcard) | O(N) | O(E_k) ≈ O(N/k) |
| Full re-link (U calls) | O(U×N) | O(N) + O(U×E_k) |
| Measured improvement | — | ~80% fewer comparisons |

### 3.2 Rate Limiter — ConcurrentHashMap + AtomicInteger

**File:** `com.architect.interceptor.RateLimitInterceptor`

```
Structure:  ConcurrentHashMap<String, Bucket>
  Bucket = { windowStart: long, count: AtomicInteger }

Per request (O(1)):
  buckets.compute(key):
    if absent OR window expired → new Bucket(now)   ← slide window
    else → return existing bucket
  bucket.count.incrementAndGet()                    ← CAS, lock-free
  if count > LIMIT → 429
```

**Why `ConcurrentHashMap`:**
Segment-level locking (not global). Each user's bucket is only contended by that user's concurrent requests. O(1) amortised get/compute.

**Why `AtomicInteger`:**
Compare-And-Swap increment — no `synchronized` block, no thread waiting. Under burst load, multiple threads increment the same counter without blocking each other.

### 3.3 Graph Edge Aggregation — LinkedHashMap + LinkedHashSet

**File:** `com.architect.service.GraphBuilderService`

```
crossRepoEndpoints: LinkedHashMap<String, LinkedHashSet<String>>
  key:   "fromRepoId->toRepoId"
  value: set of endpoint labels (deduplicated, insertion-ordered)

Purpose: 100 ApiCall rows between repo A and B calling 3 distinct
endpoints → 1 graph edge with data.endpoints = [3 labels]

Why LinkedHashMap:  deterministic iteration → stable graph output
Why LinkedHashSet:  O(1) add/contains + automatic deduplication +
                    insertion order preserved for display
```

### 3.4 SSE Emitter Registry — ConcurrentHashMap + CopyOnWriteArrayList

**File:** `com.architect.service.ScanProgressService`

```
emitters: ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>>
  key:   repoId
  value: list of active SSE subscribers for that repo's scan

emit():
  for emitter in emitters.get(repoId):    ← iterates snapshot copy
    emitter.send(event)

subscribe():
  emitters.get(repoId).add(newEmitter)    ← appends to live list

Why CopyOnWriteArrayList:
  Iteration in emit() reads a snapshot. subscribe() can add to the
  live list concurrently without ConcurrentModificationException.
  Scans are write-rarely (subscription list < 10 elements per repo),
  read-often (broadcast every 10 files scanned).
```

### 3.5 ApiCallUrlNormalizer — Internal vs External Classification

**File:** `com.architect.service.ApiCallUrlNormalizer`

**Problem (pre-fix):** `isExternalAbsoluteUrl()` returned `true` for every `http://` URL.
Node.js services calling each other via `http://catalog-service:3001/api/properties`
were permanently marked `EXTERNAL` and never matched against known endpoints.
This caused every backend-to-backend connection to be invisible in the graph.

**Fix — decision tree:**

```
isExternalAbsoluteUrl(url):
  not http/https → false  (relative path = internal)
  extract host:
    no host found       → false
    localhost / 127.x   → false  (dev local)
    raw IPv4 address    → false  (private network 10.x/172.x/192.168.x)
    NO dots in host     → false  ← KEY: "catalog-service", "user-api" have no dots
                                    Docker/K8s resolves these inside the cluster
    has a public TLD    → true   (api.stripe.com, graph.facebook.com)

normalizeForMatching(url):
  if http/https → URI.create(url).getPath()  ← strip host before matching
  strip query string
  ${expr} / $var / {pathVar} → *
  collapse slashes
```

**Result:** `http://catalog-service:3001/api/properties`
- `isExternalAbsoluteUrl` → `false` (no dots in `catalog-service`)
- `normalizeForMatching` → `/api/properties`
- `PathSegmentIndex.findCandidates("/api/properties")` → matches `GET /api/properties`
  in `catalog-service` → cross-repo CALLS edge created ✓

### 3.6 Import Classification — O(1) Set Lookup

**File:** `com.architect.service.ImportTracerService`

```
knownRepoNames: Set<String>   (built once per scan from repoRepository)

classifyImport(path):
  if path.startsWith("./") or "../"  → INTERNAL
  if path.startsWith("@"):
    scope = extractScope(path)        // "@graphite/ui" → "graphite"
    if knownRepoNames.contains(scope) → MONOREPO
  return EXTERNAL

Why Set: O(1) contains vs O(N) list scan.
Built once per RepoScannerService.scanRepo() then reused for every
import in that repo's file tree (~1 000s of imports per scan).
```

---

## 4. Request Lifecycle

```
POST /api/v1/repos/connect

 1. CorsFilter               validates Origin header
 2. JwtAuthenticationFilter  decodes Bearer JWT → sets SecurityContext
 3. Spring Security          enforces .anyRequest().authenticated()
 4. RequestTrackingInterceptor.preHandle()   records startMs
 5. RateLimitInterceptor.preHandle()         checks 300/min bucket
 6. RepoController.connectRepo()
      repoRepository.save(repo)
      repoScannerService.scanRepo(repo, QUICK)   ← @Async, returns immediately
      return RepoDto { scanStatus: "SCANNING" }
 7. RequestTrackingInterceptor.afterCompletion()
      log "POST /api/v1/repos/connect 200 12ms"
      response header X-Response-Time-Ms: 12
      response header X-Api-Version: 1

Async thread (TaskExecutor):
 8. RepoScannerService.scanRepo()
      GitHub API → file tree, file contents
      DB writes (endpoints, calls, imports, wiring facts)
      PathSegmentIndex.build() → match calls to endpoints
      @CacheEvict("graph", userId)
      ScanProgressService.emit("complete", …)

Browser EventSource /api/v1/scan/{repoId}/stream:
 9. Receives SSE events: start → files_found → endpoint_found → complete
```

---

## 5. API Versioning Strategy

### Why URL prefix versioning

| Strategy | Example | Decision |
|---|---|---|
| **URL prefix** | `/api/v1/repos` | **Chosen** — explicit, cacheable, easy to route |
| Header | `Accept: application/vnd.architect.v1+json` | Invisible in browser, poor CDN cache keys |
| Query param | `/api/repos?version=1` | Pollutes query strings |

### Path layout

```
/api/auth/**        Unversioned — GitHub OAuth callback URL (external contract)
/api/webhooks/**    Unversioned — GitHub webhook delivery (external contract)
/api/public/**      Unversioned — health + version (no auth needed)
/api/v1/**          Versioned   — all authenticated API surface
```

`/api/auth/**` and `/api/webhooks/**` are registered with external systems (GitHub App settings, repo webhook configuration). Changing them requires updating every user's GitHub configuration — they are frozen.

### Frontend

```typescript
// Two axios instances:
const api       = axios.create({ baseURL: '/api/v1' })   // versioned
const authAxios = axios.create({ baseURL: '/api' })       // OAuth only

// Same JWT interceptor attached to both.
// authAxios is only used for GET /auth/me and the OAuth redirect.
```

---

## 6. Caching Strategy

### Caffeine in-process cache

```
Cache         TTL      Max    Eviction trigger
─────────────────────────────────────────────────────────────
graph         5 min    50     @CacheEvict on scanRepo() + relinkAllRepos()
riskOverview  2 min    50     @CacheEvict on scanRepo()
insights      10 min   50     TTL only (metrics change slowly)
```

**Key:** `userId` (Long) — each user sees their own graph.

**Why Caffeine over Redis:**

| Factor | Caffeine | Redis |
|---|---|---|
| Latency | ~100 ns (heap) | ~1 ms (network) |
| Infrastructure | None | Requires Redis instance |
| Serialisation | None (Java objects) | JSON/binary |
| Fit | Single-instance dev tool | Multi-instance / distributed |

Architect is a single-instance application targeting small engineering teams. Caffeine is the correct choice. When horizontal scaling is required, replace `CacheConfig` with a Redis-backed `CacheManager` — the `@Cacheable` / `@CacheEvict` annotations on service methods do not change.
