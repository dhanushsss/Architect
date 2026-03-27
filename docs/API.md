# API overview

Base URL: `http://localhost:8080`

Authenticated app routes use header: `Authorization: Bearer <jwt>`.

Versioned app API lives under **`/api/v1`**.

## What this API supports

- Repo onboarding and scanning
- Graph and impact analysis
- PR webhook-driven risk analysis
- Optional AI explanation and enterprise/governance endpoints

## Public

| Method | Path | Notes |
|--------|------|--------|
| GET | `/api/auth/github` | Start OAuth |
| GET | `/api/auth/callback` | OAuth return |
| POST | `/api/webhooks/github` | GitHub webhooks |
| GET | `/api/public/version` | Product name + version |
| GET | `/api/public/v1/health` | Liveness |
| GET | `/api/public/v1/graph/{owner}` | Graph for key owner (`X-API-Key`) |

## Authenticated (JWT)

| Area | Prefix | Examples |
|------|--------|----------|
| User | `/api/auth` | `GET /me` |
| Repos | `/api/v1/repos` | list, connect, disconnect, relink, rescan |
| Scans | `/api/v1/scan` | trigger scan, status, SSE stream |
| Graph | `/api/v1/graph` | full graph, wiring warnings, tree, trace |
| Insights | `/api/v1/insights` | summary metrics |
| Impact | `/api/v1/impact` | endpoint/repo impact, overview |
| AI | `/api/v1/ai` | anomalies, tech-debt, streaming query/chat/docs |
| Enterprise | `/api/v1/enterprise` | governance, snapshots, orgs |
| Keys | `/api/v1/keys` | API key CRUD |
| Dashboard | `/api/v1/dashboard` | product config, risky PRs |

Rescan and scan triggers return **`QUEUED`** while work is scheduled on the DB-backed scan queue.
