import type { DeploymentResponse, DeploymentStatus } from '@/types/deployment'

export const ACTIVE_STATUSES: DeploymentStatus[] = ['PENDING', 'RUNNING']
export const FINISHED_STATUSES: DeploymentStatus[] = ['SUCCESS', 'FAILED', 'CANCELLED']

/**
 * Compare l'état connu des déploiements à une nouvelle liste.
 * Renvoie les déploiements qui étaient en attente ou en cours, et qui
 * viennent de se terminer. Met à jour l'état connu.
 * Au premier appel (état vide), rien n'est signalé : on ne notifie pas
 * des déploiements terminés avant l'ouverture de la page.
 */
export function detectFinishedDeployments(
  known: Map<number, DeploymentStatus>,
  deployments: DeploymentResponse[],
  initialized: boolean,
): DeploymentResponse[] {
  const finished: DeploymentResponse[] = []
  for (const deployment of deployments) {
    const previous = known.get(deployment.id)
    if (
      initialized
      && previous !== undefined
      && ACTIVE_STATUSES.includes(previous)
      && FINISHED_STATUSES.includes(deployment.status)
    ) {
      finished.push(deployment)
    }
    known.set(deployment.id, deployment.status)
  }
  return finished
}

export function hasActiveDeployment(deployments: DeploymentResponse[]) {
  return deployments.some(deployment => ACTIVE_STATUSES.includes(deployment.status))
}
