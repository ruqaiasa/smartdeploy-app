import httpClient from './httpClient'

import type {
  CreateUserRequest,
  UpdateRoleRequest,
  UpdateStatusRequest,
  UserPageResponse,
  UserResponse,
  UserSearchParams,
} from '@/types/user'

export async function getUsers(
  params: UserSearchParams = {},
): Promise<UserPageResponse> {
  const response =
    await httpClient.get<UserPageResponse>(
      '/api/users',
      {
        params,
      },
    )

  return response.data
}

export async function createUser(
  request: CreateUserRequest,
): Promise<UserResponse> {
  const response =
    await httpClient.post<UserResponse>(
      '/api/users',
      request,
    )

  return response.data
}

export async function updateUserRole(
  id: number,
  request: UpdateRoleRequest,
): Promise<UserResponse> {
  const response =
    await httpClient.patch<UserResponse>(
      `/api/users/${id}/role`,
      request,
    )

  return response.data
}

export async function updateUserStatus(
  id: number,
  request: UpdateStatusRequest,
): Promise<UserResponse> {
  const response =
    await httpClient.patch<UserResponse>(
      `/api/users/${id}/status`,
      request,
    )

  return response.data
}

export async function deleteUser(
  id: number,
): Promise<void> {
  await httpClient.delete(
    `/api/users/${id}`,
  )
}

export async function restoreUser(
  id: number,
): Promise<UserResponse> {
  const response =
    await httpClient.patch<UserResponse>(
      `/api/users/${id}/restore`,
    )

  return response.data
}