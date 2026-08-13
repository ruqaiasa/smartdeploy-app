import httpClient from './httpClient'

import type {
  BuildStatus,
  CreateVersionRequest,
  UpdateBuildMetadataRequest,
  UpdateVersionRequest,
  VersionPageResponse,
  VersionResponse,
  VersionSearchParams,
} from '@/types/version'

const VERSIONS_ENDPOINT = '/api/versions'

export async function getVersions(
  params: VersionSearchParams = {},
): Promise<VersionPageResponse> {
  const response =
    await httpClient.get<VersionPageResponse>(
      VERSIONS_ENDPOINT,
      {
        params,
      },
    )

  return response.data
}

export async function getVersionById(
  id: number,
): Promise<VersionResponse> {
  const response =
    await httpClient.get<VersionResponse>(
      `${VERSIONS_ENDPOINT}/${id}`,
    )

  return response.data
}

export async function getVersionsByApplication(
  applicationId: number,
): Promise<VersionResponse[]> {
  const response =
    await httpClient.get<VersionResponse[]>(
      `${VERSIONS_ENDPOINT}/application/${applicationId}`,
    )

  return response.data
}

export async function createVersion(
  request: CreateVersionRequest,
): Promise<VersionResponse> {
  const response =
    await httpClient.post<VersionResponse>(
      VERSIONS_ENDPOINT,
      request,
    )

  return response.data
}

export async function updateVersion(
  id: number,
  request: UpdateVersionRequest,
): Promise<VersionResponse> {
  const response =
    await httpClient.put<VersionResponse>(
      `${VERSIONS_ENDPOINT}/${id}`,
      request,
    )

  return response.data
}

export async function deleteVersion(
  id: number,
): Promise<void> {
  await httpClient.delete(
    `${VERSIONS_ENDPOINT}/${id}`,
  )
}

export async function restoreVersion(
  id: number,
): Promise<VersionResponse> {
  const response =
    await httpClient.patch<VersionResponse>(
      `${VERSIONS_ENDPOINT}/${id}/restore`,
    )

  return response.data
}

export async function updateVersionBuildStatus(
  id: number,
  buildStatus: BuildStatus,
): Promise<VersionResponse> {
  const response =
    await httpClient.patch<VersionResponse>(
      `${VERSIONS_ENDPOINT}/${id}/status`,
      {
        buildStatus,
      },
    )

  return response.data
}

export async function updateVersionMetadata(
  id: number,
  request: UpdateBuildMetadataRequest,
): Promise<VersionResponse> {
  const response =
    await httpClient.patch<VersionResponse>(
      `${VERSIONS_ENDPOINT}/${id}/metadata`,
      request,
    )

  return response.data
}