import httpClient from './httpClient'

import type {
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