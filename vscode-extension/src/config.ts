import * as vscode from 'vscode'

export function getApiBase(): string {
  return vscode.workspace.getConfiguration('architect').get<string>('apiUrl') ?? 'http://localhost:8080'
}

export function getJwtToken(): string {
  return vscode.workspace.getConfiguration('architect').get<string>('jwtToken') ?? ''
}

export function isCodeLensEnabled(): boolean {
  return vscode.workspace.getConfiguration('architect').get<boolean>('enableCodeLens') ?? true
}
