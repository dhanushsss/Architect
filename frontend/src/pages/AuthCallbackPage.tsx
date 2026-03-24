import { useEffect } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'

export default function AuthCallbackPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()

  useEffect(() => {
    const token = params.get('token')
    const error = params.get('error')
    if (token) {
      localStorage.setItem('architect_token', token)
      navigate('/', { replace: true })
    } else {
      navigate('/login?error=' + (error || 'unknown'), { replace: true })
    }
  }, [params, navigate])

  return (
    <div className="min-h-screen flex items-center justify-center bg-slate-900">
      <div className="text-slate-400 text-lg">Authenticating...</div>
    </div>
  )
}
