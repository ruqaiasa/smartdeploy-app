/** Portail développeur : contrôle de compatibilité et discussion. */
export type CheckStatus = 'OK' | 'INFO' | 'WARNING' | 'ERROR'

export interface CompatibilityCheck {
  id: string
  label: string
  status: CheckStatus
  detail: string
  fix: string | null
}

export interface CompatibilityReport {
  repository: string
  branch: string
  verdict: 'READY' | 'ALMOST_READY' | 'NOT_READY'
  verdictLabel: string
  recommendedArchitecture: 'BACKEND' | 'FULLSTACK_EXTERNAL' | 'FULLSTACK_MANAGED' | null
  architectureLabel: string | null
  backendDirectory: string | null
  frontendDirectory: string | null
  checks: CompatibilityCheck[]
}

export interface DeploymentComment {
  id: number
  authorName: string
  authorEmail: string
  authorRole: string | null
  message: string
  createdAt: string
  mine: boolean
}

/** « Mon espace » : les applications de l’utilisateur et l’état de leurs environnements. */
export interface WorkspaceEnvironment {
  id: number
  name: string
  type: string | null
  currentVersion: string | null
  currentSince: string | null
  lastDeploymentId: number | null
  lastStatus: string | null
  lastVersion: string | null
  lastAt: string | null
}

export interface WorkspaceApplication {
  id: number
  name: string
  description: string | null
  repositoryUrl: string
  technology: string | null
  active: boolean
  environments: WorkspaceEnvironment[]
  latestVersions: { id: number; version: string; branch: string; pinned: boolean }[]
}

export interface Workspace {
  applications: number
  inService: number
  pendingRequests: number
  items: WorkspaceApplication[]
}
