# Real-time PR engine

**Goal:** On every PR → detect what may break across repos → before merge.

## End-to-end flow

1. **PR opened / updated** (`opened`, `synchronize`)
2. **GitHub webhook** → `POST /api/webhooks/github`
3. **Fetch changed files** — `GET /repos/{owner}/{repo}/pulls/{pr}/files`
4. **Targeted scan** — For each changed file (cap: `PR_MAX_FILES_SCAN`, default 50), fetch **PR head** content (`GET /contents/{path}?ref={head_sha}`), run **EndpointExtractorService** only (no full repo tree)
5. **Map to graph** — Union:
   - API endpoints already in DB whose `file_path` is in the changed list
   - DB endpoints whose path+method match endpoints extracted from PR head
6. **Impact** — For those endpoints, aggregate **cross-repo** callers from `api_calls` (existing graph)
7. **PR comment** — Markdown: verdict, risk score, touched APIs, downstream repos, sample call sites
8. **Optional** — GitHub **commit status** on PR head (merge blocking if branch protection + failure)
9. **Slack** — Unchanged: alert when verdict ≠ SAFE TO MERGE (if `SLACK_WEBHOOK_URL` set)

## Configuration (`application.yml` / env)

| Property | Env | Default | Description |
|----------|-----|---------|-------------|
| `app.pr-engine.max-changed-files-to-scan` | `PR_MAX_FILES_SCAN` | 50 | Max changed files to fetch + parse per PR |
| `app.pr-engine.post-commit-status` | `PR_POST_COMMIT_STATUS` | `false` | Post GitHub commit status on PR head |
| `app.pr-engine.fail-on-review-required` | `PR_FAIL_ON_REVIEW` | `false` | If status enabled: also mark **failure** for REVIEW REQUIRED (not only BLOCKED) |
| `app.frontend-url` | `FRONTEND_URL` | `http://localhost:3000` | Link in PR comment to Architect impact page |

**Commit status behavior (when enabled):**

- `BLOCKED` → `failure`
- `REVIEW REQUIRED` → `failure` only if `fail-on-review-required: true`, else `success`
- `SAFE TO MERGE` → `success`

Requires OAuth token with access to the repo (`repo` scope).

## Performance

- No full-repo scan on PR — only changed paths, capped count
- Caller graph read from PostgreSQL (last full scan)
- Webhook returns **200 immediately**; analysis runs **async** (`@Async`)

## Prerequisites for accurate results

1. Connect the repo in Architect and run at least one **deep scan** so endpoints and cross-repo calls exist in the DB.
2. Configure the GitHub webhook on the repository pointing to your deployed `/api/webhooks/github`.
3. Set `FRONTEND_URL` in production so PR comments link to your app.

## Code map

| Piece | Location |
|-------|----------|
| Webhook entry | `WebhookController` |
| Orchestration | `PRAnalysisService` |
| Targeted impact | `ImpactAnalysisService#analyzePullRequestTargeted` |
| PR head file content | `GitHubService#getFileContentAtRef` |
| Commit status | `GitHubService#createCommitStatus` |

## PR comment scenarios (5 templates)

Classification uses **downstream repo count**, **dependent file count**, and whether **API routes** were detected in the PR.

| Scenario | When | Tone |
|----------|------|------|
| **Wide cascading** | ≥4 repos, or ≥3 repos + ≥10 dependent files | Large blast radius, phased rollout |
| **Critical cross-service** | ≥2 repos (below wide threshold) | Multiple services, coordinate before merge |
| **Medium internal** | Exactly 1 downstream repo | Quick validation |
| **Orphan API risk** | API touched, **zero** cross-repo callers in graph | Dead code vs untracked callers |
| **Safe refactor** | No API surface detected, or no graph impact | Safe to merge |

Implementation: `PrCommentFormatter` + `ImpactDto.prOrphanEndpoints` (endpoints in PR with no tracked cross-repo callers).

## Future (not in this MVP)

- Targeted **ImportTracer** / **FrontendCallDetector** on changed files only
- GitHub **Checks API** (richer than statuses)
- Cache / incremental endpoint index
