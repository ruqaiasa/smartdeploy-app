export type AuditAction =
  | 'LOGIN_SUCCESS'
  | 'LOGIN_FAILURE'
  | 'DEPLOYMENT_CREATED'
  | 'DEPLOYMENT_STARTED'
  | 'DEPLOYMENT_SUCCEEDED'
  | 'DEPLOYMENT_FAILED'
  | 'DEPLOYMENT_CANCELLED'
  | 'DEPLOYMENT_ROLLBACK_REQUESTED'
  | 'DEPLOYMENT_REPLACED'
  | 'DEPLOYMENT_EXPIRED'
  | 'DEPLOYMENT_DELETED'

export interface AuditLogResponse {
  id: number
  action: AuditAction
  actor: string
  targetType: string | null
  targetId: number | null
  applicationName: string | null
  environmentName: string | null
  summary: string
  occurredAt: string
}

export interface AuditLogPageResponse {
  content: AuditLogResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface AuditLogSearchParams {
  page?: number
  size?: number
  actions?: AuditAction[]
  search?: string
}
