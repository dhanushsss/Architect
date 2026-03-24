# Architect — product roadmap (build plan)

**Loop:** PR → Comment → Value → Trust → Dependence → Expansion

---

## Phase 1 — CORE *(first real users)*

| Deliverable | Status |
|-------------|--------|
| PR webhook engine | Done — `POST /api/webhooks/github` |
| Targeted scan (changed files @ PR head) | Done — `PRAnalysisService` |
| Accurate impact detection | Done — graph + stricter path matching |
| Perfect PR comments (5 scenarios) | Done — `PrCommentFormatter` |
| Dashboard: webhook URL + risky PRs list | Done |
| **De-emphasize** graph / AI chat / governance / API keys | `UI_CORE_ONLY=true` hides secondary nav |

**Env:** `APP_PUBLIC_URL` = your API base (shown as webhook URL).

---

## Phase 2 — TRUST *(reliable)*

| Deliverable | Status |
|-------------|--------|
| Accuracy — stricter endpoint path matching | Done — min 8-char suffix / equality |
| External vs internal | Done — explained in **risk factors** (connected repos only) |
| Confidence score (0–100) | Done — freshness + coverage |
| **“Why exactly is this risky?”** | Done — bullet list + confidence in **every** PR comment |

---

## Phase 3 — STICKINESS *(workflow)*

| Deliverable | Status |
|-------------|--------|
| GitHub commit status (optional block) | Done — `PR_POST_COMMIT_STATUS`, `PR_FAIL_ON_REVIEW` |
| Slack alerts | Done — non–SAFE TO MERGE |
| **Top risky PRs this week** | Done — `GET /api/dashboard/risky-prs-week` + dashboard widget |
| Persist every PR run | Done — `pr_analysis_runs` (Flyway V4) |

---

## Phase 4 — EXPANSION *(after traction)*

| Deliverable | Status |
|-------------|--------|
| CI/CD — trigger rescan from Actions | Doc — [`ci/github-actions-rescan.yml`](ci/github-actions-rescan.yml) |
| Incremental / path scan | Use `POST /api/scan/{id}?mode=quick` or full scan on main merge |
| Architecture insights (no graph-first) | Done — `GET /api/insights/summary` |
| AI **explanation** (not chat) | Done — `POST /api/ai/explain-pr-risk` + Insights page demo |

---

## Configuration cheat sheet

| Variable | Phase |
|----------|-------|
| `UI_CORE_ONLY=true` | 1 — minimal nav |
| `APP_PUBLIC_URL` | 1 — webhook URL in UI |
| `PR_POST_COMMIT_STATUS=true` | 3 — merge gate |
| `PR_FAIL_ON_REVIEW=true` | 3 — fail on REVIEW REQUIRED |
| `SLACK_WEBHOOK_URL` | 3 |
| `ANTHROPIC_API_KEY` | 4 — explain-pr-risk narrative |

---

## Documents

| File | Purpose |
|------|---------|
| [`PR_ENGINE.md`](PR_ENGINE.md) | Webhook flow, scenarios, performance |
| [`ci/github-actions-rescan.yml`](ci/github-actions-rescan.yml) | Rescan after merge |
| [`README.md`](../README.md) | Setup + API |
