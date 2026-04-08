import { RiskBadge } from '../ui/Badge'
import Spinner from '../ui/Spinner'
import ConfidenceBreakdown from './ConfidenceBreakdown'
import type { ImpactDto } from '../../types'

interface Props {
  impact?: ImpactDto
  loading?: boolean
  lastScannedAt?: string | null
}

export default function ImpactPanel({ impact, loading }: Props) {
  if (loading) return (
    <div className="flex items-center justify-center p-8">
      <Spinner />
    </div>
  )
  if (!impact) return null

  return (
    <div className="space-y-4">
      {/* Risk Score */}
      <div className="card">
        <div className="flex items-center justify-between mb-3">
          <h3 className="font-semibold text-white">Change Risk</h3>
          <RiskBadge risk={impact.riskScore} />
        </div>
        <p className="text-slate-400 text-sm">
          <strong className="text-white">{impact.dependentsCount} repos</strong> and{' '}
          <strong className="text-white">{impact.affectedFiles.length} files</strong> depend on this.
          Changing it is <strong className={
            impact.riskScore === 'HIGH' ? 'text-red-400' :
            impact.riskScore === 'MEDIUM' ? 'text-yellow-400' : 'text-green-400'
          }>{impact.riskScore.toLowerCase()} risk</strong>.
        </p>
      </div>

      {/* Confidence Breakdown */}
      <ConfidenceBreakdown impact={impact} />

      {/* Affected Repos */}
      {impact.affectedRepos.length > 0 && (
        <div className="card">
          <h3 className="font-semibold text-white mb-3">Affected Repos ({impact.affectedRepos.length})</h3>
          <div className="space-y-2">
            {impact.affectedRepos.map(repo => (
              <div key={repo.id} className="flex items-center justify-between">
                <span className="text-slate-200 text-sm font-medium">{repo.name}</span>
                <span className="text-slate-500 text-xs">{repo.detail}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Affected Files */}
      {impact.affectedFiles.length > 0 && (
        <div className="card">
          <h3 className="font-semibold text-white mb-3">Affected Files ({impact.affectedFiles.length})</h3>
          <div className="space-y-1 max-h-48 overflow-y-auto">
            {impact.affectedFiles.map(file => (
              <div key={file.id} className="text-xs">
                <span className="text-slate-300 font-mono">{file.name}</span>
                <span className="text-slate-500 ml-2">{file.detail}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Changed endpoints */}
      {impact.changedEndpoints && impact.changedEndpoints.length > 0 && (
        <div className="card">
          <h3 className="font-semibold text-white mb-3">Changed Endpoints ({impact.changedEndpoints.length})</h3>
          <div className="space-y-1 max-h-32 overflow-y-auto">
            {impact.changedEndpoints.map((ep, i) => (
              <div key={i} className="text-xs text-blue-400 font-mono">{ep}</div>
            ))}
          </div>
        </div>
      )}

      {/* Orphan endpoints */}
      {impact.orphanEndpoints && impact.orphanEndpoints.length > 0 && (
        <div className="card border-yellow-500/30">
          <h3 className="font-semibold text-yellow-400 mb-3">Dead Code ({impact.orphanEndpoints.length})</h3>
          <p className="text-slate-400 text-xs mb-2">These endpoints are never called by any frontend:</p>
          <div className="space-y-1 max-h-32 overflow-y-auto">
            {impact.orphanEndpoints.map((ep, i) => (
              <div key={i} className="text-xs text-slate-400 font-mono">{ep}</div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
