interface Props {
  lastScannedAt: string | null
  repoName?: string
}

/**
 * Warns when graph data may be stale. Shown on graph and impact pages.
 * Trust doesn't degrade gradually — it drops suddenly. Make staleness visible.
 */
export default function StalenessBanner({ lastScannedAt, repoName }: Props) {
  if (!lastScannedAt) {
    return (
      <div className="bg-red-500/10 border border-red-500/30 rounded-lg px-4 py-3 text-sm">
        <div className="flex items-center gap-2">
          <span className="text-red-400 font-semibold">Never scanned</span>
          {repoName && <span className="text-slate-500">({repoName})</span>}
        </div>
        <p className="text-red-400/80 text-xs mt-1">
          This repo has never been scanned. Impact analysis may miss dependencies entirely.
          Run a deep scan for accurate results.
        </p>
      </div>
    )
  }

  const scannedDate = new Date(lastScannedAt)
  const now = new Date()
  const hoursAgo = Math.floor((now.getTime() - scannedDate.getTime()) / (1000 * 60 * 60))

  if (hoursAgo < 24) return null

  const isStale = hoursAgo > 48
  const borderColor = isStale ? 'border-red-500/30' : 'border-yellow-500/30'
  const bgColor = isStale ? 'bg-red-500/10' : 'bg-yellow-500/10'
  const textColor = isStale ? 'text-red-400' : 'text-yellow-400'
  const subColor = isStale ? 'text-red-400/80' : 'text-yellow-400/80'

  const timeStr = hoursAgo >= 48
    ? `${Math.floor(hoursAgo / 24)} days ago`
    : `${hoursAgo} hours ago`

  return (
    <div className={`${bgColor} border ${borderColor} rounded-lg px-4 py-3 text-sm`}>
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className={`${textColor} font-semibold`}>
            {isStale ? 'Stale graph' : 'Graph aging'}
          </span>
          <span className="text-slate-500">Last scan: {timeStr}</span>
          {repoName && <span className="text-slate-600">({repoName})</span>}
        </div>
        {isStale && (
          <span className={`${subColor} text-xs`}>
            Confidence reduced
          </span>
        )}
      </div>
      <p className={`${subColor} text-xs mt-1`}>
        {isStale
          ? 'Graph data is over 48 hours old. Rescan to reduce false positives and negatives.'
          : 'Graph data is aging. Consider rescanning before high-risk merges.'}
      </p>
    </div>
  )
}
