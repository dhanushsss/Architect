import { Handle, Position, type NodeProps } from '@xyflow/react'
import { LanguageBadge } from '../../ui/Badge'

export default function RepoNode({ data, selected }: NodeProps) {
  const d = data as {
    label: string
    language?: string | null
    data: Record<string, unknown>
    zoom?: number
  }

  const epCount = (d.data?.endpointCount as number) || 0
  const scanStatus = d.data?.scanStatus as string | undefined
  const zoom = d.zoom ?? 1

  // Fix 6: level-of-detail rendering
  if (zoom < 0.35) {
    return (
      <div className={`bg-slate-800 border-2 rounded-lg px-2 py-1.5 flex items-center gap-1.5 ${
        selected ? 'border-blue-400' : 'border-blue-500/50'
      }`} style={{ minWidth: 80 }}>
        <Handle type="target" position={Position.Left} className="!bg-blue-400 !border-blue-600 !w-2 !h-2" />
        <div className="w-2 h-2 rounded-full bg-blue-400 flex-shrink-0" />
        <span className="text-white text-xs font-semibold truncate">{d.label}</span>
        <Handle type="source" position={Position.Right} className="!bg-blue-400 !border-blue-600 !w-2 !h-2" />
      </div>
    )
  }

  return (
    <div className={`bg-slate-800 border-2 rounded-xl px-4 py-3 shadow-lg ${
      selected ? 'border-blue-400 shadow-blue-400/20 ring-2 ring-blue-400/30' : 'border-blue-500/50'
    }`} style={{ minWidth: 170 }}>
      <Handle type="target" position={Position.Left} className="!bg-blue-400 !border-blue-600 !w-3 !h-3" />

      <div className="flex items-center gap-2 mb-1.5">
        <div className={`w-2.5 h-2.5 rounded-full flex-shrink-0 ${
          scanStatus === 'SCANNING' ? 'bg-indigo-400 animate-pulse' :
          scanStatus === 'COMPLETE' ? 'bg-blue-400' :
          scanStatus === 'FAILED'   ? 'bg-red-400' : 'bg-slate-500'
        }`} />
        <span className="text-white font-semibold text-sm truncate">{d.label}</span>
      </div>

      <div className="flex items-center gap-2">
        <LanguageBadge language={d.language} />
        {epCount > 0 && (
          <span className="text-slate-400 text-xs font-medium">
            {zoom >= 0.7 ? `${epCount} endpoint${epCount !== 1 ? 's' : ''}` : epCount}
          </span>
        )}
      </div>

      <Handle type="source" position={Position.Right} className="!bg-blue-400 !border-blue-600 !w-3 !h-3" />
    </div>
  )
}
