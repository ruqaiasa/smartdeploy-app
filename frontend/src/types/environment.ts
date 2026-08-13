export type EnvironmentType =
  | 'DEVELOPMENT'
  | 'TEST'
  | 'STAGING'
  | 'PRODUCTION'

export interface EnvironmentResponse {
  id: number
  name: string
  description: string | null
  type: EnvironmentType
  namespace: string
  clusterUrl: string | null

  applicationId: number
  applicationName: string

  active: boolean
  deleted: boolean
  createdAt: string
  updatedAt: string
}

export interface CreateEnvironmentRequest {
  name: string
  description: string
  type: EnvironmentType
  namespace: string
  clusterUrl: string
  applicationId: number
}

export interface UpdateEnvironmentRequest {
  name: string
  description: string
  type: EnvironmentType
  namespace: string
  clusterUrl: string
  applicationId: number
}

export interface EnvironmentSearchParams {
  page?: number
  size?: number
  search?: string
  type?: EnvironmentType
  active?: boolean
  applicationId?: number
}

export interface EnvironmentPageResponse {
  content: EnvironmentResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}