import { Handle, Position, type NodeProps } from '@xyflow/react'

const METHOD_COLORS: Record<string, string> = {
  GET:    'text-green-400 bg-green-500/10',
  POST:   'text-blue-400 bg-blue-500/10',
  PUT:    'text-yellow-400 bg-yellow-500/10',
  DELETE: 'text-red-400 bg-red-500/10',
  PATCH:  'text-purple-400 bg-purple-500/10',
  DEFAULT:'text-slate-400 bg-slate-500/10',
}

// Fix 7: node border / bg tinted by risk score
const RISK_STYLE: Record<string, { border: string; bg: string; badge: string }> = {
  HIGH:   { border: 'border-red-500/70',    bg: 'bg-red-950/40',    badge: 'bg-red-500/20 text-red-300' },
  MEDIUM: { border: 'border-yellow-500/60', bg: 'bg-yellow-950/30', badge: 'bg-yellow-500/20 text-yellow-300' },
  LOW:    { border: 'border-green-500/30',  bg: 'bg-slate-800',     badge: '' },
}

// Fix 7: shorten long paths — keep last 2 segments
function shortPath(path: string): string {
  const segs = path.split('/').filter(Boolean)
  if (segs.length <= 3) return '/' + segs.join('/')
  return '/…/' + segs.slice(-2).join('/')
}

export default function ApiNode({ data, selected }: NodeProps) {
  const d = data as { label: string; language?: string; data: Record<string, unknown>; zoom?: number }
  const parts = (d.label || '').split(' ')
  const method = parts[0]
  const rawPath = parts.slice(1).join(' ')
  const methodColor = METHOD_COLORS[method] || METHOD_COLORS.DEFAULT

  const riskScore = d.data?.riskScore as string | undefined
  const callerCount = (d.data?.callerCount as number) || 0
  const framework = d.data?.framework as string | undefined
  const risk = RISK_STYLE[riskScore ?? 'LOW'] ?? RISK_STYLE.LOW

  const zoom = d.zoom ?? 1

  // Fix 6: zoom level rendering
  if (zoom < 0.35) {
    // Level 1 — just method dot
    return (
      <div className={`rounded-lg border ${risk.border} ${risk.bg} px-2 py-1.5 flex items-center gap-1.5`}
           style={{ minWidth: 80 }}>
        <Handle type="target" position={Position.Left} className="!bg-green-400 !border-green-600 !w-2 !h-2" />
        <span className={`text-xs font-bold px-1 rounded font-mono ${methodColor}`}>{method}</span>
        <Handle type="source" position={Position.Right} className="!bg-green-400 !border-green-600 !w-2 !h-2" />
      </div>
    )
  }

  if (zoom < 0.7) {
    // Level 2 — method + short path + risk badge
    return (
      <div className={`rounded-xl border-2 px-3 py-2 shadow-lg ${risk.border} ${risk.bg} ${selected ? 'ring-2 ring-green-400' : ''}`}
           style={{ minWidth: 160 }}>
        <Handle type="target" position={Position.Left} className="!bg-green-400 !border-green-600 !w-3 !h-3" />
        <div className="flex items-center gap-2">
          <span className={`text-xs font-bold px-1.5 py-0.5 rounded font-mono ${methodColor}`}>{method}</span>
          <span className="text-slate-200 text-xs font-mono truncate">{shortPath(rawPath)}</span>
          {riskScore && riskScore !== 'LOW' && (
            <span className={`ml-auto text-xs font-bold px-1 rounded ${risk.badge}`}>{riskScore[0]}</span>
          )}
        </div>
        <Handle type="source" position={Position.Right} className="!bg-green-400 !border-green-600 !w-3 !h-3" />
      </div>
    )
  }

  // Level 3 — full detail
  return (
    <div
      className={`border-2 rounded-xl px-3 py-2.5 shadow-lg group ${risk.border} ${risk.bg} ${selected ? 'ring-2 ring-green-400' : ''}`}
      style={{ minWidth: 180, maxWidth: 220 }}
    >
      <Handle type="target" position={Position.Left} className="!bg-green-400 !border-green-600 !w-3 !h-3" />

      {/* Method + path */}
      <div className="flex items-center gap-2 mb-1">
        <span className={`text-xs font-bold px-1.5 py-0.5 rounded font-mono flex-shrink-0 ${methodColor}`}>{method}</span>
        <span className="text-slate-200 text-xs font-mono truncate">{shortPath(rawPath)}</span>
      </div>

      {/* Risk badge + caller count */}
      <div className="flex items-center gap-2">
        {riskScore && riskScore !== 'LOW' && (
          <span className={`text-xs font-semibold px-1.5 py-0.5 rounded ${risk.badge}`}>
            {riskScore === 'HIGH' ? '🔴' : '🟡'} {riskScore}
          </span>
        )}
        {callerCount > 0 && (
          <span className="text-slate-500 text-xs">{callerCount} caller{callerCount !== 1 ? 's' : ''}</span>
        )}
        {/* Fix 7: framework on hover only */}
        {framework && (
          <span className="text-slate-600 text-xs opacity-0 group-hover:opacity-100 transition-opacity ml-auto truncate">
            {framework}
          </span>
        )}
      </div>

      <Handle type="source" position={Position.Right} className="!bg-green-400 !border-green-600 !w-3 !h-3" />
    </div>
  )
}
