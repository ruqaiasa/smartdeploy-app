import type { DeploymentStatus } from '@/types/deployment'

export interface RecentDeployment {
  id: number

  applicationId: number
  applicationName: string

  version: string

  environmentId: number
  environmentName: string
  environmentType: string

  status: DeploymentStatus

  createdAt: string
  finishedAt: string | null
}

export interface Dashboard {
  totalApplications: number
  activeApplications: number

  totalEnvironments: number
  activeEnvironments: number

  totalDeployments: number
  successfulDeployments: number
  failedDeployments: number

  successRate: number

  openAlerts: number

  recentDeployments: RecentDeployment[]
}