import clsx from 'clsx'

const LANG_COLORS: Record<string, string> = {
  Java: 'bg-orange-500/20 text-orange-400 border-orange-500/30',
  JavaScript: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30',
  TypeScript: 'bg-blue-500/20 text-blue-400 border-blue-500/30',
  Python: 'bg-blue-400/20 text-blue-300 border-blue-400/30',
  Go: 'bg-cyan-500/20 text-cyan-400 border-cyan-500/30',
  Ruby: 'bg-red-400/20 text-red-400 border-red-400/30',
  Rust: 'bg-orange-400/20 text-orange-300 border-orange-400/30',
  default: 'bg-slate-500/20 text-slate-400 border-slate-500/30'
}

export function LanguageBadge({ language }: { language?: string | null }) {
  if (!language) return null
  const color = LANG_COLORS[language] || LANG_COLORS.default
  return (
    <span className={clsx('text-xs font-medium px-1.5 py-0.5 rounded border', color)}>
      {language}
    </span>
  )
}

export function ScanStatusBadge({ status }: { status: string }) {
  const config: Record<string, string> = {
    COMPLETE: 'bg-green-500/20 text-green-400 border-green-500/30',
    SCANNING: 'bg-yellow-500/20 text-yellow-400 border-yellow-500/30',
    FAILED: 'bg-red-500/20 text-red-400 border-red-500/30',
    PENDING: 'bg-slate-500/20 text-slate-400 border-slate-500/30'
  }
  return (
    <span className={clsx('text-xs font-medium px-1.5 py-0.5 rounded border', config[status] || config.PENDING)}>
      {status === 'SCANNING' ? '⟳ Scanning...' : status}
    </span>
  )
}

export function RiskBadge({ risk }: { risk: string }) {
  return (
    <span className={clsx('font-semibold px-2 py-1 rounded text-sm border',
      risk === 'HIGH' ? 'badge-high' :
      risk === 'MEDIUM' ? 'badge-medium' : 'badge-low'
    )}>
      {risk}
    </span>
  )
}
