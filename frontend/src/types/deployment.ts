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
  /** Images réellement déployées, référencées par empreinte (sha256). */
  deployedBackendImage?: string | null
  deployedFrontendImage?: string | null
  /** Promotion : déploiement dont les images ont été réutilisées, sans reconstruction. */
  imagesFromId?: number | null
  /** Commit exact construit par le pipeline. */
  builtCommit?: string | null
  /** Vrai pour le déploiement actuellement en service dans son environnement. */
  current?: boolean
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
  /** RG-V5 : confirmation explicite pour redéployer la version en service. */
  confirmRedeploy?: boolean
  /** RG-V4 : reconstruire même si la version a déjà une image. */
  forceRebuild?: boolean
}

/** Vérification préalable d’un déploiement (règles RG-V4 à RG-V8). */
export interface DeploymentPrecheck {
  currentDeploymentId: number | null
  currentVersion: string | null
  currentSince: string | null
  sameVersionInService: boolean
  olderThanCurrent: boolean
  rollbackTargetId: number | null
  degraded: { deploymentId: number; environmentName: string; at: string; cause: string } | null
  lastFailure: {
    deploymentId: number
    at: string
    title: string
    detail: string | null
    codeCause: boolean
    unchangedSinceFailure: boolean | null
    unverifiableReason: string | null
  } | null
  reuse: { deploymentId: number; environmentName: string; commit: string | null } | null
  pinnedCommit: string | null
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