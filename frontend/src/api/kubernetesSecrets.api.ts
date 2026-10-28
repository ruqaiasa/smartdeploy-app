import httpClient from './httpClient'

export type KubernetesSecretSummary = { name: string; keys: string[] }

export async function listKubernetesSecrets(environmentId: number): Promise<KubernetesSecretSummary[]> {
  const response = await httpClient.get<KubernetesSecretSummary[]>('/api/kubernetes/secrets', {
    params: { environmentId },
  })
  return response.data
}

export async function createKubernetesSecret(request: {
  environmentId: number
  name: string
  key: string
  password: string
}): Promise<KubernetesSecretSummary> {
  const response = await httpClient.post<KubernetesSecretSummary>('/api/kubernetes/secrets', request)
  return response.data
}
