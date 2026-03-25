import { create } from 'zustand'
import type { GraphNode } from '../types'

interface GraphStore {
  selectedNode: GraphNode | null
  setSelectedNode: (node: GraphNode | null) => void
  focusedNodeId: string | null          // Fix 2 — focus mode
  setFocusedNodeId: (id: string | null) => void
  activeFilters: Set<string>
  toggleFilter: (filter: string) => void
  searchQuery: string
  setSearchQuery: (q: string) => void
  collapsedGroups: Set<string>          // Fix 3 — group collapse state
  toggleGroup: (groupId: string) => void
  expandedGroups: Set<string>
}

export const useGraphStore = create<GraphStore>((set) => ({
  selectedNode: null,
  setSelectedNode: (node) => set({ selectedNode: node }),

  // Fix 2: focus mode — null = no focus (all visible)
  focusedNodeId: null,
  setFocusedNodeId: (id) => set({ focusedNodeId: id }),

  // Default: service-to-service tree view — toggle "Endpoints" to expand groups
  activeFilters: new Set(['CALLS', 'WIRED', 'IMPORTS']),
  toggleFilter: (filter) => set(state => {
    const next = new Set(state.activeFilters)
    if (next.has(filter)) next.delete(filter)
    else next.add(filter)
    return { activeFilters: next }
  }),

  searchQuery: '',
  setSearchQuery: (q) => set({ searchQuery: q }),

  // Fix 3: collapsed groups — all start collapsed
  collapsedGroups: new Set<string>(),
  expandedGroups: new Set<string>(),
  toggleGroup: (groupId) => set(state => {
    const next = new Set(state.expandedGroups)
    if (next.has(groupId)) next.delete(groupId)
    else next.add(groupId)
    return { expandedGroups: next }
  }),
}))
