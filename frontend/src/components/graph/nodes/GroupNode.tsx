import { Handle, Position, type NodeProps } from '@xyflow/react'
import { ChevronDown } from 'lucide-react'
import { useGraphStore } from '../../../store/graphStore'

const RISK_CONFIG: Record<string, { border: string; dot: string; badge: string }> = {
  HIGH:   { border: 'border-red-500/60',    dot: 'bg-red-400',    badge: 'bg-red-500/15 text-red-400' },
  MEDIUM: { border: 'border-yellow-500/60', dot: 'bg-yellow-400', badge: 'bg-yellow-500/15 text-yellow-400' },
  LOW:    { border: 'border-green-500/40',  dot: 'bg-green-400',  badge: 'bg-green-500/15 text-green-400' },
}

export default function GroupNode({ data, selected }: NodeProps) {
  const d = data as {
    label: string
    count: number
    riskScore?: string
    callerCount?: number
    groupId: string
    zoom?: number
  }

  const { toggleGroup } = useGraphStore()
  const risk = d.riskScore ?? 'LOW'
  const cfg = RISK_CONFIG[risk] ?? RISK_CONFIG.LOW

  // Fix 6: zoom level 1 (zoomed out) — minimal info
  const isZoomedOut = (d.zoom ?? 1) < 0.45

  return (
    <div
      className={`bg-slate-800 border-2 rounded-xl shadow-lg cursor-pointer select-none transition-all hover:brightness-110 ${
        selected ? 'ring-2 ring-indigo-400' : ''
      } ${cfg.border}`}
      style={{ minWidth: 164, padding: isZoomedOut ? '6px 12px' : '8px 14px' }}
      onDoubleClick={() => toggleGroup(d.groupId)}
      title="Double-click to expand"
    >
      <Handle type="target" position={Position.Left} className="!bg-slate-400 !border-slate-600 !w-3 !h-3" />

      {isZoomedOut ? (
        /* Level 1: just dot + count */
        <div className="flex items-center gap-1.5">
          <span className={`w-2.5 h-2.5 rounded-full flex-shrink-0 ${cfg.dot}`} />
          <span className="text-slate-200 text-xs font-semibold truncate">{d.label}</span>
          <span className="ml-auto text-slate-500 text-xs">{d.count}</span>
        </div>
      ) : (
        /* Level 2+: full info */
        <>
          <div className="flex items-center gap-2 mb-1">
            <span className={`w-2 h-2 rounded-full flex-shrink-0 ${cfg.dot}`} />
            <span className="text-white font-semibold text-sm truncate capitalize">{d.label}</span>
            <span className={`ml-auto text-xs font-bold px-1.5 py-0.5 rounded ${cfg.badge}`}>
              {risk}
            </span>
          </div>
          <div className="flex items-center gap-2 text-xs text-slate-400">
            <span>{d.count} endpoint{d.count !== 1 ? 's' : ''}</span>
            {(d.callerCount ?? 0) > 0 && (
              <span className="text-slate-500">· {d.callerCount} caller{d.callerCount !== 1 ? 's' : ''}</span>
            )}
            <span className="ml-auto flex items-center gap-0.5 text-slate-600">
              <ChevronDown className="w-3 h-3" /> expand
            </span>
          </div>
        </>
      )}

      <Handle type="source" position={Position.Right} className="!bg-slate-400 !border-slate-600 !w-3 !h-3" />
    </div>
  )
}
