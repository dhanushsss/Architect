# Product versioning

Architect uses **semantic versioning** (`MAJOR.MINOR.PATCH`).

## Source of truth

| What | Where |
|------|--------|
| Runtime version (API + UI) | `app.product-version` in `application.yml` |
| Override in deploy | Env `ARCHITECT_VERSION=1.2.0` |
| Maven artifact | `backend/pom.xml` `<version>` |
| Frontend package | `frontend/package.json` `version` |

On release, bump **all three** to the same value so builds and UI stay aligned.

## Public endpoints

- **`GET /api/public/version`** — JSON: `product`, `version`, `publicApi` (no auth).
- **`GET /api/public/v1/health`** — includes same `version` in the payload.

## Public HTTP API versioning

Integrations use path **`/api/public/v1/...`** (e.g. graph by API key). Breaking changes to that surface should ship as **`/api/public/v2/...`** while keeping v1 until deprecated.

## Git tags

Tag releases: `git tag v1.1.0 && git push origin v1.1.0`.
