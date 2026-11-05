import httpClient from '@/api/httpClient'
import type { IncidentAnalysis } from '@/types/incident'

/** Dernier diagnostic du déploiement, ou null s'il n'a pas encore été analysé. */
export async function getIncidentAnalysis(deploymentId: number): Promise<IncidentAnalysis | null> {
  const response = await httpClient.get<unknown>(`/api/deployments/${deploymentId}/analysis`)
  return response.status === 204 ? null : asAnalysis(response.data)
}

/** Toute réponse qui n'est pas un diagnostic valide (page d'erreur, proxy…) vaut « aucune analyse ». */
function asAnalysis(data: unknown): IncidentAnalysis | null {
  if (!data || typeof data !== 'object') return null
  const candidate = data as Partial<IncidentAnalysis>
  return typeof candidate.code === 'string' && Array.isArray(candidate.evidence) ? (candidate as IncidentAnalysis) : null
}

/** Relance l'analyse (DevOps, administrateur). */
export async function rerunIncidentAnalysis(deploymentId: number): Promise<IncidentAnalysis> {
  const response = await httpClient.post<unknown>(`/api/deployments/${deploymentId}/analysis`)
  const analysis = asAnalysis(response.data)
  if (!analysis) throw new Error('Réponse d’analyse invalide')
  return analysis
}
