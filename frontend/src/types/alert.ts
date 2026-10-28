export type AlertType =
  'POD_UNAVAILABLE' | 'POD_AVAILABILITY_DEGRADED' | 'HIGH_RESTART_COUNT' | 'DEPLOYMENT_DEGRADED'

export type AlertSeverity = 'WARNING' | 'CRITICAL'

export type AlertStatus = 'OPEN' | 'RESOLVED'

export interface Alert {
  id: number

  applicationId: number
  applicationName: string

  environmentId: number
  environmentName: string

  deploymentId: number | null

  type: AlertType
  severity: AlertSeverity
  status: AlertStatus

  title: string
  message: string

  metricName: string | null
  currentValue: number | null
  thresholdValue: number | null

  createdAt: string
  lastDetectedAt: string
  resolvedAt: string | null
}
