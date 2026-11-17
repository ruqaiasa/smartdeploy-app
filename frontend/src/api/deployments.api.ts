import httpClient from './httpClient'

import type {
  DeploymentPrecheck,
  PromotionProposal,
  CreateDeploymentRequest,
  DeploymentPageResponse,
  DeploymentResponse,
  DeploymentSearchParams,
  UpdateDeploymentMetadataRequest,
  UpdateDeploymentStatusRequest,
} from '@/types/deployment'

const DEPLOYMENTS_ENDPOINT =
  '/api/deployments'

export async function getDeployments(
  params: DeploymentSearchParams = {},
): Promise<DeploymentPageResponse> {
  const response =
    await httpClient.get<DeploymentPageResponse>(
      DEPLOYMENTS_ENDPOINT,
      {
        params,
      },
    )

  return response.data
}

export async function getDeploymentById(
  id: number,
): Promise<DeploymentResponse> {
  const response =
    await httpClient.get<DeploymentResponse>(
      `${DEPLOYMENTS_ENDPOINT}/${id}`,
    )

  return response.data
}

export async function createDeployment(
  request: CreateDeploymentRequest,
): Promise<DeploymentResponse> {
  const response =
    await httpClient.post<DeploymentResponse>(
      DEPLOYMENTS_ENDPOINT,
      request,
    )

  return response.data
}

export async function deleteDeployment(
  id: number,
): Promise<void> {
  await httpClient.delete(
    `${DEPLOYMENTS_ENDPOINT}/${id}`,
  )
}

export async function updateDeploymentStatus(
  id: number,
  request: UpdateDeploymentStatusRequest,
): Promise<DeploymentResponse> {
  const response =
    await httpClient.patch<DeploymentResponse>(
      `${DEPLOYMENTS_ENDPOINT}/${id}/status`,
      request,
    )

  return response.data
}

export async function updateDeploymentMetadata(
  id: number,
  request: UpdateDeploymentMetadataRequest,
): Promise<DeploymentResponse> {
  const response =
    await httpClient.patch<DeploymentResponse>(
      `${DEPLOYMENTS_ENDPOINT}/${id}/metadata`,
      request,
    )

  return response.data
}

export async function rollbackDeployment(
  id: number,
): Promise<DeploymentResponse> {
  const response =
    await httpClient.post<DeploymentResponse>(
      `${DEPLOYMENTS_ENDPOINT}/${id}/rollback`,
    )

  return response.data
}

/**
 * Annule un déploiement en attente ou en cours : arrêt du build Jenkins
 * (ou retrait de la file d'attente), puis statut CANCELLED.
 */
export async function cancelDeployment(
  id: number,
): Promise<DeploymentResponse> {
  const response =
    await httpClient.post<DeploymentResponse>(
      `${DEPLOYMENTS_ENDPOINT}/${id}/cancel`,
    )

  return response.data
}

/** Ce qu’il faut savoir avant de déployer cette version sur cet environnement. */
export async function getDeploymentPrecheck(versionId: number, environmentId: number): Promise<DeploymentPrecheck> {
  const response = await httpClient.get<DeploymentPrecheck>('/api/deployments/precheck', { params: { versionId, environmentId } })
  return response.data
}

/* Approbation avant la production (principe des quatre yeux) */
export async function approveDeployment(id: number, comment: string): Promise<DeploymentResponse> {
  const response = await httpClient.post<DeploymentResponse>(`/api/deployments/${id}/approve`, { comment })
  return response.data
}

export async function rejectDeployment(id: number, comment: string): Promise<DeploymentResponse> {
  const response = await httpClient.post<DeploymentResponse>(`/api/deployments/${id}/reject`, { comment })
  return response.data
}

export async function getPendingApprovalsCount(): Promise<number> {
  const response = await httpClient.get<{ count: number }>('/api/deployments/approvals/pending-count')
  return response.data.count ?? 0
}

/** Promotion guidée : vers quel environnement ce déploiement validé peut monter, avec sa configuration. */
export async function getPromotion(deploymentId: number): Promise<PromotionProposal> {
  const response = await httpClient.get<PromotionProposal>(`/api/deployments/${deploymentId}/promotion`)
  return response.data
}
