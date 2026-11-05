export type GuardStatus =
  | 'WATCHING'
  | 'HEALTHY'
  | 'HEALTHY_WITH_WARNINGS'
  | 'DEGRADED'
  | 'INCONCLUSIVE'
  | 'SUPERSEDED'

export type GuardLevel = 'OK' | 'UNKNOWN' | 'WARNING' | 'CRITICAL'

export interface GuardCheck {
  key: string
  label: string
  level: GuardLevel
  observed: string | null
  reference: string | null
  explanation: string
}

export interface DeploymentGuard {
  id: number
  deploymentId: number
  status: GuardStatus
  verdict: string | null
  checks: GuardCheck[]
  evaluations: number
  rollbackTargetId: number | null
  autoRollbackDeploymentId: number | null
  startedAt: string
  endsAt: string
  lastEvaluatedAt: string | null
  decidedAt: string | null
}
