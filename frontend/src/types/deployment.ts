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
export type KubernetesDatabaseMode = 'EXTERNAL' | 'MANAGED'

export interface KubernetesComponentRequest {
  name: string
  image: string
  replicas: number
  containerPort: number

  repositoryUrl?: string
  gitBranch?: string
  dockerfilePath?: string
  buildContext?: string
}
export interface KubernetesCiCdRequest {
  buildStrategy: 'MAVEN' | 'DOCKER_ONLY'
  sonarMode: 'MAVEN' | 'CLI' | 'SKIP'
  runBackendTests: boolean
}
export interface KubernetesDatabaseRequest {
  mode: KubernetesDatabaseMode
  databaseName: string
  username: string
  passwordSecretName: string
  passwordSecretKey: string
  externalHost: string | null
  port: number
  storageGi: number
}

export interface KubernetesIngressRequest {
  host: string
  ingressClassName: string | null
  tlsSecretName: string | null
}

export interface KubernetesApplicationRequest {
  namespace: string

  frontend: KubernetesComponentRequest | null
  backend: KubernetesComponentRequest | null

  database: KubernetesDatabaseRequest | null
  ingress: KubernetesIngressRequest | null

  cicd?: KubernetesCiCdRequest | null
}

export interface CreateDeploymentRequest {
  versionId: number
  environmentId: number
  kubernetesConfig: KubernetesApplicationRequest
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