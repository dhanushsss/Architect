# Bug fixes — Graph floating nodes investigation

**Date:** 2026-03-25
**Symptom:** `booking-ui`, `catalog-service`, and `user-service` showed no connections in the
dependency graph. Only `booking-service → booking-registry` and `api-gateway → booking-registry`
(incorrectly matched to its own endpoint) were visible.

---

## Bug 1 — Java endpoints never extracted (EndpointExtractorService)

**File:** `EndpointExtractorService.java` · `SpringParser.parse()`

**Root cause:** Double `Matcher.find()` call consumed the regex match:

```java
// BEFORE (broken):
Matcher m = METHOD_MAPPING.matcher(line);
if (!m.find()) {            // ← this call consumes the match
    m = SIMPLE_MAPPING.matcher(line);
}
if (m.find()) {             // ← tries to find a SECOND occurrence → always empty
    results.add(ep(...));
}

// AFTER (fixed):
Matcher m = METHOD_MAPPING.matcher(line);
boolean methodMatched = m.find();   // store result
if (!methodMatched) {
    m = SIMPLE_MAPPING.matcher(line);
    methodMatched = m.find();
}
if (methodMatched) {
    results.add(ep(m.group(1), joinPaths(classPrefix, m.group(2)), ...));
}
```

**Impact:** Every Java service (user-service, booking-service) had 0 extracted endpoints.
No CALLS edges could be built. Fixed: user-service now shows 4 endpoints.

---

## Bug 2 — ECJ Javadoc comment termination (PathSegmentIndex)

**File:** `PathSegmentIndex.java` (Javadoc)

**Root cause:** The Javadoc contained `{@code /*/services}` which has `*/` (asterisk then slash).
Spring Boot DevTools uses Eclipse JDT (ECJ) for incremental compilation. ECJ terminates a block
comment at the first `*/` it encounters — even inside `{@code …}`. This generated a broken `.class`
file. At runtime, `new PathSegmentIndex(…)` threw:

```
java.lang.Error: Unresolved compilation problems
```

`catch (Exception e)` does **not** catch `java.lang.Error`, so all scans failed silently.

**Fix:** Replace `*` with `&#42;` HTML entity in the affected Javadoc snippets:
```java
// BEFORE: {code /*/services}
// AFTER:  {code /&#42;/services}
```

**Note:** `mvn compile` (standard javac) did NOT reproduce this — it only appeared at runtime
when DevTools recompiled with ECJ.

---

## Bug 3 — Scan catch block missed java.lang.Error (RepoScannerService)

**File:** `RepoScannerService.java`

**Root cause:** The scan try-catch used `catch (Exception e)`. `java.lang.Error` is not a subclass
of `Exception` — it is a sibling under `Throwable`. So when PathSegmentIndex threw `Error`,
the catch block was skipped entirely, leaving repos stuck in `SCANNING` status.

```java
// BEFORE:
} catch (Exception e) { ... }

// AFTER:
} catch (Throwable e) { ... }
```

---

## Bug 4 — LazyInitializationException in log statement (RepoScannerService)

**File:** `RepoScannerService.java` · `buildDependencyEdges()`

**Root cause:** A debug log statement accessed `call.getCallerRepo().getName()` and
`matched.getRepo().getName()` outside a Hibernate session. `ApiCall.callerRepo` was LAZY-loaded,
triggering `LazyInitializationException`.

```java
// BEFORE (broken outside session):
log.debug("… {} → {}/{}", call.getCallerRepo().getName(), matched.getRepo().getName(), matched.getPath());

// AFTER (IDs are always loaded):
log.debug("… callerRepo={} → endpoint id={} path={}", call.getCallerRepo().getId(), matched.getId(), matched.getPath());
```

---

## Bug 5 — ApiEndpoint.repo LAZY fetch (ApiEndpoint)

**File:** `ApiEndpoint.java`

**Root cause:** `@ManyToOne(fetch = FetchType.LAZY)` on the `repo` field. When `GraphBuilderService`
and `buildDependencyEdges` accessed `endpoint.getRepo()` outside a Hibernate session, a
`LazyInitializationException` was thrown. This caused `api-gateway`, `booking-ui`, and
`booking-registry` connections to be invisible.

```java
// BEFORE:
@ManyToOne(fetch = FetchType.LAZY)

// AFTER:
@ManyToOne(fetch = FetchType.EAGER)
```

---

## Bug 6 — StaleStateException on concurrent scans (GitHubEtagCacheRepository)

**File:** `GitHubEtagCacheRepository.java`

**Root cause:** Default Spring Data `deleteByRepo(Repo repo)` uses JPA select-then-delete (loads
entities, then deletes each individually). When two concurrent scans tried to delete the same
ETag cache entries, the second delete found the rows already gone and threw
`org.springframework.orm.jpa.JpaObjectRetrievalFailureException: StaleStateException`.

```java
// BEFORE: default JPA select-then-delete (implicit)

// AFTER: single bulk DELETE query — idempotent, no select
@Modifying
@Query("DELETE FROM GitHubEtagCache g WHERE g.repo = :repo")
void deleteByRepo(@Param("repo") Repo repo);
```

---

## Bug 7 — ${VAR}/path URLs rejected (BackendHttpCallDetectorService)

**File:** `BackendHttpCallDetectorService.java` · `looksLikeUrl()`

**Root cause:** `looksLikeUrl` only accepted URLs starting with `http://`, `https://`, or `/`.
Backend services using env-var-prefixed base URLs (`${REGISTRY}/services`,
`${REGISTRY}/register`) were silently discarded before being stored as `api_calls`.

```java
// BEFORE:
return t.startsWith("http://") || t.startsWith("https://") || t.startsWith("/");

// AFTER:
return t.startsWith("http://") || t.startsWith("https://") || t.startsWith("/")
    || (t.startsWith("${") && t.contains("/"))   // Java/Node: ${SERVICE_URL}/path
    || (t.startsWith("{") && t.contains("/"));   // Python f-string: {REGISTRY}/register
```

---

## Bug 8 — Python f-string URLs not detected (BackendHttpCallDetectorService)

**File:** `BackendHttpCallDetectorService.java` · `PY_REQUESTS` pattern

**Root cause:** The Python regex matched `('url')` and `("url")` but not `f"url"` or `f'url'`.
Services calling `client.post(f"{REGISTRY}/register", …)` were not detected.

```java
// BEFORE:
Pattern.compile("(?:requests|httpx|session|client)\\s*\\.\\s*(get|post|…)\\s*\\(\\s*([\"'])(.+?)\\2", …)

// AFTER (added f? before the quote):
Pattern.compile("(?:requests|httpx|session|client)\\s*\\.\\s*(get|post|…)\\s*\\(\\s*f?([\"'])(.+?)\\2", …)
```

**Result:** `catalog-service` now detects calls to `{REGISTRY}/register`, `{REGISTRY}/heartbeat`,
and `{user_base}/api/users`, creating CALLS edges to `booking-registry` and `user-service`.

---

## Bug 9 — Wrong match priority in PathSegmentIndex (PathSegmentIndex)

**File:** `PathSegmentIndex.java` · `findCandidates()`

**Root cause:** When `api-gateway` called `${REGISTRY}/services` (normalised to `/*/services`),
Level 3 matching ran before the leading-wildcard stripping logic. Level 3 found
`/gateway/services` on `api-gateway` itself (same depth, wildcard match) and returned it —
creating a self-loop instead of a connection to `booking-registry`'s `/services` endpoint.

**Fix:** Try leading-wildcard stripping **first**. If stripping yields a match, return it
immediately without falling through to Level 3:

```java
// In findCandidates():
if (norm.startsWith("/*")) {
    String stripped = stripLeadingWildcardSegments(norm);  // /*/services → /services
    if (!stripped.equals(norm) && !stripped.equals("/")) {
        findCandidatesForNorm(stripped, results);
        if (!results.isEmpty()) return results;  // early exit — correct match found
    }
}
findCandidatesForNorm(norm, results);  // fallback: try original pattern
```

---

## Bug 10 — Self-match in buildDependencyEdges step 1 (RepoScannerService)

**File:** `RepoScannerService.java` · `buildDependencyEdges()` step 1

**Root cause:** When `api-gateway` was scanned before `booking-registry`, the PathSegmentIndex
only contained `api-gateway`'s own endpoints. `${REGISTRY}/services` normalised to `/*/services`.
After stripping → `/services` — no match (booking-registry not yet in DB). Level 3 fallback
matched `/gateway/services` on `api-gateway` itself (same depth, wildcard).

Even after Bug 9 was fixed, this still happened because the stripped path `/services` had no
entry in the index when booking-registry wasn't scanned yet.

**Fix:** Exclude same-repo matches in both step 1 and step 2 of `buildDependencyEdges`.
The call stays `UNRESOLVED` and is correctly linked when the target repo is scanned:

```java
// After matchCallToEndpoint():
if (matched != null && matched.getRepo().getId().equals(repo.getId())) {
    matched = null;  // discard self-match; leave as UNRESOLVED for later re-link
}
```

Applied to both step 1 (this repo's calls) and step 2 (retroactive re-link of other repos).

---

## Bug 11 — Node.js gateway routes not detected (RuntimeWiringExtractorService)

**File:** `RuntimeWiringExtractorService.java`

**Root cause:** The wiring extractor only parsed Spring Cloud Gateway YAML (`lb://service-id`).
The `api-gateway` repo uses a Node.js Express server (`server.js`) with a JavaScript routing
map. No GATEWAY_ROUTE facts were created, so no edges were built from api-gateway to
user-service, catalog-service, or booking-service.

**Fix:** Added `fromJsGatewayFile()` to detect routing map objects in Node.js entry files:

```javascript
// Detected pattern:
const ROUTE_TO_SERVICE = {
  bookings:   "booking-service",   // → GATEWAY_ROUTE /api/bookings/**  → booking-service
  users:      "user-service",      // → GATEWAY_ROUTE /api/users/**     → user-service
  properties: "catalog-service",   // → GATEWAY_ROUTE /api/properties/** → catalog-service
};
```

Also extended `isRuntimeWiringScanPath()` to include `server.js`, `server.ts`, `server.mjs`,
`gateway.js`, `gateway.ts`, `index.js`, `index.ts`, `app.js`, `app.ts`.

---

## Final graph state after all fixes

| Edge | Type | How detected |
|------|------|--------------|
| booking-ui → api-gateway | WIRED (UI_PROXY) | VITE_PROXY fact, port 8880 → gateway |
| api-gateway → booking-service | WIRED (GATEWAY) | JS routing map: `bookings: "booking-service"` |
| api-gateway → user-service | WIRED (GATEWAY) | JS routing map: `users: "user-service"` |
| api-gateway → catalog-service | WIRED (GATEWAY) | JS routing map: `properties: "catalog-service"` |
| api-gateway → booking-registry | CALLS | `${REGISTRY}/services` f-string → `/services` |
| booking-service → booking-registry | CALLS | `${REGISTRY}/register`, `${REGISTRY}/heartbeat` |
| catalog-service → booking-registry | CALLS | `{REGISTRY}/heartbeat` Python f-string |
| catalog-service → api-gateway | CALLS | `{user_base}/api/users` Python f-string → `/api/users` |

All 6 repos connected — no floating nodes.
