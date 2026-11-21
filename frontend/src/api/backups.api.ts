import httpClient from '@/api/httpClient'
import type { BackupEnvironment, BackupInfo, EnvironmentProtection, PlatformBackupRun, PlatformBackupStatus, RestoreResult, StorageLocationStatus } from '@/types/backup'

export async function getBackups(environmentId: number): Promise<BackupInfo[]> {
  const response = await httpClient.get<BackupInfo[]>(`/api/environments/${environmentId}/backups`)
  return Array.isArray(response.data) ? response.data : []
}

export async function createBackup(environmentId: number): Promise<string> {
  const response = await httpClient.post<{ name: string }>(`/api/environments/${environmentId}/backups`)
  return response.data.name
}

export async function restoreBackup(environmentId: number, backupName: string, mode: 'side' | 'replace'): Promise<RestoreResult> {
  const response = await httpClient.post<RestoreResult>(
    `/api/environments/${environmentId}/backups/${encodeURIComponent(backupName)}/restore-${mode}`,
  )
  return response.data
}

export async function getRestorePhase(restoreName: string): Promise<string> {
  const response = await httpClient.get<{ phase: string }>(`/api/restores/${encodeURIComponent(restoreName)}`)
  return response.data.phase
}

/** Environnements actifs, avec leur présence dans le cluster (les déployés en premier). */
export async function getBackupEnvironments(): Promise<BackupEnvironment[]> {
  const response = await httpClient.get<BackupEnvironment[]>('/api/backups/environments')
  return Array.isArray(response.data) ? response.data : []
}

/** Points de restauration pris avant les déploiements. */
export async function getRestorePoints(): Promise<BackupInfo[]> {
  const response = await httpClient.get<BackupInfo[]>('/api/backups/restore-points')
  return Array.isArray(response.data) ? response.data : []
}

/** État de protection de chaque environnement déployé. */
export async function getProtection(): Promise<EnvironmentProtection[]> {
  const response = await httpClient.get<EnvironmentProtection[]>('/api/backups/protection')
  return Array.isArray(response.data) ? response.data : []
}

export async function getStorageLocation(): Promise<StorageLocationStatus> {
  const response = await httpClient.get<StorageLocationStatus>('/api/backups/storage-location')
  return response.data
}

/* Sauvegarde de la base de SmartDeploy elle-même */
export async function getPlatformBackupStatus(): Promise<PlatformBackupStatus> {
  const response = await httpClient.get<PlatformBackupStatus>('/api/platform-backup/status')
  return response.data
}

export async function runPlatformBackup(): Promise<PlatformBackupRun> {
  const response = await httpClient.post<PlatformBackupRun>('/api/platform-backup/run')
  return response.data
}

/** Supprime une sauvegarde terminée (et ses données dans le stockage). */
export async function deleteBackup(environmentId: number, backupName: string): Promise<void> {
  await httpClient.delete(`/api/backups/${encodeURIComponent(backupName)}`, { params: { environmentId } })
}
