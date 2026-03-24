# Architect — Cross-Repo Dependency Mapper

**Architect** helps engineering teams see how repos depend on each other and what breaks when APIs or shared code change. You connect GitHub repos, scan their code (endpoints, API calls, imports, config), and get an interactive dependency graph, impact and risk analysis, optional AI explanations, and enterprise governance—with GitHub and Slack integration and a public API for tooling.

**Quick start:** PostgreSQL running → `cd backend && ./mvnw spring-boot:run` → `cd frontend && npm i && npm run dev` → open http://localhost:3000 and log in with GitHub (set `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `JWT_SECRET`, `DB_*` in env).

---

## Table of contents

- [What the Product Does](#what-the-product-does)
- [Prerequisites](#prerequisites)
- [Security](#security-who-can-call-what)
- [Setup & Run](#setup--run)
- [How to Test It](#how-to-test-it)
- [Frontend routes](#frontend-routes)
- [API Reference](#api-reference)
- [Application Flow](#application-flow-high-level)
- [Project Layout](#project-layout-concise)
- [Summary](#summary)
- [Scan modes (quick vs deep)](#scan-modes-quick-vs-deep)
- [Frontend vs backend API usage](#frontend-vs-backend-api-usage)
- [Real-time PR engine](#real-time-pr-engine)
- [Product roadmap (phases 1–4)](docs/ROADMAP.md)
- [Versioning & releases](docs/VERSIONING.md) — **current: 1.1.0**
- [E2E & testing analysis](docs/E2E_AND_TESTING.md) — manual flow vs automated gaps
- [API call detection → CALL edges](docs/API_CALL_DETECTION.md) — cross-repo graph + EXTERNAL
- [Runtime URL wiring](docs/RUNTIME_WIRING.md) — gateway, Vite proxy, Eureka (no Git coupling)

---

## What the Product Does

**In one sentence:** Architect maps dependencies across your GitHub repos so you can see who is affected by API or code changes, assess risk before merging, and explain your architecture with AI.

| Area | What it does |
|------|----------------|
| **Discovery** | Connect GitHub repos (OAuth). **Deep scan:** API endpoints, frontend API calls, **component imports** (classified INTERNAL / MONOREPO / EXTERNAL, with `resolvedFile` where applicable), **config** references, then cross-repo resolution. **Quick scan:** endpoints + API calls only; skips config-style files (`.yaml`, `.yml`, `.xml`, `.properties`, `.json`) and does **not** collect imports/config or build **IMPORTS** edges. Priority order: controllers, routers, API files first. **SSE** progress stream during scan. |
| **Graph & trace** | Interactive dependency graph (repos, API endpoints, edges: defines / calls / imports). **Package tree** per repo (component imports grouped by package). **Import trace** per file: what a file imports and where those components are used. |
| **Impact & risk** | **Per endpoint/repo** and **dashboard** overview. **Real-time PR engine:** on `pull_request` (opened/updated), **async** pipeline — list changed files → **targeted scan** (PR head content only, capped file count) → extract APIs → match **existing graph** → aggregate cross-repo callers → **PR comment** + optional **GitHub commit status** (merge block) + **Slack**. See [Real-time PR engine](#real-time-pr-engine) and [`docs/PR_ENGINE.md`](docs/PR_ENGINE.md). |
| **AI (Anthropic)** | **Query** (NL over graph), **chat**, **docs** per repo, **onboard** trace—all **SSE**. **Anomalies**, **tech-debt**, **history**. If `ANTHROPIC_API_KEY` is unset, streaming endpoints return **demo/mock** content; GET endpoints still run. |
| **Enterprise** | **Organizations** and members. **Governance dashboard:** endpoint health (active / orphaned / deprecated), counts. **SOC2-style audit** report. **Dependency snapshots** and **diff** between snapshots for change tracking. |
| **Auth & platform** | **GitHub OAuth** + **JWT** (`Authorization: Bearer`) for almost all `/api/*` routes. **API keys** are **not** accepted on those routes—only **JWT**. Keys unlock **`GET /api/public/v1/graph/{owner}`** via **`X-API-Key`**. Keys are created with **JWT** on `/api/keys`. Each key stores `rateLimitPerHour` (default 100). Integrations: **GitHub** (repos, webhook), **Slack** (risk alerts). |

**Tech stack**

- **Backend:** Java 21, Spring Boot 3.2, PostgreSQL, Flyway, JWT, WebFlux (GitHub API), Anthropic SDK, Bucket4j (dependency present).
- **Frontend:** React 18, Vite, TypeScript, TanStack Query, Zustand, React Router, @xyflow/react, Tailwind, Axios.

---

## Prerequisites

- **Java 21**
- **Node.js** (v18+)
- **PostgreSQL** (e.g. 14+)
- **GitHub OAuth app** (client ID + secret)
- **Optional:** Anthropic API key (for AI features), Slack webhook (for PR risk alerts)

---

## Security (who can call what)

| Path pattern | Access |
|--------------|--------|
| `/api/auth/**`, `/api/webhooks/**`, `/api/public/**`, `/actuator/health` | **Public** (no auth) |
| All other `/api/*` | **JWT required** (Bearer token from GitHub login) |

- **Public API** (`/api/public/v1/*`): **`X-API-Key`** for `GET /graph/{owner}`; **`/health`** is open. API keys do **not** authenticate the rest of `/api/*`—use **JWT** from GitHub login there.

---

## Setup & Run

### 1. Database

Create a database and user:

```bash
createdb architect
# Or in psql: CREATE USER architect WITH PASSWORD 'architect'; CREATE DATABASE architect OWNER architect;
```

### 2. Backend

```bash
cd backend
# Set env or use .env at project root (see below)
export DB_URL=jdbc:postgresql://localhost:5432/architect
export DB_USERNAME=architect
export DB_PASSWORD=architect
export GITHUB_CLIENT_ID=your_github_client_id
export GITHUB_CLIENT_SECRET=your_github_client_secret
export GITHUB_REDIRECT_URI=http://localhost:8080/api/auth/callback
export JWT_SECRET=your-256-bit-secret
export FRONTEND_URL=http://localhost:3000

./mvnw spring-boot:run
# Or: mvn spring-boot:run
```

Backend runs at **http://localhost:8080**. Flyway runs on startup: **`V1__init.sql`** (core schema), **`V2__ai_enterprise.sql`** (AI, orgs, snapshots, audit, API keys), **`V3__component_enrichment.sql`** (`component_imports.import_type`, `resolved_file`; `dependency_edges` import metadata + indexes).

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend runs at **http://localhost:3000** and proxies `/api` to `http://localhost:8080`.

### 4. Environment (optional .env at project root)

You can use a `.env` in the project root; the backend reads:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` — PostgreSQL
- `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `GITHUB_REDIRECT_URI` — OAuth
- `JWT_SECRET`, `JWT_EXPIRATION_MS` — JWT
- `FRONTEND_URL` — where the UI lives (for OAuth redirect)
- `ANTHROPIC_API_KEY` — AI features
- `SLACK_WEBHOOK_URL` — Slack notifications
- `PR_MAX_FILES_SCAN` — max changed files parsed per PR (default 50)
- `PR_POST_COMMIT_STATUS` — `true` to post GitHub commit status on PR head
- `PR_FAIL_ON_REVIEW` — if status enabled, fail commit on REVIEW REQUIRED too

---

## Real-time PR engine

**Intent:** *On every PR → detect what may break across repos → before merge.*

1. Webhook: `POST /api/webhooks/github` (`pull_request` **opened** / **synchronize**).
2. **Changed files** from GitHub pulls API.
3. **Targeted scan:** fetch each changed source file at **PR head SHA** (not full repo scan); run endpoint extraction; merge with DB endpoints in those paths.
4. **Impact:** cross-repo **callers** from stored graph (`api_calls` → endpoints).
5. **Comment** on PR (simple UX: verdict, risk, repos, call sites).
6. **Optional:** `PR_POST_COMMIT_STATUS=true` → GitHub status on head (`failure` if BLOCKED; optional failure on REVIEW REQUIRED via `PR_FAIL_ON_REVIEW`).
7. Runs **asynchronously** so the webhook returns quickly.

**Requires:** repo connected in Architect + at least one **deep scan** so the graph has endpoints and callers. **Full detail:** [`docs/PR_ENGINE.md`](docs/PR_ENGINE.md).

### Product phases (executed in code)

| Phase | Focus | Key flags / APIs |
|-------|--------|------------------|
| **1 Core** | PR value first | `UI_CORE_ONLY=true` — hides Graph, AI, Governance, API Keys nav; webhook on Dashboard |
| **2 Trust** | Why + confidence | PR comment **“Why exactly is this risky?”** + **confidence %**; tighter path matching |
| **3 Stickiness** | Workflow | `pr_analysis_runs` table; **`GET /api/dashboard/risky-prs-week`**; Slack; commit status |
| **4 Expansion** | Insights + CI | **`GET /api/insights/summary`**; **`POST /api/ai/explain-pr-risk`**; [`docs/ci/github-actions-rescan.yml`](docs/ci/github-actions-rescan.yml) |

Full plan: **[`docs/ROADMAP.md`](docs/ROADMAP.md)**.

---

## How to Test It

### Manual (UI) flow

1. **Login**  
   - Open http://localhost:3000 → redirects to `/login`.  
   - Click “Login with GitHub” → GitHub OAuth → callback stores JWT in `localStorage` and redirects to app.

2. **Connect repos**  
   - Dashboard: “Connect repos” → lists your GitHub repos → connect one or more.  
   - Repos are stored per user (and optionally per org when using enterprise).

3. **Scan**  
   - From dashboard: default is **deep** (see [Scan modes](#scan-modes-quick-vs-deep)). API also supports `?mode=quick`.  
   - Async scan clears prior repo data, fetches GitHub tree (priority files first), then extracts per mode. Poll `GET /api/scan/{repoId}/status` or **SSE** `GET /api/scan/{repoId}/stream` for events (`start`, `files_found`, `progress`, `endpoint_found`, `complete`, `failed`). Status leaves `SCANNING` when done.

4. **Graph**  
   - Go to **Graph** page.  
   - Loads `/api/graph` (nodes: repos + API endpoints; edges: defines, calls, imports).  
   - Optional: `/api/graph/tree/{repoId}` for a package-grouped component tree; `/api/graph/trace?repoId=&file=` for import trace (what a file imports and where components are used).  
   - Use filters and click nodes to see details and navigate to impact.

5. **Impact**  
   - Dashboard can show a risk overview via `GET /api/impact/overview` (risk cards per repo, sorted by risk).  
   - From graph or links: open impact for an endpoint or repo via `/api/impact/endpoint/:id` or `/api/impact/repo/:id`.

6. **AI (optional)**  
   - **AI** page: anomalies, tech-debt, history; generate docs for a repo.  
   - Requires `ANTHROPIC_API_KEY` configured.

7. **Governance (optional)**  
   - **Governance** page: governance summary, SOC2-style audit report, create/list/diff dependency snapshots, orgs.

8. **API keys (optional)**  
   - Create/revoke with **JWT** on `/api/keys`. Use the raw key only as **`X-API-Key`** on **`GET /api/public/v1/graph/{owner}`** (path segment is ignored; graph is for the key owner).

### Backend / automated tests

```bash
cd backend
./mvnw test   # currently runs 0 tests — see docs/E2E_AND_TESTING.md
```

There are **no** Java tests under `src/test` yet and **no** Playwright/Cypress E2E. The **manual UI flow** above is the practical end-to-end check today.

### API (e.g. curl)

- **Auth:** Browser flow above; then use the JWT from `localStorage` or from callback URL.  
- **With JWT:**
  ```bash
  export TOKEN="<jwt_from_login>"
  curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/auth/me
  curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/repos
  curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/graph
  ```
- **With API key** (public API; use `X-API-Key` header):
  ```bash
  curl -s -H "X-API-Key: <your_api_key>" http://localhost:8080/api/public/v1/graph/me
  curl -s http://localhost:8080/api/public/v1/health
  ```

---

## Frontend routes

| Path | Page | Description |
|------|------|-------------|
| `/login` | LoginPage | Login with GitHub |
| `/auth/callback` | AuthCallbackPage | OAuth callback; stores JWT, redirects to app |
| `/` | DashboardPage | Dashboard; connect repos, trigger scan |
| `/graph` | GraphPage | Dependency graph (repos, endpoints, edges) |
| `/impact/:type/:id` | ImpactPage | Impact for endpoint or repo (`type`: `endpoint` \| `repo`) |
| `/ai` | AiQueryPage | UI: **anomalies** + **tech-debt** tabs. Backend also exposes **query**, **chat**, **docs**, **onboard** (SSE)—not all wired in this UI. |
| `/governance` | GovernancePage | Enterprise: governance, SOC2, snapshots, orgs |
| `/api-keys` | ApiKeysPage | Create and revoke API keys |

All routes except `/login` and `/auth/callback` require a valid JWT in `localStorage` (`architect_token`).

---

## API Reference

All endpoints under `/api` unless noted. **Auth:** JWT = `Authorization: Bearer <jwt>` (from login). API key = `X-API-Key: <key>` (public API only).

| Method | Path | Auth | Description |
|--------|------|------|--------------|
| **Auth** | | | |
| GET | `/api/auth/github` | — | Redirect to GitHub OAuth |
| GET | `/api/auth/callback?code=...` | — | OAuth callback; redirects to frontend with `?token=<jwt>` |
| GET | `/api/auth/me` | JWT | Current user (id, login, name, avatarUrl) |
| **Repos** | | | |
| GET | `/api/repos` | JWT | List connected repos |
| GET | `/api/repos/github` | JWT | List user's GitHub repos (to connect) |
| POST | `/api/repos/connect` | JWT | Connect repo (body: githubId, fullName, name, language, defaultBranch, htmlUrl, private, description) |
| DELETE | `/api/repos/{repoId}` | JWT | Disconnect repo |
| **Scan** | | | |
| POST | `/api/scan/{repoId}` | JWT | Start async scan for one repo. Optional `?mode=quick` or `?mode=deep` (default: deep) |
| POST | `/api/scan/all` | JWT | Start async scan for all user repos. Optional `?mode=quick` or `?mode=deep` |
| GET | `/api/scan/{repoId}/stream` | JWT | SSE stream: live scan progress events for the repo |
| GET | `/api/scan/{repoId}/status` | JWT | Scan status + endpoint/call/import counts |
| **Graph & Impact** | | | |
| GET | `/api/graph` | JWT | Full dependency graph (nodes + edges) |
| GET | `/api/graph/tree/{repoId}` | JWT | Package-grouped tree of components for a repo (tree, totalImports, byType) |
| GET | `/api/graph/trace?repoId=&file=` | JWT | Origin trace: imports in a file + where each component is used (imports, usedBy) |
| GET | `/api/impact/endpoint/{endpointId}` | JWT | Impact for an API endpoint (numeric id) |
| GET | `/api/impact/repo/{repoId}` | JWT | Impact for a repo (numeric id) |
| GET | `/api/impact/overview` | JWT | Risk overview: lightweight risk cards for all scanned repos (sorted by risk, for dashboard) |
| **AI** (Anthropic; SSE = streaming) | | | |
| POST | `/api/ai/query` | JWT | NL query over graph (SSE); body: `{ "question": "..." }` |
| POST | `/api/ai/chat` | JWT | Chat (SSE); body: `{ "message": "..." }` |
| POST | `/api/ai/docs/{repoId}` | JWT | Generate architecture docs for repo (SSE) |
| POST | `/api/ai/onboard` | JWT | Onboarding trace (SSE); body: `{ "feature": "auth" }` |
| GET | `/api/ai/anomalies` | JWT | List detected anomalies |
| GET | `/api/ai/tech-debt` | JWT | Tech-debt summary |
| GET | `/api/ai/history` | JWT | AI query history |
| **Enterprise** | | | |
| GET | `/api/enterprise/governance` | JWT | Governance dashboard |
| GET | `/api/enterprise/audit/soc2` | JWT | SOC2-style audit report |
| GET | `/api/enterprise/orgs/{orgId}/audit` | JWT | Org audit logs (?page=0) |
| POST | `/api/enterprise/orgs` | JWT | Create org; body: `{ "name": "..." }` |
| GET | `/api/enterprise/orgs` | JWT | List my orgs |
| GET | `/api/enterprise/orgs/{orgId}/members` | JWT | List org members |
| POST | `/api/enterprise/orgs/{orgId}/members` | JWT | Add member; body: `{ "userId", "role" }` |
| DELETE | `/api/enterprise/orgs/{orgId}/members/{targetUserId}` | JWT | Remove member |
| POST | `/api/enterprise/snapshots` | JWT | Create dependency snapshot; body: `{ "label": "..." }` |
| GET | `/api/enterprise/snapshots` | JWT | List snapshots |
| GET | `/api/enterprise/snapshots/{id}/diff/{compareId}` | JWT | Diff two snapshots |
| **API Keys** | | | |
| GET | `/api/keys` | JWT | List my API keys |
| POST | `/api/keys` | JWT | Create key; body: `{ "name", "scopes" }` (default scopes: `read:graph`) |
| DELETE | `/api/keys/{keyId}` | JWT | Revoke key |
| **Public API** (key in header) | | | |
| GET | `/api/public/version` | — | Product version JSON (`product`, `version`, `publicApi`) |
| GET | `/api/public/v1/health` | — | Health + version |
| GET | `/api/public/v1/graph/{owner}` | X-API-Key | Full graph for the **API key’s user** (`owner` path unused) |
| **Webhooks** | | | |
| POST | `/api/webhooks/github` | — | GitHub webhook (e.g. pull_request → impact analysis, PR comment, Slack) |

---

## Application Flow (High Level)

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. AUTH                                                                      │
│    User → Login with GitHub → OAuth callback → JWT stored → All API calls    │
│    use Bearer JWT. API key only for /api/public/v1/graph.                    │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 2. CONNECT REPOS                                                             │
│    GET /api/repos/github (list GitHub repos)                                 │
│    POST /api/repos/connect (connect repo by githubId, fullName, etc.)        │
│    Repos stored per user (and optionally org_id).                            │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 3. SCAN (async)                                                              │
│    POST /api/scan/{repoId}?mode=quick|deep (default deep). Same for /all.    │
│    SSE: GET /api/scan/{repoId}/stream.                                       │
│    DEEP: all scannable extensions; imports + config; IMPORTS edges + meta. │
│    QUICK: skips .yaml/.yml/.xml/.properties/.json; no imports/config;       │
│    CALLS edges only. Priority: *controller*, *router*, *api*, etc. first.    │
│    Both: clear repo scan data → GitHub tree → endpoints + api_calls →        │
│    buildDependencyEdges (CALLS always; IMPORTS only in DEEP).                 │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 4. GRAPH                                                                     │
│    GET /api/graph — full graph. GET /api/graph/tree/{repoId} — component     │
│    tree per repo. GET /api/graph/trace?repoId=&file= — imports + usedBy.     │
│    GraphBuilderService: nodes (REPO, API_ENDPOINT), edges (DEFINES, CALLS,    │
│    IMPORTS). Frontend: @xyflow/react; filters and node details.               │
└─────────────────────────────────────────────────────────────────────────────┘
                                        │
                                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│ 5. IMPACT                                                                    │
│    GET /api/impact/overview — risk cards for all scanned repos (dashboard).  │
│    GET /api/impact/endpoint/{id} or GET /api/impact/repo/{id} — full impact  │
│    for one endpoint or repo (callers/consumers affected).                    │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ 6. GITHUB WEBHOOK — PR engine (async)                                      │
│    POST /api/webhooks/github → PRAnalysisService:                           │
│    PR files → fetch PR head per changed file (cap) → extract endpoints →    │
│    match graph → impact → PR comment (FRONTEND_URL) → optional commit status │
│    → Slack if risky.                                                         │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│ OTHER FLOWS                                                                  │
│ • AI: GET/POST /api/ai/* (anomalies, tech-debt, history, query/chat/docs/    │
│   onboard via SSE). Requires ANTHROPIC_API_KEY.                             │
│ • Enterprise: /api/enterprise/* (governance, audit, snapshots, orgs, members)│
│ • API Keys: JWT on /api/keys. X-API-Key on public graph only.               │
│ • PR comment links use `FRONTEND_URL` (see PR engine section).               │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## Project Layout (concise)

```
Architect/
├── backend/                    # Spring Boot
│   ├── src/main/java/com/architect/
│   │   ├── controller/         # Auth, Repo, Scan, Graph, Analysis(Impact), Ai, Enterprise, ApiKey, Webhook, PublicApi
│   │   ├── service/            # GitHub, RepoScanner, ScanProgress, GraphBuilder, Impact, Ai, …
│   │   ├── repository/        # JPA repos for all entities
│   │   ├── model/             # User, Repo, ApiEndpoint, ApiCall, ComponentImport, ...
│   │   ├── dto/               # GraphDto, NodeDto, EdgeDto, ScanStatusDto, ImpactDto, ...
│   │   ├── config/            # AppProperties, Security, CORS, WebClient
│   │   └── security/          # JWT filter + token provider
│   └── src/main/resources/
│       ├── application.yml
│       └── db/migration/      # V1__init, V2__ai_enterprise, V3__component_enrichment
├── frontend/
│   ├── src/
│   │   ├── pages/             # Dashboard, Graph, Impact, Login, AuthCallback, Ai, Governance, ApiKeys
│   │   ├── components/        # graph (nodes, edges, filters), panels, layout, ui
│   │   ├── hooks/             # useGraph, useRepos, useImpact
│   │   ├── store/             # graphStore (Zustand)
│   │   ├── services/          # api.ts (auth, repo, scan, graph, impact, ai, enterprise, apiKey)
│   │   └── types/
│   ├── vite.config.ts         # proxy /api → localhost:8080
│   └── package.json
├── .env                       # Local env (DB, GitHub, JWT, etc.)
└── README.md                  # This file
```

---

## Scan modes (quick vs deep)

| | **Quick** | **Deep** (default) |
|---|-----------|-------------------|
| **API endpoints** | Yes | Yes |
| **Frontend API calls** | Yes | Yes |
| **Skips scanning** | `.yaml`, `.yml`, `.xml`, `.properties`, `.json` | No (unless in skip dirs like `node_modules`) |
| **Component imports** | No | Yes (INTERNAL / MONOREPO / EXTERNAL + `resolvedFile`) |
| **Config dependencies** | No | Yes |
| **CALLS edges** (cross-repo) | Yes | Yes |
| **IMPORTS edges** | No | Yes (with metadata on `dependency_edges`) |

---

## Frontend vs backend API usage

The **React app** (`frontend/src/services/api.ts`) calls:

| Client helper | Backend | Notes |
|---------------|---------|--------|
| `authApi`, `repoApi` | `/api/auth/*`, `/api/repos/*` | — |
| `scanApi.triggerScan` / `scanAll` | `POST /scan/{id}`, `POST /scan/all` | **No `?mode=`** → backend default **deep** |
| `scanApi.getStatus` | `GET /scan/{id}/status` | Does not use SSE stream |
| `graphApi` | `/graph`, `/graph/tree/{id}`, `/graph/trace` | — |
| `impactApi` | `/impact/*` including `/overview` | — |
| `aiApi` | GET anomalies, tech-debt, history; POST docs (SSE) | **Not wired in api.ts:** `/ai/query`, `/ai/chat`, `/ai/onboard` (available via curl/UI if added) |
| `enterpriseApi`, `apiKeyApi` | `/enterprise/*`, `/keys` | — |

To use **quick scan** or **SSE** from scripts, call the API directly with `?mode=quick` or open an EventSource to `/api/scan/{repoId}/stream` with JWT.

---

## Summary

- **Product:** Architect maps cross-repo dependencies so teams see who is affected by API or code changes. **Deep** scan: endpoints, calls, imports (typed), config, IMPORTS edges; **quick**: faster, calls + endpoints only. Graph, tree, trace, impact overview, PR webhook + Slack, AI (real or demo without Anthropic key), enterprise, JWT app + X-API-Key public graph.
- **Run:** PostgreSQL + backend on 8080 + frontend on 3000; set GitHub OAuth and JWT (and optionally Anthropic/Slack).
- **Test:** Login via UI → connect repos → scan (optional `?mode=quick|deep`, or subscribe to scan stream) → open Graph and Impact; use impact overview on dashboard; run `mvn test` for backend; call `/api/*` with JWT; use `X-API-Key` for `/api/public/v1/graph/{owner}` and `/api/public/v1/health`.
- **Flow:** Auth → Connect repos → Scan (async; quick/deep, SSE; DEEP resolves imports→repos) → Graph / tree / trace → Impact; optional AI, Enterprise, keys (JWT to manage, X-API-Key for public graph only), GitHub webhook (PR comment link in code uses localhost—override for prod).
