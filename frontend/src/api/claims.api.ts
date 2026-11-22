import httpClient from '@/api/httpClient'
import type { Claim, ClaimCause, ClaimMessage, CreateClaimRequest } from '@/types/portal'

export async function getClaims(): Promise<Claim[]> {
  const data = (await httpClient.get<Claim[]>('/api/claims')).data
  return Array.isArray(data) ? data : []
}
export async function getClaim(id: number): Promise<Claim> {
  return (await httpClient.get<Claim>(`/api/claims/${id}`)).data
}
export async function createClaim(request: CreateClaimRequest): Promise<Claim> {
  return (await httpClient.post<Claim>('/api/claims', request)).data
}
export async function getClaimThread(id: number): Promise<ClaimMessage[]> {
  const data = (await httpClient.get<ClaimMessage[]>(`/api/claims/${id}/messages`)).data
  return Array.isArray(data) ? data : []
}
export async function postClaimMessage(id: number, message: string, askRequester: boolean): Promise<ClaimMessage> {
  return (await httpClient.post<ClaimMessage>(`/api/claims/${id}/messages`, { message, askRequester })).data
}
export async function assignClaim(id: number): Promise<Claim> {
  return (await httpClient.post<Claim>(`/api/claims/${id}/assign`)).data
}
export async function resolveClaim(id: number, resolution: string, cause: ClaimCause): Promise<Claim> {
  return (await httpClient.post<Claim>(`/api/claims/${id}/resolve`, { resolution, cause })).data
}
export async function confirmClaim(id: number): Promise<Claim> {
  return (await httpClient.post<Claim>(`/api/claims/${id}/confirm`)).data
}
export async function reopenClaim(id: number, message: string): Promise<Claim> {
  return (await httpClient.post<Claim>(`/api/claims/${id}/reopen`, { message })).data
}
