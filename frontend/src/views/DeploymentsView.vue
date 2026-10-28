<script setup lang="ts">
import {
  computed,
  onMounted,
  ref,
} from 'vue'

import DeploymentFormModal from '@/components/deployments/DeploymentFormModal.vue'

import {
  useApplicationsStore,
} from '@/stores/applications'

import {
  useDeploymentsStore,
} from '@/stores/deployments'

import {
  useEnvironmentsStore,
} from '@/stores/environments'

import {
  useMonitoringStore,
} from '@/stores/monitoring'

import { useAuthStore } from '@/stores/auth'

import type {
  CreateDeploymentRequest,
} from '@/types/deployment'

const deploymentsStore =
  useDeploymentsStore()

const authStore = useAuthStore()

const canManageDeployments = computed(() =>
  authStore.hasRole('DEVOPS', 'ADMIN'),
)

const canDeleteDeployments = computed(() =>
  authStore.hasRole('ADMIN'),
)

const applicationsStore =
  useApplicationsStore()

const environmentsStore =
  useEnvironmentsStore()

const monitoringStore =
  useMonitoringStore()

const modalOpen = ref(false)

const monitoringOpen = ref(false)

const monitoredDeploymentName =
  ref<string | null>(null)

onMounted(async () => {
  await Promise.all([
    deploymentsStore.loadDeployments(),

    applicationsStore.loadApplications({
      page: 0,
      size: 100,
      active: true,
    }),

    environmentsStore.loadEnvironments({
      page: 0,
      size: 100,
      active: true,
    }),
  ])
})

function openCreateModal() {
  modalOpen.value = true
}

function closeModal() {
  if (deploymentsStore.actionLoading) {
    return
  }

  modalOpen.value = false
}

async function submitDeployment(
  request: CreateDeploymentRequest,
) {
  try {
    await deploymentsStore.create(
      request,
    )

    closeModal()
  } catch {
    // Store
  }
}

async function rollback(
  id: number,
) {
  const confirmed =
    window.confirm(
      'Voulez-vous créer un rollback de ce déploiement ?',
    )

  if (!confirmed) {
    return
  }

  try {
    await deploymentsStore.rollback(id)
  } catch {
    // Store
  }
}

async function removeDeployment(
  id: number,
) {
  const confirmed =
    window.confirm(
      'Voulez-vous supprimer ce déploiement ?',
    )

  if (!confirmed) {
    return
  }

  try {
    await deploymentsStore.remove(id)
  } catch {
    // Store
  }
}

async function openMonitoring(
  deploymentId: number,
  applicationName: string,
) {
  monitoredDeploymentName.value =
    applicationName

  monitoringOpen.value = true

  await monitoringStore
    .loadDeploymentMetrics(
      deploymentId,
    )
}

function closeMonitoring() {
  monitoringOpen.value = false

  monitoredDeploymentName.value = null

  monitoringStore.clear()
}

async function previousPage() {
  if (
    deploymentsStore.page === 0
    || deploymentsStore.loading
  ) {
    return
  }

  deploymentsStore.page -= 1

  await deploymentsStore.loadDeployments()
}

async function nextPage() {
  if (
    deploymentsStore.page + 1
      >= deploymentsStore.totalPages
    || deploymentsStore.loading
  ) {
    return
  }

  deploymentsStore.page += 1

  await deploymentsStore.loadDeployments()
}

async function applyFilters() {
  deploymentsStore.page = 0

  await deploymentsStore.loadDeployments()
}

async function resetFilters() {
  deploymentsStore.search = ''
  deploymentsStore.status = null
  deploymentsStore.applicationId = null

  deploymentsStore.page = 0

  await deploymentsStore.loadDeployments()
}

function formatDate(
  value: string | null,
) {
  if (!value) {
    return '—'
  }

  return new Date(value)
    .toLocaleString(
      'fr-FR',
      {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',

        hour: '2-digit',
        minute: '2-digit',
      },
    )
}

function formatDuration(
  seconds: number | null,
) {
  if (
    seconds === null
    || seconds === undefined
  ) {
    return '—'
  }

  if (seconds < 60) {
    return `${seconds}s`
  }

  const minutes =
    Math.floor(seconds / 60)

  const remainingSeconds =
    seconds % 60

  return `${minutes}m ${remainingSeconds}s`
}

function formatMetric(
  value: number | null | undefined,
  unit = '',
) {
  if (
    value === null
    || value === undefined
    || Number.isNaN(value)
  ) {
    return '—'
  }

  return `${value.toFixed(2)}${unit}`
}

function formatHealthStatus(
  status: string,
) {
  switch (status) {
    case 'HEALTHY':
      return 'Sain'

    case 'WARNING':
      return 'Attention'

    case 'CRITICAL':
      return 'Critique'

    default:
      return status
  }
}
</script>

<template>
  <section class="deployments-page">
    <header class="page-header">
      <div>
        <h2>
          Déploiements
        </h2>

        <p>
          Déployez les versions de vos applications
          vers leurs environnements.
        </p>
      </div>

      <button
        v-if="canManageDeployments"
        type="button"
        class="primary-button"
        :disabled="
          deploymentsStore.actionLoading
        "
        @click="openCreateModal"
      >
        + Nouveau déploiement
      </button>
    </header>

    <section class="toolbar">
      <input
        v-model="deploymentsStore.search"
        type="search"
        placeholder="Application, version, environnement..."
        @keyup.enter="applyFilters"
      />

      <select
        v-model="
          deploymentsStore.applicationId
        "
        @change="applyFilters"
      >
        <option :value="null">
          Toutes les applications
        </option>

        <option
          v-for="
            application
            in applicationsStore.applications
          "
          :key="application.id"
          :value="application.id"
        >
          {{ application.name }}
        </option>
      </select>

      <select
        v-model="deploymentsStore.status"
        @change="applyFilters"
      >
        <option :value="null">
          Tous les statuts
        </option>

        <option value="PENDING">
          PENDING
        </option>

        <option value="RUNNING">
          RUNNING
        </option>

        <option value="SUCCESS">
          SUCCESS
        </option>

        <option value="FAILED">
          FAILED
        </option>

        <option value="CANCELLED">
          CANCELLED
        </option>

        <option value="ROLLED_BACK">
          ROLLED_BACK
        </option>
      </select>

      <button
        type="button"
        class="secondary-button"
        :disabled="
          deploymentsStore.loading
        "
        @click="applyFilters"
      >
        Rechercher
      </button>

      <button
        type="button"
        class="secondary-button"
        :disabled="
          deploymentsStore.loading
        "
        @click="resetFilters"
      >
        Réinitialiser
      </button>
    </section>

    <p
      v-if="
        deploymentsStore.errorMessage
      "
      class="error-message"
    >
      {{
        deploymentsStore.errorMessage
      }}
    </p>

    <section class="table-card">
      <div
        v-if="
          deploymentsStore.loading
        "
        class="state-message"
      >
        Chargement des déploiements...
      </div>

      <div
        v-else-if="
          deploymentsStore.deployments
            .length === 0
        "
        class="state-message"
      >
        Aucun déploiement trouvé.
      </div>

      <div
        v-else
        class="table-wrapper"
      >
        <table>
          <thead>
            <tr>
              <th>Application</th>
              <th>Version</th>
              <th>Environnement</th>
              <th>Statut</th>
              <th>Demandé par</th>
              <th>Build Jenkins</th>
              <th>Durée</th>
              <th>Créé le</th>
              <th>Actions</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="
                deployment
                in deploymentsStore.deployments
              "
              :key="deployment.id"
            >
              <td>
                <strong>
                  {{
                    deployment.applicationName
                  }}
                </strong>
              </td>

              <td>
                <span class="version-badge">
                  {{ deployment.version }}
                </span>
              </td>

              <td>
                <div class="environment-info">
                  <strong>
                    {{
                      deployment.environmentName
                    }}
                  </strong>

                  <small>
                    {{
                      deployment.environmentType
                    }}
                  </small>
                </div>
              </td>

              <td>
                <span
                  class="status-badge"
                  :class="
                    deployment.status
                      .toLowerCase()
                      .replace('_', '-')
                  "
                >
                  {{ deployment.status }}
                </span>
              </td>

              <td>
                {{
                  deployment.requestedByEmail
                }}
              </td>

              <td>
                {{
                  deployment.jenkinsBuildNumber
                  ?? '—'
                }}
              </td>

              <td>
                {{
                  formatDuration(
                    deployment.durationSeconds,
                  )
                }}
              </td>

              <td>
                {{
                  formatDate(
                    deployment.createdAt,
                  )
                }}
              </td>

              <td>
                <div class="actions">
                  <a
                    v-if="
                      deployment.deploymentLogsUrl
                    "
                    :href="
                      deployment.deploymentLogsUrl
                    "
                    target="_blank"
                    rel="noopener noreferrer"
                    class="
                      action-button
                      logs
                    "
                  >
                    Logs
                  </a>

                  <button
                    v-if="
                      deployment.status
                        === 'SUCCESS'
                    "
                    type="button"
                    class="
                      action-button
                      monitoring
                    "
                    @click="
                      openMonitoring(
                        deployment.id,
                        deployment.applicationName,
                      )
                    "
                  >
                    Monitoring
                  </button>

                  <button
                    v-if="
                      canManageDeployments
                      && deployment.status
                        === 'SUCCESS'
                    "
                    type="button"
                    class="
                      action-button
                      rollback
                    "
                    :disabled="
                      deploymentsStore
                        .actionLoading
                    "
                    @click="
                      rollback(
                        deployment.id,
                      )
                    "
                  >
                    Rollback
                  </button>

                  <button
                    v-if="canDeleteDeployments"
                    type="button"
                    class="
                      action-button
                      danger
                    "
                    :disabled="
                      deploymentsStore
                        .actionLoading
                    "
                    @click="
                      removeDeployment(
                        deployment.id,
                      )
                    "
                  >
                    Supprimer
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <footer class="pagination">
      <p>
        {{
          deploymentsStore.totalElements
        }}
        déploiement(s)
      </p>

      <div class="pagination-actions">
        <button
          type="button"
          :disabled="
            deploymentsStore.page === 0
          "
          @click="previousPage"
        >
          Précédent
        </button>

        <span>
          Page
          {{ deploymentsStore.page + 1 }}
          sur
          {{
            Math.max(
              deploymentsStore.totalPages,
              1,
            )
          }}
        </span>

        <button
          type="button"
          :disabled="
            deploymentsStore.page + 1
              >= deploymentsStore.totalPages
          "
          @click="nextPage"
        >
          Suivant
        </button>
      </div>
    </footer>

    <DeploymentFormModal
      :open="modalOpen"
      :applications="
        applicationsStore.applications
      "
      :environments="
        environmentsStore.environments
      "
      :loading="
        deploymentsStore.actionLoading
      "
      @close="closeModal"
      @submit="submitDeployment"
    />

    <!-- MONITORING -->

    <div
      v-if="monitoringOpen"
      class="monitoring-overlay"
      @click.self="closeMonitoring"
    >
      <section class="monitoring-panel">
        <header class="monitoring-header">
          <div>
            <span class="monitoring-label">
              Observabilité
            </span>

            <h3>
              Monitoring du déploiement
            </h3>

            <p>
              {{ monitoredDeploymentName }}

              <span
                v-if="
                  monitoringStore
                    .currentDeploymentId
                "
              >
                · Déploiement
                #{{
                  monitoringStore
                    .currentDeploymentId
                }}
              </span>
            </p>
          </div>

          <button
            type="button"
            class="monitoring-close"
            @click="closeMonitoring"
          >
            ×
          </button>
        </header>

        <div
          v-if="monitoringStore.loading"
          class="monitoring-state"
        >
          Interrogation de Prometheus...
        </div>

        <div
          v-else-if="
            monitoringStore.errorMessage
          "
          class="
            monitoring-state
            monitoring-error
          "
        >
          {{
            monitoringStore.errorMessage
          }}
        </div>

        <template
          v-else-if="
            monitoringStore.metrics
          "
        >
          <div class="health-section">
            <span>
              État opérationnel
            </span>

            <strong
              class="health-badge"
              :class="
                monitoringStore.metrics
                  .healthStatus
                  .toLowerCase()
              "
            >
              {{
                formatHealthStatus(
                  monitoringStore.metrics
                    .healthStatus,
                )
              }}
            </strong>
          </div>

          <div class="metrics-grid">
            <article class="metric-card">
              <span>
                Pods disponibles
              </span>

              <strong>
                {{
                  monitoringStore.metrics
                    .runningPods
                }}
                /
                {{
                  monitoringStore.metrics
                    .desiredPods
                }}
              </strong>

              <small>
                Kubernetes
              </small>
            </article>

            <article class="metric-card">
              <span>
                CPU
              </span>

              <strong>
                {{
                  formatMetric(
                    monitoringStore.metrics
                      .cpuUsagePercent,
                    ' %',
                  )
                }}
              </strong>

              <small>
                Utilisation actuelle
              </small>
            </article>

            <article class="metric-card">
              <span>
                Mémoire
              </span>

              <strong>
                {{
                  formatMetric(
                    monitoringStore.metrics
                      .memoryUsageMb,
                    ' MB',
                  )
                }}
              </strong>

              <small>
                Working set
              </small>
            </article>

            <article class="metric-card">
              <span>
                Redémarrages
              </span>

              <strong>
                {{
                  monitoringStore.metrics
                    .restartCount
                }}
              </strong>

              <small>
                Pods Kubernetes
              </small>
            </article>
          </div>
        </template>

        <footer class="monitoring-footer">
          <span>
            Données récupérées depuis Prometheus
          </span>

          <button
            type="button"
            class="monitoring-refresh"
            :disabled="
              monitoringStore.loading
            "
            @click="monitoringStore.refresh"
          >
            {{
              monitoringStore.loading
                ? 'Actualisation...'
                : 'Actualiser'
            }}
          </button>
        </footer>
      </section>
    </div>
  </section>
</template>

<style scoped>
.deployments-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.page-header h2 {
  margin: 0;
  color: #0f172a;
  font-size: 26px;
}

.page-header p {
  margin: 7px 0 0;
  color: #64748b;
}

.primary-button,
.secondary-button {
  min-height: 42px;
  padding: 0 16px;
  border-radius: 10px;
  font-weight: 700;
}

.primary-button {
  border: 0;
  background: #2563eb;
  color: #ffffff;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
}

.toolbar {
  display: grid;
  grid-template-columns:
    minmax(220px, 1fr)
    210px
    170px
    auto
    auto;
  gap: 12px;
}

.toolbar input,
.toolbar select {
  min-height: 43px;
  padding: 0 13px;
  border: 1px solid #cbd5e1;
  border-radius: 9px;
  background: #ffffff;
}

.table-card {
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 15px;
  background: #ffffff;
  box-shadow:
    0 8px 24px
    rgb(15 23 42 / 4%);
}

.table-wrapper {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 15px 16px;
  border-bottom: 1px solid #f1f5f9;
  text-align: left;
  vertical-align: middle;
}

th {
  color: #64748b;
  font-size: 10px;
  text-transform: uppercase;
}

td {
  color: #334155;
  font-size: 12px;
}

.version-badge {
  display: inline-flex;
  padding: 5px 8px;
  border-radius: 7px;
  background: #eff6ff;
  color: #1d4ed8;
  font-weight: 800;
}

.environment-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.environment-info small {
  color: #94a3b8;
}

.status-badge {
  display: inline-flex;
  padding: 5px 9px;
  border-radius: 999px;
  font-size: 9px;
  font-weight: 800;
}

.status-badge.pending {
  background: #f1f5f9;
  color: #475569;
}

.status-badge.running {
  background: #eff6ff;
  color: #1d4ed8;
}

.status-badge.success {
  background: #dcfce7;
  color: #15803d;
}

.status-badge.failed {
  background: #fef2f2;
  color: #b91c1c;
}

.status-badge.cancelled {
  background: #fff7ed;
  color: #c2410c;
}

.status-badge.rolled-back {
  background: #f5f3ff;
  color: #6d28d9;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.action-button {
  min-height: 31px;
  padding: 0 9px;
  border: 0;
  border-radius: 7px;
  font-size: 10px;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
}

.action-button.logs {
  display: inline-flex;
  align-items: center;
  background: #eff6ff;
  color: #1d4ed8;
}

.action-button.monitoring {
  background: #ecfeff;
  color: #0e7490;
}

.action-button.rollback {
  background: #f5f3ff;
  color: #6d28d9;
}

.action-button.danger {
  background: #fef2f2;
  color: #b91c1c;
}

.action-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.error-message {
  margin: 0;
  padding: 11px 13px;
  border: 1px solid #fecaca;
  border-radius: 9px;
  background: #fef2f2;
  color: #b91c1c;
}

.state-message {
  padding: 55px;
  color: #64748b;
  text-align: center;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.pagination p {
  margin: 0;
  color: #64748b;
  font-size: 12px;
}

.pagination-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.pagination-actions button {
  min-height: 35px;
  padding: 0 12px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #ffffff;
}

.pagination-actions span {
  color: #64748b;
  font-size: 11px;
}

/* MONITORING */

.monitoring-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    rgb(15 23 42 / 55%);
}

.monitoring-panel {
  width: min(760px, 100%);
  overflow: hidden;
  border-radius: 18px;
  background: #ffffff;
  box-shadow:
    0 25px 60px
    rgb(15 23 42 / 25%);
}

.monitoring-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 24px;
  border-bottom: 1px solid #e2e8f0;
}

.monitoring-label {
  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  text-transform: uppercase;
}

.monitoring-header h3 {
  margin: 6px 0 0;
  color: #0f172a;
  font-size: 21px;
}

.monitoring-header p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.monitoring-close {
  border: 0;
  background: transparent;
  color: #64748b;
  font-size: 25px;
  cursor: pointer;
}

.health-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 24px 24px 0;
  padding: 16px;
  border-radius: 12px;
  background: #f8fafc;
  color: #475569;
}

.health-badge {
  padding: 6px 11px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
}

.health-badge.healthy {
  background: #dcfce7;
  color: #15803d;
}

.health-badge.warning {
  background: #ffedd5;
  color: #c2410c;
}

.health-badge.critical {
  background: #fee2e2;
  color: #b91c1c;
}

.metrics-grid {
  display: grid;
  grid-template-columns:
    repeat(2, minmax(0, 1fr));
  gap: 14px;
  padding: 20px 24px 24px;
}

.metric-card {
  display: flex;
  min-height: 125px;
  flex-direction: column;
  padding: 18px;
  border: 1px solid #e2e8f0;
  border-radius: 13px;
}

.metric-card span {
  color: #64748b;
  font-size: 12px;
}

.metric-card strong {
  margin-top: 10px;
  color: #0f172a;
  font-size: 25px;
}

.metric-card small {
  margin-top: auto;
  color: #94a3b8;
}

.monitoring-state {
  padding: 70px 24px;
  color: #64748b;
  text-align: center;
}

.monitoring-error {
  color: #b91c1c;
}

.monitoring-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 15px;
  padding: 18px 24px;
  border-top: 1px solid #e2e8f0;
}

.monitoring-footer span {
  color: #94a3b8;
  font-size: 11px;
}

.monitoring-refresh {
  min-height: 38px;
  padding: 0 16px;
  border: 0;
  border-radius: 9px;
  background: #2563eb;
  color: #ffffff;
  font-weight: 700;
  cursor: pointer;
}

.monitoring-refresh:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 900px) {
  .toolbar {
    grid-template-columns:
      repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 650px) {
  .page-header,
  .pagination {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar {
    grid-template-columns: 1fr;
  }

  .metrics-grid {
    grid-template-columns: 1fr;
  }

  .monitoring-footer {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>