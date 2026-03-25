# Architect Impact Lens (VS Code)

MVP extension that shows **Architect** dependency impact above Spring `@*Mapping` and Express-style `app.get` / `router.get` routes.

## Setup

1. **Install dependencies** (from this folder):

   ```bash
   npm install && npm run compile
   ```

2. In VS Code, **Run Extension** (F5) from this folder, or package with `vsce`.

3. Open **Settings** and configure:

   | Setting | Description |
   |--------|-------------|
   | `architect.apiUrl` | API base, e.g. `http://localhost:8080` |
   | `architect.jwtToken` | Paste the JWT from the Architect web app (after GitHub login; from `localStorage` or network requests) |
   | `architect.enableCodeLens` | Toggle CodeLens (default on) |

4. Open a **Java** or **TypeScript/JavaScript** service file that defines HTTP routes.

5. You should see **“Architect: checking impact…”** above each detected route. **Click** the lens to call `GET /api/v1/impact/endpoint/{id}` and show how many repos depend on that endpoint, with a link to open the Architect graph.

## How matching works

- On first use (and every **5 minutes**), the extension calls `GET /api/v1/graph` and builds a map from `METHOD + normalized path` → endpoint id (`ep-{id}` nodes).
- Route detection uses regex on the file (not a full parser); complex frameworks may need path tweaks.

OAuth in the editor is **not** required for this MVP — JWT paste is enough.
