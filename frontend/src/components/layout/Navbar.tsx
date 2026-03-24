import { Link, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  LayoutDashboard,
  GitFork,
  BrainCircuit,
  Shield,
  KeyRound,
  Layers,
} from 'lucide-react'
import { authApi, dashboardApi, versionApi } from '../../services/api'
import clsx from 'clsx'

export default function Navbar() {
  const location = useLocation()
  const { data: user } = useQuery({ queryKey: ['me'], queryFn: authApi.getMe })
  const { data: cfg } = useQuery({
    queryKey: ['product-config'],
    queryFn: dashboardApi.getProductConfig,
    staleTime: 60_000,
  })
  const { data: productVer } = useQuery({
    queryKey: ['product-version'],
    queryFn: versionApi.get,
    staleTime: 300_000,
    retry: 1,
  })

  const logout = () => {
    localStorage.removeItem('architect_token')
    window.location.href = '/login'
  }

  const core = cfg?.coreOnly === true

  const navItems: { to: string; icon: typeof LayoutDashboard; label: string }[] = [
    { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
  ]
  if (!core) {
    navItems.push(
      { to: '/graph', icon: GitFork, label: 'Risk Map' },
      { to: '/insights', icon: Layers, label: 'Architecture' },
      { to: '/ai', icon: BrainCircuit, label: 'Insights' },
      { to: '/governance', icon: Shield, label: 'Governance' },
      { to: '/api-keys', icon: KeyRound, label: 'API Keys' }
    )
  } else {
    navItems.push({ to: '/insights', icon: Layers, label: 'Snapshot' })
  }

  return (
    <nav className="h-14 bg-slate-900 border-b border-slate-700/50 flex items-center px-4 gap-4 sticky top-0 z-50">
      <Link to="/" className="flex items-center gap-2 mr-4">
        <div className="w-7 h-7 bg-indigo-500 rounded-lg flex items-center justify-center">
          <svg className="w-4 h-4 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
          </svg>
        </div>
        <span className="font-bold text-white">Architect</span>
        {productVer?.version && (
          <span className="text-[10px] text-slate-500 font-mono tabular-nums hidden sm:inline">
            v{productVer.version}
          </span>
        )}
      </Link>

      {navItems.map(({ to, icon: Icon, label }) => (
        <Link
          key={to}
          to={to}
          className={clsx(
            'flex items-center gap-1.5 px-3 py-1.5 rounded-md text-sm transition-colors',
            location.pathname === to || (to !== '/' && location.pathname.startsWith(to))
              ? 'bg-indigo-500/20 text-indigo-400'
              : 'text-slate-400 hover:text-white hover:bg-slate-800'
          )}
        >
          <Icon className="w-4 h-4" />
          {label}
        </Link>
      ))}

      {core && (
        <span className="text-xs text-amber-400/90 hidden md:inline border border-amber-500/30 px-2 py-0.5 rounded">
          Core mode
        </span>
      )}

      <div className="flex-1" />

      {user && (
        <div className="flex items-center gap-3">
          {user.avatarUrl && (
            <img src={user.avatarUrl} alt={user.login} className="w-7 h-7 rounded-full" />
          )}
          <span className="text-sm text-slate-300 hidden sm:block">{user.name || user.login}</span>
          <button onClick={logout} className="text-slate-400 hover:text-red-400 transition-colors p-1" type="button">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
            </svg>
          </button>
        </div>
      )}
    </nav>
  )
}
