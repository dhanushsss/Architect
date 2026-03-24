import { Search, Layers } from 'lucide-react'
import { useGraphStore } from '../../store/graphStore'
import clsx from 'clsx'

const FILTERS = [
  { key: 'CALLS',      label: 'Calls',    color: 'text-indigo-400 border-indigo-500/30 bg-indigo-500/10',  desc: 'HTTP calls in code' },
  { key: 'WIRED',      label: 'Wired',    color: 'text-pink-400 border-pink-500/30 bg-pink-500/10',       desc: 'Gateway, UI proxy, registry (config)' },
  { key: 'IMPORTS',    label: 'Imports',  color: 'text-purple-400 border-purple-500/30 bg-purple-500/10', desc: 'Import graph' },
  { key: 'DEFINES',    label: 'Defines',  color: 'text-blue-400 border-blue-500/30 bg-blue-500/10',       desc: 'Repo → endpoint' },
  { key: 'READS',      label: 'Reads',    color: 'text-orange-400 border-orange-500/30 bg-orange-500/10', desc: 'Config reads' },
]

export default function GraphFilterBar() {
  const { activeFilters, toggleFilter, searchQuery, setSearchQuery } = useGraphStore()

  return (
    <div className="absolute top-4 left-4 z-40 flex flex-col gap-2">
      {/* Row 1: search + edge filters */}
      <div className="flex items-center gap-2 flex-wrap">
        {/* Search */}
        <div className="relative">
          <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            placeholder="Search nodes…"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            className="bg-slate-900/90 border border-slate-700 rounded-lg pl-8 pr-3 py-1.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500 w-36"
          />
        </div>

        {/* Edge filters — CALLS active by default */}
        {FILTERS.map(f => (
          <button
            key={f.key}
            onClick={() => toggleFilter(f.key)}
            title={f.desc}
            className={clsx(
              'text-xs font-medium px-2.5 py-1.5 rounded-lg border transition-all',
              activeFilters.has(f.key)
                ? f.color
                : 'text-slate-500 border-slate-700 bg-slate-900/90 opacity-40',
            )}
          >
            {f.label}
          </button>
        ))}
      </div>

      {/* Row 2: hint when all filters are on */}
      {activeFilters.size >= 5 && (
        <div className="flex items-center gap-1.5 text-xs text-slate-500 pl-1">
          <Layers className="w-3 h-3" />
          Tip: hide edge types to reduce noise
        </div>
      )}

      {/* Row 3: hint for group expand */}
      <div className="text-xs text-slate-600 pl-1">
        Click a <span className="text-blue-400">repo</span> → see which repos it links to · double-click a group to expand
      </div>
    </div>
  )
}
