import { useCallback, useMemo, useEffect } from 'react'
import {
  ReactFlow, ReactFlowProvider, Background, Controls, MiniMap,
  BackgroundVariant, useNodesState, useEdgesState,
  useViewport,
  type Node, type Edge, type NodeTypes, MarkerType, Panel,
} from '@xyflow/react'
import '@xyflow/react/dist/style.css'
import dagre from '@dagrejs/dagre'
import RepoNode from './nodes/RepoNode'
import ApiNode from './nodes/ApiNode'
import ConfigNode from './nodes/ConfigNode'
import ComponentNode from './nodes/ComponentNode'
import GroupNode from './nodes/GroupNode'
import type { GraphDto, GraphEdge, GraphNode } from '../../types'
import { useGraphStore } from '../../store/graphStore'

// ─── node type registry ───────────────────────────────────────────────────
const nodeTypes: NodeTypes = {
  REPO: RepoNode,
  API_ENDPOINT: ApiNode,
  CONFIG: ConfigNode,
  COMPONENT: ComponentNode,
  GROUP: GroupNode,
}

// ─── edge colours ─────────────────────────────────────────────────────────
const EDGE_COLORS: Record<string, string> = {
  CALLS:      '#6366f1',
  CALLS_CROSS: '#22d3ee', // repo → repo (cross-service)
  WIRED:      '#ec4899',
  DEFINES:    '#3b82f6',
  IMPORTS:    '#a855f7',
  READS:      '#f59e0b',
  DEPENDS_ON: '#22c55e',
}

function edgeColor(e: GraphEdge): string {
  if (e.type === 'IMPORTS') {
    const t = e.data?.importType as string | undefined
    if (t === 'INTERNAL') return '#22c55e'
    if (t === 'MONOREPO') return '#eab308'
    return '#ef4444'
  }
  if (e.type === 'CALLS' && e.data?.callTier === 'REPO_TO_REPO') {
    return EDGE_COLORS.CALLS_CROSS
  }
  if (e.type === 'WIRED') {
    const k = e.data?.wiringKind as string | undefined
    if (k === 'BACKEND_HTTP') return '#fb923c'
    if (k === 'UI_PROXY') return '#f472b6'
    if (k === 'GATEWAY') return '#e879f9'
    return EDGE_COLORS.WIRED
  }
  return EDGE_COLORS[e.type] || '#64748b'
}

// ─── Fix 3: group key from endpoint path ─────────────────────────────────
function groupKeyFor(repoId: unknown, label: string): string {
  const path = label.replace(/^(GET|POST|PUT|DELETE|PATCH)\s+/, '')
  const segs = path.split('/').filter(Boolean)
  const slug = segs.length >= 2 && segs[0] === 'api' ? segs[1] : segs[0] ?? 'other'
  return `group-${repoId ?? 'x'}-${slug}`
}

function slugFromGroupId(groupId: string): string {
  return groupId.split('-').slice(2).join('-')
}

function riskColor(risk: string | undefined): string {
  if (risk === 'HIGH')   return '#ef4444'
  if (risk === 'MEDIUM') return '#f59e0b'
  return '#22c55e'
}

// ─── Fix 4: dagre layout ──────────────────────────────────────────────────
function applyDagre(rawNodes: Node[], rawEdges: Edge[]): Node[] {
  const g = new dagre.graphlib.Graph()
  g.setGraph({ rankdir: 'LR', ranksep: 150, nodesep: 55, edgesep: 25 })
  g.setDefaultEdgeLabel(() => ({}))

  rawNodes.forEach(n => {
    const w = (n.style?.width as number) ?? 180
    const h = (n.style?.height as number) ?? 64
    g.setNode(n.id, { width: w, height: h })
  })
  rawEdges.forEach(e => {
    if (g.hasNode(e.source) && g.hasNode(e.target)) g.setEdge(e.source, e.target)
  })
  dagre.layout(g)

  return rawNodes.map(n => {
    const pos = g.node(n.id)
    if (!pos) return n
    const w = (n.style?.width as number) ?? 180
    const h = (n.style?.height as number) ?? 64
    return { ...n, position: { x: pos.x - w / 2, y: pos.y - h / 2 } }
  })
}

// ─── Fix 2: focus — connected node ids ───────────────────────────────────
function connectedSet(nodeId: string, edges: Edge[]): Set<string> {
  const ids = new Set([nodeId])
  edges.forEach(e => {
    if (e.source === nodeId) ids.add(e.target)
    if (e.target === nodeId) ids.add(e.source)
  })
  return ids
}

function makeEdge(e: GraphEdge): Edge {
  const color = edgeColor(e)
  const crossRepoCall = e.type === 'CALLS' && e.data?.callTier === 'REPO_TO_REPO'
  return {
    id: e.id, source: e.source, target: e.target,
    label: e.label,
    type: 'smoothstep',
    animated: e.type === 'CALLS' || e.type === 'WIRED',
    style: {
      stroke: color,
      strokeWidth: crossRepoCall ? 2.5 : e.type === 'CALLS' ? 2 : 1.5,
    },
    labelStyle: { fill: '#94a3b8', fontSize: 10 },
    labelBgStyle: { fill: '#1e293b', fillOpacity: 0.9 },
    markerEnd: { type: MarkerType.ArrowClosed, color },
  }
}

// ─── build all elements ───────────────────────────────────────────────────
function buildElements(
  graphNodes: GraphNode[],
  graphEdges: GraphEdge[],
  expandedGroups: Set<string>,
  focusedNodeId: string | null,
  activeFilters: Set<string>,
  searchQuery: string,
  zoom: number,
): { nodes: Node[]; edges: Edge[] } {

  const visibleEdges = graphEdges.filter(e => activeFilters.has(e.type))

  let visible = graphNodes
  if (searchQuery) {
    const q = searchQuery.toLowerCase()
    visible = graphNodes.filter(n => n.label.toLowerCase().includes(q))
  }

  const nodes: Node[] = []
  const edges: Edge[] = []
  const nodeIdSet = new Set<string>()

  // Repo nodes
  visible.filter(n => n.type === 'REPO').forEach(n => {
    nodeIdSet.add(n.id)
    nodes.push({
      id: n.id, type: 'REPO', position: { x: 0, y: 0 },
      data: { label: n.label, language: n.language, data: n.data, zoom },
      style: { width: 190, height: 68 },
    })
  })

  // Group endpoint nodes by URL prefix
  const epNodes = visible.filter(n => n.type === 'API_ENDPOINT')
  const groups = new Map<string, GraphNode[]>()
  epNodes.forEach(n => {
    const key = groupKeyFor(n.data?.repoId, n.label)
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(n)
  })

  groups.forEach((endpoints, groupId) => {
    const isExpanded = expandedGroups.has(groupId)
    const risks = endpoints.map(e => (e.data?.riskScore as string) || 'LOW')
    const groupRisk = risks.includes('HIGH') ? 'HIGH' : risks.includes('MEDIUM') ? 'MEDIUM' : 'LOW'
    const callerCount = endpoints.reduce((s, e) => s + ((e.data?.callerCount as number) || 0), 0)

    if (!isExpanded) {
      // Collapsed group node
      nodeIdSet.add(groupId)
      nodes.push({
        id: groupId, type: 'GROUP', position: { x: 0, y: 0 },
        data: {
          label: `${slugFromGroupId(groupId)} APIs`,
          count: endpoints.length,
          riskScore: groupRisk,
          callerCount,
          groupId,
          zoom,
        },
        style: { width: 184, height: 64 },
      })
      // DEFINES → group (deduplicated)
      visibleEdges
        .filter(e => e.type === 'DEFINES' && endpoints.some(ep => ep.id === e.target))
        .forEach(e => {
          const eid = `ge-${e.source}-${groupId}`
          if (!edges.find(x => x.id === eid)) {
            edges.push({
              id: eid, source: e.source, target: groupId,
              type: 'smoothstep',
              style: { stroke: EDGE_COLORS.DEFINES, strokeWidth: 1.5 },
              markerEnd: { type: MarkerType.ArrowClosed, color: EDGE_COLORS.DEFINES },
            })
          }
        })
    } else {
      // Expanded: individual endpoint nodes
      endpoints.forEach(n => {
        nodeIdSet.add(n.id)
        nodes.push({
          id: n.id, type: 'API_ENDPOINT', position: { x: 0, y: 0 },
          data: { label: n.label, language: n.language, data: n.data, zoom },
          style: { width: 200, height: 58 },
        })
      })
      // DEFINES → individual endpoints
      visibleEdges
        .filter(e => e.type === 'DEFINES' && endpoints.some(ep => ep.id === e.target))
        .forEach(e => {
          if (nodeIdSet.has(e.source)) edges.push(makeEdge(e))
        })
    }
  })

  // CONFIG / COMPONENT nodes
  visible.filter(n => n.type !== 'REPO' && n.type !== 'API_ENDPOINT').forEach(n => {
    nodeIdSet.add(n.id)
    nodes.push({
      id: n.id, type: n.type, position: { x: 0, y: 0 },
      data: { label: n.label, language: n.language, data: n.data, zoom },
      style: { width: 170, height: 58 },
    })
  })

  // All non-DEFINES edges between visible nodes
  visibleEdges.filter(e => e.type !== 'DEFINES').forEach(e => {
    if (nodeIdSet.has(e.source) && nodeIdSet.has(e.target)) edges.push(makeEdge(e))
  })

  // Fix 2: focus mode opacity
  const focused = focusedNodeId ? connectedSet(focusedNodeId, edges) : null
  const styledNodes = focused
    ? nodes.map(n => ({
        ...n,
        style: { ...n.style, opacity: focused.has(n.id) ? 1 : 0.07, transition: 'opacity 0.2s' },
      }))
    : nodes

  // Fix 4: dagre layout
  const laidOut = applyDagre(styledNodes, edges)
  return { nodes: laidOut, edges }
}

// ─── inner component (needs ReactFlow viewport context) ───────────────────
function GraphInner({ graph }: { graph: GraphDto }) {
  const {
    setSelectedNode, focusedNodeId, setFocusedNodeId,
    activeFilters, searchQuery, expandedGroups,
  } = useGraphStore()

  const { zoom } = useViewport()

  const { nodes: initNodes, edges: initEdges } = useMemo(
    () => buildElements(graph.nodes, graph.edges, expandedGroups, focusedNodeId, activeFilters, searchQuery, zoom),
    // zoom intentionally excluded — handled by separate effect below
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [graph, expandedGroups, focusedNodeId, activeFilters, searchQuery],
  )

  const [nodes, setNodes, onNodesChange] = useNodesState(initNodes)
  const [edges, setEdges, onEdgesChange] = useEdgesState(initEdges)

  useEffect(() => { setNodes(initNodes); setEdges(initEdges) }, [initNodes, initEdges])

  // Fix 6: pass zoom into node data for level-of-detail rendering
  useEffect(() => {
    setNodes(ns => ns.map(n => ({ ...n, data: { ...n.data, zoom } })))
  }, [zoom])

  // Escape exits focus mode
  useEffect(() => {
    const h = (ev: KeyboardEvent) => { if (ev.key === 'Escape') setFocusedNodeId(null) }
    window.addEventListener('keydown', h)
    return () => window.removeEventListener('keydown', h)
  }, [setFocusedNodeId])

  const onNodeClick = useCallback((_: React.MouseEvent, node: Node) => {
    const gn = graph.nodes.find(n => n.id === node.id)
    if (gn) setSelectedNode(gn)
    setFocusedNodeId(node.id)
  }, [graph.nodes, setSelectedNode, setFocusedNodeId])

  const onPaneClick = useCallback(() => {
    setSelectedNode(null)
    setFocusedNodeId(null)
  }, [setSelectedNode, setFocusedNodeId])

  return (
    <ReactFlow
      nodes={nodes} edges={edges}
      onNodesChange={onNodesChange} onEdgesChange={onEdgesChange}
      onNodeClick={onNodeClick} onPaneClick={onPaneClick}
      nodeTypes={nodeTypes}
      fitView fitViewOptions={{ padding: 0.18 }}
      minZoom={0.06} maxZoom={2.5}
      attributionPosition="bottom-right"
    >
      <Background variant={BackgroundVariant.Dots} color="#1e293b" gap={20} size={1} />
      <Controls className="!bg-slate-800 !border-slate-700" />
      <MiniMap
        className="!bg-slate-900 !border-slate-700"
        nodeColor={n => {
          if (n.type === 'REPO')         return '#3b82f6'
          if (n.type === 'API_ENDPOINT') return riskColor((n.data as any)?.data?.riskScore)
          if (n.type === 'GROUP')        return riskColor((n.data as any)?.riskScore)
          if (n.type === 'CONFIG')       return '#f59e0b'
          return '#a855f7'
        }}
      />
      {/* Fix 2: focus mode hint panel */}
      {focusedNodeId && (
        <Panel position="bottom-center">
          <div className="bg-slate-900/90 border border-slate-700 rounded-lg px-3 py-1.5 text-xs text-slate-400 flex items-center gap-2">
            <span className="w-1.5 h-1.5 rounded-full bg-indigo-400 animate-pulse" />
            Focus mode · press <kbd className="bg-slate-700 px-1 rounded mx-0.5">Esc</kbd> or click canvas to exit
          </div>
        </Panel>
      )}
    </ReactFlow>
  )
}

// ─── wrapper: ReactFlowProvider gives GraphInner the viewport context ─────
// GraphInner uses useViewport() which requires being inside the provider.
// ReactFlow itself sets up the provider, but the hook call in the same
// component that renders <ReactFlow> is too early — so we wrap one level up.
export default function DependencyGraph({ graph }: { graph: GraphDto }) {
  return (
    <div className="w-full h-full bg-slate-950">
      <ReactFlowProvider>
        <GraphInner graph={graph} />
      </ReactFlowProvider>
    </div>
  )
}
