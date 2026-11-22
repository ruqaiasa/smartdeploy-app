import httpClient from '@/api/httpClient'
import type { ArticleRequest, KnowledgeArticle } from '@/types/portal'

export async function searchKnowledge(q: string): Promise<KnowledgeArticle[]> {
  const data = (await httpClient.get<KnowledgeArticle[]>('/api/knowledge', { params: { q } })).data
  return Array.isArray(data) ? data : []
}
export async function suggestKnowledge(text: string): Promise<KnowledgeArticle[]> {
  const data = (await httpClient.get<KnowledgeArticle[]>('/api/knowledge/suggest', { params: { text } })).data
  return Array.isArray(data) ? data : []
}
export async function createArticle(request: ArticleRequest): Promise<KnowledgeArticle> {
  return (await httpClient.post<KnowledgeArticle>('/api/knowledge', request)).data
}
export async function updateArticle(id: number, request: ArticleRequest): Promise<KnowledgeArticle> {
  return (await httpClient.put<KnowledgeArticle>(`/api/knowledge/${id}`, request)).data
}
export async function deleteArticle(id: number): Promise<void> {
  await httpClient.delete(`/api/knowledge/${id}`)
}
export async function articleFromClaim(claimId: number): Promise<KnowledgeArticle> {
  return (await httpClient.post<KnowledgeArticle>(`/api/knowledge/from-claim/${claimId}`)).data
}
export async function markHelpful(id: number): Promise<KnowledgeArticle> {
  return (await httpClient.post<KnowledgeArticle>(`/api/knowledge/${id}/helpful`)).data
}
