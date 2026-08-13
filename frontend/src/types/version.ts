export type BuildStatus =
  | 'PENDING'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'

export interface VersionResponse {
  id: number

  applicationId: number
  applicationName: string

  version: string

  gitBranch: string
  gitCommit: string | null

  dockerImage: string | null

  buildStatus: BuildStatus
  buildNumber: number | null

  artifactPath: string | null
  buildLogsUrl: string | null

  deleted: boolean

  createdAt: string
  updatedAt: string
}

export interface CreateVersionRequest {
  applicationId: number

  version: string
  gitBranch: string

  gitCommit: string
  dockerImage: string
  artifactPath: string
  buildLogsUrl: string
}

export interface UpdateVersionRequest {
  version: string
  gitBranch: string

  gitCommit: string
  dockerImage: string
  artifactPath: string
  buildLogsUrl: string
}

export interface UpdateBuildStatusRequest {
  buildStatus: BuildStatus
}

export interface UpdateBuildMetadataRequest {
  buildNumber: number | null
  dockerImage: string
  artifactPath: string
  buildLogsUrl: string
}

export interface VersionPageResponse {
  content: VersionResponse[]

  page: number
  size: number

  totalElements: number
  totalPages: number

  first: boolean
  last: boolean
}

export interface VersionSearchParams {
  page?: number
  size?: number

  search?: string

  buildStatus?: BuildStatus

  applicationId?: number
}