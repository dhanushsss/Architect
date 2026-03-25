# Setup

Zerqis needs GitHub access to list/connect repos and (optionally) receive PR webhooks. The app stores scan results in PostgreSQL. For product context (problem, solution, supported stacks), see the [root README](../README.md).

## GitHub OAuth app

| Field | Value |
|--------|--------|
| Homepage URL | `http://localhost:3000` |
| Authorization callback URL | `http://localhost:8080/api/auth/callback` |

## Required environment

| Variable | Purpose |
|----------|---------|
| `GITHUB_CLIENT_ID` | OAuth client ID |
| `GITHUB_CLIENT_SECRET` | OAuth secret |
| `JWT_SECRET` | Long random string (signing JWTs) |
| `DB_URL` | e.g. `jdbc:postgresql://localhost:5432/architect` |
| `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL credentials |

## Common optional variables

| Variable | Purpose |
|----------|---------|
| `FRONTEND_URL` | UI base URL (PR comment links); default `http://localhost:3000` |
| `GITHUB_REDIRECT_URI` | Must match OAuth app callback |
| `GITHUB_WEBHOOK_SECRET` | Validates `POST /api/webhooks/github` |
| `ANTHROPIC_API_KEY` | AI features + optional PR “AI Insight” block |
| `SLACK_WEBHOOK_URL` | PR risk alerts |
| `PR_MAX_FILES_SCAN` | Max changed files analyzed per PR (default 50) |
| `PR_POST_COMMIT_STATUS` | Post GitHub status on PR head (`true`/`false`) |
| `PR_FAIL_ON_REVIEW` | Treat REVIEW REQUIRED as failed status when status posting is on |

## JWT storage (browser)

After login, the app stores the token as `zerqis_token` (legacy `architect_token` is migrated on next sign-in).
