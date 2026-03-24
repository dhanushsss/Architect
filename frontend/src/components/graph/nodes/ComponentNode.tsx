import { Handle, Position, type NodeProps } from '@xyflow/react'

export default function ComponentNode({ data, selected }: NodeProps) {
  const nodeData = data as { label: string; data: Record<string, unknown> }
  return (
    <div className={`bg-slate-800 border-2 rounded-xl px-3 py-2 max-w-[200px] shadow-lg ${
      selected ? 'border-purple-400 shadow-purple-400/20' : 'border-purple-500/40'
    }`}>
      <Handle type="target" position={Position.Left} className="!bg-purple-400 !border-purple-600 !w-3 !h-3" />
      <div className="flex items-center gap-2">
        <div className="w-3 h-3 rounded-sm bg-purple-400 flex-shrink-0" />
        <span className="text-slate-200 text-xs truncate">{nodeData.label}</span>
      </div>
      <Handle type="source" position={Position.Right} className="!bg-purple-400 !border-purple-600 !w-3 !h-3" />
    </div>
  )
}
