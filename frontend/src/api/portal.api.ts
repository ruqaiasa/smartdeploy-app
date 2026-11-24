import httpClient from '@/api/httpClient'
import type { Scorecard, ScorecardSummary, AnalysisInsight, AnalystStats, CompatibilityReport, DeploymentComment, DeploymentProgress, LogExcerpt, PodSummary, TimelineItem, WorkQueue, Workspace } from '@/types/portal'

export async function checkCompatibility(repositoryUrl: string, branch: string): Promise<CompatibilityReport> {
  const response = await httpClient.post<CompatibilityReport>('/api/portal/compatibility', { repositoryUrl, branch })
  return response.data
}

export async function getComments(deploymentId: number): Promise<DeploymentComment[]> {
  const response = await httpClient.get<DeploymentComment[]>(`/api/deployments/${deploymentId}/comments`)
  return Array.isArray(response.data) ? response.data : []
}

export async function addComment(deploymentId: number, message: string): Promise<DeploymentComment> {
  const response = await httpClient.post<DeploymentComment>(`/api/deployments/${deploymentId}/comments`, { message })
  return response.data
}

export async function getWorkspace(): Promise<Workspace> {
  const response = await httpClient.get<Workspace>('/api/portal/workspace')
  return response.data
}

export async function getProgress(deploymentId: number): Promise<DeploymentProgress> {
  return (await httpClient.get<DeploymentProgress>(`/api/deployments/${deploymentId}/progress`)).data
}

export async function getPods(deploymentId: number): Promise<PodSummary[]> {
  const data = (await httpClient.get<PodSummary[]>(`/api/deployments/${deploymentId}/pods`)).data
  return Array.isArray(data) ? data : []
}

export async function getLogs(deploymentId: number, pod: string, previous: boolean, errorsOnly: boolean): Promise<LogExcerpt> {
  return (await httpClient.get<LogExcerpt>(`/api/deployments/${deploymentId}/logs`, { params: { pod, previous, errorsOnly } })).data
}

export async function getWorkQueue(): Promise<WorkQueue> {
  return (await httpClient.get<WorkQueue>('/api/portal/queue')).data
}

export async function getInsight(deploymentId: number): Promise<AnalysisInsight> {
  return (await httpClient.get<AnalysisInsight>(`/api/deployments/${deploymentId}/insight`)).data
}
export async function sendAnalysisFeedback(deploymentId: number, verdict: 'CORRECT' | 'PARTIAL' | 'WRONG', actualCause: string): Promise<void> {
  await httpClient.post(`/api/deployments/${deploymentId}/analysis/feedback`, { verdict, actualCause })
}
export async function getAnalystStats(): Promise<AnalystStats> {
  return (await httpClient.get<AnalystStats>('/api/portal/analyst-stats')).data
}
export async function getTimeline(deploymentId: number): Promise<TimelineItem[]> {
  const data = (await httpClient.get<TimelineItem[]>(`/api/deployments/${deploymentId}/timeline`)).data
  return Array.isArray(data) ? data : []
}

/** Messages non lus des discussions de déploiement. */
export interface UnreadThread { deploymentId: number; applicationName: string; version: string; environmentName: string; unread: number; lastMessage: string; lastAuthor: string; lastAt: string }
export async function getUnreadDiscussions(): Promise<{ total: number; threads: UnreadThread[] }> {
  return (await httpClient.get<{ total: number; threads: UnreadThread[] }>('/api/discussions/unread')).data
}
export async function markDiscussionRead(deploymentId: number): Promise<void> {
  await httpClient.post(`/api/deployments/${deploymentId}/comments/read`)
}

/** Toutes les conversations récentes (lues ou non), pour la messagerie de l’équipe. */
export async function getRecentDiscussions(): Promise<UnreadThread[]> {
  const data = (await httpClient.get<UnreadThread[]>('/api/discussions/recent')).data
  return Array.isArray(data) ? data : []
}

export async function getScorecard(applicationId: number, refresh = false): Promise<Scorecard> {
  return (await httpClient.get<Scorecard>(`/api/portal/applications/${applicationId}/scorecard`, { params: { refresh } })).data
}
export async function getScorecards(): Promise<ScorecardSummary[]> {
  const data = (await httpClient.get<ScorecardSummary[]>('/api/portal/scorecards')).data
  return Array.isArray(data) ? data : []
}

/** Les applications en ligne : chaque environnement en service, sa version et son adresse. */
export interface OnlineApplication { environmentId: number; applicationName: string; environmentName: string; environmentType: string | null; version: string; deploymentId: number; url: string | null }
export async function getOnlineApplications(): Promise<OnlineApplication[]> {
  const data = (await httpClient.get<OnlineApplication[]>('/api/portal/online')).data
  return Array.isArray(data) ? data : []
}

/** Post-mortem automatique : le rapport d’incident d’un déploiement. */
export interface PostMortem {
  deploymentId: number
  title: string
  severity: string
  kind: string
  summary: string
  applicationName: string
  environmentName: string
  environmentType: string | null
  version: string
  requestedBy: string | null
  impactStart: string | null
  detectedAt: string | null
  recoveredAt: string | null
  minutesToDetect: number | null
  minutesToRecover: number | null
  impact: string
  detection: string
  cause: string
  causeDetail: string | null
  decisiveLine: string | null
  confidence: string | null
  recovery: string
  actions: string[]
  lessons: string[]
  timeline: { at: string; text: string; tone: string | null }[]
  generatedAt: string
}
export async function getPostMortem(deploymentId: number): Promise<PostMortem> {
  return (await httpClient.get<PostMortem>(`/api/deployments/${deploymentId}/postmortem`)).data
}
