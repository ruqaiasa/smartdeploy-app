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

/** Disponibilité du stockage des sauvegardes (emplacement Velero « default »). */
export interface StorageLocationStatus {
  phase: string | null
  readable: boolean
  checkedAt: string | null
}

/** Une exécution de la sauvegarde de la base de SmartDeploy. */
export interface PlatformBackupRun {
  id: number
  startedAt: string
  finishedAt: string | null
  success: boolean
  sizeBytes: number | null
  uploaded: boolean
  triggeredBy: string | null
  message: string | null
}

export interface PlatformBackupStatus {
  enabled: boolean
  uploadConfigured: boolean
  directory: string
  retention: number
  lastRun: PlatformBackupRun | null
  lastSuccess: PlatformBackupRun | null
}
