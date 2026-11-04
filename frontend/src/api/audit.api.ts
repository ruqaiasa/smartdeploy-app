import httpClient from './httpClient'

import type { AuditIntegrityReport, AuditLogPageResponse, AuditLogSearchParams, NotificationTestResult } from '@/types/audit'

const AUDIT_ENDPOINT = '/api/audit-logs'

/** Journal d'activité (administrateurs). */
export async function getAuditLogs(params: AuditLogSearchParams = {}): Promise<AuditLogPageResponse> {
  const response = await httpClient.get<AuditLogPageResponse>(AUDIT_ENDPOINT, {
    params: {
      page: params.page ?? 0,
      size: params.size ?? 20,
      // Spring lit une liste séparée par des virgules
      actions: params.actions?.length ? params.actions.join(',') : undefined,
      search: params.search?.trim() || undefined,
    },
  })
  return response.data
}

/** Vérifie la chaîne d'empreintes du journal (détection des modifications). */
export async function verifyAuditIntegrity(): Promise<AuditIntegrityReport> {
  const response = await httpClient.get<AuditIntegrityReport>(`${AUDIT_ENDPOINT}/integrity`)
  return response.data
}

/** Envoie un message de test sur Slack (administrateurs). */
export async function sendTestNotification(): Promise<NotificationTestResult> {
  const response = await httpClient.post<NotificationTestResult>('/api/notifications/test')
  return response.data
}
