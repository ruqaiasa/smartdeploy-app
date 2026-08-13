import httpClient from './httpClient'

import type {
  CreateEnvironmentRequest,
  EnvironmentPageResponse,
  EnvironmentResponse,
  EnvironmentSearchParams,
  UpdateEnvironmentRequest,
} from '@/types/environment'

const ENVIRONMENTS_ENDPOINT = '/api/environments'

export async function getEnvironments(
  params: EnvironmentSearchParams = {},
): Promise<EnvironmentPageResponse> {
  const response =
    await httpClient.get<EnvironmentPageResponse>(
      ENVIRONMENTS_ENDPOINT,
      {
        params,
      },
    )

  return response.data
}

export async function getEnvironmentById(
  id: number,
): Promise<EnvironmentResponse> {
  const response =
    await httpClient.get<EnvironmentResponse>(
      `${ENVIRONMENTS_ENDPOINT}/${id}`,
    )

  return response.data
}

export async function createEnvironment(
  request: CreateEnvironmentRequest,
): Promise<EnvironmentResponse> {
  const response =
    await httpClient.post<EnvironmentResponse>(
      ENVIRONMENTS_ENDPOINT,
      request,
    )

  return response.data
}

export async function updateEnvironment(
  id: number,
  request: UpdateEnvironmentRequest,
): Promise<EnvironmentResponse> {
  const response =
    await httpClient.put<EnvironmentResponse>(
      `${ENVIRONMENTS_ENDPOINT}/${id}`,
      request,
    )

  return response.data
}

export async function deleteEnvironment(
  id: number,
): Promise<void> {
  await httpClient.delete(
    `${ENVIRONMENTS_ENDPOINT}/${id}`,
  )
}

export async function restoreEnvironment(
  id: number,
): Promise<EnvironmentResponse> {
  const response =
    await httpClient.patch<EnvironmentResponse>(
      `${ENVIRONMENTS_ENDPOINT}/${id}/restore`,
    )

  return response.data
}

export async function activateEnvironment(
  id: number,
): Promise<EnvironmentResponse> {
  const response =
    await httpClient.patch<EnvironmentResponse>(
      `${ENVIRONMENTS_ENDPOINT}/${id}/activate`,
    )

  return response.data
}

export async function deactivateEnvironment(
  id: number,
): Promise<EnvironmentResponse> {
  const response =
    await httpClient.patch<EnvironmentResponse>(
      `${ENVIRONMENTS_ENDPOINT}/${id}/deactivate`,
    )

  return response.data
}