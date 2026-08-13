<script setup lang="ts">
import {
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

import type {
  CreateDeploymentRequest,
} from '@/types/deployment'

const deploymentsStore =
  useDeploymentsStore()

const applicationsStore =
  useApplicationsStore()

const environmentsStore =
  useEnvironmentsStore()

const modalOpen = ref(false)

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
  border:
    1px solid #cbd5e1;

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

  border:
    1px solid #cbd5e1;

  border-radius: 9px;

  background: #ffffff;
}

.table-card {
  overflow: hidden;

  border:
    1px solid #e2e8f0;

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

  border-bottom:
    1px solid #f1f5f9;

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
}

.action-button.logs {
  display: inline-flex;
  align-items: center;

  background: #eff6ff;

  color: #1d4ed8;
}

.action-button.rollback {
  background: #f5f3ff;

  color: #6d28d9;
}

.action-button.danger {
  background: #fef2f2;

  color: #b91c1c;
}

.error-message {
  margin: 0;

  padding: 11px 13px;

  border:
    1px solid #fecaca;

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

  border:
    1px solid #cbd5e1;

  border-radius: 8px;

  background: #ffffff;
}

.pagination-actions span {
  color: #64748b;

  font-size: 11px;
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
}
</style>