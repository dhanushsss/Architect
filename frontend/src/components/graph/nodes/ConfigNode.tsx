import { Handle, Position, type NodeProps } from '@xyflow/react'

export default function ConfigNode({ data, selected }: NodeProps) {
  const nodeData = data as { label: string; data: Record<string, unknown> }
  return (
    <div className={`bg-slate-800 border-2 rounded-xl px-3 py-2 max-w-[200px] shadow-lg ${
      selected ? 'border-orange-400 shadow-orange-400/20' : 'border-orange-500/40'
    }`}>
      <Handle type="target" position={Position.Left} className="!bg-orange-400 !border-orange-600 !w-3 !h-3" />
      <div className="flex items-center gap-2">
        <div className="w-3 h-3 rounded-full bg-orange-400 flex-shrink-0" />
        <span className="text-slate-200 text-xs truncate font-mono">{nodeData.label}</span>
      </div>
      <Handle type="source" position={Position.Right} className="!bg-orange-400 !border-orange-600 !w-3 !h-3" />
    </div>
  )
}
