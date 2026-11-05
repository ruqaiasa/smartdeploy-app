import httpClient from '@/api/httpClient'
import type { ConfidenceScore } from '@/types/confidence'

/** Score de confiance d'une version sur un environnement, avant son déploiement. */
export async function getConfidenceScore(versionId: number, environmentId: number): Promise<ConfidenceScore | null> {
  const response = await httpClient.get<unknown>('/api/confidence', { params: { versionId, environmentId } })
  const data = response.data as Partial<ConfidenceScore> | null
  return data && typeof data.score === 'number' && Array.isArray(data.factors) ? (data as ConfidenceScore) : null
}
