import Navbar from '../components/layout/Navbar'
import DependencyGraph from '../components/graph/DependencyGraph'
import GraphFilterBar from '../components/graph/GraphFilterBar'
import NodeDetailPanel from '../components/panels/NodeDetailPanel'
import Spinner from '../components/ui/Spinner'
import { useGraph } from '../hooks/useGraph'
import { useGraphStore } from '../store/graphStore'
import { RefreshCw } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'

export default function GraphPage() {
  const { data: graph, isLoading, isError } = useGraph()
  const { selectedNode, setSelectedNode } = useGraphStore()
  const qc = useQueryClient()

  return (
    <div className="h-screen flex flex-col bg-slate-900">
      <Navbar />

      {/* Stats bar */}
      {graph && (
        <div className="bg-slate-900 border-b border-slate-700/50 px-4 py-2 flex items-center gap-6 text-sm">
          {[
            { label: 'Repos', value: graph.stats.totalRepos, color: 'text-blue-400' },
            { label: 'Endpoints', value: graph.stats.totalEndpoints, color: 'text-green-400' },
            { label: 'Calls', value: graph.stats.totalCalls, color: 'text-indigo-400' },
            { label: 'External', value: graph.stats.totalExternalCalls ?? 0, color: 'text-rose-400' },
            { label: 'Wired', value: graph.stats.totalWiredEdges ?? 0, color: 'text-pink-400' },
            { label: 'Imports', value: graph.stats.totalImports, color: 'text-purple-400' },
            { label: 'Edges', value: graph.stats.totalEdges, color: 'text-slate-400' }
          ].map(s => (
            <span key={s.label} className="flex items-center gap-1.5">
              <span className={`font-semibold ${s.color}`}>{s.value}</span>
              <span className="text-slate-500">{s.label}</span>
            </span>
          ))}
          <div className="flex-1" />
          <button onClick={() => qc.invalidateQueries({ queryKey: ['graph'] })}
            className="text-slate-400 hover:text-white p-1 transition-colors">
            <RefreshCw className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Main area */}
      <div className="flex-1 relative overflow-hidden">
        {isLoading && (
          <div className="absolute inset-0 flex items-center justify-center bg-slate-950 z-10">
            <div className="text-center">
              <Spinner className="w-8 h-8 mx-auto mb-3" />
              <p className="text-slate-400">Building dependency graph...</p>
            </div>
          </div>
        )}

        {isError && (
          <div className="absolute inset-0 flex items-center justify-center bg-slate-950">
            <p className="text-red-400">Failed to load graph. Make sure you have scanned your repos.</p>
          </div>
        )}

        {graph && graph.nodes.length === 0 && (
          <div className="absolute inset-0 flex items-center justify-center bg-slate-950">
            <div className="text-center">
              <p className="text-slate-400 text-lg mb-2">No data yet</p>
              <p className="text-slate-500 text-sm">Go to Dashboard, connect repos, and run a scan.</p>
            </div>
          </div>
        )}

        {graph && graph.nodes.length > 0 && (
          <>
            <GraphFilterBar />
            <DependencyGraph graph={graph} />
            {selectedNode && graph && (
              <NodeDetailPanel
                node={selectedNode}
                graph={graph}
                onClose={() => setSelectedNode(null)}
              />
            )}
          </>
        )}
      </div>
    </div>
  )
}
