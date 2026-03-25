import { useEffect, useState } from 'react'
import Navbar from '../components/layout/Navbar'
import api from '../services/api'

export default function ApiKeysPage() {
  const [keys, setKeys] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const [creating, setCreating] = useState(false)
  const [newKey, setNewKey] = useState<any>(null)
  const [form, setForm] = useState({ name: '', scopes: 'read:graph' })

  useEffect(() => { loadKeys() }, [])

  const loadKeys = async () => {
    setLoading(true)
    try {
      const { data } = await api.get('/keys')
      setKeys(data)
    } finally { setLoading(false) }
  }

  const generateKey = async (e: React.FormEvent) => {
    e.preventDefault()
    setCreating(true)
    try {
      const { data } = await api.post('/keys', form)
      setNewKey(data)
      await loadKeys()
    } finally { setCreating(false) }
  }

  const revokeKey = async (id: number) => {
    if (!confirm('Revoke this API key? This cannot be undone.')) return
    await api.delete(`/keys/${id}`)
    await loadKeys()
  }

  return (
    <div className="min-h-screen bg-gray-950 text-gray-100">
      <Navbar />
      <div className="max-w-3xl mx-auto px-6 py-8">
        <h1 className="text-2xl font-bold mb-2">🔑 API Keys</h1>
        <p className="text-gray-400 text-sm mb-8">Use API keys to access the Zerqis Public API from your tools and integrations.</p>

        {/* New key created */}
        {newKey && (
          <div className="mb-6 bg-green-900/30 border border-green-700 rounded-xl p-4">
            <p className="text-sm font-semibold text-green-300 mb-2">✅ API Key Created — Copy it now!</p>
            <p className="text-xs text-gray-400 mb-2">{newKey.warning}</p>
            <div className="flex items-center gap-2">
              <code className="flex-1 bg-gray-900 px-3 py-2 rounded-lg text-sm font-mono text-green-200 break-all">{newKey.key}</code>
              <button
                onClick={() => { navigator.clipboard.writeText(newKey.key); }}
                className="px-3 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-xs"
              >
                Copy
              </button>
            </div>
            <button onClick={() => setNewKey(null)} className="mt-3 text-xs text-gray-400 hover:text-gray-200">Dismiss</button>
          </div>
        )}

        {/* Create form */}
        <div className="bg-gray-800 rounded-xl p-5 mb-6">
          <h2 className="text-sm font-semibold mb-4">Generate New Key</h2>
          <form onSubmit={generateKey} className="space-y-3">
            <div>
              <label className="text-xs text-gray-400 block mb-1">Key Name</label>
              <input
                value={form.name}
                onChange={e => setForm(f => ({ ...f, name: e.target.value }))}
                placeholder="e.g. My CI/CD Pipeline"
                required
                className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-sm focus:outline-none focus:border-indigo-500"
              />
            </div>
            <div>
              <label className="text-xs text-gray-400 block mb-1">Scopes</label>
              <select
                value={form.scopes}
                onChange={e => setForm(f => ({ ...f, scopes: e.target.value }))}
                className="w-full px-3 py-2 bg-gray-700 border border-gray-600 rounded-lg text-sm focus:outline-none"
              >
                <option value="read:graph">read:graph — Read dependency graph data</option>
                <option value="read:graph read:impact">read:graph read:impact — Graph + impact analysis</option>
                <option value="read:graph write:scan">read:graph write:scan — Graph + trigger scans</option>
              </select>
            </div>
            <button
              type="submit"
              disabled={creating}
              className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 rounded-lg text-sm font-medium"
            >
              {creating ? 'Generating...' : '+ Generate Key'}
            </button>
          </form>
        </div>

        {/* Key list */}
        <div className="space-y-3">
          <h2 className="text-sm font-semibold text-gray-400">Active Keys</h2>
          {loading ? <p className="text-gray-400">Loading...</p> :
           keys.length === 0 ? (
             <p className="text-center py-8 text-gray-500">No API keys yet</p>
           ) : (
             keys.map(k => (
               <div key={k.id} className="bg-gray-800 rounded-xl p-4 flex items-center justify-between">
                 <div>
                   <p className="font-medium text-sm">{k.name}</p>
                   <p className="text-xs text-gray-400 mt-0.5 font-mono">{k.prefix}</p>
                   <div className="flex gap-3 mt-1 text-xs text-gray-500">
                     <span>🔐 {k.scopes}</span>
                     <span>⚡ {k.rateLimitPerHour}/hr</span>
                     {k.lastUsedAt && <span>Used: {new Date(k.lastUsedAt).toLocaleDateString()}</span>}
                   </div>
                 </div>
                 <button
                   onClick={() => revokeKey(k.id)}
                   className="px-3 py-1.5 bg-red-900/50 hover:bg-red-800 text-red-300 text-xs rounded-lg"
                 >
                   Revoke
                 </button>
               </div>
             ))
           )}
        </div>

        {/* API Usage docs */}
        <div className="mt-8 bg-gray-800 rounded-xl p-5">
          <h2 className="text-sm font-semibold mb-3">Using the API</h2>
          <div className="space-y-4 text-sm text-gray-300">
            <div>
              <p className="text-xs text-gray-400 mb-1">Get dependency graph</p>
              <code className="block bg-gray-900 px-3 py-2 rounded-lg text-xs font-mono whitespace-pre">{`curl -H "X-API-Key: arc_..." \\
  http://localhost:8080/api/graph/YOUR_GITHUB_LOGIN`}</code>
            </div>
            <div>
              <p className="text-xs text-gray-400 mb-1">Trigger scan</p>
              <code className="block bg-gray-900 px-3 py-2 rounded-lg text-xs font-mono whitespace-pre">{`curl -X POST -H "X-API-Key: arc_..." \\
  http://localhost:8080/api/scan/REPO_ID`}</code>
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
