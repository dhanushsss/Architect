# End-to-end testing — analysis

## What “end-to-end” means here

| Layer | Span | Current state |
|-------|------|----------------|
| **True E2E (browser)** | User → React UI → Vite proxy → Spring → PostgreSQL → GitHub (live) | **Not implemented** (no Playwright/Cypress) |
| **API E2E** | HTTP client → full Spring app → DB (mock/stub GitHub) | **Not implemented** |
| **Unit / slice tests** | Single class or `@WebMvcTest` | **Not implemented** (`backend/src/test` is empty) |
| **Manual E2E** | Human follows README flow | **Documented** in README “How to Test It” |

---

## Documented user journey (manual E2E)

This is the only full product path exercised today:

1. **Login** — `/login` → GitHub OAuth → JWT in `localStorage` → redirect.
2. **Dashboard** — connect repos (GitHub API), trigger scan.
3. **Scan** — async job; SSE or poll status until complete.
4. **Graph** — `GET /api/graph`, React Flow.
5. **Impact** — overview + per-endpoint/repo views.
6. **Insights** — architecture summary (if enabled).
7. **AI** — anomalies, tech-debt (JWT APIs).
8. **Governance** — governance, SOC2, snapshots.
9. **API keys** — create key → optional `GET /api/public/v1/graph/*` with `X-API-Key`.

**PR engine path** (separate E2E): GitHub webhook → `POST /api/webhooks/github` → targeted scan → PR comment (needs repo + webhook secret + test PR).

---

## Gaps and risks

| Gap | Risk |
|-----|------|
| No automated tests | Regressions (e.g. double `/api` URL bugs) only caught manually or in prod. |
| OAuth in the critical path | Browser E2E either needs **test GitHub app + bot user** or **test-only auth bypass** (dev profile). |
| GitHub dependency | Scans and repo list need network + real tokens; CI usually needs **recorded fixtures** or **WireMock** for GitHub API. |
| PostgreSQL | Full stack tests need **Testcontainers** or **H2** (schema drift vs Flyway). |
| SSE / webhooks | Harder to assert in simple HTTP tests; may need dedicated integration tests. |

---

## Recommended direction (prioritized)

### 1. API smoke tests (highest ROI)

- Spring Boot **`@SpringBootTest`** + **Testcontainers PostgreSQL** + Flyway.
- **Mock** `WebClient`/GitHub layer or use `@MockBean` on services that call GitHub.
- **Public routes** (no JWT): `GET /api/public/version`, `GET /api/public/v1/health`.
- **JWT routes**: generate a test JWT with the same secret as test profile, hit `/api/auth/me`, `/api/keys` (empty list), `/api/ai/anomalies` (empty graph → empty list).

This validates security config, routing, and DB wiring without a browser.

### 2. Contract / regression tests for the frontend

- Either **Vitest** + **MSW** mocking `/api/*` (fast, no backend), or
- **Playwright** against `vite preview` + backend on CI with test DB (slower, closer to real E2E).

Minimum Playwright smoke: login page shows version; **if** you add a `?test_token=` or dev-only bypass, dashboard loads.

### 3. Full browser E2E (later)

- Playwright: login (or bypass), navigate Dashboard → Graph, assert no console errors and key API 200s (intercept network).
- Webhook E2E: `curl` signed payload to `/api/webhooks/github` with test fixture JSON (document expected signature).

---

## Commands today

| Command | Effect |
|---------|--------|
| `cd backend && mvn test` | **0 tests** — succeeds but does not validate behavior. |
| `cd frontend && npm run build` | Typecheck + bundle (catches TS errors, not runtime E2E). |

---

## Summary

**End-to-end coverage today = manual checklist in README.** There is no automated E2E or integration test suite. Next practical step: **API smoke tests with Testcontainers + test JWT**, then optional **Playwright** for critical UI paths once auth strategy for CI is decided.
