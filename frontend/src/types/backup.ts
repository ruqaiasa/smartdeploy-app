export type BackupOrigin = 'SCHEDULED' | 'PRE_DEPLOYMENT' | 'MANUAL'

export interface BackupInfo {
  name: string
  origin: BackupOrigin
  deploymentId: number | null
  /** Version en service au moment d’un point de restauration : ce qu’il permet de retrouver. */
  protectedVersion: string | null
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

export type ProtectionLevel = 'OK' | 'WARNING' | 'CRITICAL'

export interface EnvironmentProtection {
  environmentId: number
  applicationName: string | null
  environmentName: string
  namespace: string
  level: ProtectionLevel
  lastBackupName: string | null
  lastBackupAt: string | null
  message: string
}
