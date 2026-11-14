export type RaidStatus = 'HEALTHY' | 'DEGRADED' | 'RECOVERING' | 'UNKNOWN'

export interface RaidSnapshot {
  device: string
  status: RaidStatus
  requiredDisks: number | null
  activeDisks: number | null
  failedDisks: number | null
  recovering: boolean
  message: string
  checkedAt: string
}

export interface StorageHealthResponse {
  prometheusReachable: boolean
  raids: RaidSnapshot[]
}
