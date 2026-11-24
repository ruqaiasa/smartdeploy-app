import httpClient from '@/api/httpClient'

/** Rendez-vous en visioconférence entre développeurs et équipe DevOps. */
export interface MeetingContact { id: number; name: string; email: string; role: string | null }
export interface Meeting {
  id: number
  room: string
  roomUrl: string
  topic: string
  agenda: string | null
  status: 'PROPOSED' | 'CONFIRMED' | 'DECLINED' | 'CANCELLED'
  organizer: MeetingContact
  invitee: MeetingContact
  organizedByMe: boolean
  deploymentId: number | null
  claimId: number | null
  slots: string[]
  startsAt: string | null
  durationMinutes: number
  createdAt: string
}
export interface ProposeMeeting { topic: string; agenda: string; inviteeId: number; deploymentId: number | null; claimId: number | null; slots: string[]; durationMinutes: number }

export async function getMeetings(): Promise<Meeting[]> {
  const data = (await httpClient.get<Meeting[]>('/api/meetings')).data
  return Array.isArray(data) ? data : []
}
export async function getMeeting(id: number): Promise<Meeting> {
  return (await httpClient.get<Meeting>(`/api/meetings/${id}`)).data
}
export async function getMeetingContacts(): Promise<MeetingContact[]> {
  const data = (await httpClient.get<MeetingContact[]>('/api/meetings/contacts')).data
  return Array.isArray(data) ? data : []
}
export async function proposeMeeting(request: ProposeMeeting): Promise<Meeting> {
  return (await httpClient.post<Meeting>('/api/meetings', request)).data
}
export async function acceptMeeting(id: number, slot: number): Promise<Meeting> {
  return (await httpClient.post<Meeting>(`/api/meetings/${id}/accept`, { slot })).data
}
export async function declineMeeting(id: number): Promise<Meeting> {
  return (await httpClient.post<Meeting>(`/api/meetings/${id}/decline`)).data
}
/** Télécharge le fichier d’agenda (.ics) du rendez-vous (l’en-tête d’authentification est nécessaire). */
export async function downloadMeetingIcs(id: number): Promise<void> {
  const response = await httpClient.get(`/api/meetings/${id}/ics`, { responseType: 'blob' })
  const url = URL.createObjectURL(response.data as Blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `smartdeploy-visio-${id}.ics`
  a.click()
  URL.revokeObjectURL(url)
}
