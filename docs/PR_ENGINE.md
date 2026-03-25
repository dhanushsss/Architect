# PR engine

## Webhook

- **URL:** `POST /api/webhooks/github`
- **Events:** `pull_request` (`opened`, `synchronize`)
- Optional **HMAC** validation via `GITHUB_WEBHOOK_SECRET`

## Flow

1. Load PR changed files from GitHub.
2. Fetch a **capped** set of file contents at **PR head** and extract endpoints.
3. Join with **existing graph** in PostgreSQL (`api_calls` → endpoints).
4. Build **impact** → **verdict** / numeric score (core logic; unchanged by AI).
5. Post **PR comment** (Markdown). Optional **commit status** and **Slack** if configured.
6. A **full-repo scan** for that repo may be **enqueued** as a **PR-priority** job (see scan queue) so the graph stays fresh.

Analysis runs **asynchronously** after the webhook returns `200`.

## Configuration

| Env / `application.yml` | Default | Description |
|-------------------------|---------|-------------|
| `PR_MAX_FILES_SCAN` | 50 | Max changed files fetched per PR |
| `PR_POST_COMMIT_STATUS` | false | Post GitHub status on PR head |
| `PR_FAIL_ON_REVIEW` | false | If status on: fail on REVIEW REQUIRED, not only BLOCKED |
| `FRONTEND_URL` | http://localhost:3000 | Links in comments |
| `ANTHROPIC_API_KEY` | — | If set and risk is not LOW, comment may include an **AI Insight** section (explanation only; no score change) |
| `PR_AI_RISK_TIMEOUT_MS` | 3000 | Budget for that LLM call |

## Code entry points

- `WebhookController` — webhook HTTP
- `PRAnalysisService` — orchestration
- `ImpactAnalysisService#analyzePullRequestTargeted` — targeted impact
- `PrCommentFormatter` — comment body

**Prerequisite:** connect the repo in Zerqis and run at least one **deep scan** so callers and endpoints exist in the DB.
