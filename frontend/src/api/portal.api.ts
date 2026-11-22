import httpClient from '@/api/httpClient'
import type { CompatibilityReport, DeploymentComment, Workspace } from '@/types/portal'

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
