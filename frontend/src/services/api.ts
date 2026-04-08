import axios from 'axios'
import { clearAuthToken, getAuthToken } from '../authToken'
import type { Repo, GithubRepo, GraphDto, ScanStatus, ImpactDto, User, AtlasDashboardDto, DeadEndpointDto, PredictionAccuracy } from '../types'

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').trim().replace(/\/$/, '')
const V1_BASE_URL = API_BASE ? `${API_BASE}/api/v1` : '/api/v1'
const AUTH_BASE_URL = API_BASE ? `${API_BASE}/api` : '/api'

// ── Versioned API client (all /api/v1/** endpoints) ──────────────────────────
const api = axios.create({
  baseURL: V1_BASE_URL,
  headers: { 'Content-Type': 'application/json' }
})

// ── Non-versioned client (auth + webhooks stay at /api/auth/** by design) ────
// These paths are registered with GitHub OAuth and must never change without
// updating the GitHub App callback URL.
const authAxios = axios.create({
  baseURL: AUTH_BASE_URL,
  headers: { 'Content-Type': 'application/json' }
})

// Attach JWT to every versioned request
const attachToken = (config: any) => {
  const token = getAuthToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
}
api.interceptors.request.use(attachToken)
authAxios.interceptors.request.use(attachToken)

// Auto-logout on 401
const handle401 = (err: any) => {
  if (err.response?.status === 401) {
    clearAuthToken()
    window.location.href = '/login'
  }
  return Promise.reject(err)
}
api.interceptors.response.use(res => res, handle401)
authAxios.interceptors.response.use(res => res, handle401)

// ── Auth (non-versioned — OAuth integration) ─────────────────────────────────
export const authApi = {
  getMe: () => authAxios.get<User>('/auth/me').then(r => r.data),
  loginWithGithub: () => {
    window.location.href = API_BASE ? `${API_BASE}/api/auth/github` : '/api/auth/github'
  }
}

// ── Repos ─────────────────────────────────────────────────────────────────────
export const repoApi = {
  listConnected: () => api.get<Repo[]>('/repos').then(r => r.data),
  listGithub: () => api.get<GithubRepo[]>('/repos/github').then(r => r.data),
  connect: (repo: GithubRepo) => api.post<Repo>('/repos/connect', {
    githubId: repo.id,
    fullName: repo.full_name,
    name: repo.name,
    language: repo.language,
    defaultBranch: repo.default_branch,
    htmlUrl: repo.html_url,
    private: repo.private,
    description: repo.description
  }).then(r => r.data),
  disconnect: (repoId: number) => api.delete(`/repos/${repoId}`),
  /** Re-evaluate all unresolved cross-repo connections — call after connecting new repos. */
  relink: () => api.post<{ status: string; message: string }>('/repos/relink').then(r => r.data),
  rescan: (repoId: number, mode: 'quick' | 'deep' = 'deep') =>
    api.post<{ status: string }>(`/repos/${repoId}/rescan?mode=${mode}`).then(r => r.data),
}

// ── Scans ─────────────────────────────────────────────────────────────────────
export const scanApi = {
  triggerScan: (repoId: number, mode: 'quick' | 'deep' | 'incremental' = 'deep') =>
    api.post<ScanStatus>(`/scan/${repoId}`, null, { params: { mode } }).then(r => r.data),
  scanAll: () => api.post<ScanStatus[]>('/scan/all').then(r => r.data),
  getStatus: (repoId: number) => api.get<ScanStatus>(`/scan/${repoId}/status`).then(r => r.data)
}

// ── Graph ─────────────────────────────────────────────────────────────────────
export const graphApi = {
  getGraph: () => api.get<GraphDto>('/graph').then(r => r.data),
  getWiringWarnings: () =>
    api.get<{ id: number; repoId: number | null; factType: string; message: string; createdAt: string | null }[]>(
      '/graph/warnings'
    ).then(r => r.data),
  getPackageTree: (repoId: number) => api.get<any>(`/graph/tree/${repoId}`).then(r => r.data),
  traceComponent: (repoId: number, file: string) =>
    api.get<any>(`/graph/trace`, { params: { repoId, file } }).then(r => r.data),
}

// ── Impact / Risk ─────────────────────────────────────────────────────────────
export const impactApi = {
  getEndpointImpact: (endpointId: string) => api.get<ImpactDto>(`/impact/endpoint/${endpointId}`).then(r => r.data),
  getRepoImpact: (repoId: string) => api.get<ImpactDto>(`/impact/repo/${repoId}`).then(r => r.data),
  getRiskOverview: () => api.get<any[]>('/impact/overview').then(r => r.data)
}

// ── AI ────────────────────────────────────────────────────────────────────────
export const aiApi = {
  getAnomalies: () => api.get<any[]>('/ai/anomalies').then(r => r.data),
  getTechDebt: () => api.get<any>('/ai/tech-debt').then(r => r.data),
  getHistory: () => api.get<any[]>('/ai/history').then(r => r.data),
  generateDocs: (repoId: number) => api.post<void>(`/ai/docs/${repoId}`).then(r => r.data),
  explainPrRisk: (body: { verdict: string; numericScore: number; scenario: string; riskFactors: string[] }) =>
    api.post<{ explanation: string }>('/ai/explain-pr-risk', body).then(r => r.data),
}

// ── Enterprise ────────────────────────────────────────────────────────────────
export const enterpriseApi = {
  getGovernance: () => api.get<any>('/enterprise/governance').then(r => r.data),
  getSoc2Report: () => api.get<any>('/enterprise/audit/soc2').then(r => r.data),
  createSnapshot: (label?: string) => api.post<any>('/enterprise/snapshots', { label }).then(r => r.data),
  listSnapshots: () => api.get<any[]>('/enterprise/snapshots').then(r => r.data),
  diffSnapshots: (a: number, b: number) => api.get<any>(`/enterprise/snapshots/${a}/diff/${b}`).then(r => r.data),
  getOrgs: () => api.get<any[]>('/enterprise/orgs').then(r => r.data),
  createOrg: (name: string) => api.post<any>('/enterprise/orgs', { name }).then(r => r.data),
}

// ── API Keys ──────────────────────────────────────────────────────────────────
export const apiKeyApi = {
  list: () => api.get<any[]>('/keys').then(r => r.data),
  generate: (name: string, scopes: string) => api.post<any>('/keys', { name, scopes }).then(r => r.data),
  revoke: (id: number) => api.delete(`/keys/${id}`),
}

// ── Dashboard ─────────────────────────────────────────────────────────────────
export type ProductConfig = {
  coreOnly: boolean
  webhookUrl: string
  webhookEvents: string[]
  frontendUrl: string
  features: { prEngine: boolean; commitStatus: boolean; slackAlerts: boolean }
}

export const dashboardApi = {
  getProductConfig: () => api.get<ProductConfig>('/dashboard/product-config').then(r => r.data),
  getRiskyPrsWeek: () => api.get<any[]>('/dashboard/risky-prs-week').then(r => r.data),
}

// ── Insights ──────────────────────────────────────────────────────────────────
export const insightsApi = {
  summary: () => api.get<Record<string, unknown>>('/insights/summary').then(r => r.data),
}

// ── Atlas ─────────────────────────────────────────────────────────────────────
export const atlasApi = {
  getDashboard: () => api.get<AtlasDashboardDto>('/atlas/dashboard').then(r => r.data),
  getDeadEndpoints: () => api.get<DeadEndpointDto[]>('/atlas/dead-endpoints').then(r => r.data),
}

// ── Version (no auth) ─────────────────────────────────────────────────────────
export type ProductVersionInfo = { product: string; version: string; publicApi: string }

export const predictionApi = {
  globalAccuracy: () => authAxios.get<PredictionAccuracy>('/predictions/accuracy').then(r => r.data),
  repoAccuracy: (owner: string, repo: string) =>
    authAxios.get<PredictionAccuracy>(`/predictions/accuracy/${owner}/${repo}`).then(r => r.data),
}

export const versionApi = {
  get: () => authAxios.get<ProductVersionInfo>('/public/version').then(r => r.data),
}

export default api
