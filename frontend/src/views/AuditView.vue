<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { getAuditLogs, sendTestNotification, verifyAuditIntegrity } from '@/api/audit.api'
import type { AuditAction, AuditIntegrityReport, AuditLogResponse, NotificationTestResult } from '@/types/audit'

/* ---------------------------------------------------------------
 * Libellés, catégories et tonalités des actions
 * --------------------------------------------------------------- */
const ACTION_LABELS: Record<AuditAction, string> = {
  LOGIN_SUCCESS: 'Connexion',
  LOGIN_FAILURE: 'Connexion refusée',
  LOGOUT: 'Déconnexion',
  ACCESS_DENIED: 'Accès refusé',
  API_KEY_REJECTED: 'Clé API refusée',
  SUSPICIOUS_ACTIVITY: 'Activité suspecte',
  RESOURCE_CREATED: 'Création',
  RESOURCE_UPDATED: 'Modification',
  RESOURCE_DELETED: 'Suppression',
  USER_ROLE_CHANGED: 'Rôle modifié',
  USER_STATUS_CHANGED: 'Statut de compte',
  ACTION_FAILED: 'Action échouée',
  ALERT_OPENED: 'Alerte ouverte',
  ALERT_RESOLVED: 'Alerte résolue',
  GUARD_PASSED: 'Validé par le gardien',
  GUARD_DEGRADED: 'Dégradation détectée',
  GUARD_INCONCLUSIVE: 'Garde non concluante',
  GUARD_AUTO_ROLLBACK: 'Retour automatique',
  INCIDENT_ANALYZED: 'Incident analysé',
  DEPLOYMENT_CREATED: 'Déploiement lancé',
  DEPLOYMENT_STARTED: 'Pipeline démarré',
  DEPLOYMENT_SUCCEEDED: 'Déploiement réussi',
  DEPLOYMENT_FAILED: 'Déploiement échoué',
  DEPLOYMENT_CANCELLED: 'Déploiement annulé',
  DEPLOYMENT_ROLLBACK_REQUESTED: 'Rollback demandé',
  DEPLOYMENT_REPLACED: 'Déploiement remplacé',
  DEPLOYMENT_EXPIRED: 'Délai dépassé',
  DEPLOYMENT_DELETED: 'Déploiement supprimé',
}
const ACTION_TONES: Record<AuditAction, 'success' | 'danger' | 'info' | 'neutral' | 'warning'> = {
  LOGIN_SUCCESS: 'success',
  LOGIN_FAILURE: 'danger',
  LOGOUT: 'neutral',
  ACCESS_DENIED: 'danger',
  API_KEY_REJECTED: 'danger',
  SUSPICIOUS_ACTIVITY: 'danger',
  RESOURCE_CREATED: 'info',
  RESOURCE_UPDATED: 'info',
  RESOURCE_DELETED: 'neutral',
  USER_ROLE_CHANGED: 'warning',
  USER_STATUS_CHANGED: 'warning',
  ACTION_FAILED: 'danger',
  ALERT_OPENED: 'warning',
  ALERT_RESOLVED: 'success',
  GUARD_PASSED: 'success',
  GUARD_DEGRADED: 'danger',
  GUARD_INCONCLUSIVE: 'neutral',
  GUARD_AUTO_ROLLBACK: 'warning',
  INCIDENT_ANALYZED: 'warning',
  DEPLOYMENT_CREATED: 'info',
  DEPLOYMENT_STARTED: 'info',
  DEPLOYMENT_SUCCEEDED: 'success',
  DEPLOYMENT_FAILED: 'danger',
  DEPLOYMENT_CANCELLED: 'neutral',
  DEPLOYMENT_ROLLBACK_REQUESTED: 'info',
  DEPLOYMENT_REPLACED: 'neutral',
  DEPLOYMENT_EXPIRED: 'danger',
  DEPLOYMENT_DELETED: 'neutral',
}
const CATEGORIES: { key: string; label: string; actions: AuditAction[] }[] = [
  { key: 'all', label: 'Tout', actions: [] },
  {
    key: 'security', label: 'Sécurité',
    actions: ['LOGIN_SUCCESS', 'LOGIN_FAILURE', 'LOGOUT', 'ACCESS_DENIED', 'API_KEY_REJECTED', 'SUSPICIOUS_ACTIVITY'],
  },
  {
    key: 'admin', label: 'Administration',
    actions: ['RESOURCE_CREATED', 'RESOURCE_UPDATED', 'RESOURCE_DELETED', 'USER_ROLE_CHANGED', 'USER_STATUS_CHANGED'],
  },
  {
    key: 'deployments', label: 'Déploiements',
    actions: ['DEPLOYMENT_CREATED', 'DEPLOYMENT_STARTED', 'DEPLOYMENT_SUCCEEDED', 'DEPLOYMENT_FAILED', 'DEPLOYMENT_DELETED', 'GUARD_PASSED', 'GUARD_DEGRADED', 'GUARD_INCONCLUSIVE', 'GUARD_AUTO_ROLLBACK', 'INCIDENT_ANALYZED'],
  },
  {
    key: 'recovery', label: 'Annulations et rollbacks',
    actions: ['DEPLOYMENT_CANCELLED', 'DEPLOYMENT_ROLLBACK_REQUESTED', 'DEPLOYMENT_REPLACED', 'DEPLOYMENT_EXPIRED'],
  },
  {
    key: 'failures', label: 'Échecs et alertes',
    actions: ['SUSPICIOUS_ACTIVITY', 'GUARD_DEGRADED', 'ALERT_OPENED', 'ALERT_RESOLVED', 'LOGIN_FAILURE', 'ACCESS_DENIED', 'API_KEY_REJECTED', 'ACTION_FAILED', 'DEPLOYMENT_FAILED', 'DEPLOYMENT_EXPIRED'],
  },
]

/* ---------------------------------------------------------------
 * État de la page
 * --------------------------------------------------------------- */
const logs = ref<AuditLogResponse[]>([])
const loading = ref(false)
const errorMessage = ref('')
const page = ref(0)
const size = 20
const totalElements = ref(0)
const totalPages = ref(0)
const category = ref('all')
const searchInput = ref('')
const search = ref('')
const integrity = ref<AuditIntegrityReport | null>(null)
const verifying = ref(false)
const notificationResult = ref<NotificationTestResult | null>(null)
const sendingTest = ref(false)
let requestSequence = 0
let searchTimer: ReturnType<typeof setTimeout> | null = null

const hasFilters = computed(() => category.value !== 'all' || !!search.value)
const rangeLabel = computed(() => {
  if (!totalElements.value) return '0 événement'
  const start = page.value * size + 1
  const end = Math.min(start + logs.value.length - 1, totalElements.value)
  return `${start}–${end} sur ${totalElements.value}`
})

async function load() {
  const sequence = ++requestSequence
  loading.value = true
  errorMessage.value = ''
  try {
    const actions = CATEGORIES.find(c => c.key === category.value)?.actions ?? []
    const result = await getAuditLogs({ page: page.value, size, actions, search: search.value })
    if (sequence !== requestSequence) return
    logs.value = result.content
    totalElements.value = result.totalElements
    totalPages.value = result.totalPages
  } catch (error: unknown) {
    if (sequence !== requestSequence) return
    const err = error as { response?: { status?: number; data?: { message?: string } } }
    errorMessage.value = err.response?.status === 403
      ? 'Le journal d’activité est réservé aux administrateurs.'
      : err.response?.data?.message || 'Impossible de charger le journal d’activité.'
  } finally {
    if (sequence === requestSequence) loading.value = false
  }
}

async function testSlack() {
  sendingTest.value = true
  try {
    notificationResult.value = await sendTestNotification()
  } catch {
    notificationResult.value = { sent: false, message: 'Le serveur n’a pas répondu.' }
  } finally {
    sendingTest.value = false
  }
}
async function checkIntegrity() {
  verifying.value = true
  try {
    integrity.value = await verifyAuditIntegrity()
  } catch {
    integrity.value = {
      valid: false, checkedEntries: 0, unchainedEntries: 0, firstInvalidId: null,
      message: 'Vérification impossible : le serveur n’a pas répondu.', verifiedAt: new Date().toISOString(),
    }
  } finally {
    verifying.value = false
  }
}
function reload() {
  page.value = 0
  void load()
}
function setCategory(key: string) {
  if (category.value === key) return
  category.value = key
  reload()
}
function clearFilters() {
  if (searchTimer) clearTimeout(searchTimer)
  searchInput.value = ''
  search.value = ''
  category.value = 'all'
  reload()
}
function goToPage(target: number) {
  if (target < 0 || target >= totalPages.value || loading.value) return
  page.value = target
  void load()
}

watch(searchInput, value => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    if (value.trim() === search.value) return
    search.value = value.trim()
    reload()
  }, 300)
})

onMounted(load)
onBeforeUnmount(() => { if (searchTimer) clearTimeout(searchTimer) })

/* ---------------------------------------------------------------
 * Mise en forme
 * --------------------------------------------------------------- */
const relativeFormatter = new Intl.RelativeTimeFormat('fr', { numeric: 'auto' })
function formatRelative(value: string) {
  const seconds = Math.round((new Date(value).getTime() - Date.now()) / 1000)
  const abs = Math.abs(seconds)
  if (abs < 60) return 'à l’instant'
  if (abs < 3600) return relativeFormatter.format(Math.round(seconds / 60), 'minute')
  if (abs < 86400) return relativeFormatter.format(Math.round(seconds / 3600), 'hour')
  if (abs < 86400 * 7) return relativeFormatter.format(Math.round(seconds / 86400), 'day')
  return formatDate(value)
}
function formatDate(value: string) {
  return new Date(value).toLocaleString('fr-FR', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}
function target(log: AuditLogResponse) {
  return [log.applicationName, log.environmentName].filter(Boolean).join(' · ')
}
</script>

<template>
  <section class="au">
    <header class="au-header">
      <div>
        <h1>Journal d’activité</h1>
        <p>Qui a fait quoi, quand et d’où : sécurité, administration, déploiements et rollbacks.</p>
      </div>
      <div class="au-header-actions">
        <button type="button" class="au-btn au-btn-ghost" :disabled="sendingTest" @click="testSlack">
          {{ sendingTest ? 'Envoi…' : 'Tester Slack' }}
        </button>
        <button type="button" class="au-btn au-btn-ghost" :disabled="verifying" @click="checkIntegrity">
          {{ verifying ? 'Vérification…' : 'Vérifier l’intégrité' }}
        </button>
        <button type="button" class="au-btn au-btn-ghost" :disabled="loading" @click="load">Actualiser</button>
      </div>
    </header>

    <div v-if="notificationResult" class="au-integrity" :class="notificationResult.sent ? 'is-valid' : 'is-broken'" role="status">
      <strong>{{ notificationResult.sent ? 'Notification envoyée' : 'Notification non envoyée' }}</strong>
      <span>{{ notificationResult.message }}</span>
    </div>

    <div v-if="integrity" class="au-integrity" :class="integrity.valid ? 'is-valid' : 'is-broken'" role="status">
      <strong>{{ integrity.valid ? 'Intégrité vérifiée' : 'Altération détectée' }}</strong>
      <span>{{ integrity.message }}</span>
      <span class="au-muted">Chaque entrée contient l’empreinte SHA-256 de la précédente : toute modification ou suppression directe en base rompt la chaîne.</span>
    </div>

    <label class="au-search">
      <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><circle cx="9" cy="9" r="5.5" fill="none" stroke="currentColor" stroke-width="1.8" /><path d="M13.2 13.2L17 17" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
      <span class="au-sr">Rechercher</span>
      <input v-model="searchInput" type="search" placeholder="Rechercher un utilisateur, une application, un environnement" autocomplete="off" />
    </label>

    <div class="au-filters" role="group" aria-label="Filtrer par catégorie">
      <button v-for="c in CATEGORIES" :key="c.key" type="button" class="au-chip" :class="{ 'is-active': category === c.key }"
        :aria-pressed="category === c.key" @click="setCategory(c.key)">{{ c.label }}</button>
      <button v-if="hasFilters" type="button" class="au-link" @click="clearFilters">Effacer les filtres</button>
    </div>

    <p v-if="errorMessage" class="au-alert" role="alert">{{ errorMessage }}</p>

    <section class="au-card">
      <div v-if="loading && !logs.length" class="au-empty">Chargement du journal…</div>
      <div v-else-if="!logs.length" class="au-empty">
        <strong>{{ hasFilters ? 'Aucun événement ne correspond à ces critères.' : 'Aucun événement enregistré pour l’instant.' }}</strong>
      </div>
      <table v-else class="au-table" :class="{ 'is-refreshing': loading }">
        <thead>
          <tr>
            <th scope="col">Date</th>
            <th scope="col">Action</th>
            <th scope="col">Auteur</th>
            <th scope="col">Cible</th>
            <th scope="col">Détail</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="log in logs" :key="log.id">
            <td data-label="Date"><span :title="formatDate(log.occurredAt)">{{ formatRelative(log.occurredAt) }}</span></td>
            <td data-label="Action"><span class="au-action" :class="`is-${ACTION_TONES[log.action] ?? 'neutral'}`">{{ ACTION_LABELS[log.action] ?? log.action }}</span></td>
            <td data-label="Auteur" class="au-actor">
              {{ log.actor }}
              <small v-if="log.ipAddress" class="au-muted au-block" :title="log.userAgent ?? ''">{{ log.ipAddress }}</small>
            </td>
            <td data-label="Cible"><span v-if="target(log)">{{ target(log) }}</span><span v-else class="au-muted">—</span></td>
            <td data-label="Détail" class="au-summary">
              {{ log.summary }}
              <small v-if="log.details" class="au-muted au-block">{{ log.details }}</small>
            </td>
          </tr>
        </tbody>
      </table>
    </section>

    <footer v-if="totalElements" class="au-pagination">
      <span class="au-muted">{{ rangeLabel }}</span>
      <div class="au-pages">
        <button type="button" class="au-btn au-btn-ghost" :disabled="page === 0 || loading" @click="goToPage(page - 1)">Précédent</button>
        <span class="au-muted">Page {{ page + 1 }} sur {{ Math.max(totalPages, 1) }}</span>
        <button type="button" class="au-btn au-btn-ghost" :disabled="page + 1 >= totalPages || loading" @click="goToPage(page + 1)">Suivant</button>
      </div>
    </footer>
  </section>
</template>

<style scoped>
.au {
  --ink: var(--color-gray-900, #111827);
  --muted: var(--color-gray-500, #6b7280);
  --line: var(--color-gray-200, #e5e7eb);
  --soft: var(--color-gray-50, #f9fafb);
  --accent: var(--color-primary-hover, #2563eb);
  display: flex; flex-direction: column; gap: 16px; color: var(--ink); font-size: 14px;
}
.au-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; margin-bottom: 8px; }
.au-header h1 { margin: 0; font-size: 24px; font-weight: 600; letter-spacing: -0.015em; }
.au-header p { margin: 4px 0 0; color: var(--muted); }
.au-btn { height: 38px; padding: 0 16px; border-radius: 8px; font: inherit; font-size: 14px; font-weight: 500; cursor: pointer; }
.au-btn-ghost { border: 1px solid var(--line); background: #fff; color: var(--ink); }
.au-btn-ghost:hover:not(:disabled) { background: var(--soft); }
.au-btn:disabled { opacity: .5; cursor: not-allowed; }
.au-search { position: relative; display: flex; align-items: center; }
.au-search svg { position: absolute; left: 12px; color: var(--muted); pointer-events: none; }
.au-search input { width: 100%; height: 40px; padding: 0 12px 0 38px; border: 1px solid var(--line); border-radius: 8px; font: inherit; font-size: 14px; color: var(--ink); background: #fff; }
.au-search input:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px rgba(37, 99, 235, .15); }
.au-filters { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.au-chip { height: 32px; padding: 0 12px; border: 1px solid var(--line); border-radius: 999px; background: #fff; color: var(--ink); font: inherit; font-size: 13px; cursor: pointer; }
.au-chip.is-active { border-color: var(--ink); background: var(--ink); color: #fff; }
.au-link { padding: 0; border: 0; background: none; color: var(--accent); font: inherit; font-size: 13px; cursor: pointer; }
.au-alert { margin: 0; padding: 12px 14px; border: 1px solid #fecaca; border-radius: 8px; background: #fef2f2; color: #b91c1c; font-size: 13px; }
.au-card { overflow: hidden; border: 1px solid var(--line); border-radius: 12px; background: #fff; }
.au-table { width: 100%; border-collapse: collapse; transition: opacity .15s ease; }
.au-table.is-refreshing { opacity: .6; }
.au-table th { padding: 12px 16px; border-bottom: 1px solid var(--line); background: var(--soft); color: var(--muted); font-size: 12px; font-weight: 500; text-align: left; white-space: nowrap; }
.au-table td { padding: 12px 16px; border-bottom: 1px solid var(--line); vertical-align: top; }
.au-table tbody tr:last-child td { border-bottom: 0; }
.au-action { display: inline-block; padding: 3px 10px; border-radius: 999px; font-size: 12.5px; font-weight: 500; white-space: nowrap; }
.au-action.is-success { background: #ecfdf5; color: #047857; }
.au-action.is-danger { background: #fef2f2; color: #b91c1c; }
.au-action.is-info { background: var(--color-primary-light, #f0f6ff); color: var(--accent); }
.au-action.is-neutral { background: var(--soft); color: var(--muted); }
.au-action.is-warning { background: #fffbeb; color: #b45309; }
.au-block { display: block; margin-top: 2px; font-size: 12px; }
.au-header-actions { display: flex; gap: 8px; }
.au-integrity { display: flex; flex-direction: column; gap: 4px; padding: 12px 14px; border: 1px solid; border-radius: 10px; font-size: 13px; }
.au-integrity.is-valid { border-color: #a7f3d0; background: #ecfdf5; color: #065f46; }
.au-integrity.is-broken { border-color: #fecaca; background: #fef2f2; color: #991b1b; }
.au-actor { white-space: nowrap; }
.au-actor .au-block { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
.au-table td[data-label="Date"] { white-space: nowrap; color: var(--muted); }
.au-summary { color: var(--ink); line-height: 1.5; }
.au-muted { color: var(--muted); }
.au-empty { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 56px 24px; color: var(--muted); text-align: center; }
.au-empty strong { color: var(--ink); font-weight: 600; }
.au-pagination { display: flex; align-items: center; justify-content: space-between; gap: 12px; font-size: 13px; }
.au-pages { display: flex; align-items: center; gap: 12px; }
.au-sr { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
.au-btn:focus-visible, .au-chip:focus-visible, .au-link:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
@media (max-width: 900px) {
  .au-header { flex-direction: column; align-items: stretch; }
  .au-table thead { display: none; }
  .au-table, .au-table tbody, .au-table tr, .au-table td { display: block; width: 100%; }
  .au-table tbody tr { padding: 14px 16px; border-top: 1px solid var(--line); }
  .au-table tbody tr:first-child { border-top: 0; }
  .au-table td { padding: 3px 0; border: 0; }
  .au-table td[data-label]::before { content: attr(data-label); display: block; color: var(--muted); font-size: 12px; }
  .au-pagination { flex-direction: column; align-items: stretch; }
  .au-pages { justify-content: space-between; }
}
</style>
