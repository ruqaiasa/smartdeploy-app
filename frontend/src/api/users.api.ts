import type { UserRole } from '@/types/auth'
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
/** RG-U1 : valider une inscription en choisissant le rôle, ou la refuser. */
export async function approveUser(id: number, role: UserRole): Promise<UserResponse> {
  return (await httpClient.post<UserResponse>(`/api/users/${id}/approve`, { role })).data
}
export async function rejectUser(id: number): Promise<void> {
  await httpClient.post(`/api/users/${id}/reject`)
}

/** Comptes en attente de validation (notification de l’administrateur). */
export async function getPendingUsers(): Promise<UserResponse[]> {
  const data = (await httpClient.get<UserResponse[]>('/api/users/pending')).data
  return Array.isArray(data) ? data : []
}
