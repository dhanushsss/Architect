# API call detection → graph CALL edges

## Step 1 — Detect

### Frontend (FrontendCallDetectorService)

| Source | Patterns detected |
|--------|-------------------|
| **JS/TS/JSX/TSX** | `fetch("…")`, `` fetch(`…`) ``, `axios.get/post/…("…")`, `` axios.get(`…`) ``, `api.get('/path')`, Angular `this.http.get`, Vue `$http` |

Detects **relative** paths (`/api/users`) — the browser resolves them against the current host.

### Backend (BackendHttpCallDetectorService)

| Language | Patterns detected |
|----------|-------------------|
| **Java** | `WebClient`/`RestClient` `.uri("…")`, `RestTemplate.get/postForObject/ForEntity("url")`, `URI.create("url")` |
| **Node.js** | `axios.get/post/…('url')`, `axios({url: 'url'})`, `got('url')`, `got.get('url')`, `fetch('http://…')` (absolute only), `superagent.get('url')`, `needle('get','url')` |
| **Python** | `requests.get/post/…('url')`, `httpx.get/post/…('url')`, `session.get/post/…('url')`, `client.get/post/…('url')`, **including `f"…"` f-strings** |

**Python f-string support:** The detector matches `f"..."` and `f'...'` in addition to plain strings:
```python
client.post(f"{REGISTRY}/register", json={...})   # detected ✓
client.get(f"{user_base}/api/users")               # detected ✓
```

**URL acceptance rules (`looksLikeUrl`):**
- `http://…` or `https://…` — absolute URL
- `/path` — relative path
- `${VAR}/path` — Java/Node env-var template
- `{VAR}/path` — Python f-string variable prefix

Stored per detected call:
- `callerRepo`, `urlPattern` (raw), `httpMethod`, `filePath`, `lineNumber`, `callType`
- `normalizedPattern` — `{id}`, `${id}`, `$id` → `*`; host stripped from absolute URLs
- `targetKind`: `INTERNAL_ENDPOINT` (matched), `EXTERNAL` (public host), `UNRESOLVED` (no match yet)

---

## Step 2 — Normalise

`ApiCallUrlNormalizer.normalizeForMatching(url)`:

1. **Absolute URL** → extract path only: `http://catalog-service:3001/api/properties` → `/api/properties`
2. **Strip query string**: `/api/items?page=1` → `/api/items`
3. **Template variables → `*`**: `${userId}`, `$id`, `{id}`, `{REGISTRY}` all become `*`
4. **Collapse slashes** + ensure leading `/`

`ApiCallUrlNormalizer.isExternalAbsoluteUrl(url)` — returns `true` only for URLs pointing at a
publicly-routable host (has dots + known public TLD like `.com`, `.io`, `.dev`).
Docker/K8s service names (`catalog-service`, `user-api`) have no dots → classified as INTERNAL.

---

## Step 3 — Match (PathSegmentIndex)

`PathSegmentIndex.findCandidates(callPath)`:

```
1. Strip leading wildcard segments (env-var base URL patterns):
     /*/services → /services, /*/api/users → /api/users
   Try matching stripped path first (prevents wrong same-depth matches)

2. Level 1: exactIndex.get(norm)        → O(1) — most frontend calls
3. Level 2: wildcardIndex[depth]        → O(E_k) — CRUD endpoints with {id}
4. Level 3: if norm has * and no match → check same-depth exact endpoints
     (call has a wildcard, endpoint is exact — e.g. base-URL + static path)
```

**Self-match exclusion:** During `buildDependencyEdges`, matches where the endpoint belongs
to the same repo as the caller are discarded. This prevents a service's outbound calls from
resolving to its own endpoints when the real target is not yet scanned. The call stays
`UNRESOLVED` and is re-linked when the target repo is scanned.

HTTP method preference: when multiple candidates exist, the one with a matching HTTP method is preferred.

---

## Step 4 — Graph edges

| Edge | Colour | Meaning |
|------|--------|---------|
| **CALLS** (cyan, animated) | Repo → Repo | Aggregated cross-service calls (count + endpoint list in `data`) |
| **CALLS** (indigo) | Repo → Endpoint | Same-repo caller → defined endpoint |
| **DEFINES** | Repo → Endpoint | Endpoint is defined in this repo |

Toggle visibility: **Defines** · **Calls** · **Imports** filter bar in the graph UI.

---

## Step 5 — Data model

```
api_calls
  caller_repo_id     bigint FK → repos
  endpoint_id        bigint FK → api_endpoints (NULL until matched)
  url_pattern        text    — raw detected URL
  normalized_pattern text    — after normalisation (* wildcards)
  http_method        varchar
  target_kind        varchar — INTERNAL_ENDPOINT | EXTERNAL | UNRESOLVED
  external_host      varchar — set when EXTERNAL (e.g. api.stripe.com)
  call_type          varchar — java-webclient-uri | node-axios | python-requests | …
  file_path          text
  line_number        int
```

---

## Edge cases

1. **Dynamic template literals** — `` `/api/users/${id}` `` → normalised `/api/users/*`
2. **Python f-strings** — `f"{REGISTRY}/register"` → normalised `/*/register` → stripped to `/register`
3. **Variable-built URLs** — `url = base + "/api/users"; restTemplate.getForObject(url, …)` — NOT detected (variable, not string literal); use config-based wiring instead
4. **External services** — `https://api.stripe.com/…` → `targetKind=EXTERNAL`, `externalHost=api.stripe.com`, no endpoint match
5. **Scan order** — Repo A scanned before Repo B: A's calls to B stay UNRESOLVED; resolved retroactively when B is scanned (step 2 of `buildDependencyEdges`)

**Re-scan** repos after adding new inter-service calls to populate edges.
