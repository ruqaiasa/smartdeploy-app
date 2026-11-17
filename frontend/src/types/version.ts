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

  /** RG-V2 : figée à sa première construction réussie (commit et image fixés). */
  pinnedAt?: string | null
  pinned?: boolean
  /** RG-V10 : environnements où elle est en service, et verdict de son dernier déploiement. */
  inServiceEnvironments?: string[]
  lastVerdict?: string | null
  lastVerdictTone?: 'ok' | 'warning' | 'ko' | 'pending' | 'muted' | null
  /** Chemin de livraison : environnements où la version a été validée par le gardien. */
  validatedEnvironments?: string[]
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