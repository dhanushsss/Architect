# Architect — Technology Stack & Dependency Rationale

**Version:** 1.1.0 · **Updated:** 2026-03-24

---

## Backend (Java 21 / Spring Boot 3.2)

### Core Framework

| Dependency | Version | Why |
|---|---|---|
| **Spring Boot 3.2** | 3.2.0 | Convention-over-configuration production runtime. Auto-configures DB connections, security filter chain, async executor, cache, and actuator in ~50 lines of YAML. |
| **Java 21** | LTS | Virtual threads (Project Loom) available if needed. Records (`RiskResult`, `ApiError`) — immutable value objects with zero boilerplate. Pattern matching in switch for language dispatch in `EndpointExtractorService`. |

### Web Layer

| Dependency | Why |
|---|---|
| `spring-boot-starter-web` | Embedded Tomcat, Spring MVC, `HandlerInterceptor` API for `RequestTrackingInterceptor` and `RateLimitInterceptor`. |
| `spring-boot-starter-webflux` | **Not** used for the API layer. Used exclusively for `WebClient` in `GitHubService` — non-blocking HTTP client for GitHub API calls. Using Tomcat + WebClient is valid; the full reactive stack (Reactor/Netty) would be overengineering for this synchronous domain model. |
| **`BackendHttpCallDetectorService`** | Multi-language outbound call detector (Java WebClient/RestTemplate, Node.js axios/got/superagent/node-fetch, Python requests/httpx/aiohttp). Combined with `ApiCallUrlNormalizer.isExternalAbsoluteUrl()` fix (Docker/K8s hostnames with no dots are treated as INTERNAL, not external), this enables cross-repo edge detection between Node.js microservices. |

### Security

| Dependency | Why |
|---|---|
| `spring-boot-starter-security` | Filter chain, `SecurityContext`, `@AuthenticationPrincipal`. Provides `ExceptionTranslationFilter` which maps `AccessDeniedException` → 403. |
| `jjwt-api` / `jjwt-impl` / `jjwt-jackson` (0.12.3) | Stateless JWT authentication. No session state — correct for a SaaS API with GitHub OAuth tokens. JJWT is the de-facto standard in the Spring ecosystem; `jjwt-jackson` provides Jackson-based claim parsing without a separate adapter. |

### Persistence

| Dependency | Why |
|---|---|
| `spring-boot-starter-data-jpa` | Hibernate ORM + Spring Data repositories. `@Transactional(readOnly=true)` on service methods tells Hibernate not to dirty-check entities — measurable performance gain on large graph builds. Avoids N+1 with explicit `JOIN FETCH` or lazy loading inside transactions. |
| `postgresql` (runtime) | PostgreSQL JSONB columns (`metadata`, `details`) hold variable-shape data without schema migrations. Used in `DependencyEdge.metadata`, `AuditLog.details`. |
| `flyway-core` | Schema migration as code. Version-numbered SQL files (`V1__init.sql` → `V6__runtime_wiring_facts.sql`) are reproducible and reviewable in PRs. `ddl-auto: validate` ensures the entity model always matches the DB schema — catches drift at startup, not at runtime. |

### Caching

| Dependency | Why |
|---|---|
| `spring-boot-starter-cache` | Spring's `@Cacheable` / `@CacheEvict` abstraction. The cache provider is swappable (Caffeine today, Redis when scaling) without changing service code. |
| `caffeine` | W-TinyLFU eviction — near-optimal hit rate. O(1) amortised get/put. Zero network latency (heap-only). Right choice for a single-instance application. See `ARCHITECTURE.md §6` for the Caffeine vs Redis decision. |

### Async & Concurrency

| Feature | Why |
|---|---|
| `@Async` (Spring) | GitHub API calls (getRepoTree, getFileContent) are I/O-bound. Running scans on Spring's `TaskExecutor` thread pool frees the request thread immediately — `connectRepo` returns in <50ms while the scan runs for minutes in the background. |
| `ConcurrentHashMap` | Used in `ScanProgressService` (SSE emitter registry) and `RateLimitInterceptor` (token buckets). Segment-level locking — O(1) amortised, no global lock. |
| `AtomicInteger` | Lock-free counter in `RateLimitInterceptor`. CAS increment avoids `synchronized` overhead under concurrent requests from the same user. |
| `CopyOnWriteArrayList` | SSE emitter list in `ScanProgressService`. Iteration (broadcast) reads a snapshot; concurrent subscribe/unsubscribe modifies the live list. No `ConcurrentModificationException`. |

### AI / LLM

| Dependency | Version | Why |
|---|---|---|
| `anthropic-java` | 2.15.0 | Official Anthropic Java SDK. Typed request/response builders, streaming support. Used in `AiService` for anomaly detection, tech-debt analysis, and PR risk explanation via Claude claude-sonnet-4-6. |

### Rate Limiting

| Dependency | Version | Why |
|---|---|---|
| `bucket4j-core` | 8.7.0 | Available for future use if per-endpoint rate limiting with refill strategies is needed. Current `RateLimitInterceptor` implements a simpler sliding-window with `AtomicInteger` — sufficient for the 300/min use case and avoids Bucket4j overhead. |

### Serialisation

| Dependency | Why |
|---|---|
| `jackson-dataformat-yaml` | Parse Spring config YAML in `AppProperties`. Also used to parse `application.yml` files from scanned repos in `RuntimeWiringExtractorService`. |

### Observability

| Dependency | Why |
|---|---|
| `spring-boot-starter-actuator` | `/actuator/health` for load-balancer health checks. `/actuator/info` for version reporting. Caffeine cache stats exposed via `/actuator/metrics` when `recordStats()` is enabled in `CacheConfig`. |

---

## Frontend (React 18 / TypeScript)

### Core

| Package | Version | Why |
|---|---|---|
| **React 18** | 18.x | Concurrent rendering. `useEffect` + `useState` cover all state needs; no need for signals or fine-grained reactivity at this scale. |
| **TypeScript** | 5.x | Strict typing across API response types (`GraphDto`, `ImpactDto`, `Repo`). Catches `undefined` access bugs at compile time — critical when rendering complex graph data. |
| **Vite** | 5.x | Dev server with HMR in <100ms. Built-in proxy (`/api → localhost:8080`) eliminates CORS in development. Production build with Rollup produces optimised chunks. |

### Routing & State

| Package | Why |
|---|---|
| `react-router-dom` v6 | Declarative routes. `<Navigate>` for auth guards. No Redux needed — server state is owned by React Query. |
| `@tanstack/react-query` v5 | Server state cache with `queryKey`-based invalidation. `useMutation` with `onSuccess → qc.invalidateQueries` is the exact pattern needed for POST → GET invalidation after scan/relink. Eliminates manual `useEffect + fetch + loading/error state` boilerplate across 10+ API calls. |

### Graph Rendering

| Package | Why |
|---|---|
| `@xyflow/react` (React Flow) | Declarative node-edge graph renderer. Handles pan/zoom, node selection, minimap, and custom node types. `useNodesState` / `useEdgesState` manage graph state with built-in change handlers. `ReactFlowProvider` exposes `useViewport()` for zoom-level rendering decisions. |
| `@dagrejs/dagre` | Directed-graph auto-layout. `rankdir: 'LR'` produces the left-to-right repo → API → caller hierarchy. Without Dagre, nodes require manual positioning — unusable for dynamic graphs. |

### HTTP Client

| Package | Why |
|---|---|
| `axios` | Interceptors for JWT attachment and 401 → redirect. Two instances (`api` at `/api/v1`, `authAxios` at `/api`) with shared interceptor logic via a `attachToken` function — avoids duplicating the Bearer header logic. Slightly more ergonomic than `fetch` for typed responses and error handling. |

### UI Components

| Package | Why |
|---|---|
| `lucide-react` | Tree-shakeable SVG icon library. Only the icons imported are bundled. Consistent icon style across Dashboard, Navbar, and node components. |
| `tailwindcss` | Utility-first CSS. Zero runtime cost — classes compiled to atomic CSS at build time. Dark theme (`bg-slate-900`, `text-white`) implemented entirely in class names with no CSS files. |

### Real-Time

| API | Why |
|---|---|
| Browser `EventSource` | Native SSE client for scan progress stream. No additional library needed. Auto-reconnects on network drop. Used in `useScanProgress` hook with `repoId`-scoped streams (`/api/v1/scan/{repoId}/stream`). |

---

## Infrastructure & Configuration

| Tool / Format | Why |
|---|---|
| **PostgreSQL 15** | JSONB for variable-shape data (edge metadata, PR analysis results, audit log details). `ON DELETE CASCADE` foreign keys keep data consistent when a repo is disconnected. `BIGSERIAL` primary keys for all tables. |
| **Flyway** | SQL migration files in `src/main/resources/db/migration/`. `validate` DDL mode means the app refuses to start if the entity model doesn't match the DB — eliminates runtime surprises. |
| **application.yml** | `${ENV_VAR:default}` pattern for all secrets. GitHub credentials, JWT secret, Anthropic key are environment variables — never in source. |
| **GitHub OAuth App** | Free tier. Provides `access_token` with `repo` scope for reading private repos. Token stored in `users.access_token` (DB) and sent as `Authorization: token xxx` to GitHub API. |

---

## What We Chose NOT to Use (and Why)

| Technology | Why excluded |
|---|---|
| **Redis** | No horizontal scaling needed at current scale. Caffeine provides the same Spring Cache interface with zero infrastructure overhead. Swap in Redis by replacing `CacheConfig.cacheManager()` bean — no service code changes. |
| **Kafka / RabbitMQ** | Async scan tasks fit Spring `@Async` + SSE. Message queue would add operational complexity (broker, consumers, DLQ) for no benefit on single-instance. |
| **GraphQL** | REST is sufficient. The graph data shape is fixed and returned whole — no N+1 problem because `GraphBuilderService` does one DB query pass per entity type. |
| **Redux / Zustand** | React Query handles all server state. Local UI state (focused node, edge filter, collapsed groups) is `useState` — no global state store needed. |
| **Bucket4j (active rate limiting)** | `bucket4j-core` is in `pom.xml` for future use. Current `RateLimitInterceptor` uses a simpler `AtomicInteger` sliding-window that covers the 300/min use case with less code. |
| **Spring WebFlux (reactive)** | `WebClient` is reactive but used in a synchronous context (`.block()`-equivalent via Mono). Fully reactive would require reactive repositories (R2DBC), reactive security, and reactive interceptors — significant complexity for a domain that is inherently sequential (scan a repo, write to DB, build graph). |
