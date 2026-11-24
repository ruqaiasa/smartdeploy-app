/** Portail développeur : contrôle de compatibilité et discussion. */
export type CheckStatus = 'OK' | 'INFO' | 'WARNING' | 'ERROR'

export interface CompatibilityCheck {
  id: string
  label: string
  status: CheckStatus
  detail: string
  fix: string | null
}

export interface CompatibilityReport {
  repository: string
  branch: string
  verdict: 'READY' | 'ALMOST_READY' | 'NOT_READY'
  verdictLabel: string
  recommendedArchitecture: 'BACKEND' | 'FULLSTACK_EXTERNAL' | 'FULLSTACK_MANAGED' | null
  architectureLabel: string | null
  backendDirectory: string | null
  frontendDirectory: string | null
  checks: CompatibilityCheck[]
}

export interface DeploymentComment {
  id: number
  authorName: string
  authorEmail: string
  authorRole: string | null
  message: string
  createdAt: string
  mine: boolean
}

/** « Mon espace » : les applications de l’utilisateur et l’état de leurs environnements. */
export interface WorkspaceEnvironment {
  id: number
  name: string
  type: string | null
  currentVersion: string | null
  currentSince: string | null
  lastDeploymentId: number | null
  lastStatus: string | null
  lastVersion: string | null
  lastAt: string | null
}

export interface WorkspaceApplication {
  id: number
  name: string
  description: string | null
  repositoryUrl: string
  technology: string | null
  active: boolean
  environments: WorkspaceEnvironment[]
  latestVersions: { id: number; version: string; branch: string; pinned: boolean }[]
}

export interface Workspace {
  applications: number
  inService: number
  pendingRequests: number
  items: WorkspaceApplication[]
}

/** Fiche de déploiement : progression réelle, pods et journaux. */
export interface PhaseState { key: string; label: string; status: 'DONE' | 'CURRENT' | 'FAILED' | 'PENDING' }
export interface DeploymentProgress {
  deploymentId: number
  status: string
  currentStage: string | null
  currentStageIndex: number
  totalStages: number
  phases: PhaseState[]
  elapsedSeconds: number | null
  estimatedRemainingSeconds: number | null
  medianSeconds: number | null
  applicationUrl: string | null
  errorMessage: string | null
  source: 'none' | 'final' | 'jenkins' | 'unavailable'
  /** Pourquoi un déploiement accepté n’a pas encore démarré. */
  pendingReason: string | null
  /** Sans adresse publique : de quoi ouvrir l’application pour un essai. */
  namespace: string | null
  accessService: string | null
  accessPort: number | null
}
export interface PodSummary { name: string; component: string; ready: boolean; restarts: number; phase: string | null; lastTermination: string | null }
export interface LogExcerpt { pod: string; previous: boolean; lines: string[]; totalLines: number }

/** Console DevOps : file de travail triée par urgence. */
export interface QueueItem {
  kind: 'APPROVAL' | 'ALERT' | 'FAILURE' | 'CLAIM' | 'ACCOUNT'
  urgency: number
  title: string
  detail: string
  applicationName: string | null
  environmentName: string | null
  environmentType: string | null
  deploymentId: number | null
  requester: string | null
  since: string | null
  link: string
}
export interface WorkQueue { approvals: number; alerts: number; failures: number; claims: number; items: QueueItem[] }

/** Réclamations. */
export type ClaimType = 'INCIDENT' | 'CONTESTED_FAILURE' | 'RESOURCES' | 'ACCESS' | 'QUESTION'
export type ClaimPriority = 'CRITICAL' | 'HIGH' | 'NORMAL'
export type ClaimStatus = 'OPEN' | 'IN_PROGRESS' | 'WAITING_REQUESTER' | 'RESOLVED' | 'CLOSED'
export type ClaimCause = 'CODE' | 'CONFIGURATION' | 'RESOURCES' | 'PLATFORM' | 'DEPENDENCY' | 'USAGE'
export interface Claim {
  id: number
  type: ClaimType
  priority: ClaimPriority
  status: ClaimStatus
  title: string
  description: string | null
  applicationId: number
  applicationName: string
  environmentName: string | null
  deploymentId: number | null
  requesterEmail: string
  assigneeEmail: string | null
  resolution: string | null
  cause: ClaimCause | null
  context: string | null
  createdAt: string
  updatedAt: string | null
  firstResponseAt: string | null
  resolvedAt: string | null
  waitingFor: 'TEAM' | 'REQUESTER' | null
}
export interface ClaimMessage { id: number; authorName: string; authorRole: string | null; message: string; systemEvent: boolean; createdAt: string; mine: boolean }
export interface CreateClaimRequest { type: ClaimType; priority: ClaimPriority; title: string; description: string; applicationId: number; environmentId: number | null; deploymentId: number | null }

/** Base de connaissances. */
export interface KnowledgeArticle { id: number; title: string; symptoms: string | null; cause: string | null; solution: string; signature: string | null; helpfulCount: number; sourceClaimId: number | null; authorEmail: string | null; createdAt: string; score: number | null }
export interface ArticleRequest { title: string; symptoms: string; cause: string; solution: string; signature: string }

/** Analyste v2. */
export interface AnalysisInsight {
  kind: 'NONE' | 'CODE' | 'ENVIRONMENT' | 'CODE_UNCERTAIN' | 'UNKNOWN'
  verdict: string
  baseVersion: string | null
  baseCommit: string | null
  headCommit: string | null
  changedFiles: string[]
  suspectFiles: string[]
  commits: { sha: string; message: string; author: string }[]
  sameVersionSucceeded: boolean
  feedback: 'CORRECT' | 'PARTIAL' | 'WRONG' | null
  suggestions: KnowledgeArticle[]
  /** Analyste apprenant : confiance recalibrée par les avis de l’équipe. */
  learning: { code: string; reviews: number; confirmed: number; partial: number; refuted: number; precision: number | null; baseConfidence: string; calibratedConfidence: string; note: string } | null
  /** La solution qui a marché sur l’incident passé le plus proche. */
  knownFix: { deploymentId: number; version: string; environmentName: string; similarity: number; resolution: string; resolutionMinutes: number | null; confirmed: boolean } | null
}
export interface AnalystStats { total: number; correct: number; partial: number; wrong: number; precision: number | null; byCode: { code: string; total: number; correct: number; partial: number; wrong: number }[] }

/** Fil d’un déploiement : messages et événements. */
export interface TimelineItem { kind: 'MESSAGE' | 'EVENT'; at: string | null; text: string; author: string; authorRole: string | null; mine: boolean; tone: string | null }

/** Fiche d’excellence d’une application. */
export interface ScorecardCriterion { axis: string; label: string; passed: boolean | null; points: number; detail: string; recommendation: string | null }
export interface Scorecard {
  applicationId: number
  applicationName: string
  score: number | null
  grade: string
  previousScore: number | null
  axes: { key: string; label: string; score: number | null }[]
  criteria: ScorecardCriterion[]
  recommendations: ScorecardCriterion[]
  history: number[]
  computedAt: string
}
export interface ScorecardSummary { applicationId: number; applicationName: string; score: number | null; grade: string }
