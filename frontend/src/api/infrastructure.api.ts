import httpClient from '@/api/httpClient'
import type { StorageHealthResponse } from '@/types/infrastructure'

/** État des RAID de la machine du cluster (vérifié toutes les 30 s par le backend). */
export async function getStorageHealth(): Promise<StorageHealthResponse> {
  const response = await httpClient.get<StorageHealthResponse>('/api/infrastructure/storage')
  return response.data
}
