import type { UserRole } from '@/types/auth'

export interface UserResponse {
  id: number
  firstName: string
  lastName: string
  email: string
  role: UserRole
  enabled: boolean
  deleted: boolean
  createdAt: string
  updatedAt: string
}

export interface UserPageResponse {
  content: UserResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface UserSearchParams {
  page?: number
  size?: number
  search?: string
  role?: UserRole
  enabled?: boolean
}

export interface CreateUserRequest {
  firstName: string
  lastName: string
  email: string
  password: string
  role: UserRole
}

export interface UpdateRoleRequest {
  role: UserRole
}

export interface UpdateStatusRequest {
  enabled: boolean
}