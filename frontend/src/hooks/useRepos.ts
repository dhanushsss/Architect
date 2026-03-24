import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { repoApi, scanApi } from '../services/api'

export function useConnectedRepos() {
  return useQuery({
    queryKey: ['repos'],
    queryFn: repoApi.listConnected
  })
}

export function useGithubRepos() {
  return useQuery({
    queryKey: ['github-repos'],
    queryFn: repoApi.listGithub,
    enabled: false
  })
}

export function useScanRepo() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: scanApi.triggerScan,
    onSuccess: () => {
      setTimeout(() => qc.invalidateQueries({ queryKey: ['repos'] }), 2000)
    }
  })
}

export function useScanAll() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: scanApi.scanAll,
    onSuccess: () => {
      setTimeout(() => qc.invalidateQueries({ queryKey: ['repos'] }), 2000)
    }
  })
}

export function useConnectRepo() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: repoApi.connect,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['repos'] })
  })
}

export function useDisconnectRepo() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: repoApi.disconnect,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['repos'] })
  })
}
