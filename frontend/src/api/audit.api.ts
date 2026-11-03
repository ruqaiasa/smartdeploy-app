import httpClient from './httpClient'

import type { AuditLogPageResponse, AuditLogSearchParams } from '@/types/audit'

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
