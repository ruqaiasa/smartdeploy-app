import httpClient from './httpClient'
import type { Dashboard } from '@/types/dashboard'

const DASHBOARD_ENDPOINT = '/api/dashboard'

export async function getDashboard(): Promise<Dashboard> {
  const response =
    await httpClient.get<Dashboard>(
      DASHBOARD_ENDPOINT,
    )

  return response.data
}