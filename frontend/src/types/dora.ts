export type DoraLevel = 'ELITE' | 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE'

export interface DoraMetric {
  key: string
  label: string
  value: number | null
  unit: string
  level: DoraLevel
  detail: string
  definition: string
}

export interface DoraReport {
  periodDays: number
  from: string
  to: string
  metrics: DoraMetric[]
  notes: string[]
}
