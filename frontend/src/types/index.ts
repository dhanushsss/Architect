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
  scanStatus: 'PENDING' | 'SCANNING' | 'COMPLETE' | 'FAILED'
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
  dependentsCount: number
  affectedRepos: AffectedItem[]
  affectedFiles: AffectedItem[]
  orphanEndpoints?: string[]
}
