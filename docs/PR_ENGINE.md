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

---

## How to test PR comments (warnings / “errors”)

Zerqis does **not** use GitHub Checks “errors” by default. You mainly see a **markdown comment** on the PR with a **verdict** and risk text. Optional **commit status** (red X / green ✓ on the latest commit) only appears if you turn it on.

### 1. One-time setup

1. **Connect the same repo** in Zerqis (Dashboard → connect) with the user whose GitHub token can read the repo.
2. Run a **deep scan** on that repo and wait until it finishes (`COMPLETE`). Without this, impact is empty and comments are often “safe” or low-signal.
3. **Webhook** on GitHub: **Settings → Webhooks → Add webhook**
   - **Payload URL:** `https://<your-public-backend>/api/webhooks/github`  
     Local dev: use **ngrok**, **Cloudflare Tunnel**, or similar — GitHub cannot call `localhost`.
   - **Content type:** `application/json`
   - **Events:** Let me select → **Pull requests**
   - **Secret:** optional; if set, it must match `GITHUB_WEBHOOK_SECRET` in your backend env or signature validation returns **401** and nothing runs.

### 2. Trigger analysis

- Open a **new PR**, or **push a new commit** to an existing PR (`opened` / `synchronize`).

### 3. What you’ll see

| Where | What |
|--------|------|
| **PR conversation** | A comment from your GitHub user (the token) starting with **Zerqis:** and a scenario (e.g. High Risk, Safe to Merge). That’s the main “warning” surface. |
| **Latest commit** | No status line unless `PR_POST_COMMIT_STATUS=true`. If on: **failure** for `BLOCKED`; for `REVIEW REQUIRED`, failure only if `PR_FAIL_ON_REVIEW=true`. |
| **Backend logs** | Lines like `Queued PR analysis`, `PR #… Zerqis: verdict=…`, or stack traces if something broke. |

### 4. Getting a “stronger” warning

- Change code that touches **API routes** other services **call** (per your graph), so **dependentsCount** goes up → higher-risk templates.
- Ensure **consumer repos** are also connected and scanned so `api_calls` link to endpoints.

### 5. If nothing appears

- Confirm the webhook **Recent Deliveries** show **200** (not 401/404/502).
- Confirm the repo is **connected** in Zerqis (webhook is ignored if the repo isn’t in the DB).
- Watch backend logs for `PR analysis failed`.
