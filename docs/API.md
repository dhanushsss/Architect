# Architect API Reference

Base URL: `http://localhost:8080`

All authenticated endpoints require: `Authorization: Bearer <jwt-token>`

---

## Authentication

### `GET /api/auth/github`
Initiates GitHub OAuth flow. Redirects browser to GitHub.

**Response**: Redirect to `github.com/login/oauth/authorize`

---

### `GET /api/auth/callback?code={code}`
GitHub OAuth callback. Exchanges code for token, creates/updates user, issues JWT.

**Response**: Redirect to `{FRONTEND_URL}/auth/callback?token={jwt}`

---

### `GET /api/auth/me` 🔒
Returns current authenticated user.

**Response** `200 OK`:
```json
{
  "id": 1,
  "githubId": 12345678,
  "login": "johndoe",
  "name": "John Doe",
  "avatarUrl": "https://avatars.githubusercontent.com/u/12345678"
}
```

---

## Repositories

### `GET /api/repos` 🔒
Returns all connected repositories for the current user.

**Response** `200 OK`:
```json
[
  {
    "id": 1,
    "githubId": 987654321,
    "name": "frontend-react",
    "fullName": "myorg/frontend-react",
    "description": "Main React frontend",
    "primaryLanguage": "TypeScript",
    "htmlUrl": "https://github.com/myorg/frontend-react",
    "isPrivate": false,
    "scanStatus": "COMPLETE",
    "lastScannedAt": "2025-01-15T10:30:00",
    "endpointCount": 0
  }
]
```

---

### `GET /api/repos/github` 🔒
Returns all repositories accessible via the user's GitHub token (paginated, all orgs).

**Response** `200 OK`: Array of GitHub repo objects

---

### `POST /api/repos/connect` 🔒
Connects a GitHub repository for scanning.

**Request Body**:
```json
{
  "githubId": 987654321,
  "fullName": "myorg/frontend-react",
  "name": "frontend-react",
  "language": "TypeScript",
  "defaultBranch": "main",
  "htmlUrl": "https://github.com/myorg/frontend-react",
  "private": false,
  "description": "Main React frontend"
}
```

**Response** `200 OK`: RepoDto (see above)

---

### `DELETE /api/repos/{repoId}` 🔒
Disconnects a repository and removes all its scan data.

**Response** `204 No Content`

---

## Scanning

### `POST /api/scan/{repoId}` 🔒
Triggers an async scan of the specified repository.

**Response** `202 Accepted`:
```json
{
  "repoId": 1,
  "repoName": "frontend-react",
  "status": "SCANNING",
  "message": "Scan started"
}
```

---

### `POST /api/scan/all` 🔒
Triggers scans for all connected repositories.

**Response** `202 Accepted`: Array of ScanStatusDto

---

### `GET /api/scan/{repoId}/status` 🔒
Returns current scan status and counts.

**Response** `200 OK`:
```json
{
  "repoId": 1,
  "repoName": "frontend-react",
  "status": "COMPLETE",
  "endpointsFound": 0,
  "callsFound": 47,
  "importsFound": 12
}
```

**Scan Status values**: `PENDING` | `SCANNING` | `COMPLETE` | `FAILED`

---

## Graph

### `GET /api/graph` 🔒
Returns the full dependency graph for all connected repos.

**Response** `200 OK`:
```json
{
  "nodes": [
    {
      "id": "repo-1",
      "label": "frontend-react",
      "type": "REPO",
      "language": "TypeScript",
      "data": {
        "fullName": "myorg/frontend-react",
        "language": "TypeScript",
        "scanStatus": "COMPLETE",
        "endpointCount": 0,
        "htmlUrl": "https://github.com/myorg/frontend-react"
      }
    },
    {
      "id": "ep-42",
      "label": "GET /api/users/profile",
      "type": "API_ENDPOINT",
      "language": "java",
      "data": {
        "repoId": 2,
        "repoName": "backend-api",
        "filePath": "src/main/java/UserController.java",
        "lineNumber": 24,
        "framework": "Spring Boot"
      }
    }
  ],
  "edges": [
    {
      "id": "edge-call-7",
      "source": "repo-1",
      "target": "ep-42",
      "label": "calls",
      "type": "CALLS"
    }
  ],
  "stats": {
    "totalRepos": 5,
    "totalEndpoints": 78,
    "totalCalls": 134,
    "totalImports": 23,
    "totalEdges": 189
  }
}
```

**Node types**: `REPO` | `API_ENDPOINT` | `COMPONENT` | `CONFIG`

**Edge types**: `DEFINES` | `CALLS` | `IMPORTS` | `READS` | `DEPENDS_ON`

---

## Impact Analysis

### `GET /api/impact/endpoint/{endpointId}` 🔒
Returns impact analysis for a specific API endpoint.

**Response** `200 OK`:
```json
{
  "subjectId": "42",
  "subjectType": "API_ENDPOINT",
  "subjectLabel": "GET /api/users/profile",
  "riskScore": "HIGH",
  "dependentsCount": 3,
  "affectedRepos": [
    { "id": "1", "name": "frontend-react", "type": "REPO", "detail": "TypeScript" },
    { "id": "3", "name": "admin-angular", "type": "REPO", "detail": "TypeScript" },
    { "id": "5", "name": "mobile-app", "type": "REPO", "detail": "JavaScript" }
  ],
  "affectedFiles": [
    { "id": "7", "name": "src/services/userService.ts", "type": "FILE", "detail": "Line 34 (axios)" },
    { "id": "12", "name": "src/app/user/user.service.ts", "type": "FILE", "detail": "Line 19 (angular-http)" }
  ],
  "orphanEndpoints": null
}
```

---

### `GET /api/impact/repo/{repoId}` 🔒
Returns impact analysis for a repository (which repos import from it, orphan endpoints).

**Response** `200 OK`: Same structure as above, includes `orphanEndpoints` array

---

## Webhooks (Phase 2)

### `POST /api/webhooks/github`
GitHub webhook receiver. No auth required. Handles `pull_request` events.

**Headers required**: `X-GitHub-Event: pull_request`

**Response** `200 OK`

**Behavior**: On PR open/synchronize, if repo is connected and risk is HIGH/MEDIUM:
1. Posts impact analysis comment to the PR
2. Sends Slack notification (if configured)

---

## Error Responses

All errors follow this format:
```json
{
  "timestamp": "2025-01-15T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Repo not found: 999"
}
```

| Status | Meaning |
|--------|---------|
| 400 | Bad request / invalid parameters |
| 401 | Unauthorized — missing or invalid JWT |
| 403 | Forbidden — resource belongs to another user |
| 404 | Resource not found |
| 500 | Internal server error |

---

## Phase 3 — AI Understanding Layer

All AI endpoints stream responses via Server-Sent Events (SSE).

### `POST /api/ai/query` (SSE)
Natural language query about your architecture.

**Request:**
```json
{ "question": "Which repos call the payment API?" }
```
**Response**: SSE stream of text chunks. Powered by Claude claude-opus-4-6.

---

### `POST /api/ai/chat` (SSE)
Codebase Q&A chat — ask anything about your repos.

**Request:** `{ "message": "How does authentication work in this system?" }`

---

### `POST /api/ai/onboard` (SSE)
Developer onboarding trace — traces a feature end-to-end across repos.

**Request:** `{ "feature": "user login" }`

---

### `POST /api/ai/docs/{repoId}` (SSE)
Generate architecture documentation for a specific repo.

---

### `GET /api/ai/anomalies`
Detect architectural anomalies: broken API calls, orphan endpoints, circular dependencies.

**Response:** `[{ "type": "BROKEN_API_CALL", "severity": "HIGH", "description": "...", "repo": "...", "file": "..." }]`

---

### `GET /api/ai/tech-debt`
Tech debt radar report for all scanned repos.

**Response:** `{ "overallRisk": "MEDIUM", "orphanEndpoints": 3, "brokenCalls": 1, "recommendations": [...] }`

---

### `GET /api/ai/history`
Query history for the authenticated user (last 20 queries).

---

## Phase 4 — Enterprise & Compliance

### `POST /api/enterprise/orgs`
Create an organization. Creator becomes ADMIN.

**Request:** `{ "name": "My Company" }`

### `GET /api/enterprise/orgs`
List organizations the authenticated user belongs to.

### `GET /api/enterprise/orgs/{orgId}/members`
List all members of an organization.

### `POST /api/enterprise/orgs/{orgId}/members`
Add a member. Requires ADMIN role.

**Request:** `{ "userId": 42, "role": "DEVELOPER" }`

### `DELETE /api/enterprise/orgs/{orgId}/members/{userId}`
Remove a member from the organization.

### `GET /api/enterprise/governance`
API governance dashboard — all endpoints with health status, caller counts, and orphan/deprecated flags.

### `GET /api/enterprise/audit/soc2`
Generate SOC2-style dependency audit report with sensitive endpoint detection.

### `GET /api/enterprise/orgs/{orgId}/audit?page=0`
Paginated audit log for an organization.

### `POST /api/enterprise/snapshots`
Capture a snapshot of the current dependency graph.

**Request:** `{ "label": "Before v2 migration" }` (optional label)

### `GET /api/enterprise/snapshots`
List all captured snapshots.

### `GET /api/enterprise/snapshots/{snapshotId}/diff/{compareId}`
Compare two snapshots — returns added/removed nodes and edges.

---

## Phase 5 — Platform & Ecosystem

### `POST /api/keys`
Generate a new API key.

**Request:** `{ "name": "My CI Pipeline", "scopes": "read:graph" }`

**Response:** `{ "key": "arc_...", "prefix": "arc_abc...", "warning": "Store securely — shown once" }`

### `GET /api/keys`
List all active API keys for the authenticated user.

### `DELETE /api/keys/{keyId}`
Revoke an API key.
