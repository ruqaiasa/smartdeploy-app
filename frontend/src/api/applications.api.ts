import httpClient from './httpClient'

import type {
  ApplicationDetailsResponse,
  ApplicationResponse,
  ApplicationSearchParams,
  CreateApplicationRequest,
  PageResponse,
  UpdateApplicationRequest,
} from '@/types/application'


const APPLICATIONS_ENDPOINT = '/api/applications'

export async function getApplications(
  params: ApplicationSearchParams = {},
): Promise<PageResponse<ApplicationResponse>> {
  const response =
    await httpClient.get<PageResponse<ApplicationResponse>>(
      APPLICATIONS_ENDPOINT,
      {
        params,
      },
    )

  return response.data
}

export async function getApplicationById(
  id: number,
): Promise<ApplicationResponse> {
  const response =
    await httpClient.get<ApplicationResponse>(
      `${APPLICATIONS_ENDPOINT}/${id}`,
    )

  return response.data
}

export async function createApplication(
  request: CreateApplicationRequest,
): Promise<ApplicationResponse> {
  const response =
    await httpClient.post<ApplicationResponse>(
      APPLICATIONS_ENDPOINT,
      request,
    )

  return response.data
}

export async function updateApplication(
  id: number,
  request: UpdateApplicationRequest,
): Promise<ApplicationResponse> {
  const response =
    await httpClient.put<ApplicationResponse>(
      `${APPLICATIONS_ENDPOINT}/${id}`,
      request,
    )

  return response.data
}

export async function deleteApplication(
  id: number,
): Promise<void> {
  await httpClient.delete(
    `${APPLICATIONS_ENDPOINT}/${id}`,
  )
}

export async function restoreApplication(
  id: number,
): Promise<ApplicationResponse> {
  const response =
    await httpClient.patch<ApplicationResponse>(
      `${APPLICATIONS_ENDPOINT}/${id}/restore`,
    )

  return response.data
}

export async function activateApplication(
  id: number,
): Promise<ApplicationResponse> {
  const response =
    await httpClient.patch<ApplicationResponse>(
      `${APPLICATIONS_ENDPOINT}/${id}/activate`,
    )

  return response.data
}

export async function deactivateApplication(
  id: number,
): Promise<ApplicationResponse> {
  const response =
    await httpClient.patch<ApplicationResponse>(
      `${APPLICATIONS_ENDPOINT}/${id}/deactivate`,
    )

  return response.data
}
export async function getApplicationDetails(
  id: number,
): Promise<ApplicationDetailsResponse> {
  const response =
    await httpClient.get<ApplicationDetailsResponse>(
      `${APPLICATIONS_ENDPOINT}/${id}/details`,
    )

  return response.data
}