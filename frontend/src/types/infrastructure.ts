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

/** Occupation du système de fichiers posé sur un RAID. */
export interface FilesystemUsage {
  device: string
  mountpoint: string
  sizeBytes: number
  availableBytes: number
  usedPercent: number
  level: 'OK' | 'WARNING' | 'CRITICAL'
  message: string
  checkedAt: string
}

export interface StorageHealthResponse {
  prometheusReachable: boolean
  raids: RaidSnapshot[]
  filesystems?: FilesystemUsage[]
}
