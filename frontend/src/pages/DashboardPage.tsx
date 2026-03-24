import { useState, useEffect, useRef } from 'react'
import { Link } from 'react-router-dom'
import { GitFork, Plus, RefreshCw, Trash2, Github, ChevronDown, ChevronUp, Link2 } from 'lucide-react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import Navbar from '../components/layout/Navbar'
import { ScanStatusBadge, LanguageBadge } from '../components/ui/Badge'
import Spinner from '../components/ui/Spinner'
import { repoApi, scanApi, impactApi, dashboardApi } from '../services/api'
import type { GithubRepo } from '../types'

// ── scan progress hook (Change 2) ────────────────────────────────────────────
interface ScanEvent { type: string; data: any }

function useScanProgress(repoId: number | null) {
  const [events, setEvents] = useState<ScanEvent[]>([])
  const [active, setActive] = useState(false)
  const esRef = useRef<EventSource | null>(null)

  useEffect(() => {
    if (!repoId) return
    const token = localStorage.getItem('architect_token')
    const es = new EventSource(`/api/v1/scan/${repoId}/stream`, {})
    esRef.current = es
    setActive(true)
    setEvents([])

    const handle = (name: string) => (e: MessageEvent) => {
      try {
        const data = JSON.parse(e.data)
        setEvents(prev => [...prev.slice(-19), { type: name, data }])
        if (name === 'complete' || name === 'failed') { setActive(false); es.close() }
      } catch {}
    }

    ;['start', 'files_found', 'endpoint_found', 'progress', 'complete', 'failed']
      .forEach(name => es.addEventListener(name, handle(name) as any))

    es.onerror = () => { setActive(false); es.close() }
    return () => { es.close(); setActive(false) }
  }, [repoId])

  return { events, active }
}

// ── risk badge ────────────────────────────────────────────────────────────────
function RiskBadge({ verdict, score }: { verdict: string; score: number }) {
  const cfg = verdict === 'BLOCKED'
    ? { bg: 'bg-red-500/15 border-red-500/40 text-red-400', dot: 'bg-red-400' }
    : verdict === 'REVIEW REQUIRED'
    ? { bg: 'bg-yellow-500/15 border-yellow-500/40 text-yellow-400', dot: 'bg-yellow-400' }
    : { bg: 'bg-green-500/15 border-green-500/40 text-green-400', dot: 'bg-green-400' }
  return (
    <span className={`inline-flex items-center gap-1.5 text-xs font-semibold px-2.5 py-1 rounded-full border ${cfg.bg}`}>
      <span className={`w-1.5 h-1.5 rounded-full ${cfg.dot}`} />
      {score.toFixed(1)} · {verdict}
    </span>
  )
}

// ── main page ─────────────────────────────────────────────────────────────────
export default function DashboardPage() {
  const qc = useQueryClient()
  const [showConnectPanel, setShowConnectPanel] = useState(false)
  const [activeScanRepoId, setActiveScanRepoId] = useState<number | null>(null)
  const { events: scanEvents, active: scanActive } = useScanProgress(activeScanRepoId)

  const { data: repos = [], isLoading } = useQuery({ queryKey: ['repos'], queryFn: repoApi.listConnected })
  const { data: riskOverview = [] } = useQuery({
    queryKey: ['risk-overview'],
    queryFn: impactApi.getRiskOverview,
    enabled: repos.some(r => r.scanStatus === 'COMPLETE')
  })
  const { data: productCfg } = useQuery({
    queryKey: ['product-config'],
    queryFn: dashboardApi.getProductConfig,
    staleTime: 60_000,
  })
  const { data: riskyPrsWeek = [] } = useQuery({
    queryKey: ['risky-prs-week'],
    queryFn: dashboardApi.getRiskyPrsWeek,
  })
  const { data: githubRepos = [], isFetching: fetchingGH, refetch: fetchGH } = useQuery({
    queryKey: ['github-repos'],
    queryFn: repoApi.listGithub,
    enabled: false
  })

  const connectMutation = useMutation({
    mutationFn: repoApi.connect,
    onSuccess: (repo: any) => {
      qc.invalidateQueries({ queryKey: ['repos'] })
      setActiveScanRepoId(repo.id)  // auto-subscribe to scan progress
    }
  })

  const disconnectMutation = useMutation({
    mutationFn: repoApi.disconnect,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['repos'] })
  })

  const scanMutation = useMutation({
    mutationFn: (repoId: number) => {
      setActiveScanRepoId(repoId)
      return scanApi.triggerScan(repoId)
    },
    onSuccess: () => setTimeout(() => {
      qc.invalidateQueries({ queryKey: ['repos'] })
      qc.invalidateQueries({ queryKey: ['risk-overview'] })
    }, 2000)
  })

  const scanAllMutation = useMutation({
    mutationFn: scanApi.scanAll,
    onSuccess: () => setTimeout(() => {
      qc.invalidateQueries({ queryKey: ['repos'] })
      qc.invalidateQueries({ queryKey: ['risk-overview'] })
    }, 2000)
  })

  const [relinkDone, setRelinkDone] = useState(false)
  const relinkMutation = useMutation({
    mutationFn: repoApi.relink,
    onSuccess: () => {
      setRelinkDone(true)
      setTimeout(() => {
        setRelinkDone(false)
        qc.invalidateQueries({ queryKey: ['repos'] })
        qc.invalidateQueries({ queryKey: ['risk-overview'] })
      }, 3000)
    }
  })

  // When scan completes, refresh repo list + risk overview
  useEffect(() => {
    const last = scanEvents[scanEvents.length - 1]
    if (last?.type === 'complete') {
      setTimeout(() => {
        qc.invalidateQueries({ queryKey: ['repos'] })
        qc.invalidateQueries({ queryKey: ['risk-overview'] })
      }, 500)
    }
  }, [scanEvents])

  const connectedIds = new Set(repos.map(r => r.githubId))
  const completedRepos = repos.filter(r => r.scanStatus === 'COMPLETE').length
  const scanningRepos = repos.filter(r => r.scanStatus === 'SCANNING')

  // Latest scan event label
  const latestEvent = scanEvents[scanEvents.length - 1]
  const scanLabel = latestEvent
    ? latestEvent.type === 'start'       ? `Scanning ${latestEvent.data.repo}…`
    : latestEvent.type === 'endpoint_found' ? `✓ Found ${latestEvent.data.count} endpoints in ${latestEvent.data.file?.split('/').pop()}`
    : latestEvent.type === 'progress'    ? `Scanning… ${latestEvent.data.endpointsFound} endpoints found`
    : latestEvent.type === 'complete'    ? `✓ Scan done — ${latestEvent.data.endpointsFound} endpoints found`
    : latestEvent.type === 'failed'      ? `✗ Scan failed`
    : null
    : null

  return (
    <div className="min-h-screen bg-slate-900">
      <Navbar />

      <main className="max-w-5xl mx-auto p-6">
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold text-white">Change Risk Dashboard</h1>
            <p className="text-slate-400 text-sm mt-1">{repos.length} repos connected · {completedRepos} scanned</p>
          </div>
          <div className="flex gap-3">
            {repos.length > 1 && (
              <button
                onClick={() => relinkMutation.mutate()}
                disabled={relinkMutation.isPending || relinkDone}
                title="Re-evaluate cross-repo connections — use this after connecting new repos"
                className="btn-secondary flex items-center gap-2 text-sm"
              >
                <Link2 className={`w-4 h-4 ${relinkMutation.isPending ? 'animate-pulse' : ''}`} />
                {relinkDone ? 'Re-linked ✓' : 'Re-link Repos'}
              </button>
            )}
            {repos.length > 0 && (
              <button
                onClick={() => scanAllMutation.mutate()}
                disabled={scanAllMutation.isPending}
                className="btn-secondary flex items-center gap-2 text-sm"
              >
                <RefreshCw className={`w-4 h-4 ${scanAllMutation.isPending ? 'animate-spin' : ''}`} />
                Scan All
              </button>
            )}
            <button
              onClick={() => { setShowConnectPanel(p => !p); if (!showConnectPanel) fetchGH() }}
              className="btn-primary flex items-center gap-2 text-sm"
            >
              <Plus className="w-4 h-4" />
              Connect Repos
              {showConnectPanel ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
            </button>
          </div>
        </div>

        {/* Change 2 — live scan progress ticker */}
        {/* PR engine — webhook + risky PRs this week */}
        <div className="mb-6 grid md:grid-cols-2 gap-4">
          <div className="bg-slate-800/50 border border-indigo-500/20 rounded-xl p-4">
            <h2 className="text-sm font-semibold text-white mb-1">PR protection</h2>
            <p className="text-xs text-slate-400 mb-2">
              Repo → Settings → Webhooks → Payload URL below. Content type JSON. Events:{' '}
              <code className="text-indigo-300">pull_request</code>
            </p>
            <div className="flex gap-2">
              <code className="flex-1 text-[11px] text-emerald-300 bg-slate-950 p-2 rounded break-all">
                {productCfg?.webhookUrl || 'Loading…'}
              </code>
              <button
                type="button"
                className="text-xs px-2 py-1 bg-slate-700 rounded text-white shrink-0"
                onClick={() => productCfg?.webhookUrl && navigator.clipboard.writeText(productCfg.webhookUrl)}
              >
                Copy
              </button>
            </div>
            <p className="text-[10px] text-slate-500 mt-2">
              Commit status: {productCfg?.features?.commitStatus ? 'on' : 'off'} · Slack:{' '}
              {productCfg?.features?.slackAlerts ? 'on' : 'off'}
            </p>
          </div>
          <div className="bg-slate-800/50 border border-slate-700 rounded-xl p-4 max-h-52 overflow-y-auto">
            <h2 className="text-sm font-semibold text-white mb-2">Risky PRs (7 days)</h2>
            {riskyPrsWeek.length === 0 ? (
              <p className="text-xs text-slate-500">No PR analyses yet. Open a PR on a connected repo.</p>
            ) : (
              <ul className="space-y-2 text-xs">
                {riskyPrsWeek.slice(0, 8).map((p: any) => (
                  <li key={p.id} className="flex justify-between gap-2 border-b border-slate-700/50 pb-2">
                    <span className="text-slate-300 truncate">
                      {p.repoName} #{p.prNumber}{' '}
                      <span className={p.verdict === 'BLOCKED' ? 'text-red-400' : 'text-amber-400'}>
                        {p.verdict}
                      </span>
                    </span>
                    <a href={p.prUrl} target="_blank" rel="noreferrer" className="text-indigo-400 shrink-0">
                      PR ↗
                    </a>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        {(scanActive || latestEvent?.type === 'complete') && scanLabel && (
          <div className={`mb-4 px-4 py-2.5 rounded-lg border flex items-center gap-3 text-sm transition-colors ${
            latestEvent?.type === 'complete'
              ? 'bg-green-500/10 border-green-500/30 text-green-300'
              : latestEvent?.type === 'failed'
              ? 'bg-red-500/10 border-red-500/30 text-red-300'
              : 'bg-indigo-500/10 border-indigo-500/30 text-indigo-300'
          }`}>
            {scanActive && <RefreshCw className="w-3.5 h-3.5 animate-spin shrink-0" />}
            {scanLabel}
          </div>
        )}

        {/* Fix 1 — Risk-first: flagged repos at top, graph is a drill-down */}
        {riskOverview.length > 0 && (
          <div className="mb-6 space-y-4">
            {/* HIGH / BLOCKED attention band */}
            {riskOverview.some((i: any) => i.verdict === 'BLOCKED' || i.verdict === 'REVIEW REQUIRED') && (
              <div>
                <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2 flex items-center gap-1.5">
                  <span className="w-1.5 h-1.5 rounded-full bg-red-400 animate-pulse" />
                  Needs Attention
                </h2>
                <div className="space-y-2">
                  {riskOverview
                    .filter((i: any) => i.verdict === 'BLOCKED' || i.verdict === 'REVIEW REQUIRED')
                    .map((item: any) => (
                      <div key={item.repoId}
                        className={`flex items-center justify-between px-4 py-3 rounded-xl border ${
                          item.verdict === 'BLOCKED'
                            ? 'bg-red-950/30 border-red-500/40'
                            : 'bg-yellow-950/20 border-yellow-500/30'
                        }`}>
                        <div className="min-w-0">
                          <div className="flex items-center gap-2 mb-0.5">
                            <RiskBadge verdict={item.verdict} score={item.numericScore} />
                            <span className="text-white font-semibold text-sm">{item.repoName}</span>
                          </div>
                          {item.dependentsCount > 0 && (
                            <p className="text-xs text-slate-400">
                              Used by {item.dependentsCount} repo{item.dependentsCount > 1 ? 's' : ''}
                              {item.affectedRepos?.length > 0 && ` · ${item.affectedRepos.slice(0, 3).join(', ')}`}
                            </p>
                          )}
                        </div>
                        <div className="flex gap-2 ml-3 flex-shrink-0">
                          <Link
                            to={`/impact/repo/${item.repoId}`}
                            className="text-xs px-3 py-1.5 rounded-lg bg-slate-700 hover:bg-slate-600 text-white transition-colors"
                          >
                            Risk Analysis
                          </Link>
                          <Link
                            to="/graph"
                            className="text-xs px-3 py-1.5 rounded-lg bg-indigo-600/20 hover:bg-indigo-600/30 text-indigo-400 border border-indigo-500/30 transition-colors"
                          >
                            View in Graph →
                          </Link>
                        </div>
                      </div>
                    ))}
                </div>
              </div>
            )}

            {/* Summary grid for all repos */}
            <div>
              <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-2">All Repos</h2>
              <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
                {riskOverview.map((item: any) => (
                  <Link
                    key={item.repoId}
                    to={`/impact/repo/${item.repoId}`}
                    className="card hover:border-slate-600 transition-colors block py-3"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-white text-sm font-medium truncate">{item.repoName}</span>
                      <RiskBadge verdict={item.verdict} score={item.numericScore} />
                    </div>
                    {item.dependentsCount > 0 && (
                      <p className="text-xs text-slate-500 mt-1">
                        {item.dependentsCount} dependent repo{item.dependentsCount !== 1 ? 's' : ''}
                      </p>
                    )}
                  </Link>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* Currently scanning indicator */}
        {scanningRepos.length > 0 && (
          <div className="mb-4 flex items-center gap-2 text-sm text-indigo-300">
            <RefreshCw className="w-3.5 h-3.5 animate-spin" />
            Scanning {scanningRepos.map(r => r.name).join(', ')}…
          </div>
        )}

        {/* Connect panel */}
        {showConnectPanel && (
          <div className="card mb-6">
            <h2 className="font-semibold text-white mb-3 flex items-center gap-2">
              <Github className="w-4 h-4" /> Your GitHub Repositories
            </h2>
            {fetchingGH ? (
              <div className="flex justify-center py-6"><Spinner /></div>
            ) : (
              <div className="space-y-2 max-h-64 overflow-y-auto">
                {githubRepos.filter(r => !connectedIds.has(r.id)).map(repo => (
                  <div key={repo.id} className="flex items-center justify-between p-3 bg-slate-700/50 rounded-lg hover:bg-slate-700">
                    <div className="flex items-center gap-3 min-w-0">
                      <div>
                        <div className="flex items-center gap-2">
                          <span className="text-white text-sm font-medium">{repo.name}</span>
                          {repo.private && <span className="text-xs text-slate-500 border border-slate-600 px-1 rounded">private</span>}
                        </div>
                        {repo.description && <p className="text-slate-400 text-xs truncate">{repo.description}</p>}
                      </div>
                    </div>
                    <div className="flex items-center gap-2 ml-3">
                      <LanguageBadge language={repo.language} />
                      <button
                        onClick={() => connectMutation.mutate(repo as GithubRepo)}
                        disabled={connectMutation.isPending}
                        className="btn-primary text-xs py-1 px-2.5"
                      >
                        Connect
                      </button>
                    </div>
                  </div>
                ))}
                {githubRepos.filter(r => !connectedIds.has(r.id)).length === 0 && (
                  <p className="text-slate-400 text-sm text-center py-4">All repos are already connected!</p>
                )}
              </div>
            )}
          </div>
        )}

        {/* Repo list */}
        {isLoading ? (
          <div className="flex justify-center py-16"><Spinner className="w-8 h-8" /></div>
        ) : repos.length === 0 ? (
          <div className="card text-center py-16">
            <GitFork className="w-12 h-12 text-slate-600 mx-auto mb-4" />
            <h3 className="text-white font-semibold text-lg mb-2">No repos connected</h3>
            <p className="text-slate-400 text-sm mb-4">
              Connect your repos and get a risk score on every PR — before a cross-repo change breaks production.
            </p>
            <button onClick={() => { setShowConnectPanel(true); fetchGH() }} className="btn-primary">
              Connect Repositories
            </button>
          </div>
        ) : (
          <div>
            <h2 className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-3">Repos</h2>
            <div className="space-y-3">
              {repos.map(repo => (
                <div key={repo.id} className="card hover:border-slate-600 transition-colors">
                  <div className="flex items-center gap-4">
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 mb-1">
                        <a href={repo.htmlUrl} target="_blank" rel="noopener noreferrer"
                          className="text-white font-semibold hover:text-indigo-400 transition-colors">
                          {repo.name}
                        </a>
                        <LanguageBadge language={repo.primaryLanguage} />
                        {repo.isPrivate && <span className="text-xs text-slate-500 border border-slate-600 px-1 rounded">private</span>}
                      </div>
                      <div className="flex items-center gap-4 text-sm">
                        <ScanStatusBadge status={repo.scanStatus} />
                        {repo.endpointCount > 0 && <span className="text-slate-400">{repo.endpointCount} endpoints</span>}
                        {repo.lastScannedAt && (
                          <span className="text-slate-500 text-xs">
                            Last scanned {new Date(repo.lastScannedAt).toLocaleDateString()}
                          </span>
                        )}
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      {repo.scanStatus === 'COMPLETE' && (
                        <Link to={`/impact/repo/${repo.id}`} className="btn-secondary text-xs py-1.5 px-3">
                          Risk Analysis
                        </Link>
                      )}
                      <button
                        onClick={() => scanMutation.mutate(repo.id)}
                        disabled={scanMutation.isPending || repo.scanStatus === 'SCANNING'}
                        className="btn-secondary text-xs py-1.5 px-3 flex items-center gap-1.5"
                      >
                        <RefreshCw className={`w-3.5 h-3.5 ${repo.scanStatus === 'SCANNING' ? 'animate-spin' : ''}`} />
                        {repo.scanStatus === 'SCANNING' ? 'Scanning...' : 'Scan'}
                      </button>
                      <button
                        onClick={() => disconnectMutation.mutate(repo.id)}
                        className="p-1.5 text-slate-500 hover:text-red-400 transition-colors"
                        title="Disconnect"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {completedRepos > 0 && (
          <div className="mt-6 flex items-center justify-between text-sm text-slate-500 border-t border-slate-800 pt-4">
            <span>{completedRepos} repos scanned</span>
            <Link to="/graph" className="flex items-center gap-1.5 text-slate-400 hover:text-indigo-400 transition-colors">
              <GitFork className="w-3.5 h-3.5" />
              View full dependency map →
            </Link>
          </div>
        )}
      </main>
    </div>
  )
}
