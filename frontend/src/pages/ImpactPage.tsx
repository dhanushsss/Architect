import { useParams, Link } from 'react-router-dom'
import Navbar from '../components/layout/Navbar'
import ImpactPanel from '../components/panels/ImpactPanel'
import { useEndpointImpact, useRepoImpact } from '../hooks/useImpact'
import { ArrowLeft } from 'lucide-react'

export default function ImpactPage() {
  const { type, id } = useParams<{ type: string; id: string }>()

  const endpointQuery = useEndpointImpact(type === 'endpoint' ? id! : null)
  const repoQuery = useRepoImpact(type === 'repo' ? id! : null)

  const { data: impact, isLoading } = type === 'endpoint' ? endpointQuery : repoQuery

  return (
    <div className="min-h-screen bg-slate-900">
      <Navbar />
      <main className="max-w-2xl mx-auto p-6">
        <Link to="/" className="flex items-center gap-2 text-slate-400 hover:text-white text-sm mb-6 transition-colors">
          <ArrowLeft className="w-4 h-4" />
          Back to Dashboard
        </Link>

        <div className="mb-6">
          <h1 className="text-2xl font-bold text-white">Risk Analysis</h1>
          {impact && (
            <p className="text-slate-400 mt-1 text-sm">
              {impact.subjectLabel}
            </p>
          )}
        </div>

        <ImpactPanel impact={impact} loading={isLoading} />
      </main>
    </div>
  )
}
