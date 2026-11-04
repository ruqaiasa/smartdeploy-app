<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getDeployments } from '@/api/deployments.api'
import { detectFinishedDeployments, hasActiveDeployment } from '@/composables/deploymentWatch'
import type { DeploymentResponse, DeploymentStatus } from '@/types/deployment'

/*
 * Notifications de fin de déploiement, visibles sur toutes les pages.
 * Interrogation légère : toutes les 5 s quand un déploiement est en cours,
 * toutes les 20 s sinon. Option : notification du système quand l'onglet
 * est en arrière-plan, après autorisation explicite de l'utilisateur.
 */
interface Toast {
  key: number
  deployment: DeploymentResponse
}

const ACTIVE_INTERVAL_MS = 5_000
const IDLE_INTERVAL_MS = 20_000
const MAX_TOASTS = 3

const router = useRouter()
const toasts = ref<Toast[]>([])
const known = new Map<number, DeploymentStatus>()
const browserPermission = ref<NotificationPermission | 'unsupported'>(
  typeof Notification === 'undefined' ? 'unsupported' : Notification.permission,
)
let initialized = false
let stopped = false
let timer: ReturnType<typeof setTimeout> | null = null
let toastKey = 0

function title(deployment: DeploymentResponse) {
  const rollback = deployment.rollbackOfId ? 'Rollback' : 'Déploiement'
  switch (deployment.status) {
    case 'SUCCESS': return `${rollback} réussi`
    case 'FAILED': return `${rollback} échoué`
    default: return `${rollback} annulé`
  }
}
function description(deployment: DeploymentResponse) {
  return `${deployment.applicationName} ${deployment.version} sur ${deployment.environmentName}`
}
function tone(deployment: DeploymentResponse) {
  return deployment.status === 'SUCCESS' ? 'success' : deployment.status === 'FAILED' ? 'danger' : 'neutral'
}

function show(deployment: DeploymentResponse) {
  const key = ++toastKey
  toasts.value = [{ key, deployment }, ...toasts.value].slice(0, MAX_TOASTS)
  // Un échec reste affiché plus longtemps : il demande une action.
  const duration = deployment.status === 'FAILED' ? 20_000 : 8_000
  setTimeout(() => dismiss(key), duration)

  if (browserPermission.value === 'granted' && document.visibilityState === 'hidden') {
    const body = description(deployment)
      + (deployment.status === 'FAILED' && deployment.errorMessage ? `\n${deployment.errorMessage.slice(0, 140)}` : '')
    const notification = new Notification(`SmartDeploy · ${title(deployment)}`, { body, tag: `deployment-${deployment.id}` })
    notification.onclick = () => {
      window.focus()
      void router.push('/deployments')
      notification.close()
    }
  }
}

function dismiss(key: number) {
  toasts.value = toasts.value.filter(toast => toast.key !== key)
}

function open(key: number) {
  dismiss(key)
  void router.push('/deployments')
}

async function enableBrowserNotifications() {
  if (browserPermission.value === 'unsupported') return
  browserPermission.value = await Notification.requestPermission()
}

async function poll() {
  if (stopped) return
  let nextDelay = IDLE_INTERVAL_MS
  try {
    const page = await getDeployments({ page: 0, size: 20 })
    detectFinishedDeployments(known, page.content, initialized).forEach(show)
    initialized = true
    if (hasActiveDeployment(page.content)) nextDelay = ACTIVE_INTERVAL_MS
  } catch (error: unknown) {
    const status = (error as { response?: { status?: number } }).response?.status
    // Session expirée ou droits insuffisants : on arrête silencieusement.
    if (status === 401 || status === 403) {
      stopped = true
      return
    }
  }
  if (!stopped) timer = setTimeout(poll, nextDelay)
}

onMounted(poll)
onBeforeUnmount(() => {
  stopped = true
  if (timer) clearTimeout(timer)
})
</script>

<template>
  <div class="dt-stack" aria-live="polite">
    <TransitionGroup name="dt">
      <article v-for="toast in toasts" :key="toast.key" class="dt-toast" :class="`is-${tone(toast.deployment)}`" role="status">
        <span class="dt-icon" aria-hidden="true">
          <svg v-if="toast.deployment.status === 'SUCCESS'" viewBox="0 0 20 20" width="18" height="18"><path d="M5 10.5l3 3 7-7" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
          <svg v-else-if="toast.deployment.status === 'FAILED'" viewBox="0 0 20 20" width="18" height="18"><path d="M6 6l8 8M14 6l-8 8" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" /></svg>
          <svg v-else viewBox="0 0 20 20" width="18" height="18"><rect x="6" y="6" width="8" height="8" rx="1.5" fill="currentColor" /></svg>
        </span>
        <div class="dt-body">
          <strong>{{ title(toast.deployment) }}</strong>
          <span>{{ description(toast.deployment) }}</span>
          <small v-if="toast.deployment.status === 'FAILED' && toast.deployment.errorMessage" class="dt-error">{{ toast.deployment.errorMessage }}</small>
          <div class="dt-actions">
            <button type="button" class="dt-link" @click="open(toast.key)">Voir les déploiements</button>
            <button v-if="browserPermission === 'default'" type="button" class="dt-link dt-muted" @click="enableBrowserNotifications">
              Activer les notifications du navigateur
            </button>
          </div>
        </div>
        <button type="button" class="dt-close" aria-label="Fermer la notification" @click="dismiss(toast.key)">
          <svg viewBox="0 0 20 20" width="14" height="14" aria-hidden="true"><path d="M5 5l10 10M15 5L5 15" stroke="currentColor" stroke-width="2" stroke-linecap="round" /></svg>
        </button>
      </article>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.dt-stack {
  position: fixed; right: 24px; bottom: 24px; z-index: 1000;
  display: flex; flex-direction: column-reverse; gap: 10px;
  width: min(380px, calc(100vw - 40px)); pointer-events: none;
}
.dt-toast {
  display: flex; gap: 12px; padding: 14px 14px 14px 16px; pointer-events: auto;
  border: 1px solid var(--color-gray-200, #e5e7eb); border-left-width: 4px; border-radius: 10px;
  background: #fff; box-shadow: 0 12px 32px rgba(15, 23, 42, .14); color: var(--color-gray-900, #111827); font-size: 13.5px;
}
.dt-toast.is-success { border-left-color: #059669; }
.dt-toast.is-danger { border-left-color: #dc2626; }
.dt-toast.is-neutral { border-left-color: #9ca3af; }
.dt-icon { display: grid; place-items: center; flex-shrink: 0; width: 28px; height: 28px; border-radius: 50%; }
.is-success .dt-icon { background: #ecfdf5; color: #059669; }
.is-danger .dt-icon { background: #fef2f2; color: #dc2626; }
.is-neutral .dt-icon { background: #f3f4f6; color: #6b7280; }
.dt-body { display: flex; flex: 1; flex-direction: column; gap: 3px; min-width: 0; }
.dt-body strong { font-size: 14px; font-weight: 600; }
.dt-error {
  display: -webkit-box; overflow: hidden; color: #b91c1c; font-size: 12.5px; line-height: 1.45;
  -webkit-box-orient: vertical; -webkit-line-clamp: 3;
}
.dt-actions { display: flex; flex-wrap: wrap; gap: 4px 14px; margin-top: 4px; }
.dt-link { padding: 0; border: 0; background: none; color: var(--color-primary-hover, #2563eb); font: inherit; font-size: 12.5px; font-weight: 500; cursor: pointer; }
.dt-link:hover { text-decoration: underline; }
.dt-muted { color: var(--color-gray-500, #6b7280); font-weight: 400; }
.dt-close { display: grid; place-items: center; flex-shrink: 0; width: 26px; height: 26px; border: 0; border-radius: 6px; background: none; color: #9ca3af; cursor: pointer; }
.dt-close:hover { background: #f3f4f6; color: #374151; }
.dt-link:focus-visible, .dt-close:focus-visible { outline: 2px solid var(--color-primary-hover, #2563eb); outline-offset: 2px; }
.dt-enter-active, .dt-leave-active { transition: opacity .2s ease, transform .2s ease; }
.dt-enter-from, .dt-leave-to { opacity: 0; transform: translateY(8px); }
@media (prefers-reduced-motion: reduce) { .dt-enter-active, .dt-leave-active { transition: none; } }
@media (max-width: 640px) { .dt-stack { right: 12px; bottom: 12px; width: calc(100vw - 24px); } }
</style>
