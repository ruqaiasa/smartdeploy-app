import httpClient from '@/api/httpClient'

/** Supervision dans le temps et tendances du tableau de bord. */
export interface Point { timestamp: number; value: number }
export interface SeriesMarker { at: number; kind: 'DEPLOYMENT' | 'ALERT'; label: string; tone: string; deploymentId: number | null }
export interface EnvironmentSeries {
  environmentId: number
  applicationName: string
  environmentName: string
  namespace: string
  from: number
  to: number
  step: number
  cpu: Point[]
  memory: Point[]
  readyPods: Point[]
  desiredPods: Point[]
  restarts: Point[]
  cpuLimit: number | null
  memoryLimit: number | null
  markers: SeriesMarker[]
  error: string | null
}
export interface Kpi { key: string; label: string; value: number | null; previous: number | null; unit: string; higherIsBetter: boolean }
export interface Insights {
  days: number
  daily: { date: string; success: number; failed: number; other: number }[]
  kpis: Kpi[]
  causes: { code: string; title: string; count: number }[]
  environments: { environmentId: number; applicationName: string; environmentName: string; type: string | null; version: string | null; state: string; openAlerts: number; lastDeploymentAt: string | null }[]
  degradedVersions: number
  analystPrecision: number | null
  analystReviews: number
}

/** Capacité du cluster : utilisation dans le temps, et part réservée par les pods. */
export interface ClusterCapacity {
  from: number
  to: number
  cpuUsage: Point[]
  memoryUsage: Point[]
  runningPods: Point[]
  cpuRequested: number | null
  memoryRequested: number | null
  cpuCores: number | null
  memoryGb: number | null
  runningPodsNow: number | null
  error: string | null
}
export async function getClusterCapacity(hours: number): Promise<ClusterCapacity> {
  return (await httpClient.get<ClusterCapacity>('/api/monitoring/cluster', { params: { hours } })).data
}

export async function getEnvironmentSeries(environmentId: number, hours: number): Promise<EnvironmentSeries> {
  return (await httpClient.get<EnvironmentSeries>(`/api/monitoring/environments/${environmentId}/series`, { params: { hours } })).data
}
export async function getInsights(days: number): Promise<Insights> {
  return (await httpClient.get<Insights>('/api/dashboard/insights', { params: { days } })).data
}
