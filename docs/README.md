# Architect — Cross-Repo Dependency Mapper

Architect gives engineering teams an interactive visual map of all dependencies across their GitHub repositories: which frontends call which APIs, which services import which components, and which config files are referenced where.

## What it does

- **Phase 1 — See the Map**: Connect your GitHub repos, run a scan, and instantly see an interactive graph of all API calls, component imports, and config dependencies across your codebase
- **Phase 2 — Feel the Impact**: Click any API endpoint or repo to see which other repos and files would break if you changed it. Get automatic PR comments with risk scores.
- **Phase 3 — AI Understanding Layer**: Natural language queries ("Which repos call the payment API?"), codebase Q&A, developer onboarding traces, anomaly detection, and tech debt radar — powered by Claude AI
- **Phase 4 — Enterprise & Compliance**: Organizations with RBAC, SOC2/audit evidence reports, API governance dashboard, and historical dependency snapshots with diff view
- **Phase 5 — Platform & Ecosystem**: Public API with key management for CI/CD and tooling integrations

## Quick Start (5 minutes)

### Prerequisites
- Docker & Docker Compose
- A GitHub account
- A [GitHub OAuth App](https://github.com/settings/developers)

### 1. Create GitHub OAuth App

1. Go to **GitHub → Settings → Developer settings → OAuth Apps → New OAuth App**
2. Set:
   - **Application name**: `Architect`
   - **Homepage URL**: `http://localhost:3000`
   - **Authorization callback URL**: `http://localhost:8080/api/auth/callback`
3. Copy the **Client ID** and generate a **Client Secret**

### 2. Configure environment

```bash
cp .env.example .env
```

Edit `.env` and fill in your GitHub OAuth credentials:
```
GITHUB_CLIENT_ID=your_client_id
GITHUB_CLIENT_SECRET=your_client_secret
```

### 3. Run with Docker Compose

```bash
docker-compose up --build
```

### 4. Open the app

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080
- API Health: http://localhost:8080/actuator/health

### 5. Start mapping

1. Click **"Continue with GitHub"** to log in
2. Click **"Connect Repos"** → select your repositories → click **Connect**
3. Click **"Scan All"** to analyze all repos
4. Click **"View Graph"** to see your dependency map

---

## Supported Languages & Frameworks

| Language | Endpoint Detection | Frontend Calls | Import Tracing |
|----------|-------------------|----------------|----------------|
| Java | Spring Boot (`@GetMapping`, `@PostMapping`, etc.) | — | Cross-package imports |
| JavaScript/TypeScript | Express, Fastify, Koa | `fetch()`, `axios` | Scoped packages (`@org/pkg`) |
| Python | Flask, FastAPI, Django | — | Non-stdlib imports |
| Ruby | Rails, Sinatra | — | — |
| Go | Gin, Chi, `net/http` | — | — |
| Vue/Svelte | — | `useFetch`, `$http` | — |
| Angular | — | `HttpClient` | — |

---

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   React Frontend │◄──►│  Spring Boot API │◄──►│   PostgreSQL DB  │
│   (React Flow)   │    │  (Port 8080)     │    │   (Port 5432)    │
│   (Port 3000)    │    └────────┬────────┘    └─────────────────┘
└─────────────────┘             │
                                ▼
                     ┌─────────────────┐
                     │  GitHub REST API │
                     │  (File scanning) │
                     └─────────────────┘
```

See [ARCHITECTURE.md](./ARCHITECTURE.md) for the full system design.

---

## Development Setup (without Docker)

### Backend
```bash
cd backend
# Start PostgreSQL (or use Docker: docker run -e POSTGRES_DB=architect -e POSTGRES_USER=architect -e POSTGRES_PASSWORD=architect -p 5432:5432 postgres:16)
mvn spring-boot:run
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

Set environment variables from `.env.example` before starting the backend.

---

## Revenue Model (SaaS)

- **Starter**: ₹5,000/month — up to 10 repos
- **Team**: ₹15,000/month — up to 50 repos + PR integration
- **Enterprise**: ₹50,000/month — unlimited repos + Slack + dedicated support
