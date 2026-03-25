# Runtime URL wiring (repos independent in Git)

Repos are **not** linked in Git — they connect at **runtime** via URLs and config.
Architect infers these links from scanned files during a **deep scan**.

---

## What we extract

| Source file | Fact type | Example |
|-------------|-----------|---------|
| `application.yml` / `.properties` | `APP_NAME` | `spring.application.name: user-service` |
| `application.yml` / `.properties` | `SERVER_PORT` | `server.port: 3002` |
| `application.yml` / `.properties` | `EUREKA_REGISTRY` | `eureka.client.serviceUrl.defaultZone: http://localhost:8761/eureka` |
| Gateway YAML (`spring.cloud.gateway`) | `GATEWAY_ROUTE` | `lb://booking-service` + nearby `Path=/api/bookings/**` |
| Gateway YAML (http URI style) | `GATEWAY_ROUTE` | `uri: http://user-service:8080` |
| `vite.config.ts` / `.js` / `.mts` | `VITE_PROXY` | `proxy: { '/api': { target: 'http://localhost:8880' } }` |
| `.env` / `.env.local` / `.env.development` | `ENV_SERVICE_URL` | `GATEWAY_URL=http://localhost:8880` |
| **Node.js server/gateway entry files** | `GATEWAY_ROUTE` | See below |

### Node.js routing map detection

Express-based API gateways often define a routing table in code rather than YAML.
Architect detects the pattern automatically in these entry files:
`server.js`, `server.ts`, `server.mjs`, `gateway.js`, `gateway.ts`,
`index.js`, `index.ts`, `app.js`, `app.ts`.

**Detected pattern — any JS object mapping a path segment to a service name:**

```javascript
const ROUTE_TO_SERVICE = {
  bookings:   "booking-service",   // → GATEWAY_ROUTE /api/bookings/**  → booking-service
  users:      "user-service",      // → GATEWAY_ROUTE /api/users/**     → user-service
  properties: "catalog-service",   // → GATEWAY_ROUTE /api/properties/** → catalog-service
};
```

The service name must end in a recognisable suffix: `-service`, `-gateway`, `-registry`,
`-api`, `-server`, or `-client`. Path pattern is inferred as `/api/<segment>/**`.

---

## Graph edges (WIRED)

| Kind | Colour | Meaning |
|------|--------|---------|
| **GATEWAY** | magenta | Gateway YAML `lb://service-id` or Node.js routing map → target repo |
| **UI_PROXY** | pink | Vite `proxy` target port matched to another repo's `SERVER_PORT` |
| **BACKEND_HTTP** | orange | Backend calls `http://user-service:8080`, Feign `@FeignClient("…")`, or service URLs in config |

---

## API call detection (CALLS edges from source code)

Beyond config-based wiring, Architect also detects outbound HTTP calls directly in source code.

### Python f-string URLs

Python services using `httpx` or `requests` with f-string URLs are fully supported:

```python
with httpx.Client() as client:
    client.post(f"{REGISTRY}/register", json={...})   # → /*/register → booking-registry
    client.get(f"{user_base}/api/users")               # → /*/api/users → user-service
```

`{REGISTRY}` is treated as a wildcard segment (same as `${REGISTRY}` in Java/Node).
After normalisation: `{REGISTRY}/register` → `/*/register` → leading wildcard stripped → `/register`.

### Leading-wildcard stripping

All env-var-prefixed URLs (`${VAR}/path`, `{VAR}/path`) normalise to `/*/path`.
`PathSegmentIndex` strips the leading wildcard before matching to avoid false positives:

```
${REGISTRY}/services → /*/services → strip → /services → matched to booking-registry
```

---

## How port matching works (VITE_PROXY → UI_PROXY edge)

1. Extract `target: "http://localhost:8880"` from `vite.config` → port **8880**.
2. Search all repos for a `SERVER_PORT` fact equal to `8880`.
3. If exactly one match → create `UI_PROXY` edge from the UI repo to that backend repo.
4. If no match → fall back to the gateway repo (repo whose name contains "gateway").
5. If multiple matches (ambiguous) → log a warning, no edge created.

---

## Backend → backend hostname matching (BACKEND_HTTP edge)

1. Detect `http://user-service:8080` in Java source or config files.
2. Strip port → logical hostname `user-service`.
3. Resolve against `APP_NAME` facts and repo names.
4. If matched → create `BACKEND_HTTP` edge.

Blocked hosts (never create edges): `localhost`, `127.0.0.1`, public TLDs (`.com`, `.io`, etc.),
AWS/GCP domains, common third-party APIs (GitHub, Stripe, etc.).

---

## Requirements

- **Deep scan** on each repo so `application*.yml`, `vite.config`, and Node.js server files are read.
- Backend services should declare **`spring.application.name`** (Spring) or use recognisable repo-name service IDs.
- Spring Cloud Gateway config must include `gateway` in the YAML filename or content with **`lb://`** routes.
- Node.js gateways: routing map values must end in a service-name suffix (see above).

---

## Limitations

- `BASE_URL + '/api'` string concatenation in code is not resolved; use config files or f-string/template-literal URLs.
- Multiple services on the same `SERVER_PORT` → ambiguous Vite proxy; no UI_PROXY edge created (warning logged).
- Python `requests`/`httpx` aiohttp session-level URL construction not detected unless the URL literal appears on the same line as the HTTP method call.
