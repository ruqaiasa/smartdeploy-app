export type BackupOrigin = 'SCHEDULED' | 'PRE_DEPLOYMENT' | 'MANUAL'

export interface BackupInfo {
  name: string
  origin: BackupOrigin
  deploymentId: number | null
  phase: string
  startedAt: string | null
  completedAt: string | null
  expiresAt: string | null
  itemsBackedUp: number | null
  errors: number | null
  warnings: number | null
}

export interface RestoreResult {
  restoreName: string
  targetNamespace: string
}

export interface BackupEnvironment {
  environmentId: number
  applicationName: string | null
  environmentName: string
  namespace: string
  deployed: boolean
}
