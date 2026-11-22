import type { ClaimCause, ClaimPriority, ClaimStatus, ClaimType } from '@/types/portal'

export const CLAIM_TYPES: { value: ClaimType; label: string; hint: string }[] = [
  { value: 'INCIDENT', label: 'Incident', hint: 'Mon application ne fonctionne pas' },
  { value: 'CONTESTED_FAILURE', label: 'Échec contesté', hint: 'L’échec ne vient pas de mon code' },
  { value: 'RESOURCES', label: 'Ressources', hint: 'Mémoire, processeur, instances' },
  { value: 'ACCESS', label: 'Accès ou environnement', hint: 'Production, droit, accès' },
  { value: 'QUESTION', label: 'Question', hint: 'Comment faire ?' },
]
export const TYPE_LABEL: Record<ClaimType, string> = Object.fromEntries(CLAIM_TYPES.map(t => [t.value, t.label])) as Record<ClaimType, string>
export const PRIORITY_LABEL: Record<ClaimPriority, string> = { CRITICAL: 'Critique', HIGH: 'Haute', NORMAL: 'Normale' }
export const STATUS_LABEL: Record<ClaimStatus, string> = {
  OPEN: 'Ouverte', IN_PROGRESS: 'Prise en charge', WAITING_REQUESTER: 'En attente du demandeur', RESOLVED: 'Résolue', CLOSED: 'Fermée',
}
export const STATUS_TONE: Record<ClaimStatus, string> = {
  OPEN: 'critical', IN_PROGRESS: 'running', WAITING_REQUESTER: 'approval', RESOLVED: 'ok', CLOSED: 'muted',
}
export const CAUSES: { value: ClaimCause; label: string }[] = [
  { value: 'CODE', label: 'Code de l’application' }, { value: 'CONFIGURATION', label: 'Configuration' },
  { value: 'RESOURCES', label: 'Ressources' }, { value: 'PLATFORM', label: 'Plateforme' },
  { value: 'DEPENDENCY', label: 'Dépendance' }, { value: 'USAGE', label: 'Erreur d’utilisation' },
]
export function age(iso: string | null) {
  if (!iso) return ''
  const m = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  return m < 60 ? `il y a ${m} min` : m < 1440 ? `il y a ${Math.round(m / 60)} h` : `il y a ${Math.round(m / 1440)} j`
}
