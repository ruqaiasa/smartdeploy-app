export type HealthStatus =
  | 'HEALTHY'
  | 'WARNING'
  | 'CRITICAL'

export interface ApplicationMetrics {
  cpuUsagePercent: number
  memoryUsageMb: number
  runningPods: number
  desiredPods: number
  restartCount: number
  healthStatus: HealthStatus
}