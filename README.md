# Zerqis

## What problem we solve

Teams run many services and repos. **Who calls which API?** **What breaks if this PR merges?** That knowledge is usually tribal, spread across code review and production incidents. Zerqis **maps real dependencies from code** (not just docs) so you can see cross-repo impact **before** merge.

## What we do

1. **Connect** GitHub repos and **scan** source for API endpoints, outbound HTTP calls, component imports, and some runtime wiring hints.
2. **Store** a dependency **graph** in PostgreSQL and expose it in a **UI** (graph, trees, traces, impact views).
3. **Assess PR risk** via a **GitHub webhook**: changed files → targeted analysis → **comment + score** on the PR (core logic is deterministic; optional **AI** adds a short human explanation only).
4. **Operate safely at scale**: scans go through a **DB-backed queue** with limits (PR-typed jobs prioritized over manual scans), multi-instance friendly coordination without Redis/Kafka.
5. **Calibrate trust over time**: every PR analysis logs prediction signals (risk, confidence, match quality, unknowns) to support future quality tuning.

## How we solve it (at a glance)

| Piece | Approach |
|--------|-----------|
| Discovery | Tree-sitter / language-specific extractors over repo files (quick vs deep scan modes). |
| Graph | Repos, endpoints, `CALLS` / `IMPORTS` / config edges; cross-repo matching of URLs to endpoints. |
| Impact | Query graph for callers and affected repos/files; confidence reflects stale scans, unresolved calls, etc. |
| PR engine | Uses **last full scan** + **PR-head** extraction for changed paths only (capped file count). |
| Auth | GitHub OAuth → JWT for the app; **API keys** only for the public graph endpoint. |

## What we support

- **Languages (typical coverage):** Java (e.g. Spring), JavaScript/TypeScript (e.g. Express-style), Python (e.g. Flask/FastAPI), Ruby, Go — plus frontend **fetch/axios**-style calls where detected.
- **Integrations:** GitHub (OAuth, repo connect, **webhook** for PRs, optional **commit status**, **Slack** alerts).
- **AI (optional):** Anthropic — NL query/chat, docs generation, anomalies/tech-debt, and **optional PR “AI Insight”** text (does not change verdict or score).
- **Product extras:** Impact per endpoint/repo, dashboard hints, **governance** / snapshots / org-style enterprise APIs, **public graph API** with keys.

Stack: **Java 21**, **Spring Boot 3.2**, **PostgreSQL**, **Flyway**, **React (Vite)**.

## End-to-end: how Zerqis works in production

1. **Ingest**
   - User connects repos with GitHub OAuth.
   - Zerqis runs deep/incremental scans and builds endpoint/call/import graph data.
2. **Keep graph fresh**
   - Scan requests are enqueued in `scan_tasks`.
   - Workers claim jobs with DB locking (`FOR UPDATE SKIP LOCKED`) and enforce global/per-user concurrency.
3. **Analyze PR**
   - GitHub webhook (`pull_request opened/synchronize`) triggers async PR analysis.
   - Zerqis fetches changed files, extracts endpoints at PR head, and joins with the stored graph.
4. **Compute risk**
   - Deterministic logic computes verdict (`LOW` / `REVIEW REQUIRED` / `BLOCKED`) and confidence.
   - Confidence is shown with breakdown signals (direct/inferred/unknown/stale) for explainability.
5. **Explain and publish**
   - Zerqis posts a PR comment with scenario, impact, confidence breakdown, and optional AI insight.
   - Optional commit status and Slack notifications are sent based on config.
6. **Log prediction**
   - Zerqis stores each PR prediction in `pr_predictions` for later calibration and accuracy tracking.

## Prerequisites

- Java 21, Maven, Node 18+, PostgreSQL 14+
- GitHub OAuth app (callback: `http://localhost:8080/api/auth/callback`)

## Run locally

1. **Database** — create DB/user (defaults match `application.yml`):

   ```bash
   createdb zerqis
   ```

2. **Backend** (`http://localhost:8080`):

   ```bash
   cd backend && mvn spring-boot:run
   ```

3. **Frontend** (`http://localhost:3000`):

   ```bash
   cd frontend && npm i && npm run dev
   ```

Set at least: `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, `JWT_SECRET`, `DB_URL` / `DB_USERNAME` / `DB_PASSWORD`. See [docs/SETUP.md](docs/SETUP.md).

## Docker

```bash
docker compose up --build
```

Requires the same GitHub (and optional Anthropic/Slack) variables in `.env`.

## Documentation

| Doc | Contents |
|-----|----------|
| [docs/SETUP.md](docs/SETUP.md) | Environment variables & OAuth |
| [docs/API.md](docs/API.md) | HTTP API overview |
| [docs/PR_ENGINE.md](docs/PR_ENGINE.md) | PR webhook, comments, flags |

## Auth

- **JWT** — most `/api/**` routes (from GitHub login).
- **Public** — `/api/auth/**`, `/api/webhooks/**`, `/api/public/**`, `/actuator/health`.
- **API keys** — only `GET /api/public/v1/graph/{owner}` via `X-API-Key`.

Product version is exposed at `GET /api/public/version`.
