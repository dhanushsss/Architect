import { useQuery } from '@tanstack/react-query'
import Navbar from '../components/layout/Navbar'
import { insightsApi, aiApi } from '../services/api'
import { useState } from 'react'
import { Layers, Sparkles } from 'lucide-react'

export default function InsightsPage() {
  const { data: s } = useQuery({ queryKey: ['insights-summary'], queryFn: insightsApi.summary })
  const [aiText, setAiText] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  const runExplain = async () => {
    setLoading(true)
    try {
      const r = await aiApi.explainPrRisk({
        verdict: 'REVIEW REQUIRED',
        numericScore: 5,
        scenario: 'WIDE_CASCADING',
        riskFactors: [
          'Multiple services depend on shared APIs.',
          'Graph may be stale if not rescanned recently.',
        ],
      })
      setAiText(r.explanation)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-slate-900">
      <Navbar />
      <main className="max-w-3xl mx-auto p-6 space-y-8">
        <div className="flex items-center gap-2 text-white">
          <Layers className="w-7 h-7 text-indigo-400" />
          <h1 className="text-2xl font-bold">Architecture snapshot</h1>
        </div>

        <div className="grid grid-cols-2 gap-4">
          {(
            [
              ['Connected repos', s?.connectedRepos],
              ['Indexed endpoints', s?.indexedEndpoints],
              ['Cross-repo API links (code)', s?.trackedCrossRepoCallEdges],
              ['Runtime wiring links', s?.runtimeCrossRepoWires],
              ['Repos needing rescan (7d+)', s?.reposNeedingRescan],
            ] as const
          ).map(([k, v]) => (
            <div key={k} className="bg-slate-800/60 border border-slate-700 rounded-xl p-4">
              <div className="text-slate-500 text-xs uppercase tracking-wide">{k}</div>
              <div className="text-2xl font-semibold text-white mt-1">
                {v != null ? String(v) : '—'}
              </div>
            </div>
          ))}
        </div>

        <div className="bg-slate-800/40 border border-slate-700 rounded-xl p-5">
          <div className="flex items-center gap-2 text-indigo-300 mb-3">
            <Sparkles className="w-5 h-5" />
            <span className="font-medium">AI explanation (not chat)</span>
          </div>
          <p className="text-slate-400 text-sm mb-3">
            One-shot narrative for execs. Uses sample context unless you wire a real PR row.
          </p>
          <button
            type="button"
            onClick={runExplain}
            disabled={loading}
            className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 rounded-lg text-sm text-white"
          >
            {loading ? '…' : 'Generate sample risk narrative'}
          </button>
          {aiText && (
            <div className="mt-4 text-slate-200 text-sm leading-relaxed whitespace-pre-wrap border-t border-slate-700 pt-4">
              {aiText}
            </div>
          )}
        </div>
      </main>
    </div>
  )
}
