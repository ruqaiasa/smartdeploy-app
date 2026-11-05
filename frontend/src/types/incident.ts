export type IncidentConfidence = 'HIGH' | 'MEDIUM' | 'LOW'

export interface IncidentAnalysis {
  id: number
  deploymentId: number
  trigger: 'PIPELINE' | 'GUARD' | 'MANUAL'
  code: string
  title: string
  confidence: IncidentConfidence
  explanation: string
  action: string
  evidence: string[]
  alternatives: string[]
  notes: string[]
  createdAt: string
}
