export interface User {
  id: number
  githubId: number
  login: string
  name: string
  avatarUrl: string
}

export interface Repo {
  id: number
  githubId: number
  name: string
  fullName: string
  description: string | null
  primaryLanguage: string | null
  htmlUrl: string
  isPrivate: boolean
  scanStatus: 'PENDING' | 'SCANNING' | 'COMPLETE' | 'FAILED' | 'RATE_LIMITED'
  lastScannedAt: string | null
  endpointCount: number
}

export interface GithubRepo {
  id: number
  name: string
  full_name: string
  description: string | null
  language: string | null
  html_url: string
  private: boolean
  default_branch: string
}

export interface NodeData {
  label: string
  type: 'REPO' | 'API_ENDPOINT' | 'COMPONENT' | 'CONFIG'
  language?: string | null
  data: Record<string, unknown>
}

export interface GraphNode {
  id: string
  label: string
  type: 'REPO' | 'API_ENDPOINT' | 'COMPONENT' | 'CONFIG'
  language?: string | null
  data: Record<string, unknown>
}

export interface GraphEdge {
  id: string
  source: string
  target: string
  label: string
  type: string
  data?: Record<string, unknown>
}

export interface GraphDto {
  nodes: GraphNode[]
  edges: GraphEdge[]
  stats: {
    totalRepos: number
    totalEndpoints: number
    totalCalls: number
    totalImports: number
    totalEdges: number
    totalExternalCalls?: number
    totalWiredEdges?: number
  }
}

export interface ScanStatus {
  repoId: number
  repoName: string
  status: string
  endpointsFound?: number
  callsFound?: number
  importsFound?: number
  message?: string
}

export interface AffectedItem {
  id: string
  name: string
  type: string
  detail: string
}

export interface ImpactDto {
  subjectId: string
  subjectType: string
  subjectLabel: string
  riskScore: 'HIGH' | 'MEDIUM' | 'LOW'
  numericScore: number
  verdict: string
  dependentsCount: number
  affectedRepos: AffectedItem[]
  affectedFiles: AffectedItem[]
  orphanEndpoints?: string[]
  changedEndpoints?: string[]
  prOrphanEndpoints?: string[]
  // Confidence breakdown
  confidenceScore?: number
  directMatchCount?: number
  inferredMatchCount?: number
  unresolvedCallCount?: number
  staleRepoCount?: number
  unscannedRepoCount?: number
  changedFilesNotFetched?: number
  riskFactors?: string[]
}

export interface PredictionAccuracy {
  repoFullName: string | null
  totalPredictions: number
  resolvedPredictions: number
  correctPredictions: number
  incorrectPredictions: number
  revertedCount: number
  hotfixedCount: number
  accuracyPct: number | null
}

// ── Atlas ─────────────────────────────────────────────────────────────────────

export interface RepoHealthDto {
  repoId: number
  repoName: string
  repoUrl: string
  language: string | null
  healthScore: number
  healthLabel: 'HEALTHY' | 'FAIR' | 'POOR'
  /** Single most impactful reason for this score — shown directly on the card */
  primaryDiagnosis: string
  totalEndpoints: number
  orphanEndpoints: number
  utilizedEndpoints: number
  orphanRatio: number
  unresolvedCalls: number
  inboundDeps: number
  outboundDeps: number
  scanStatus: string
  lastScannedAt: string | null
  staleDays: number | null
  topOrphanEndpoints: string[]
}

export interface AtlasDashboardDto {
  systemHealthScore: number
  systemHealthLabel: 'HEALTHY' | 'FAIR' | 'POOR'
  /** All repos sorted worst-first */
  repoHealthList: RepoHealthDto[]
  /** Single most critical repo — for quick-link in summary header */
  mostCriticalRepo: RepoHealthDto | null
  criticalRepoCount: number
  totalEndpoints: number
  utilizedEndpoints: number
  totalDeadEndpoints: number
  unresolvedCallsTotal: number
  staleRepoCount: number
  neverScannedCount: number
}

export interface DeadEndpointDto {
  endpointId: number
  repoId: number
  repoName: string
  repoUrl: string
  httpMethod: string
  path: string
  filePath: string
  lineNumber: number | null
  framework: string | null
  language: string | null
  daysSinceScanned: number | null
}
