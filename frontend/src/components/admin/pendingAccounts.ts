import { reactive } from 'vue'
import { getPendingUsers } from '@/api/users.api'

/*
 * Notification de l’administrateur : toutes les 15 secondes, les comptes en attente sont relus ;
 * un nouveau compte déclenche une notification, et le nombre s’affiche sur « Utilisateurs ».
 */
export const pendingAccounts = reactive({
  count: 0,
  toast: null as { name: string; email: string } | null,
})

const known = new Set<number>()
let started = false
let timer: ReturnType<typeof setInterval> | null = null

async function poll(first: boolean) {
  try {
    const pending = await getPendingUsers()
    pendingAccounts.count = pending.length
    for (const u of pending) {
      if (!known.has(u.id)) {
        known.add(u.id)
        if (!first) pendingAccounts.toast = { name: `${u.firstName} ${u.lastName}`, email: u.email }
      }
    }
  } catch { /* hors ligne ou non administrateur : rien à notifier */ }
}

export function startPendingAccountsWatch() {
  if (started) return
  started = true
  void poll(true)
  timer = setInterval(() => void poll(false), 15000)
}

export function stopPendingAccountsWatch() {
  if (timer) clearInterval(timer)
  timer = null
  started = false
  known.clear()
}
