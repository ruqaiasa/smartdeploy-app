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
  /** Faits précis extraits des preuves (fichier, ligne, port, version corrigée…). */
  facts?: IncidentFact[]
  /** La ligne de log qui explique le mieux l’incident. */
  decisiveLine?: string | null
  /** La première analyse, faite au moment de l’incident, si celle-ci a été relancée depuis. */
  initial?: InitialIncidentAnalysis | null
  /** Mémoire des incidents : les incidents passés les plus proches, et comment ils ont été résolus. */
  similar?: SimilarIncident[]
}

export interface SimilarIncident {
  deploymentId: number
  version: string
  environmentName: string
  at: string
  /** Similarité de 0 à 100. */
  similarity: number
  title: string
  sameCause: boolean
  sharedTerms: string[]
  resolution: string
  /** Durée de résolution en minutes ; null si inconnue. */
  resolutionMinutes: number | null
}

export interface IncidentFact {
  label: string
  value: string
}

export interface InitialIncidentAnalysis {
  id: number
  title: string
  confidence: IncidentConfidence
  createdAt: string
  evidence: string[]
  facts: IncidentFact[]
  decisiveLine: string | null
}
