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
}

export interface CreateApplicationRequest {
  name: string
  description: string
  repositoryUrl: string
  technology: Technology
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