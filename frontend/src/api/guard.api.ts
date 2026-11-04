import httpClient from '@/api/httpClient'
import type { DeploymentGuard } from '@/types/guard'

/** Dernier gardien de chacun des déploiements demandés. */
export async function getDeploymentGuards(deploymentIds: number[]): Promise<DeploymentGuard[]> {
  if (!deploymentIds.length) return []
  const response = await httpClient.get<DeploymentGuard[]>('/api/deployment-guards', {
    params: { deploymentIds: deploymentIds.join(',') },
  })
  return response.data
}
