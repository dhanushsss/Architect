# API call detection → graph CALL edges

## Step 1 — Detect

| Source | Patterns |
|--------|----------|
| **JS/TS** | `fetch("…")`, `` fetch(`…`) ``, `axios.get/post/…("…")`, `` axios.get(`…`) ``, `api.get('/path')`, Angular `this.http.get`, Vue `$http` |
| **Java** | `WebClient` / `RestClient` `.uri("…")`, `RestTemplate.getForObject("…")`, `URI.create("…")` |

Stored per call:

- `callerRepo`, `urlPattern`, `httpMethod`, `filePath`, `lineNumber`, `callType`
- `normalizedPattern` — `${id}` → `*` for matching
- `targetKind`: `INTERNAL_ENDPOINT` (matched), `EXTERNAL` (`https://…`), `UNRESOLVED`

## Step 2 — Match

Relative paths are matched to **definitions** across all user repos: same path shape with `{param}` ↔ `*`, optional HTTP method preference.

## Step 3 — Graph

| Edge | Meaning |
|------|---------|
| **CALLS** (cyan, animated) | **Repo → Repo** — aggregated cross-service calls (count + endpoint list in edge `data`) |
| **CALLS** (indigo) | **Repo → Endpoint** — same-repo caller → defined API |
| **DEFINES** | Repo → Endpoint |

Toggles: **Defines**, **Calls**, **Imports**, **Reads** (filter bar).

## Step 4 — Data model

- `api_calls`: `http_method`, `normalized_pattern`, `target_kind`, `external_host`
- `dependency_edges`: still stores REPO → API_ENDPOINT for matched cross-repo calls (analytics)

## Step 5 — Product

Cross-repo **CALLS** edges feed impact: change endpoint → callers from other repos → risk.

## Edge cases

1. **Dynamic URLs** — `` `/api/users/${id}` `` → normalized `/api/users/*`
2. **Base URL** — `api.get('/users')` with shared `api` client; full `BASE_URL + "/x"` not resolved (stays UNRESOLVED unless path is literal in scan)
3. **External** — `https://api.stripe.com/...` → `targetKind=EXTERNAL`, `externalHost=api.stripe.com`, no definition match

**Re-scan** repos after deploy to populate new fields.
