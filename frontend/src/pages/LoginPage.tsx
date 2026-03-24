import { useSearchParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Github } from 'lucide-react'
import { authApi, versionApi } from '../services/api'

export default function LoginPage() {
  const [params] = useSearchParams()
  const error = params.get('error')
  const errorMessage =
    error === 'github_not_configured'
      ? 'Server is missing GitHub OAuth. Set GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET, then restart the backend.'
      : error === 'oauth_failed'
        ? 'GitHub sign-in failed. Try again.'
        : error
          ? 'Something went wrong. Try again.'
          : null
  const { data: productVer } = useQuery({
    queryKey: ['product-version'],
    queryFn: versionApi.get,
    staleTime: 300_000,
    retry: 1,
  })

  return (
    <div className="min-h-screen bg-slate-900 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-indigo-500/20 rounded-2xl mb-4">
            <svg className="w-9 h-9 text-indigo-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5}
                d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
            </svg>
          </div>
          <h1 className="text-3xl font-bold text-white">Architect</h1>
          <p className="text-slate-400 mt-2">Know what breaks before you merge</p>
        </div>

        {/* Card */}
        <div className="card">
          <h2 className="text-xl font-semibold text-white mb-1">Get started</h2>
          <p className="text-slate-400 text-sm mb-6">
            Connect your GitHub account and get a change risk score on every pull request — before it hits production.
          </p>

          {errorMessage && (
            <div className="mb-4 p-3 bg-red-500/10 border border-red-500/30 rounded-lg text-red-400 text-sm">
              {errorMessage}
            </div>
          )}

          <button
            onClick={authApi.loginWithGithub}
            className="w-full flex items-center justify-center gap-3 bg-white text-gray-900 hover:bg-gray-100 font-medium py-3 px-4 rounded-lg transition-colors"
          >
            <Github className="w-5 h-5" />
            Continue with GitHub
          </button>

          <p className="text-slate-500 text-xs text-center mt-4">
            Requires repo, read:org access to scan repositories
          </p>
        </div>

        {/* Features */}
        <div className="mt-6 grid grid-cols-3 gap-3 text-center">
          {[
            { icon: '🎯', label: 'Risk Score' },
            { icon: '⚡', label: 'PR Comments' },
            { icon: '🔔', label: 'Blast Radius' }
          ].map(f => (
            <div key={f.label} className="bg-slate-800/50 rounded-lg p-3">
              <div className="text-2xl mb-1">{f.icon}</div>
              <div className="text-slate-400 text-xs">{f.label}</div>
            </div>
          ))}
        </div>

        {productVer?.version && (
          <p className="text-center text-slate-600 text-xs mt-8 font-mono">
            {productVer.product} v{productVer.version} · public API {productVer.publicApi}
          </p>
        )}
      </div>
    </div>
  )
}
