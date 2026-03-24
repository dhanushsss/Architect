# Runtime URL wiring (repos independent in Git)

Repos are **not** linked in Git—they connect at **runtime** via URLs and config. Architect infers links from scanned files (deep scan).

## What we extract

| Source | Facts |
|--------|--------|
| `application.yml` / `.properties` | `spring.application.name`, `server.port`, Eureka `defaultZone` |
| Gateway YAML (`spring.cloud.gateway`) | `Path=...` + `lb://service-id` or `http://service:port` |
| `vite.config.*` | `proxy: { '/api': { target: 'http://localhost:8880' } }` |
| `.env` | `REGISTRY_URL`, `PUBLIC_URL`, `GATEWAY_URL` |

## Graph edges (**Wired**)

| Kind | Color | Meaning |
|------|-------|---------|
| **GATEWAY** | magenta | Gateway YAML `lb://service-id` → target repo |
| **UI_PROXY** | pink | Vite `proxy` → port matched to another repo |
| **BACKEND_HTTP** | orange | Backend calls `http://user-service:8080`, **Feign** `@FeignClient("…")`, or service URLs in `application.yml` |

### Backend URL detection

- **Java**: `WebClient`/`RestTemplate`/`URI` strings with `http://hostname` (not localhost); `@FeignClient("user-service")` and `name=value`.
- **YAML/properties** (deep scan): `http://booking-service:8080` in config lines (skips `jdbc:`, Redis, datasource URLs).

Target repo resolved by hostname vs `spring.application.name` / repo name (`booking-service`, etc.).

## Requirements

- **Deep scan** on each repo so `application*.yml` and `vite.config` are read.
- Backend services should declare **`spring.application.name`** (or repo name ≈ service id).
- Gateway config must include **`gateway`** routes with **`lb://`** or **`http://hostname:port`**.

## Limitations

- `BASE_URL + '/api'` in code is not resolved; wiring comes from **config**, not from every HTTP call.
- Multiple services on the same port → first match wins for UI→backend link.
