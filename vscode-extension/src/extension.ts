import * as vscode from 'vscode'
import { ArchitectClient } from './architectClient'
import { getApiBase, getJwtToken, isCodeLensEnabled } from './config'
import { ImpactLensProvider } from './impactLensProvider'

let client: ArchitectClient | null = null

export function activate(context: vscode.ExtensionContext): void {
  const refreshClient = () => {
    const jwt = getJwtToken()
    client = jwt ? new ArchitectClient(getApiBase(), jwt) : null
  }
  refreshClient()

  const dashboardUrl = `${getApiBase().replace(/\/$/, '')}/graph`
  const provider = new ImpactLensProvider(() => client, dashboardUrl)

  context.subscriptions.push(
    vscode.workspace.onDidChangeConfiguration((e) => {
      if (e.affectsConfiguration('architect')) refreshClient()
    })
  )

  const sel: vscode.DocumentSelector = [
    { language: 'java', scheme: 'file' },
    { language: 'typescript', scheme: 'file' },
    { language: 'javascript', scheme: 'file' },
  ]

  context.subscriptions.push(
    vscode.languages.registerCodeLensProvider(sel, {
      provideCodeLenses: async (doc, token) => {
        if (!isCodeLensEnabled()) return []
        return provider.provideCodeLenses(doc, token)
      },
    })
  )

  context.subscriptions.push(
    vscode.commands.registerCommand('architect.showEndpointImpact', async (endpointId: string) => {
      if (!client) {
        vscode.window.showWarningMessage('Set architect.jwtToken in VS Code settings (copy JWT after login).')
        return
      }
      try {
        const impact = await client.getEndpointImpact(endpointId)
        const n = impact.dependentsCount ?? 0
        const label = impact.subjectLabel ?? 'endpoint'
        const pick = await vscode.window.showInformationMessage(
          `${n} repo(s) depend on ${label} — View in Architect`,
          'Open Architect'
        )
        if (pick === 'Open Architect') {
          vscode.env.openExternal(vscode.Uri.parse(provider.getDashboardUrl()))
        }
      } catch (e) {
        const msg = e instanceof Error ? e.message : String(e)
        vscode.window.showErrorMessage(`Architect: ${msg}`)
      }
    })
  )
}

export function deactivate(): void {
  client = null
}
