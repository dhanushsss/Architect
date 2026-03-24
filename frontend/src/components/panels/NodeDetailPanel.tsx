import { useState } from 'react'
import { Link } from 'react-router-dom'
import { X, ExternalLink, Zap, GitMerge, ChevronDown, ChevronRight, Link2, ArrowRight } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import type { GraphDto, GraphEdge, GraphNode } from '../../types'
import { LanguageBadge } from '../ui/Badge'
import { graphApi } from '../../services/api'

interface Props {
  node: GraphNode | null
  graph: GraphDto
  onClose: () => void
}

const IMPORT_TYPE_COLORS: Record<string, string> = {
  INTERNAL: 'text-green-400',
  MONOREPO: 'text-yellow-400',
  EXTERNAL: 'text-red-400',
}

const IMPORT_TYPE_DOT: Record<string, string> = {
  INTERNAL: 'bg-green-400',
  MONOREPO: 'bg-yellow-400',
  EXTERNAL: 'bg-red-400',
}

export default function NodeDetailPanel({ node, graph, onClose }: Props) {
  const [tab, setTab] = useState<'details' | 'trace'>('details')
  const [expandedPkg, setExpandedPkg] = useState<string | null>(null)

  const repoId = node?.type === 'REPO' ? Number(node.id.split('-')[1]) : null

  const { data: treeData, isLoading: treeLoading } = useQuery({
    queryKey: ['package-tree', repoId],
    queryFn: () => graphApi.getPackageTree(repoId!),
    enabled: tab === 'trace' && repoId !== null,
  })

  if (!node) return null

  const data = node.data || {}
  const headerBg =
    node.type === 'REPO' ? 'bg-blue-500/10' :
    node.type === 'API_ENDPOINT' ? 'bg-green-500/10' :
    node.type === 'CONFIG' ? 'bg-orange-500/10' : 'bg-purple-500/10'
  const typeBadge =
    node.type === 'REPO' ? 'bg-blue-500/20 text-blue-400' :
    node.type === 'API_ENDPOINT' ? 'bg-green-500/20 text-green-400' :
    node.type === 'CONFIG' ? 'bg-orange-500/20 text-orange-400' : 'bg-purple-500/20 text-purple-400'

  return (
    <div className="absolute top-4 right-4 w-80 bg-slate-900 border border-slate-700 rounded-xl shadow-2xl z-50 overflow-hidden flex flex-col max-h-[calc(100vh-2rem)]">
      {/* Header */}
      <div className={`p-4 border-b border-slate-700 flex-shrink-0 ${headerBg}`}>
        <div className="flex items-start justify-between">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-1">
              <span className={`text-xs font-medium px-1.5 py-0.5 rounded ${typeBadge}`}>
                {node.type.replace('_', ' ')}
              </span>
              <LanguageBadge language={node.language} />
            </div>
            <h3 className="text-white font-semibold text-sm truncate">{node.label}</h3>
          </div>
          <button onClick={onClose} className="text-slate-400 hover:text-white ml-2 flex-shrink-0">
            <X className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Tabs — only for REPO nodes */}
      {node.type === 'REPO' && (
        <div className="flex border-b border-slate-700 flex-shrink-0">
          <button
            onClick={() => setTab('details')}
            className={`flex-1 py-2 text-xs font-medium transition-colors ${
              tab === 'details'
                ? 'text-white border-b-2 border-indigo-500'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            Details
          </button>
          <button
            onClick={() => setTab('trace')}
            className={`flex-1 py-2 text-xs font-medium flex items-center justify-center gap-1 transition-colors ${
              tab === 'trace'
                ? 'text-white border-b-2 border-indigo-500'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <GitMerge className="w-3 h-3" /> Origin Trace
          </button>
        </div>
      )}

      {/* Body */}
      <div className="overflow-y-auto flex-1">
        {tab === 'details' && (
          <div className="p-4 space-y-3">
            {node.type === 'REPO' && (
              <>
                <InfoRow label="Full Name" value={data.fullName as string} />
                <InfoRow label="Endpoints" value={String(data.endpointCount || 0)} />
                <InfoRow label="Scan Status" value={data.scanStatus as string} />
                {data.htmlUrl && (
                  <a href={data.htmlUrl as string} target="_blank" rel="noopener noreferrer"
                    className="flex items-center gap-1 text-indigo-400 text-sm hover:text-indigo-300">
                    <ExternalLink className="w-3 h-3" /> View on GitHub
                  </a>
                )}
                <RuntimeConfigBlock data={data} />
                <LinkedReposBlock nodeId={node.id} graph={graph} />
              </>
            )}

            {node.type === 'API_ENDPOINT' && (
              <>
                <InfoRow label="Repo" value={data.repoName as string} />
                <InfoRow label="File" value={data.filePath as string} mono />
                <InfoRow label="Line" value={String(data.lineNumber || '')} />
                <InfoRow label="Framework" value={data.framework as string} />
              </>
            )}

            <div className="pt-2 border-t border-slate-700">
              <Link
                to={`/impact/${node.type === 'REPO' ? 'repo' : 'endpoint'}/${node.id.split('-')[1]}`}
                className="flex items-center gap-2 text-sm text-yellow-400 hover:text-yellow-300 transition-colors"
              >
                <Zap className="w-4 h-4" />
                Analyze Impact
              </Link>
            </div>
          </div>
        )}

        {tab === 'trace' && node.type === 'REPO' && (
          <div className="p-3">
            {treeLoading && (
              <div className="text-slate-400 text-xs text-center py-6">Loading imports…</div>
            )}
            {!treeLoading && treeData && (
              <>
                {/* Summary row */}
                <div className="flex gap-3 mb-3 text-xs">
                  {(['INTERNAL', 'MONOREPO', 'EXTERNAL'] as const).map(t => (
                    <span key={t} className="flex items-center gap-1">
                      <span className={`w-2 h-2 rounded-full ${IMPORT_TYPE_DOT[t]}`} />
                      <span className={IMPORT_TYPE_COLORS[t]}>{t}</span>
                      <span className="text-slate-500">{(treeData.byType?.[t] ?? 0)}</span>
                    </span>
                  ))}
                </div>

                {/* Package tree */}
                {Object.entries(treeData.tree as Record<string, any[]>).length === 0 && (
                  <p className="text-slate-500 text-xs text-center py-4">No imports detected</p>
                )}
                {Object.entries(treeData.tree as Record<string, any[]>).map(([pkg, comps]) => (
                  <div key={pkg} className="mb-1">
                    <button
                      onClick={() => setExpandedPkg(expandedPkg === pkg ? null : pkg)}
                      className="w-full flex items-center gap-1 text-left text-slate-300 text-xs py-1 hover:text-white"
                    >
                      {expandedPkg === pkg
                        ? <ChevronDown className="w-3 h-3 flex-shrink-0" />
                        : <ChevronRight className="w-3 h-3 flex-shrink-0" />
                      }
                      <span className="font-mono truncate">{pkg}</span>
                      <span className="ml-auto text-slate-600 flex-shrink-0">{comps.length}</span>
                    </button>
                    {expandedPkg === pkg && (
                      <div className="ml-4 space-y-1 pb-1">
                        {comps.map((c: any, i: number) => (
                          <div key={i} className="flex items-start gap-2 py-0.5">
                            <span className={`w-2 h-2 rounded-full mt-1 flex-shrink-0 ${IMPORT_TYPE_DOT[c.importType] || 'bg-slate-500'}`} />
                            <div className="min-w-0">
                              <div className="text-slate-200 text-xs truncate">{c.component}</div>
                              <div className="text-slate-500 text-xs font-mono truncate">{c.filePath}:{c.lineNumber}</div>
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                ))}
              </>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

function RuntimeConfigBlock({ data }: { data: Record<string, unknown> }) {
  const appName = data.springApplicationName as string | undefined
  const reg = data.registryUrls as string[] | undefined
  const vite = data.viteProxies as { path: string; target: string }[] | undefined
  const outboundBe = data.outboundBackendHosts as string[] | undefined
  const isGw = data.isApiGateway === true
  if (!appName && !reg?.length && !vite?.length && !isGw && !outboundBe?.length) return null
  return (
    <div className="pt-3 mt-1 border-t border-slate-700/80 space-y-2">
      <div className="text-slate-500 text-[10px] font-semibold uppercase tracking-wide">Runtime (config / URLs)</div>
      {isGw && (
        <p className="text-xs text-fuchsia-300">API Gateway — routes traffic by path to backend services</p>
      )}
      {appName && <InfoRow label="spring.application.name" value={appName} mono />}
      {reg && reg.length > 0 && (
        <div>
          <span className="text-slate-500 text-xs">Registry / Eureka</span>
          <ul className="mt-1 space-y-0.5">
            {reg.map((u, i) => (
              <li key={i} className="text-[11px] font-mono text-amber-200/90 break-all">{u}</li>
            ))}
          </ul>
        </div>
      )}
      {outboundBe && outboundBe.length > 0 && (
        <div>
          <span className="text-slate-500 text-xs">Backend targets (HTTP / Feign / config)</span>
          <ul className="mt-1 space-y-0.5 max-h-28 overflow-y-auto">
            {outboundBe.map((h, i) => (
              <li key={i} className="text-[11px] font-mono text-orange-300/95 break-all">{h}</li>
            ))}
          </ul>
        </div>
      )}
      {vite && vite.length > 0 && (
        <div>
          <span className="text-slate-500 text-xs">Dev proxy (Vite)</span>
          <ul className="mt-1 space-y-1">
            {vite.map((p, i) => (
              <li key={i} className="text-[11px] text-slate-300">
                <span className="text-pink-300 font-mono">{p.path}</span>
                <span className="text-slate-500 mx-1">→</span>
                <span className="font-mono text-slate-400 break-all">{p.target}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function LinkedReposBlock({ nodeId, graph }: { nodeId: string; graph: GraphDto }) {
  const wiredOut = graph.edges.filter(
    (e: GraphEdge) => e.type === 'WIRED' && e.source === nodeId,
  )
  const wiredIn = graph.edges.filter(
    (e: GraphEdge) => e.type === 'WIRED' && e.target === nodeId,
  )
  const callsOut = graph.edges.filter(
    (e: GraphEdge) =>
      e.type === 'CALLS' && e.data?.callTier === 'REPO_TO_REPO' && e.source === nodeId,
  )
  const callsIn = graph.edges.filter(
    (e: GraphEdge) =>
      e.type === 'CALLS' && e.data?.callTier === 'REPO_TO_REPO' && e.target === nodeId,
  )
  const importsOut = graph.edges.filter(
    (e: GraphEdge) => e.type === 'IMPORTS' && e.source === nodeId,
  )
  const importsIn = graph.edges.filter(
    (e: GraphEdge) => e.type === 'IMPORTS' && e.target === nodeId,
  )
  const mergeByTarget = (edges: GraphEdge[], key: 'toRepoName' | 'fromRepoName') => {
    const m = new Map<string, { name: string; endpoints: string[]; count: number }>()
    for (const e of edges) {
      const name = String(e.data?.[key] ?? '')
      if (!name) continue
      const eps = (e.data?.endpoints as string[] | undefined) ?? []
      const prev = m.get(name)
      if (prev) {
        const set = new Set([...prev.endpoints, ...eps])
        m.set(name, { name, endpoints: [...set], count: set.size })
      } else {
        m.set(name, { name, endpoints: eps, count: eps.length || 1 })
      }
    }
    return [...m.values()]
  }
  const callsOutMerged = mergeByTarget(callsOut, 'toRepoName')
  const callsInMerged = mergeByTarget(callsIn, 'fromRepoName')
  const importTargets = [...new Set(importsOut.map(e => String(e.data?.toRepoName ?? '')).filter(Boolean))]
  const importSources = [...new Set(importsIn.map(e => String(e.data?.fromRepoName ?? '')).filter(Boolean))]

  const any =
    wiredOut.length +
      wiredIn.length +
      callsOutMerged.length +
      callsInMerged.length +
      importTargets.length +
      importSources.length >
    0

  return (
    <div className="pt-3 mt-3 border-t border-slate-700 space-y-3">
      <div className="flex items-center gap-1.5 text-slate-400 text-xs font-semibold uppercase tracking-wide">
        <Link2 className="w-3.5 h-3.5" />
        Linked repos
      </div>
      {!any && (
        <p className="text-slate-500 text-xs leading-relaxed">
          No links yet. <span className="text-pink-400">Wired</span> = gateway/UI proxy from config.{' '}
          <span className="text-cyan-400">Calls</span> = code. Run a <strong>deep</strong> scan on each repo.
        </p>
      )}
      {wiredOut.length > 0 && (
        <div>
          <div className="text-pink-400 text-[11px] font-medium mb-1.5">Wired to (config)</div>
          <ul className="space-y-1.5">
            {wiredOut.map(e => (
              <li key={e.id} className="text-xs text-slate-200">
                <span className="text-white font-medium">{String(e.data?.toRepoName ?? '')}</span>
                <span className="text-slate-500 block text-[10px] mt-0.5">{e.label}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
      {wiredIn.length > 0 && (
        <div>
          <div className="text-fuchsia-400 text-[11px] font-medium mb-1.5">Wired from (config)</div>
          <ul className="space-y-1">
            {wiredIn.map(e => (
              <li key={e.id} className="text-xs text-slate-200">
                <span className="text-white font-medium">{String(e.data?.fromRepoName ?? '')}</span>
                <span className="text-slate-500 block text-[10px] mt-0.5">{e.label}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
      {callsOutMerged.length > 0 && (
        <div>
          <div className="text-cyan-400 text-[11px] font-medium mb-1.5 flex items-center gap-1">
            <ArrowRight className="w-3 h-3" />
            This repo calls (HTTP)
          </div>
          <ul className="space-y-2">
            {callsOutMerged.map(row => (
              <li key={row.name} className="text-xs">
                <span className="text-white font-medium">{row.name}</span>
                <span className="text-slate-500 ml-1">· {row.count} endpoint{row.count === 1 ? '' : 's'}</span>
                {row.endpoints.length > 0 && row.endpoints.length <= 5 && (
                  <ul className="mt-1 ml-2 text-slate-500 font-mono text-[10px] space-y-0.5 border-l border-slate-700 pl-2">
                    {row.endpoints.slice(0, 5).map(ep => (
                      <li key={ep}>{ep}</li>
                    ))}
                  </ul>
                )}
                {row.endpoints.length > 5 && (
                  <p className="mt-1 text-slate-600 text-[10px] font-mono">{row.endpoints.slice(0, 3).join(' · ')}…</p>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
      {callsInMerged.length > 0 && (
        <div>
          <div className="text-indigo-400 text-[11px] font-medium mb-1.5">Called by (HTTP)</div>
          <ul className="space-y-1">
            {callsInMerged.map(row => (
              <li key={row.name} className="text-xs text-slate-200">
                <span className="text-white font-medium">{row.name}</span>
                <span className="text-slate-500 ml-1">· uses {row.count} of your API{row.count === 1 ? '' : 's'}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
      {importTargets.length > 0 && (
        <div>
          <div className="text-purple-400 text-[11px] font-medium mb-1.5">Imports code from</div>
          <ul className="flex flex-wrap gap-1.5">
            {importTargets.map(name => (
              <li
                key={name}
                className="text-xs px-2 py-0.5 rounded bg-purple-500/15 text-purple-200 border border-purple-500/25"
              >
                {name}
              </li>
            ))}
          </ul>
        </div>
      )}
      {importSources.length > 0 && (
        <div>
          <div className="text-purple-300 text-[11px] font-medium mb-1.5">Imported by</div>
          <ul className="flex flex-wrap gap-1.5">
            {importSources.map(name => (
              <li
                key={name}
                className="text-xs px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-600"
              >
                {name}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function InfoRow({ label, value, mono }: { label: string; value?: string; mono?: boolean }) {
  if (!value) return null
  return (
    <div>
      <span className="text-slate-500 text-xs">{label}</span>
      <div className={`text-slate-200 text-sm mt-0.5 ${mono ? 'font-mono text-xs break-all' : ''}`}>
        {value}
      </div>
    </div>
  )
}
