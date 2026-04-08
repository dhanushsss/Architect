import type { ImpactDto } from '../../types'

interface Props {
  impact: ImpactDto
}

/**
 * Visual confidence breakdown: shows WHY Zerqis is confident (or not) in its analysis.
 * Renders the same signal tree that PR comments show, but interactive and in the UI.
 */
export default function ConfidenceBreakdown({ impact }: Props) {
  const score = impact.confidenceScore
  if (score == null) return null

  const signals = buildSignals(impact)
  const color = score >= 80 ? 'green' : score >= 50 ? 'yellow' : 'red'
  const colorMap = {
    green: { bar: 'bg-green-500', text: 'text-green-400', ring: 'ring-green-500/30' },
    yellow: { bar: 'bg-yellow-500', text: 'text-yellow-400', ring: 'ring-yellow-500/30' },
    red: { bar: 'bg-red-500', text: 'text-red-400', ring: 'ring-red-500/30' },
  }
  const c = colorMap[color]

  return (
    <div className={`card ring-1 ${c.ring}`}>
      <div className="flex items-center justify-between mb-3">
        <h3 className="font-semibold text-white text-sm">Analysis Confidence</h3>
        <span className={`text-lg font-bold ${c.text}`}>{Math.round(score)}%</span>
      </div>

      {/* Progress bar */}
      <div className="w-full bg-slate-700 rounded-full h-2 mb-4">
        <div
          className={`${c.bar} h-2 rounded-full transition-all duration-500`}
          style={{ width: `${Math.min(score, 100)}%` }}
        />
      </div>

      {/* Signal tree */}
      <div className="space-y-1.5">
        {signals.map((s, i) => {
          const isLast = i === signals.length - 1
          return (
            <div key={s.label} className="flex items-start gap-2 text-xs">
              <span className="text-slate-600 font-mono whitespace-pre">
                {isLast ? '\u2514\u2500\u2500 ' : '\u251C\u2500\u2500 '}
              </span>
              <span className="shrink-0">{s.icon}</span>
              <span className="text-slate-300">
                <strong className="text-white">{s.count}</strong> {s.label}
              </span>
              <span className={`ml-auto text-xs ${s.strength === 'strong' ? 'text-green-500' : s.strength === 'medium' ? 'text-yellow-500' : 'text-slate-500'}`}>
                {s.strength}
              </span>
            </div>
          )
        })}
      </div>

      {/* Verdict */}
      {impact.verdict && (
        <div className={`mt-3 pt-3 border-t border-slate-700 text-xs ${c.text}`}>
          {impact.verdict === 'SAFE TO MERGE' && '\u2705 '}
          {impact.verdict === 'REVIEW REQUIRED' && '\u26A0\uFE0F '}
          {impact.verdict === 'BLOCKED' && '\uD83D\uDEA8 '}
          <strong>{impact.verdict}</strong>
          {impact.numericScore != null && (
            <span className="text-slate-500 ml-2">({impact.numericScore.toFixed(1)}/10)</span>
          )}
        </div>
      )}

      {/* Risk factors */}
      {impact.riskFactors && impact.riskFactors.length > 0 && (
        <div className="mt-3 pt-3 border-t border-slate-700">
          <p className="text-xs text-slate-500 mb-1.5">Why this risk:</p>
          <div className="space-y-1">
            {impact.riskFactors.slice(0, 4).map((factor, i) => (
              <p key={i} className="text-xs text-slate-400 leading-relaxed"
                 dangerouslySetInnerHTML={{ __html: factor }} />
            ))}
            {impact.riskFactors.length > 4 && (
              <p className="text-xs text-slate-600">+{impact.riskFactors.length - 4} more</p>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

interface Signal {
  icon: string
  count: number
  label: string
  strength: 'strong' | 'medium' | 'unknown'
}

function buildSignals(impact: ImpactDto): Signal[] {
  const signals: Signal[] = []
  if (impact.directMatchCount && impact.directMatchCount > 0) {
    signals.push({
      icon: '\u25CF',  // filled circle
      count: impact.directMatchCount,
      label: 'direct match(es)',
      strength: 'strong',
    })
  }
  if (impact.inferredMatchCount && impact.inferredMatchCount > 0) {
    signals.push({
      icon: '\u25D0',  // half circle
      count: impact.inferredMatchCount,
      label: 'inferred match(es)',
      strength: 'medium',
    })
  }
  if (impact.unresolvedCallCount && impact.unresolvedCallCount > 0) {
    signals.push({
      icon: '\u25CB',  // empty circle
      count: impact.unresolvedCallCount,
      label: 'unresolved call(s)',
      strength: 'unknown',
    })
  }
  if (impact.staleRepoCount && impact.staleRepoCount > 0) {
    signals.push({
      icon: '\u25CB',  // empty circle
      count: impact.staleRepoCount,
      label: 'repo(s) stale (>72h)',
      strength: 'unknown',
    })
  }
  if (impact.unscannedRepoCount && impact.unscannedRepoCount > 0) {
    signals.push({
      icon: '\u25CB',
      count: impact.unscannedRepoCount,
      label: 'repo(s) never scanned',
      strength: 'unknown',
    })
  }
  if (impact.changedFilesNotFetched && impact.changedFilesNotFetched > 0) {
    signals.push({
      icon: '\u25CB',
      count: impact.changedFilesNotFetched,
      label: 'file(s) not analyzed',
      strength: 'unknown',
    })
  }
  return signals
}
