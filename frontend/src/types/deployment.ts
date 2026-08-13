export type DeploymentStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'CANCELLED'
  | 'ROLLED_BACK'

export interface DeploymentResponse {
  id: number

  applicationId: number
  applicationName: string

  versionId: number
  version: string

  environmentId: number
  environmentName: string
  environmentType: string

  requestedById: number
  requestedByEmail: string

  status: DeploymentStatus

  startedAt: string | null
  finishedAt: string | null

  durationSeconds: number | null

  jenkinsBuildNumber: number | null

  deploymentLogsUrl: string | null

  errorMessage: string | null

  rollbackOfId: number | null

  deleted: boolean

  createdAt: string
  updatedAt: string
}

export interface CreateDeploymentRequest {
  versionId: number
  environmentId: number
}

export interface UpdateDeploymentStatusRequest {
  status: DeploymentStatus
  errorMessage: string
}

export interface UpdateDeploymentMetadataRequest {
  jenkinsBuildNumber: number | null
  deploymentLogsUrl: string
}

export interface DeploymentPageResponse {
  content: DeploymentResponse[]

  page: number
  size: number

  totalElements: number
  totalPages: number

  first: boolean
  last: boolean
}

export interface DeploymentSearchParams {
  page?: number
  size?: number

  search?: string

  status?: DeploymentStatus

  applicationId?: number
  versionId?: number
  environmentId?: number
}