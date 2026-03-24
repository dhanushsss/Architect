import { useQuery } from '@tanstack/react-query'
import { graphApi } from '../services/api'

export function useGraph() {
  return useQuery({
    queryKey: ['graph'],
    queryFn: graphApi.getGraph,
    staleTime: 60000
  })
}
