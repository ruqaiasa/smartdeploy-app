import httpClient from '@/api/httpClient'
import type { BackupEnvironment, BackupInfo, RestoreResult } from '@/types/backup'

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
