import httpClient from '@/api/httpClient'
import type { DoraReport } from '@/types/dora'

export async function getDoraReport(days: number, excludeBench: boolean): Promise<DoraReport> {
  const response = await httpClient.get<DoraReport>('/api/dora', { params: { days, excludeBench } })
  return response.data
}
