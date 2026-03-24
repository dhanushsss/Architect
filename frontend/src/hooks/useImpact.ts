import { useQuery } from '@tanstack/react-query'
import { impactApi } from '../services/api'

export function useEndpointImpact(endpointId: string | null) {
  return useQuery({
    queryKey: ['impact', 'endpoint', endpointId],
    queryFn: () => impactApi.getEndpointImpact(endpointId!),
    enabled: !!endpointId
  })
}

export function useRepoImpact(repoId: string | null) {
  return useQuery({
    queryKey: ['impact', 'repo', repoId],
    queryFn: () => impactApi.getRepoImpact(repoId!),
    enabled: !!repoId
  })
}
