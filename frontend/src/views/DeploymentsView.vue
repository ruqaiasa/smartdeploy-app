<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import DeploymentFormModal from '@/components/deployments/DeploymentFormModal.vue'
import { useApplicationsStore } from '@/stores/applications'
import { useDeploymentsStore } from '@/stores/deployments'
import { useEnvironmentsStore } from '@/stores/environments'
import { useMonitoringStore } from '@/stores/monitoring'
import { useAuthStore } from '@/stores/auth'
import type { CreateDeploymentRequest, DeploymentResponse, DeploymentStatus } from '@/types/deployment'

const deploymentsStore = useDeploymentsStore()
const applicationsStore = useApplicationsStore()
const environmentsStore = useEnvironmentsStore()
const monitoringStore = useMonitoringStore()
const authStore = useAuthStore()

const canManageDeployments = computed(() => authStore.hasRole('DEVOPS', 'ADMIN'))
const canDeleteDeployments = computed(() => authStore.hasRole('ADMIN'))

/*
 * Rollback : redéploie les images exactes (par empreinte) d'un déploiement
 * réussi, sans reconstruction. Proposé uniquement quand l'empreinte est connue.
 */
const ROLLBACK_AVAILABLE = true
function canRollback(deployment: DeploymentResponse) {
  // Jamais vers le déploiement déjà en service ; possible vers un déploiement
  // réussi plus ancien ou vers un déploiement remplacé (retour « en avant »).
  return !deployment.current
    && (deployment.status === 'SUCCESS' || deployment.status === 'ROLLED_BACK')
    && !!deployment.deployedBackendImage?.includes('@sha256:')
}

const modalOpen = ref(false)
const monitoringOpen = ref(false)
const monitoredDeployment = ref<DeploymentResponse | null>(null)

/* ---------------------------------------------------------------
 * Statuts : libellés compréhensibles et filtres rapides
 * --------------------------------------------------------------- */
const STATUS_LABELS: Record<DeploymentStatus, string> = {
  PENDING: 'En attente',
  RUNNING: 'En cours',
  SUCCESS: 'Réussi',
  FAILED: 'Échoué',
  CANCELLED: 'Annulé',
  ROLLED_BACK: 'Remplacé',
}
const statusFilters: { value: DeploymentStatus | null; label: string }[] = [
  { value: null, label: 'Tous' },
  { value: 'RUNNING', label: 'En cours' },
  { value: 'PENDING', label: 'En attente' },
  { value: 'SUCCESS', label: 'Réussis' },
  { value: 'FAILED', label: 'Échoués' },
  { value: 'CANCELLED', label: 'Annulés' },
]
const ACTIVE_STATUSES: DeploymentStatus[] = ['PENDING', 'RUNNING']

/* ---------------------------------------------------------------
 * Recherche en direct (anti-rebond de 300 ms)
 * --------------------------------------------------------------- */
const searchInput = ref(deploymentsStore.search)
let searchTimer: ReturnType<typeof setTimeout> | null = null

watch(searchInput, value => {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    const term = value.trim()
    if (term === deploymentsStore.search) return
    deploymentsStore.search = term
    void reload()
  }, 300)
})

const hasFilters = computed(() =>
  !!deploymentsStore.search || !!deploymentsStore.status || !!deploymentsStore.applicationId,
)

async function reload() {
  deploymentsStore.page = 0
  await deploymentsStore.loadDeployments()
}
async function setStatusFilter(status: DeploymentStatus | null) {
  if (deploymentsStore.status === status) return
  deploymentsStore.status = status
  await reload()
}
async function clearFilters() {
  if (searchTimer) clearTimeout(searchTimer)
  searchInput.value = ''
  deploymentsStore.search = ''
  deploymentsStore.status = null
  deploymentsStore.applicationId = null
  await reload()
}

/* ---------------------------------------------------------------
 * Actualisation automatique tant qu'un déploiement est en cours
 * --------------------------------------------------------------- */
const POLL_INTERVAL_MS = 5000
let pollTimer: ReturnType<typeof setInterval> | null = null
const hasActiveDeployments = computed(() =>
  deploymentsStore.deployments.some(d => ACTIVE_STATUSES.includes(d.status)),
)

function stopPolling() {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}
function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(() => {
    if (document.visibilityState !== 'visible' || deploymentsStore.loading) return
    void deploymentsStore.loadDeployments({}, { silent: true })
  }, POLL_INTERVAL_MS)
}
watch(hasActiveDeployments, active => (active ? startPolling() : stopPolling()), { immediate: true })

onMounted(async () => {
  await Promise.all([
    deploymentsStore.loadDeployments(),
    applicationsStore.loadApplications({ page: 0, size: 100, active: true }),
    environmentsStore.loadEnvironments({ page: 0, size: 100, active: true }),
  ])
})
onBeforeUnmount(() => {
  stopPolling()
  if (searchTimer) clearTimeout(searchTimer)
})

/* ---------------------------------------------------------------
 * Actions
 * --------------------------------------------------------------- */
function openCreateModal() { modalOpen.value = true }
function closeModal() { if (!deploymentsStore.actionLoading) modalOpen.value = false }

async function submitDeployment(request: CreateDeploymentRequest) {
  try {
    await deploymentsStore.create(request)
    closeModal()
  } catch {
    // Le message d'erreur est affiché par le store.
  }
}
async function cancelDeployment(deployment: DeploymentResponse) {
  const where = deployment.status === 'RUNNING'
    ? 'Le build Jenkins en cours sera arrêté. Si la mise à jour Kubernetes a déjà commencé, les ressources déjà appliquées restent en place.'
    : 'Le build sera retiré de la file d’attente Jenkins.'
  if (!window.confirm(`Annuler le déploiement de ${deployment.applicationName} ${deployment.version} sur ${deployment.environmentName} ?\n\n${where}`)) return
  try { await deploymentsStore.cancel(deployment.id) } catch { /* store */ }
}
async function rollback(deployment: DeploymentResponse) {
  const message = `Redéployer exactement ce déploiement (#${deployment.id}, version ${deployment.version}) sur ${deployment.environmentName} ?\n\n`
    + 'Les images déjà construites sont réutilisées, sans reconstruction. Le déploiement actuel sera marqué « Remplacé » une fois le retour réussi.'
  if (!window.confirm(message)) return
  try { await deploymentsStore.rollback(deployment.id) } catch { /* store */ }
}
async function removeDeployment(deployment: DeploymentResponse) {
  if (!window.confirm(`Supprimer le déploiement #${deployment.id} de l’historique ?`)) return
  try { await deploymentsStore.remove(deployment.id) } catch { /* store */ }
}
async function openMonitoring(deployment: DeploymentResponse) {
  monitoredDeployment.value = deployment
  monitoringOpen.value = true
  await monitoringStore.loadDeploymentMetrics(deployment.id)
}
function closeMonitoring() {
  monitoringOpen.value = false
  monitoredDeployment.value = null
  monitoringStore.clear()
}

async function goToPage(page: number) {
  if (page < 0 || page >= deploymentsStore.totalPages || deploymentsStore.loading) return
  deploymentsStore.page = page
  await deploymentsStore.loadDeployments()
}
const rangeLabel = computed(() => {
  const total = deploymentsStore.totalElements
  if (!total) return '0 déploiement'
  const start = deploymentsStore.page * deploymentsStore.size + 1
  const end = Math.min(start + deploymentsStore.deployments.length - 1, total)
  return `${start}–${end} sur ${total}`
})

/* ---------------------------------------------------------------
 * Mise en forme
 * --------------------------------------------------------------- */
const relativeFormatter = new Intl.RelativeTimeFormat('fr', { numeric: 'auto' })
function formatRelative(value: string | null) {
  if (!value) return '—'
  const seconds = Math.round((new Date(value).getTime() - Date.now()) / 1000)
  const abs = Math.abs(seconds)
  if (abs < 60) return 'à l’instant'
  if (abs < 3600) return relativeFormatter.format(Math.round(seconds / 60), 'minute')
  if (abs < 86400) return relativeFormatter.format(Math.round(seconds / 3600), 'hour')
  if (abs < 86400 * 7) return relativeFormatter.format(Math.round(seconds / 86400), 'day')
  return formatDate(value)
}
function formatDate(value: string | null) {
  if (!value) return '—'
  return new Date(value).toLocaleString('fr-FR', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}
function formatDuration(seconds: number | null) {
  if (seconds === null || seconds === undefined) return '—'
  if (seconds < 60) return `${seconds} s`
  return `${Math.floor(seconds / 60)} min ${String(seconds % 60).padStart(2, '0')} s`
}
function formatMetric(value: number | null | undefined, unit = '') {
  if (value === null || value === undefined || Number.isNaN(value)) return '—'
  return `${value.toFixed(1)}${unit}`
}
const HEALTH_LABELS: Record<string, string> = { HEALTHY: 'Sain', WARNING: 'À surveiller', CRITICAL: 'Critique' }
/** « …@sha256:3f9a0c7e1b2d… » → « sha256:3f9a0c7 » ; un tag reste lisible tel quel. */
function shortDigest(image: string) {
  const at = image.indexOf('@sha256:')
  return at >= 0 ? `sha256:${image.slice(at + 8, at + 15)}` : image.slice(image.lastIndexOf('/') + 1)
}
function imageTooltip(deployment: DeploymentResponse) {
  return [deployment.deployedBackendImage, deployment.deployedFrontendImage]
    .filter(Boolean)
    .join('\n')
}
function hasReason(deployment: DeploymentResponse) {
  return !!deployment.errorMessage && (deployment.status === 'FAILED' || deployment.status === 'CANCELLED')
}
function requesterName(email: string) { return email?.split('@')[0] || email }
</script>

<template>
  <section class="dp">
    <header class="dp-header">
      <div>
        <h1>Déploiements</h1>
        <p>Suivez le déploiement de vos versions sur chaque environnement.</p>
      </div>
      <button v-if="canManageDeployments" type="button" class="dp-btn dp-btn-primary"
        :disabled="deploymentsStore.actionLoading" @click="openCreateModal">
        <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M10 4v12M4 10h12" stroke="currentColor" stroke-width="2" stroke-linecap="round" /></svg>
        Nouveau déploiement
      </button>
    </header>

    <div class="dp-toolbar">
      <label class="dp-search">
        <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><circle cx="9" cy="9" r="5.5" fill="none" stroke="currentColor" stroke-width="1.8" /><path d="M13.2 13.2L17 17" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
        <span class="dp-sr">Rechercher</span>
        <input v-model="searchInput" type="search" placeholder="Rechercher une application, une version, un environnement" autocomplete="off" />
        <span v-if="deploymentsStore.loading && searchInput" class="dp-spinner" aria-hidden="true" />
      </label>
      <select v-model="deploymentsStore.applicationId" class="dp-select" aria-label="Filtrer par application" @change="reload">
        <option :value="null">Toutes les applications</option>
        <option v-for="application in applicationsStore.applications" :key="application.id" :value="application.id">{{ application.name }}</option>
      </select>
    </div>

    <div class="dp-filters" role="group" aria-label="Filtrer par statut">
      <button v-for="filter in statusFilters" :key="filter.label" type="button" class="dp-chip"
        :class="{ 'is-active': deploymentsStore.status === filter.value }"
        :aria-pressed="deploymentsStore.status === filter.value" @click="setStatusFilter(filter.value)">
        {{ filter.label }}
      </button>
      <button v-if="hasFilters" type="button" class="dp-link" @click="clearFilters">Effacer les filtres</button>
      <span v-if="hasActiveDeployments" class="dp-live" title="La liste s’actualise toutes les 5 secondes">
        <span class="dp-live-dot" aria-hidden="true" /> Actualisation automatique
      </span>
    </div>

    <p v-if="deploymentsStore.errorMessage" class="dp-alert" role="alert">{{ deploymentsStore.errorMessage }}</p>

    <section class="dp-card">
      <div v-if="deploymentsStore.loading && !deploymentsStore.deployments.length" class="dp-empty">
        <span class="dp-spinner" aria-hidden="true" /> Chargement des déploiements…
      </div>

      <div v-else-if="!deploymentsStore.deployments.length" class="dp-empty">
        <template v-if="hasFilters">
          <strong>Aucun déploiement ne correspond à ces critères.</strong>
          <button type="button" class="dp-btn dp-btn-ghost" @click="clearFilters">Effacer les filtres</button>
        </template>
        <template v-else>
          <strong>Aucun déploiement pour l’instant.</strong>
          <span>Lancez le premier déploiement d’une de vos applications.</span>
          <button v-if="canManageDeployments" type="button" class="dp-btn dp-btn-primary" @click="openCreateModal">Nouveau déploiement</button>
        </template>
      </div>

      <table v-else class="dp-table" :class="{ 'is-refreshing': deploymentsStore.loading }">
        <thead>
          <tr>
            <th scope="col">Application</th>
            <th scope="col">Environnement</th>
            <th scope="col">Statut</th>
            <th scope="col">Lancé</th>
            <th scope="col">Durée</th>
            <th scope="col">Build</th>
            <th scope="col"><span class="dp-sr">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <template v-for="deployment in deploymentsStore.deployments" :key="deployment.id">
            <tr :class="{ 'has-error': hasReason(deployment) }">
              <td data-label="Application">
                <div class="dp-app">
                  <strong>{{ deployment.applicationName }}</strong>
                  <span class="dp-version">{{ deployment.version }}</span>
                </div>
                <small v-if="deployment.rollbackOfId" class="dp-muted">Retour au déploiement #{{ deployment.rollbackOfId }}</small>
              </td>
              <td data-label="Environnement">
                <span>{{ deployment.environmentName }}</span>
                <small class="dp-muted dp-block">{{ deployment.environmentType }}</small>
              </td>
              <td data-label="Statut">
                <span class="dp-status" :class="`is-${deployment.status.toLowerCase()}`">
                  <span class="dp-status-dot" aria-hidden="true" />{{ STATUS_LABELS[deployment.status] ?? deployment.status }}
                </span>
                <span v-if="deployment.current" class="dp-live-badge" title="Déploiement actuellement en service sur cet environnement">En service</span>
              </td>
              <td data-label="Lancé">
                <span :title="formatDate(deployment.createdAt)">{{ formatRelative(deployment.createdAt) }}</span>
                <small class="dp-muted dp-block" :title="deployment.requestedByEmail">par {{ requesterName(deployment.requestedByEmail) }}</small>
              </td>
              <td data-label="Durée">{{ formatDuration(deployment.durationSeconds) }}</td>
              <td data-label="Build">
                <a v-if="deployment.deploymentLogsUrl" :href="deployment.deploymentLogsUrl" target="_blank" rel="noopener noreferrer" class="dp-build" title="Ouvrir les logs Jenkins">
                  #{{ deployment.jenkinsBuildNumber ?? '?' }}
                  <svg viewBox="0 0 20 20" width="12" height="12" aria-hidden="true"><path d="M8 4h8v8M16 4l-9 9" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
                </a>
                <span v-else class="dp-muted">—</span>
                <small v-if="deployment.deployedBackendImage" class="dp-muted dp-block dp-digest"
                  :title="imageTooltip(deployment)">{{ shortDigest(deployment.deployedBackendImage) }}</small>
              </td>
              <td class="dp-actions-cell">
                <div class="dp-actions">
                  <button v-if="deployment.status === 'SUCCESS'" type="button" class="dp-action" @click="openMonitoring(deployment)">Monitoring</button>
                  <button v-if="canManageDeployments && ACTIVE_STATUSES.includes(deployment.status)" type="button" class="dp-action dp-action-danger"
                    :disabled="deploymentsStore.actionLoading" @click="cancelDeployment(deployment)">Annuler</button>
                  <button v-if="ROLLBACK_AVAILABLE && canManageDeployments && canRollback(deployment)" type="button" class="dp-action"
                    :disabled="deploymentsStore.actionLoading" title="Redéployer exactement ces images" @click="rollback(deployment)">Revenir ici</button>
                  <button v-if="canDeleteDeployments" type="button" class="dp-action dp-action-danger"
                    :disabled="deploymentsStore.actionLoading || ACTIVE_STATUSES.includes(deployment.status)"
                    :title="ACTIVE_STATUSES.includes(deployment.status) ? 'Impossible de supprimer un déploiement en cours' : 'Supprimer de l’historique'"
                    @click="removeDeployment(deployment)">Supprimer</button>
                </div>
              </td>
            </tr>
            <tr v-if="hasReason(deployment)" class="dp-error-row">
              <td colspan="7"><span class="dp-error-text" :class="{ 'is-neutral': deployment.status === 'CANCELLED' }">{{ deployment.errorMessage }}</span></td>
            </tr>
          </template>
        </tbody>
      </table>
    </section>

    <footer v-if="deploymentsStore.totalElements" class="dp-pagination">
      <span class="dp-muted">{{ rangeLabel }}</span>
      <div class="dp-pages">
        <button type="button" class="dp-btn dp-btn-ghost" :disabled="deploymentsStore.page === 0 || deploymentsStore.loading" @click="goToPage(deploymentsStore.page - 1)">Précédent</button>
        <span class="dp-muted">Page {{ deploymentsStore.page + 1 }} sur {{ Math.max(deploymentsStore.totalPages, 1) }}</span>
        <button type="button" class="dp-btn dp-btn-ghost" :disabled="deploymentsStore.page + 1 >= deploymentsStore.totalPages || deploymentsStore.loading" @click="goToPage(deploymentsStore.page + 1)">Suivant</button>
      </div>
    </footer>

    <DeploymentFormModal :open="modalOpen" :applications="applicationsStore.applications"
      :environments="environmentsStore.environments" :loading="deploymentsStore.actionLoading"
      @close="closeModal" @submit="submitDeployment" />

    <!-- Monitoring d'un déploiement -->
    <Teleport to="body">
      <div v-if="monitoringOpen" class="mon-overlay" @click.self="closeMonitoring">
        <section class="mon" role="dialog" aria-modal="true" aria-labelledby="mon-title">
          <header class="mon-header">
            <div>
              <h2 id="mon-title">Monitoring</h2>
              <p v-if="monitoredDeployment">{{ monitoredDeployment.applicationName }}, version {{ monitoredDeployment.version }} sur {{ monitoredDeployment.environmentName }}</p>
            </div>
            <button type="button" class="mon-close" aria-label="Fermer" @click="closeMonitoring">
              <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M5 5l10 10M15 5L5 15" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
            </button>
          </header>

          <div v-if="monitoringStore.loading && !monitoringStore.metrics" class="mon-state"><span class="dp-spinner" aria-hidden="true" /> Interrogation de Prometheus…</div>
          <p v-else-if="monitoringStore.errorMessage" class="dp-alert mon-alert" role="alert">{{ monitoringStore.errorMessage }}</p>
          <template v-else-if="monitoringStore.metrics">
            <div class="mon-health" :class="`is-${monitoringStore.metrics.healthStatus.toLowerCase()}`">
              <span class="dp-status-dot" aria-hidden="true" />
              <strong>{{ HEALTH_LABELS[monitoringStore.metrics.healthStatus] ?? monitoringStore.metrics.healthStatus }}</strong>
              <span class="dp-muted">état opérationnel</span>
            </div>
            <dl class="mon-metrics">
              <div><dt>Pods disponibles</dt><dd>{{ monitoringStore.metrics.runningPods }}<span class="dp-muted"> / {{ monitoringStore.metrics.desiredPods }}</span></dd></div>
              <div><dt>Processeur</dt><dd>{{ formatMetric(monitoringStore.metrics.cpuUsagePercent, ' %') }}</dd></div>
              <div><dt>Mémoire</dt><dd>{{ formatMetric(monitoringStore.metrics.memoryUsageMb, ' Mo') }}</dd></div>
              <div><dt>Redémarrages (15 dernières min)</dt><dd>{{ monitoringStore.metrics.restartCount }}</dd></div>
            </dl>
          </template>

          <footer class="mon-footer">
            <span class="dp-muted">Données issues de Prometheus</span>
            <button type="button" class="dp-btn dp-btn-ghost" :disabled="monitoringStore.loading" @click="monitoringStore.refresh">
              {{ monitoringStore.loading ? 'Actualisation…' : 'Actualiser' }}
            </button>
          </footer>
        </section>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.dp, .mon-overlay {
  --ink: var(--color-gray-900, #111827);
  --muted: var(--color-gray-500, #6b7280);
  --line: var(--color-gray-200, #e5e7eb);
  --soft: var(--color-gray-50, #f9fafb);
  --accent: var(--color-primary-hover, #2563eb);
  --accent-soft: var(--color-primary-light, #f0f6ff);
}
.dp { display: flex; flex-direction: column; gap: 16px; color: var(--ink); font-size: 14px; }

/* En-tête */
.dp-header { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; margin-bottom: 8px; }
.dp-header h1 { margin: 0; font-size: 24px; font-weight: 600; letter-spacing: -0.015em; }
.dp-header p { margin: 4px 0 0; color: var(--muted); }

/* Boutons */
.dp-btn { display: inline-flex; align-items: center; justify-content: center; gap: 6px; height: 38px; padding: 0 16px; border-radius: 8px; font: inherit; font-size: 14px; font-weight: 500; white-space: nowrap; cursor: pointer; transition: background-color .15s ease, border-color .15s ease; }
.dp-btn-primary { border: 1px solid var(--accent); background: var(--accent); color: #fff; }
.dp-btn-primary:hover:not(:disabled) { background: #1d4ed8; border-color: #1d4ed8; }
.dp-btn-ghost { border: 1px solid var(--line); background: #fff; color: var(--ink); }
.dp-btn-ghost:hover:not(:disabled) { background: var(--soft); }
.dp-btn:disabled { opacity: .5; cursor: not-allowed; }
.dp-link { padding: 0; border: 0; background: none; color: var(--accent); font: inherit; font-size: 13px; cursor: pointer; }
.dp-link:hover { text-decoration: underline; }

/* Barre d'outils */
.dp-toolbar { display: grid; grid-template-columns: minmax(0, 1fr) 240px; gap: 12px; }
.dp-search { position: relative; display: flex; align-items: center; }
.dp-search > svg { position: absolute; left: 12px; color: var(--muted); pointer-events: none; }
.dp-search input { width: 100%; height: 40px; padding: 0 38px 0 38px; border: 1px solid var(--line); border-radius: 8px; background: #fff; color: var(--ink); font: inherit; font-size: 14px; }
.dp-search .dp-spinner { position: absolute; right: 12px; }
.dp-select { height: 40px; padding: 0 12px; border: 1px solid var(--line); border-radius: 8px; background: #fff; color: var(--ink); font: inherit; font-size: 14px; }
.dp-search input:focus, .dp-select:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px rgba(37, 99, 235, .15); }

/* Filtres de statut */
.dp-filters { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.dp-chip { height: 32px; padding: 0 12px; border: 1px solid var(--line); border-radius: 999px; background: #fff; color: var(--ink); font: inherit; font-size: 13px; cursor: pointer; transition: background-color .15s ease, border-color .15s ease; }
.dp-chip:hover { border-color: var(--color-gray-300, #d1d5db); }
.dp-chip.is-active { border-color: var(--ink); background: var(--ink); color: #fff; }
.dp-live { display: inline-flex; align-items: center; gap: 6px; margin-left: auto; color: var(--muted); font-size: 12px; }
.dp-live-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--accent); animation: dp-pulse 1.6s ease-in-out infinite; }

.dp-alert { margin: 0; padding: 12px 14px; border: 1px solid #fecaca; border-radius: 8px; background: #fef2f2; color: #b91c1c; font-size: 13px; }

/* Liste */
.dp-card { overflow: hidden; border: 1px solid var(--line); border-radius: 12px; background: #fff; }
.dp-table { width: 100%; border-collapse: collapse; transition: opacity .15s ease; }
.dp-table.is-refreshing { opacity: .6; }
.dp-table th { padding: 12px 16px; border-bottom: 1px solid var(--line); background: var(--soft); color: var(--muted); font-size: 12px; font-weight: 500; text-align: left; white-space: nowrap; }
.dp-table td { padding: 14px 16px; border-bottom: 1px solid var(--line); vertical-align: middle; }
.dp-table tbody tr:last-child td { border-bottom: 0; }
.dp-table tbody tr:not(.dp-error-row):hover td { background: #fcfcfd; }
.dp-table tr.has-error td { border-bottom: 0; }

.dp-app { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.dp-app strong { font-weight: 600; }
.dp-version { padding: 2px 8px; border-radius: 6px; background: var(--soft); border: 1px solid var(--line); font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; }
.dp-muted { color: var(--muted); }
.dp-block { display: block; margin-top: 2px; font-size: 12px; }
small.dp-muted { font-size: 12px; }

.dp-status { display: inline-flex; align-items: center; gap: 7px; padding: 3px 10px 3px 8px; border-radius: 999px; font-size: 13px; font-weight: 500; white-space: nowrap; }
.dp-status-dot { width: 7px; height: 7px; border-radius: 50%; background: currentColor; flex-shrink: 0; }
.dp-status.is-success { background: #ecfdf5; color: #047857; }
.dp-status.is-failed { background: #fef2f2; color: #b91c1c; }
.dp-status.is-running { background: var(--accent-soft); color: var(--accent); }
.dp-status.is-running .dp-status-dot { animation: dp-pulse 1.6s ease-in-out infinite; }
.dp-status.is-pending { background: #fffbeb; color: #b45309; }
.dp-status.is-cancelled, .dp-status.is-rolled_back { background: var(--soft); color: var(--muted); }

.dp-live-badge { display: inline-block; margin-left: 6px; padding: 2px 8px; border: 1px solid #a7f3d0; border-radius: 999px; color: #047857; font-size: 11px; font-weight: 600; vertical-align: middle; }
.dp-build { display: inline-flex; align-items: center; gap: 4px; color: var(--accent); font-variant-numeric: tabular-nums; text-decoration: none; }
.dp-build:hover { text-decoration: underline; }
.dp-digest { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 11px; cursor: help; }

.dp-actions-cell { width: 1%; }
.dp-actions { display: flex; justify-content: flex-end; gap: 4px; }
.dp-action { height: 30px; padding: 0 10px; border: 0; border-radius: 6px; background: none; color: var(--ink); font: inherit; font-size: 13px; white-space: nowrap; cursor: pointer; }
.dp-action:hover:not(:disabled) { background: var(--soft); }
.dp-action-danger { color: #b91c1c; }
.dp-action-danger:hover:not(:disabled) { background: #fef2f2; }
.dp-action:disabled { opacity: .4; cursor: not-allowed; }

.dp-error-row td { padding: 0 16px 14px; }
.dp-error-text.is-neutral { border-left-color: var(--color-gray-300, #d1d5db); background: var(--soft); color: var(--muted); }
.dp-error-text { display: block; padding: 8px 12px; border-left: 3px solid #f87171; border-radius: 4px; background: #fef2f2; color: #991b1b; font-size: 13px; }

.dp-empty { display: flex; flex-direction: column; align-items: center; gap: 8px; padding: 56px 24px; color: var(--muted); text-align: center; }
.dp-empty strong { color: var(--ink); font-weight: 600; }
.dp-empty .dp-btn { margin-top: 8px; }

.dp-pagination { display: flex; align-items: center; justify-content: space-between; gap: 12px; font-size: 13px; }
.dp-pages { display: flex; align-items: center; gap: 12px; }

.dp-spinner { width: 14px; height: 14px; border: 2px solid var(--line); border-top-color: var(--accent); border-radius: 50%; animation: dp-spin .7s linear infinite; display: inline-block; }
.dp-sr { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }

.dp-btn:focus-visible, .dp-chip:focus-visible, .dp-action:focus-visible, .dp-link:focus-visible, .dp-build:focus-visible, .mon-close:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }

@keyframes dp-spin { to { transform: rotate(360deg); } }
@keyframes dp-pulse { 0%, 100% { opacity: 1; } 50% { opacity: .35; } }

/* Monitoring */
.mon-overlay { position: fixed; inset: 0; z-index: 1000; display: grid; place-items: center; padding: 24px 16px; background: rgba(17, 24, 39, .45); }
.mon { width: min(100%, 520px); border-radius: 14px; background: #fff; color: var(--ink); font-size: 14px; box-shadow: 0 24px 64px rgba(17, 24, 39, .22); }
.mon-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding: 24px 24px 0; }
.mon-header h2 { margin: 0; font-size: 18px; font-weight: 600; }
.mon-header p { margin: 4px 0 0; color: var(--muted); font-size: 13px; }
.mon-close { display: grid; place-items: center; width: 32px; height: 32px; margin: -4px -8px 0 0; border: 0; border-radius: 8px; background: none; color: var(--muted); cursor: pointer; }
.mon-close:hover { background: var(--soft); color: var(--ink); }
.mon-state { display: flex; align-items: center; gap: 8px; padding: 32px 24px; color: var(--muted); }
.mon-alert { margin: 20px 24px 0; }
.mon-health { display: flex; align-items: center; gap: 8px; margin: 20px 24px 0; padding: 12px 14px; border-radius: 10px; font-size: 14px; }
.mon-health.is-healthy { background: #ecfdf5; color: #047857; }
.mon-health.is-warning { background: #fffbeb; color: #b45309; }
.mon-health.is-critical { background: #fef2f2; color: #b91c1c; }
.mon-health .dp-muted { font-size: 13px; }
.mon-metrics { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1px; margin: 16px 24px 0; overflow: hidden; border: 1px solid var(--line); border-radius: 10px; background: var(--line); }
.mon-metrics div { padding: 14px 16px; background: #fff; }
.mon-metrics dt { color: var(--muted); font-size: 12px; }
.mon-metrics dd { margin: 4px 0 0; font-size: 20px; font-weight: 600; font-variant-numeric: tabular-nums; }
.mon-metrics dd .dp-muted { font-size: 14px; font-weight: 400; }
.mon-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 20px; padding: 14px 24px; border-top: 1px solid var(--line); font-size: 13px; }

/* Mobile : chaque déploiement devient une carte */
@media (max-width: 900px) {
  .dp-header { flex-direction: column; align-items: stretch; }
  .dp-toolbar { grid-template-columns: 1fr; }
  .dp-live { margin-left: 0; width: 100%; }
  .dp-table thead { display: none; }
  .dp-table, .dp-table tbody, .dp-table tr, .dp-table td { display: block; width: 100%; }
  .dp-table tbody tr:not(.dp-error-row) { display: grid; grid-template-columns: 1fr 1fr; gap: 10px 16px; padding: 16px; border-top: 1px solid var(--line); }
  .dp-table tbody tr:first-child { border-top: 0; }
  .dp-table td { padding: 0; border: 0; }
  .dp-table td[data-label]::before { content: attr(data-label); display: block; margin-bottom: 2px; color: var(--muted); font-size: 12px; }
  .dp-table td[data-label="Application"] { grid-column: 1 / -1; }
  .dp-table td[data-label="Application"]::before { display: none; }
  .dp-actions-cell { grid-column: 1 / -1; width: auto; }
  .dp-actions { justify-content: flex-start; margin-left: -10px; }
  .dp-error-row td { padding: 0 16px 16px; }
  .dp-pagination { flex-direction: column; align-items: stretch; }
  .dp-pages { justify-content: space-between; }
}
@media (prefers-reduced-motion: reduce) {
  .dp *, .mon * { animation: none !important; transition: none !important; }
}
</style>
