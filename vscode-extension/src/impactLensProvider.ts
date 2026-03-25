import * as vscode from 'vscode'
import { ArchitectClient } from './architectClient'

const PATH_MAP_TTL_MS = 5 * 60 * 1000

let pathToEndpointId = new Map<string, string>()
let mapLoadedAt = 0

async function ensurePathMap(client: ArchitectClient): Promise<void> {
  const now = Date.now()
  if (now - mapLoadedAt < PATH_MAP_TTL_MS && pathToEndpointId.size > 0) {
    return
  }
  const graph = await client.getGraph()
  const next = new Map<string, string>()
  for (const n of graph.nodes ?? []) {
    if (n.type !== 'API_ENDPOINT' || !n.id?.startsWith('ep-')) continue
    const id = n.id.replace(/^ep-/, '')
    const label = n.label ?? ''
    const m = label.match(/^(GET|POST|PUT|DELETE|PATCH)\s+(.+)$/i)
    if (!m) continue
    const method = m[1].toUpperCase()
    const pathPart = normalizePathKey(m[2])
    next.set(`${method} ${pathPart}`, id)
  }
  pathToEndpointId = next
  mapLoadedAt = now
}

function normalizePathKey(p: string): string {
  return p.split('?')[0].replace(/\/+$/, '').toLowerCase() || '/'
}

function extractJavaRoutes(text: string): { line: number; method: string; path: string }[] {
  const out: { line: number; method: string; path: string }[] = []
  const lines = text.split(/\r?\n/)
  lines.forEach((line, idx) => {
    const vm = line.match(/@(?:Get|Post|Put|Delete|Patch)Mapping\s*\(\s*(?:value\s*=\s*)?["']([^"']+)["']/i)
    if (vm) {
      let method = 'GET'
      if (/@PostMapping/i.test(line)) method = 'POST'
      else if (/@PutMapping/i.test(line)) method = 'PUT'
      else if (/@DeleteMapping/i.test(line)) method = 'DELETE'
      else if (/@PatchMapping/i.test(line)) method = 'PATCH'
      out.push({ line: idx, method, path: vm[1] })
      return
    }
    const rm = line.match(/@RequestMapping\s*\(\s*(?:value\s*=\s*)?["']([^"']+)["']/i)
    if (rm && /RequestMapping/i.test(line)) {
      let method = 'GET'
      const mm = line.match(/method\s*=\s*RequestMethod\.(GET|POST|PUT|DELETE|PATCH)/i)
      if (mm) method = mm[1].toUpperCase()
      out.push({ line: idx, method, path: rm[1] })
    }
  })
  return out
}

function extractJsRoutes(text: string): { line: number; method: string; path: string }[] {
  const out: { line: number; method: string; path: string }[] = []
  const lines = text.split(/\r?\n/)
  lines.forEach((line, idx) => {
    const m = line.match(
      /(?:app|router)\s*\.\s*(get|post|put|delete|patch)\s*\(\s*['"]([^'"]+)['"]/i
    )
    if (m) {
      out.push({ line: idx, method: m[1].toUpperCase(), path: m[2] })
    }
  })
  return out
}

export class ImpactLensProvider implements vscode.CodeLensProvider {
  constructor(
    private readonly getClient: () => ArchitectClient | null,
    private readonly dashboardUrl: string
  ) {}

  getDashboardUrl(): string {
    return this.dashboardUrl
  }

  async provideCodeLenses(
    document: vscode.TextDocument,
    _token: vscode.CancellationToken
  ): Promise<vscode.CodeLens[]> {
    const c = this.getClient()
    if (!c) return []

    const lang = document.languageId
    const text = document.getText()
    const routes = lang === 'java' ? extractJavaRoutes(text) : extractJsRoutes(text)
    if (routes.length === 0) return []

    await ensurePathMap(c)

    const lenses: vscode.CodeLens[] = []
    for (const r of routes) {
      const key = `${r.method} ${normalizePathKey(r.path)}`
      const epId = pathToEndpointId.get(key)
      const line = document.lineAt(r.line)
      const range = new vscode.Range(r.line, 0, r.line, line.text.length)
      if (epId) {
        lenses.push(
          new vscode.CodeLens(range, {
            title: 'Architect: checking impact…',
            command: 'architect.showEndpointImpact',
            arguments: [epId],
          })
        )
      } else {
        lenses.push(
          new vscode.CodeLens(range, {
            title: 'Architect: route not in graph',
            command: '',
          })
        )
      }
    }
    return lenses
  }
}
