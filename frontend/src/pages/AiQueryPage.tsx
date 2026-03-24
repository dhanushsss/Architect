import { useState, useEffect } from 'react'
import Navbar from '../components/layout/Navbar'
import api from '../services/api'
import { AlertTriangle, BarChart2 } from 'lucide-react'

type View = 'anomalies' | 'techdebt'

export default function AiQueryPage() {
  const [view, setView] = useState<View>('anomalies')
  const [loading, setLoading] = useState(false)
  const [anomalies, setAnomalies] = useState<any[]>([])
  const [techDebt, setTechDebt] = useState<any>(null)

  // Auto-load anomalies on first render
  useEffect(() => { loadAnomalies() }, [])

  const loadAnomalies = async () => {
    setView('anomalies')
    if (anomalies.length > 0) return   // already loaded
    setLoading(true)
    try {
      const { data } = await api.get('/ai/anomalies')
      setAnomalies(data)
    } finally {
      setLoading(false)
    }
  }

  const loadTechDebt = async () => {
    setView('techdebt')
    if (techDebt) return               // already loaded
    setLoading(true)
    try {
      const { data } = await api.get('/ai/tech-debt')
      setTechDebt(data)
    } finally {
      setLoading(false)
    }
  }

  const severityBadge = (s: string) =>
    s === 'HIGH'   ? 'bg-red-900/60 text-red-300 border border-red-700' :
    s === 'MEDIUM' ? 'bg-yellow-900/60 text-yellow-300 border border-yellow-700' :
                     'bg-slate-700 text-slate-300 border border-slate-600'

  const riskColor = (r: string) =>
    r === 'HIGH' ? 'text-red-400' : r === 'MEDIUM' ? 'text-yellow-400' : 'text-green-400'

  return (
    <div className="min-h-screen bg-slate-900">
      <Navbar />
      <main className="max-w-4xl mx-auto p-6">

        {/* Header */}
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-white">Codebase Insights</h1>
          <p className="text-slate-400 text-sm mt-1">Automated analysis of anomalies and technical debt across your repos</p>
        </div>

        {/* Tab switcher */}
        <div className="flex gap-2 mb-6">
          <button
            onClick={loadAnomalies}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              view === 'anomalies' ? 'bg-red-500/20 text-red-400 border border-red-500/40' : 'text-slate-400 hover:text-white hover:bg-slate-800'
            }`}
          >
            <AlertTriangle className="w-4 h-4" />
            Anomaly Detection
          </button>
          <button
            onClick={loadTechDebt}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
              view === 'techdebt' ? 'bg-amber-500/20 text-amber-400 border border-amber-500/40' : 'text-slate-400 hover:text-white hover:bg-slate-800'
            }`}
          >
            <BarChart2 className="w-4 h-4" />
            Tech Debt Radar
          </button>
        </div>

        {/* ── Anomaly Detection ─────────────────────────────────────────────── */}
        {view === 'anomalies' && (
          <div>
            {loading ? (
              <div className="text-center py-16 text-slate-500">
                <div className="w-6 h-6 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin mx-auto mb-3" />
                Scanning repos for anomalies…
              </div>
            ) : anomalies.length === 0 ? (
              <div className="card text-center py-16">
                <div className="text-4xl mb-3">✅</div>
                <p className="text-white font-medium">No anomalies detected</p>
                <p className="text-slate-400 text-sm mt-1">Scan your repos first to run analysis</p>
              </div>
            ) : (
              <div className="space-y-3">
                {/* Summary counts */}
                <div className="flex gap-3 mb-4">
                  {(['HIGH', 'MEDIUM', 'LOW'] as const).map(sev => {
                    const count = anomalies.filter(a => a.severity === sev).length
                    return count > 0 ? (
                      <span key={sev} className={`text-xs font-semibold px-2.5 py-1 rounded-full ${severityBadge(sev)}`}>
                        {count} {sev}
                      </span>
                    ) : null
                  })}
                </div>
                {anomalies.map((a, i) => (
                  <div key={i} className="card flex items-start gap-3">
                    <span className={`text-xs font-bold px-2 py-0.5 rounded mt-0.5 whitespace-nowrap ${severityBadge(a.severity)}`}>
                      {a.severity}
                    </span>
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-white">{a.type?.replace(/_/g, ' ')}</p>
                      <p className="text-xs text-slate-400 mt-0.5">{a.description}</p>
                      {a.repo && (
                        <p className="text-xs text-slate-500 mt-1">
                          📦 {a.repo}{a.file ? ` — ${a.file}${a.line ? ':' + a.line : ''}` : ''}
                        </p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ── Tech Debt Radar ───────────────────────────────────────────────── */}
        {view === 'techdebt' && (
          <div>
            {loading ? (
              <div className="text-center py-16 text-slate-500">
                <div className="w-6 h-6 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin mx-auto mb-3" />
                Analysing tech debt…
              </div>
            ) : !techDebt ? null : (
              <div className="space-y-4">
                {/* Overall risk banner */}
                <div className="card flex items-center justify-between">
                  <div>
                    <p className="text-xs text-slate-400 uppercase tracking-wider">Overall Risk</p>
                    <p className={`text-3xl font-bold mt-1 ${riskColor(techDebt.overallRisk)}`}>{techDebt.overallRisk}</p>
                  </div>
                  <div className="flex gap-4 text-center">
                    <div>
                      <p className="text-2xl font-bold text-yellow-400">{techDebt.orphanEndpoints}</p>
                      <p className="text-xs text-slate-400 mt-0.5">Orphan endpoints</p>
                    </div>
                    <div>
                      <p className="text-2xl font-bold text-red-400">{techDebt.brokenCalls}</p>
                      <p className="text-xs text-slate-400 mt-0.5">Broken calls</p>
                    </div>
                  </div>
                </div>

                {/* Recommendations */}
                {techDebt.recommendations?.length > 0 && (
                  <div className="card">
                    <h3 className="text-sm font-semibold text-white mb-3">Recommendations</h3>
                    <ul className="space-y-2">
                      {(techDebt.recommendations as string[]).map((r, i) => (
                        <li key={i} className="flex items-start gap-2 text-sm text-slate-300">
                          <span className="text-indigo-400 mt-0.5 shrink-0">→</span> {r}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                {/* Per-repo debt scores */}
                {techDebt.repoDebtScores && Object.keys(techDebt.repoDebtScores).length > 0 && (
                  <div className="card">
                    <h3 className="text-sm font-semibold text-white mb-3">Debt Score by Repo</h3>
                    <div className="space-y-3">
                      {Object.entries(techDebt.repoDebtScores as Record<string, number>)
                        .sort(([, a], [, b]) => b - a)
                        .map(([repo, score]) => (
                          <div key={repo} className="flex items-center gap-3">
                            <span className="text-sm text-slate-300 w-36 truncate shrink-0">{repo}</span>
                            <div className="flex-1 bg-slate-700 rounded-full h-2">
                              <div
                                className={`h-2 rounded-full ${score > 60 ? 'bg-red-500' : score > 30 ? 'bg-yellow-500' : 'bg-indigo-500'}`}
                                style={{ width: `${Math.min(100, score)}%` }}
                              />
                            </div>
                            <span className="text-xs text-slate-400 w-8 text-right shrink-0">{score}</span>
                          </div>
                        ))}
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>
        )}

      </main>
    </div>
  )
}
