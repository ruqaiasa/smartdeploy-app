import { reactive } from 'vue'
import { getUnreadDiscussions, markDiscussionRead, type UnreadThread } from '@/api/portal.api'

/*
 * Messages non lus des discussions : relus toutes les 15 secondes. Un nouveau message déclenche
 * une notification ; l’icône de messagerie et chaque ligne de déploiement affichent leur compteur.
 */
export const discussions = reactive({
  total: 0,
  threads: [] as UnreadThread[],
  byDeployment: {} as Record<number, number>,
  toast: null as UnreadThread | null,
})

let timer: ReturnType<typeof setInterval> | null = null
let lastSeen = ''

async function poll(first: boolean) {
  try {
    const data = await getUnreadDiscussions()
    discussions.total = data.total
    discussions.threads = data.threads
    discussions.byDeployment = Object.fromEntries(data.threads.map(t => [t.deploymentId, t.unread]))
    const newest = data.threads[0]
    const key = newest ? `${newest.deploymentId}-${newest.lastAt}` : ''
    if (!first && newest && key !== lastSeen) discussions.toast = newest
    lastSeen = key
  } catch { /* hors ligne : rien à signaler */ }
}

export function startDiscussionsWatch() {
  if (timer) return
  void poll(true)
  timer = setInterval(() => void poll(false), 15000)
}

export function stopDiscussionsWatch() {
  if (timer) clearInterval(timer)
  timer = null
  lastSeen = ''
}

/** La discussion d’un déploiement vient d’être lue. */
export async function markRead(deploymentId: number) {
  await markDiscussionRead(deploymentId).catch(() => undefined)
  const count = discussions.byDeployment[deploymentId] ?? 0
  if (count) {
    discussions.total = Math.max(0, discussions.total - count)
    discussions.threads = discussions.threads.filter(t => t.deploymentId !== deploymentId)
    discussions.byDeployment = { ...discussions.byDeployment, [deploymentId]: 0 }
  }
  if (discussions.toast?.deploymentId === deploymentId) discussions.toast = null
}
