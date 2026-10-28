import httpClient from './httpClient'

import type {
  ApplicationMetrics,
} from '@/types/monitoring'

const MONITORING_ENDPOINT =
  '/api/monitoring'

export async function getDeploymentMetrics(
  deploymentId: number,
): Promise<ApplicationMetrics> {
  const response =
    await httpClient.get<ApplicationMetrics>(
      `${MONITORING_ENDPOINT}/deployments/${deploymentId}/metrics`,
    )

  return response.data
}