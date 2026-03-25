import { useEffect, useState } from 'react'
import { HelpCircle } from 'lucide-react'
import Navbar from '../components/layout/Navbar'
import api from '../services/api'

/** Matches backend `GovernanceService` heuristics for endpoint health. */
const ENDPOINT_STATUS_TIPS = {
  ACTIVE:
    'At least one API call from your connected repos was matched to this endpoint, and it is not flagged by the v1→v2 deprecation heuristic.',
  ORPHANED:
    'No scanned calls from your connected repos resolve to this endpoint. It may be unused, only called from outside the graph, or use URL patterns we could not match.',
  DEPRECATED:
    'This path is under /v1/ while the same repo also has routes under /v2/ — Zerqis treats that as potentially superseded (heuristic).',
} as const

function StatLabel({ label, tip }: { label: string; tip?: string }) {
  if (!tip) return <p className="text-xs text-gray-400 mb-1">{label}</p>
  return (
    <p className="text-xs text-gray-400 mb-1">
      <span className="inline-flex items-center gap-1 cursor-help" title={tip}>
        {label}
        <HelpCircle className="w-3.5 h-3.5 text-gray-500 shrink-0" aria-hidden />
      </span>
    </p>
  )
}

type GovTab = 'dashboard' | 'soc2' | 'snapshots'

type GovStatCard = { label: string; value: string | number; color: string; tip?: string }

export default function GovernancePage() {
  const [tab, setTab] = useState<GovTab>('dashboard')
  const [governance, setGovernance] = useState<any>(null)
  const [soc2, setSoc2] = useState<any>(null)
  const [snapshots, setSnapshots] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const [snapshotLabel, setSnapshotLabel] = useState('')
  const [creating, setCreating] = useState(false)
  const [diff, setDiff] = useState<any>(null)
  const [diffA, setDiffA] = useState<string>('')
  const [diffB, setDiffB] = useState<string>('')

  useEffect(() => {
    if (tab === 'dashboard' && !governance) loadGovernance()
    if (tab === 'soc2' && !soc2) loadSoc2()
    if (tab === 'snapshots') loadSnapshots()
  }, [tab])

  const loadGovernance = async () => {
    setLoading(true)
    try {
      const { data } = await api.get('/enterprise/governance')
      setGovernance(data)
    } finally { setLoading(false) }
  }

  const loadSoc2 = async () => {
    setLoading(true)
    try {
      const { data } = await api.get('/enterprise/audit/soc2')
      setSoc2(data)
    } finally { setLoading(false) }
  }

  const loadSnapshots = async () => {
    setLoading(true)
    try {
      const { data } = await api.get('/enterprise/snapshots')
      setSnapshots(data)
    } finally { setLoading(false) }
  }

  const createSnapshot = async () => {
    setCreating(true)
    try {
      await api.post('/enterprise/snapshots', { label: snapshotLabel || undefined })
      setSnapshotLabel('')
      await loadSnapshots()
    } finally { setCreating(false) }
  }

  const computeDiff = async () => {
    if (!diffA || !diffB) return
    const { data } = await api.get(`/enterprise/snapshots/${diffA}/diff/${diffB}`)
    setDiff(data)
  }

  const statusColor = (s: string) =>
    s === 'ACTIVE' ? 'text-green-400' : s === 'ORPHANED' ? 'text-yellow-400' : 'text-red-400'

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <Navbar />
      <div className="max-w-6xl mx-auto px-6 py-8">
        <h1 className="text-2xl font-bold mb-6">🏛️ Governance & Compliance</h1>

        {/* Tabs */}
        <div className="flex gap-1 bg-gray-900 p-1 rounded-xl w-fit mb-6">
          {[
            { id: 'dashboard', label: '📊 API Dashboard' },
            { id: 'soc2', label: '🔒 SOC2 Audit' },
            { id: 'snapshots', label: '📷 Snapshots' },
          ].map(t => (
            <button
              key={t.id}
              onClick={() => setTab(t.id as GovTab)}
              className={`px-4 py-2 rounded-lg text-sm transition-colors ${tab === t.id ? 'bg-indigo-600 text-white' : 'text-gray-400 hover:text-gray-200'}`}
            >
              {t.label}
            </button>
          ))}
        </div>

        {/* API Dashboard */}
        {tab === 'dashboard' && (
          loading ? <p className="text-gray-400">Loading...</p> : governance ? (
            <div className="space-y-6">
              {/* Stats */}
              <div className="grid grid-cols-4 gap-4">
                {([
                  { label: 'Total Endpoints', value: governance.totalEndpoints, color: 'text-indigo-400' },
                  {
                    label: 'Orphaned',
                    value: governance.orphanedEndpoints,
                    color: 'text-yellow-400',
                    tip: ENDPOINT_STATUS_TIPS.ORPHANED,
                  },
                  {
                    label: 'Deprecated',
                    value: governance.deprecatedEndpoints,
                    color: 'text-red-400',
                    tip: ENDPOINT_STATUS_TIPS.DEPRECATED,
                  },
                  {
                    label: 'Health Score',
                    value: governance.healthScore + '%',
                    color: governance.healthScore > 70 ? 'text-green-400' : 'text-orange-400',
                  },
                ] satisfies GovStatCard[]).map(s => (
                  <div key={s.label} className="bg-gray-800 rounded-xl p-4">
                    <StatLabel label={s.label} tip={s.tip} />
                    <p className={`text-2xl font-bold ${s.color}`}>{s.value}</p>
                  </div>
                ))}
              </div>

              {/* Summary bullets */}
              <div className="bg-gray-800 rounded-xl p-4">
                <h3 className="text-sm font-semibold mb-3">Summary</h3>
                <ul className="space-y-1">
                  {(governance.summary as string[]).map((s: string, i: number) => (
                    <li key={i} className="text-sm text-gray-300 flex items-center gap-2">
                      <span className="text-indigo-400">•</span> {s}
                    </li>
                  ))}
                </ul>
              </div>

              {/* Endpoint table */}
              <div className="bg-gray-800 rounded-xl overflow-hidden">
                <div className="px-4 py-3 border-b border-gray-700 flex justify-between items-center">
                  <h3 className="text-sm font-semibold">All Endpoints</h3>
                  <span className="text-xs text-gray-400">{governance.endpoints?.length} total</span>
                </div>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-xs text-gray-400 border-b border-gray-700">
                        <th className="text-left px-4 py-2">Repo</th>
                        <th className="text-left px-4 py-2">Method</th>
                        <th className="text-left px-4 py-2">Path</th>
                        <th className="text-left px-4 py-2">Framework</th>
                        <th className="text-left px-4 py-2">Callers</th>
                        <th className="text-left px-4 py-2">
                          <span
                            className="inline-flex items-center gap-1 cursor-help"
                            title={`ACTIVE — ${ENDPOINT_STATUS_TIPS.ACTIVE} ORPHANED — ${ENDPOINT_STATUS_TIPS.ORPHANED} DEPRECATED — ${ENDPOINT_STATUS_TIPS.DEPRECATED}`}
                          >
                            Status
                            <HelpCircle className="w-3.5 h-3.5 text-gray-500" aria-hidden />
                          </span>
                        </th>
                      </tr>
                    </thead>
                    <tbody>
                      {(governance.endpoints as any[]).slice(0, 50).map((ep: any) => (
                        <tr key={ep.id} className="border-b border-gray-700/50 hover:bg-gray-700/30">
                          <td className="px-4 py-2 text-gray-300">{ep.repo}</td>
                          <td className="px-4 py-2">
                            <span className={`text-xs font-mono px-1.5 py-0.5 rounded ${ep.method === 'GET' ? 'bg-green-900 text-green-300' : ep.method === 'POST' ? 'bg-blue-900 text-blue-300' : 'bg-orange-900 text-orange-300'}`}>
                              {ep.method}
                            </span>
                          </td>
                          <td className="px-4 py-2 font-mono text-xs text-gray-300">{ep.path}</td>
                          <td className="px-4 py-2 text-xs text-gray-400">{ep.framework}</td>
                          <td className="px-4 py-2 text-center text-gray-300">{ep.callerCount}</td>
                          <td className="px-4 py-2">
                            <span
                              className={`text-xs cursor-help border-b border-dotted border-gray-600 ${statusColor(ep.status)}`}
                              title={
                                ep.status === 'ACTIVE'
                                  ? ENDPOINT_STATUS_TIPS.ACTIVE
                                  : ep.status === 'ORPHANED'
                                    ? ENDPOINT_STATUS_TIPS.ORPHANED
                                    : ep.status === 'DEPRECATED'
                                      ? ENDPOINT_STATUS_TIPS.DEPRECATED
                                      : undefined
                              }
                            >
                              {ep.status}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            </div>
          ) : null
        )}

        {/* SOC2 Audit */}
        {tab === 'soc2' && (
          loading ? <p className="text-gray-400">Generating SOC2 report...</p> : soc2 ? (
            <div className="space-y-6">
              <div className="bg-gray-800 rounded-xl p-5">
                <div className="flex justify-between items-start mb-4">
                  <div>
                    <h2 className="font-semibold">SOC2 Dependency Audit Report</h2>
                    <p className="text-xs text-gray-400 mt-1">Generated: {new Date(soc2.generatedAt).toLocaleString()}</p>
                  </div>
                  <span className="text-xs bg-indigo-800 text-indigo-200 px-2 py-1 rounded">{soc2.totalRepos} repos</span>
                </div>

                <h3 className="text-sm font-semibold mb-3">Compliance Checklist</h3>
                <div className="space-y-2">
                  {soc2.compliance && Object.entries(soc2.compliance).map(([key, val]: any) => (
                    <div key={key} className="flex items-center gap-3">
                      <span className={val ? 'text-green-400' : 'text-red-400'}>{val ? '✅' : '❌'}</span>
                      <span className="text-sm text-gray-300">{key.replace(/([A-Z])/g, ' $1').trim()}</span>
                    </div>
                  ))}
                </div>
              </div>

              <div className="bg-gray-800 rounded-xl overflow-hidden">
                <div className="px-4 py-3 border-b border-gray-700 flex justify-between items-center">
                  <h3 className="text-sm font-semibold">Sensitive Endpoints</h3>
                  <span className="text-xs text-yellow-400">{soc2.sensitiveEndpointCount} found</span>
                </div>
                {soc2.sensitiveEndpoints?.length === 0 ? (
                  <p className="px-4 py-6 text-gray-400 text-sm text-center">No sensitive endpoints detected</p>
                ) : (
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-xs text-gray-400 border-b border-gray-700">
                        <th className="text-left px-4 py-2">Repo</th>
                        <th className="text-left px-4 py-2">Method</th>
                        <th className="text-left px-4 py-2">Path</th>
                        <th className="text-left px-4 py-2">Category</th>
                      </tr>
                    </thead>
                    <tbody>
                      {soc2.sensitiveEndpoints?.map((ep: any, i: number) => (
                        <tr key={i} className="border-b border-gray-700/50 hover:bg-gray-700/30">
                          <td className="px-4 py-2 text-gray-300">{ep.repo}</td>
                          <td className="px-4 py-2">
                            <span className="text-xs font-mono bg-gray-700 px-1.5 py-0.5 rounded">{ep.method}</span>
                          </td>
                          <td className="px-4 py-2 font-mono text-xs text-gray-300">{ep.path}</td>
                          <td className="px-4 py-2">
                            <span className="text-xs bg-red-900 text-red-300 px-1.5 py-0.5 rounded">{ep.category}</span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </div>
          ) : null
        )}

        {/* Snapshots */}
        {tab === 'snapshots' && (
          <div className="space-y-6">
            <div className="bg-gray-800 rounded-xl p-4">
              <h3 className="text-sm font-semibold mb-3">Create New Snapshot</h3>
              <div className="flex gap-3">
                <input
                  value={snapshotLabel}
                  onChange={e => setSnapshotLabel(e.target.value)}
                  placeholder="Snapshot label (optional)"
                  className="flex-1 px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-sm focus:outline-none focus:border-indigo-500"
                />
                <button
                  onClick={createSnapshot}
                  disabled={creating}
                  className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 rounded-lg text-sm font-medium"
                >
                  {creating ? 'Creating...' : '📷 Capture'}
                </button>
              </div>
            </div>

            {snapshots.length > 0 && (
              <div className="bg-gray-800 rounded-xl p-4">
                <h3 className="text-sm font-semibold mb-3">Compare Snapshots (Diff)</h3>
                <div className="flex gap-3 items-end">
                  <div className="flex-1">
                    <label className="text-xs text-gray-400 mb-1 block">Snapshot A</label>
                    <select value={diffA} onChange={e => setDiffA(e.target.value)}
                      className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-sm focus:outline-none">
                      <option value="">Select...</option>
                      {snapshots.map(s => <option key={s.id} value={s.id}>{s.label || 'Snapshot ' + s.id} — {new Date(s.createdAt).toLocaleDateString()}</option>)}
                    </select>
                  </div>
                  <div className="flex-1">
                    <label className="text-xs text-gray-400 mb-1 block">Snapshot B</label>
                    <select value={diffB} onChange={e => setDiffB(e.target.value)}
                      className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-sm focus:outline-none">
                      <option value="">Select...</option>
                      {snapshots.map(s => <option key={s.id} value={s.id}>{s.label || 'Snapshot ' + s.id} — {new Date(s.createdAt).toLocaleDateString()}</option>)}
                    </select>
                  </div>
                  <button onClick={computeDiff} disabled={!diffA || !diffB}
                    className="px-4 py-2 bg-purple-600 hover:bg-purple-500 disabled:opacity-50 rounded-lg text-sm font-medium">
                    Compare
                  </button>
                </div>
                {diff && (
                  <div className="mt-4 grid grid-cols-4 gap-3">
                    {[
                      { label: 'Added Nodes', value: diff.addedNodes, color: 'text-green-400' },
                      { label: 'Removed Nodes', value: diff.removedNodes, color: 'text-red-400' },
                      { label: 'Added Edges', value: diff.addedEdges, color: 'text-green-400' },
                      { label: 'Removed Edges', value: diff.removedEdges, color: 'text-red-400' },
                    ].map(s => (
                      <div key={s.label} className="bg-gray-700 rounded-lg p-3">
                        <p className="text-xs text-gray-400">{s.label}</p>
                        <p className={`text-xl font-bold ${s.color}`}>{s.value}</p>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            )}

            <div className="space-y-3">
              {loading ? <p className="text-gray-400">Loading snapshots...</p> :
               snapshots.length === 0 ? (
                 <p className="text-center py-8 text-gray-500">No snapshots yet. Capture your first snapshot above.</p>
               ) : (
                 snapshots.map(s => (
                   <div key={s.id} className="bg-gray-800 rounded-xl p-4 flex items-center justify-between">
                     <div>
                       <p className="font-medium text-sm">{s.label || 'Snapshot ' + s.id}</p>
                       <p className="text-xs text-gray-400 mt-0.5">{new Date(s.createdAt).toLocaleString()}</p>
                     </div>
                     <div className="flex gap-4 text-xs text-gray-400">
                       <span>🔵 {s.nodeCount} nodes</span>
                       <span>→ {s.edgeCount} edges</span>
                     </div>
                   </div>
                 ))
               )}
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
