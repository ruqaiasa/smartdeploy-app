export type AuditAction =
  | 'LOGIN_SUCCESS'
  | 'LOGIN_FAILURE'
  | 'LOGOUT'
  | 'ACCESS_DENIED'
  | 'API_KEY_REJECTED'
  | 'SUSPICIOUS_ACTIVITY'
  | 'RESOURCE_CREATED'
  | 'RESOURCE_UPDATED'
  | 'RESOURCE_DELETED'
  | 'USER_ROLE_CHANGED'
  | 'USER_STATUS_CHANGED'
  | 'ACTION_FAILED'
  | 'ALERT_OPENED'
  | 'ALERT_RESOLVED'
  | 'GUARD_PASSED'
  | 'GUARD_DEGRADED'
  | 'GUARD_INCONCLUSIVE'
  | 'INCIDENT_ANALYZED'
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
  details: string | null
  ipAddress: string | null
  userAgent: string | null
  occurredAt: string
}

export interface AuditIntegrityReport {
  valid: boolean
  checkedEntries: number
  unchainedEntries: number
  firstInvalidId: number | null
  message: string
  verifiedAt: string
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

export interface NotificationTestResult {
  sent: boolean
  message: string
}
