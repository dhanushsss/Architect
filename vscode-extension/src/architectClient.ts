import * as https from 'https'
import * as http from 'http'

export interface GraphNode {
  id: string
  type: string
  label: string
  data?: Record<string, unknown>
}

export interface GraphDto {
  nodes: GraphNode[]
}

export interface ImpactDto {
  dependentsCount?: number
  subjectLabel?: string
}

export class ArchitectClient {
  constructor(
    private readonly baseUrl: string,
    private readonly jwt: string
  ) {}

  async getGraph(): Promise<GraphDto> {
    return this.requestJson<GraphDto>('GET', '/api/v1/graph')
  }

  async getEndpointImpact(endpointId: string): Promise<ImpactDto> {
    return this.requestJson<ImpactDto>('GET', `/api/v1/impact/endpoint/${encodeURIComponent(endpointId)}`)
  }

  private requestJson<T>(method: string, path: string): Promise<T> {
    const base = this.baseUrl.endsWith('/') ? this.baseUrl.slice(0, -1) : this.baseUrl
    const url = new URL(path.startsWith('/') ? path : `/${path}`, `${base}/`)

    const isHttps = url.protocol === 'https:'
    const lib = isHttps ? https : http
    const headers: Record<string, string> = {
      Accept: 'application/json',
    }
    if (this.jwt) {
      headers['Authorization'] = `Bearer ${this.jwt}`
    }

    return new Promise((resolve, reject) => {
      const req = lib.request(
        {
          hostname: url.hostname,
          port: url.port || (isHttps ? 443 : 80),
          path: url.pathname + url.search,
          method,
          headers,
        },
        (res) => {
          const chunks: Buffer[] = []
          res.on('data', (c) => chunks.push(c as Buffer))
          res.on('end', () => {
            const body = Buffer.concat(chunks).toString('utf8')
            if (res.statusCode && res.statusCode >= 400) {
              reject(new Error(`Architect ${res.statusCode}: ${body}`))
              return
            }
            try {
              resolve(JSON.parse(body) as T)
            } catch (e) {
              reject(e)
            }
          })
        }
      )
      req.on('error', reject)
      req.end()
    })
  }
}
