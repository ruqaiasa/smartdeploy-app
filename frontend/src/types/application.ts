import type {
  EnvironmentResponse,
} from '@/types/environment'

export type Technology =
  | 'SPRING_BOOT'
  | 'ANGULAR'
  | 'VUE'
  | 'REACT'
  | 'NODE_JS'
  | 'OTHER'

export interface ApplicationResponse {
  id: number
  name: string
  description: string | null
  repositoryUrl: string
  technology: Technology
  active: boolean
  createdAt: string
  updatedAt: string
  /** RG-O1 : propriétaire de l’application. */
  ownerId?: number | null
  ownerEmail?: string | null
}

export interface CreateApplicationRequest {
  name: string
  description: string
  repositoryUrl: string
  technology: Technology
  /** Propriétaire désigné par un administrateur ou un DevOps. */
  ownerId?: number | null
}

export interface UpdateApplicationRequest {
  name: string
  description: string
  repositoryUrl: string
  technology: Technology
}

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface ApplicationSearchParams {
  page?: number
  size?: number
  search?: string
  technology?: Technology
  active?: boolean
  /** « Mes applications » : seulement celles dont l’utilisateur est propriétaire. */
  mine?: boolean
}

export interface ApplicationDetailsResponse {
  id: number
  name: string
  description: string | null
  repositoryUrl: string
  technology: Technology
  active: boolean
  deleted: boolean
  createdAt: string
  updatedAt: string

  environments: EnvironmentResponse[]
}