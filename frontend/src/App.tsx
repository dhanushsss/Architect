import { Routes, Route, Navigate } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import GraphPage from './pages/GraphPage'
import ImpactPage from './pages/ImpactPage'
import AuthCallbackPage from './pages/AuthCallbackPage'
import AiQueryPage from './pages/AiQueryPage'
import GovernancePage from './pages/GovernancePage'
import ApiKeysPage from './pages/ApiKeysPage'
import InsightsPage from './pages/InsightsPage'

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const token = localStorage.getItem('architect_token')
  if (!token) return <Navigate to="/login" replace />
  return <>{children}</>
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/auth/callback" element={<AuthCallbackPage />} />
      <Route path="/" element={<PrivateRoute><DashboardPage /></PrivateRoute>} />
      <Route path="/graph" element={<PrivateRoute><GraphPage /></PrivateRoute>} />
      <Route path="/impact/:type/:id" element={<PrivateRoute><ImpactPage /></PrivateRoute>} />
      {/* Phase 3 — AI Understanding */}
      <Route path="/ai" element={<PrivateRoute><AiQueryPage /></PrivateRoute>} />
      {/* Phase 4 — Enterprise & Compliance */}
      <Route path="/governance" element={<PrivateRoute><GovernancePage /></PrivateRoute>} />
      {/* Phase 5 — Platform */}
      <Route path="/api-keys" element={<PrivateRoute><ApiKeysPage /></PrivateRoute>} />
      <Route path="/insights" element={<PrivateRoute><InsightsPage /></PrivateRoute>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
