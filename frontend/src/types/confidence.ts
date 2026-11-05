export interface ConfidenceFactor {
  label: string
  points: number
  explanation: string
}

export interface ConfidenceScore {
  score: number
  level: 'HIGH' | 'MEDIUM' | 'LOW'
  levelLabel: string
  advice: string
  factors: ConfidenceFactor[]
}
