<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import EnvironmentFormModal from '@/components/environments/EnvironmentFormModal.vue'

import { useApplicationsStore } from '@/stores/applications'
import { useEnvironmentsStore } from '@/stores/environments'

import type {
  CreateEnvironmentRequest,
  EnvironmentResponse,
  UpdateEnvironmentRequest,
} from '@/types/environment'

const environmentsStore = useEnvironmentsStore()
const applicationsStore = useApplicationsStore()

const search = ref('')
const modalOpen = ref(false)

const selectedEnvironment =
  ref<EnvironmentResponse | null>(null)

const filteredEnvironments = computed(() => {
  const normalizedSearch =
    search.value.trim().toLowerCase()

  if (!normalizedSearch) {
    return environmentsStore.environments
  }

  return environmentsStore.environments.filter(
    (environment) => {
      const description =
        environment.description?.toLowerCase() ?? ''

      const clusterUrl =
        environment.clusterUrl?.toLowerCase() ?? ''

      const applicationName =
        environment.applicationName
          ?.toLowerCase() ?? ''

      return (
        environment.name
          .toLowerCase()
          .includes(normalizedSearch)
        || description.includes(normalizedSearch)
        || environment.type
          .toLowerCase()
          .includes(normalizedSearch)
        || environment.namespace
          .toLowerCase()
          .includes(normalizedSearch)
        || clusterUrl.includes(normalizedSearch)
        || applicationName.includes(normalizedSearch)
      )
    },
  )
})

onMounted(async () => {
  await Promise.all([
    environmentsStore.loadEnvironments(),

    applicationsStore.loadApplications({
      page: 0,
      size: 100,
      active: true,
    }),
  ])
})

function openCreateModal() {
  selectedEnvironment.value = null
  modalOpen.value = true
}

function openEditModal(
  environment: EnvironmentResponse,
) {
  selectedEnvironment.value = environment
  modalOpen.value = true
}

function closeModal() {
  if (environmentsStore.actionLoading) {
    return
  }

  modalOpen.value = false
  selectedEnvironment.value = null
}

async function submitEnvironment(
  request:
    | CreateEnvironmentRequest
    | UpdateEnvironmentRequest,
) {
  try {
    if (selectedEnvironment.value) {
      await environmentsStore.update(
        selectedEnvironment.value.id,
        request,
      )
    } else {
      await environmentsStore.create(
        request as CreateEnvironmentRequest,
      )
    }

    closeModal()
  } catch {
    // Le store contient déjà le message backend.
  }
}

async function activate(id: number) {
  try {
    await environmentsStore.activate(id)
  } catch {
    // Le store contient déjà le message backend.
  }
}

async function deactivate(id: number) {
  try {
    await environmentsStore.deactivate(id)
  } catch {
    // Le store contient déjà le message backend.
  }
}

async function remove(id: number) {
  const confirmed = window.confirm(
    'Voulez-vous vraiment supprimer cet environnement ?',
  )

  if (!confirmed) {
    return
  }

  try {
    await environmentsStore.remove(id)
  } catch {
    // Le store contient déjà le message backend.
  }
}

async function previousPage() {
  if (
    environmentsStore.page === 0
    || environmentsStore.loading
  ) {
    return
  }

  environmentsStore.page -= 1

  await environmentsStore.loadEnvironments()
}

async function nextPage() {
  if (
    environmentsStore.page + 1
      >= environmentsStore.totalPages
    || environmentsStore.loading
  ) {
    return
  }

  environmentsStore.page += 1

  await environmentsStore.loadEnvironments()
}

function formatDate(date: string) {
  return new Date(date).toLocaleDateString(
    'fr-FR',
    {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    },
  )
}
</script>

<template>
  <section class="environments-page">
    <div class="page-header">
      <div>
        <h2>
          Environnements
        </h2>

        <p>
          Gérez les environnements associés
          à vos applications.
        </p>
      </div>

      <button
        type="button"
        class="primary-button"
        :disabled="
          environmentsStore.actionLoading
          || applicationsStore.applications.length === 0
        "
        @click="openCreateModal"
      >
        + Nouvel environnement
      </button>
    </div>

    <section class="toolbar">
      <div class="search-wrapper">
        <input
          v-model="search"
          type="search"
          placeholder="Rechercher un environnement..."
        />
      </div>

      <button
        type="button"
        class="secondary-button"
        :disabled="
          environmentsStore.loading
          || environmentsStore.actionLoading
        "
        @click="environmentsStore.refresh()"
      >
        Rafraîchir
      </button>
    </section>

    <p
      v-if="applicationsStore.applications.length === 0"
      class="warning-message"
    >
      Vous devez créer au moins une application active
      avant de créer un environnement.
    </p>

    <p
      v-if="environmentsStore.errorMessage"
      class="error-message"
    >
      {{ environmentsStore.errorMessage }}
    </p>

    <section class="table-card">
      <div
        v-if="environmentsStore.loading"
        class="state-message"
      >
        Chargement des environnements...
      </div>

      <div
        v-else-if="filteredEnvironments.length === 0"
        class="state-message"
      >
        Aucun environnement trouvé.
      </div>

      <div
        v-else
        class="table-wrapper"
      >
        <table>
          <thead>
            <tr>
              <th>Nom</th>
              <th>Application</th>
              <th>Type</th>
              <th>Namespace</th>
              <th>Cluster</th>
              <th>État</th>
              <th>Créé le</th>
              <th>Actions</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="environment in filteredEnvironments"
              :key="environment.id"
            >
              <td>
                <div class="environment-name">
                  <strong>
                    {{ environment.name }}
                  </strong>

                  <small>
                    {{
                      environment.description
                      || 'Aucune description'
                    }}
                  </small>
                </div>
              </td>

              <td>
                <span class="application-badge">
                  {{
                    environment.applicationName
                    || `Application #${environment.applicationId}`
                  }}
                </span>
              </td>

              <td>
                <span
                  class="type-badge"
                  :class="
                    environment.type.toLowerCase()
                  "
                >
                  {{ environment.type }}
                </span>
              </td>

              <td>
                <code>
                  {{ environment.namespace }}
                </code>
              </td>

              <td>
                <a
                  v-if="environment.clusterUrl"
                  :href="environment.clusterUrl"
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Ouvrir
                </a>

                <span
                  v-else
                  class="empty-value"
                >
                  Non configuré
                </span>
              </td>

              <td>
                <span
                  class="status-badge"
                  :class="{
                    active: environment.active,
                    inactive: !environment.active,
                  }"
                >
                  {{
                    environment.active
                      ? 'Actif'
                      : 'Inactif'
                  }}
                </span>
              </td>

              <td>
                {{ formatDate(environment.createdAt) }}
              </td>

              <td>
                <div class="actions">
                  <button
                    type="button"
                    class="action-button edit"
                    :disabled="
                      environmentsStore.actionLoading
                    "
                    @click="openEditModal(environment)"
                  >
                    Modifier
                  </button>

                  <button
                    v-if="environment.active"
                    type="button"
                    class="action-button warning"
                    :disabled="
                      environmentsStore.actionLoading
                    "
                    @click="
                      deactivate(environment.id)
                    "
                  >
                    Désactiver
                  </button>

                  <button
                    v-else
                    type="button"
                    class="action-button success"
                    :disabled="
                      environmentsStore.actionLoading
                    "
                    @click="
                      activate(environment.id)
                    "
                  >
                    Activer
                  </button>

                  <button
                    type="button"
                    class="action-button danger"
                    :disabled="
                      environmentsStore.actionLoading
                    "
                    @click="remove(environment.id)"
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
        {{ environmentsStore.totalElements }}
        environnement(s)
      </p>

      <div class="pagination-actions">
        <button
          type="button"
          :disabled="
            environmentsStore.page === 0
            || environmentsStore.loading
            || environmentsStore.actionLoading
          "
          @click="previousPage"
        >
          Précédent
        </button>

        <span>
          Page {{ environmentsStore.page + 1 }}
          sur
          {{
            Math.max(
              environmentsStore.totalPages,
              1,
            )
          }}
        </span>

        <button
          type="button"
          :disabled="
            environmentsStore.page + 1
              >= environmentsStore.totalPages
            || environmentsStore.loading
            || environmentsStore.actionLoading
          "
          @click="nextPage"
        >
          Suivant
        </button>
      </div>
    </footer>

    <EnvironmentFormModal
      :open="modalOpen"
      :environment="selectedEnvironment"
      :applications="
        applicationsStore.applications
      "
      :loading="
        environmentsStore.actionLoading
      "
      @close="closeModal"
      @submit="submitEnvironment"
    />
  </section>
</template>

<style scoped>
.environments-page {
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
  padding: 0 17px;

  border-radius: 10px;
  font-weight: 700;
}

.primary-button {
  border: 0;
  background: #2563eb;
  color: #ffffff;
}

.primary-button:hover:not(:disabled) {
  background: #1d4ed8;
}

.secondary-button {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
}

.primary-button:disabled,
.secondary-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.search-wrapper {
  width: min(100%, 430px);
}

.search-wrapper input {
  width: 100%;
  min-height: 43px;
  padding: 0 14px;

  border: 1px solid #cbd5e1;
  border-radius: 10px;
  outline: none;

  background: #ffffff;
}

.search-wrapper input:focus {
  border-color: #2563eb;

  box-shadow:
    0 0 0 4px rgb(37 99 235 / 10%);
}

.warning-message,
.error-message {
  margin: 0;
  padding: 12px 14px;

  border-radius: 10px;
  font-size: 13px;
}

.warning-message {
  border: 1px solid #fde68a;
  background: #fffbeb;
  color: #b45309;
}

.error-message {
  border: 1px solid #fecaca;
  background: #fef2f2;
  color: #b91c1c;
}

.table-card {
  overflow: hidden;

  border: 1px solid #e2e8f0;
  border-radius: 15px;
  background: #ffffff;

  box-shadow:
    0 8px 24px rgb(15 23 42 / 4%);
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
  padding: 16px 20px;
  border-bottom: 1px solid #f1f5f9;

  text-align: left;
  vertical-align: middle;
}

th {
  color: #64748b;
  font-size: 11px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

td {
  color: #334155;
  font-size: 13px;
}

.environment-name {
  display: flex;
  min-width: 180px;
  flex-direction: column;
}

.environment-name strong {
  color: #0f172a;
}

.environment-name small {
  margin-top: 4px;
  color: #64748b;
}

.application-badge,
.type-badge,
.status-badge {
  display: inline-flex;
  padding: 5px 9px;

  border-radius: 999px;

  font-size: 10px;
  font-weight: 800;
}

.application-badge {
  background: #f1f5f9;
  color: #334155;
}

.type-badge.development {
  background: #eff6ff;
  color: #1d4ed8;
}

.type-badge.test {
  background: #fefce8;
  color: #a16207;
}

.type-badge.staging {
  background: #f5f3ff;
  color: #6d28d9;
}

.type-badge.production {
  background: #fef2f2;
  color: #b91c1c;
}

.status-badge.active {
  background: #dcfce7;
  color: #15803d;
}

.status-badge.inactive {
  background: #f1f5f9;
  color: #64748b;
}

code {
  padding: 5px 8px;

  border-radius: 7px;
  background: #f1f5f9;
  color: #334155;
}

a {
  color: #2563eb;
  font-weight: 700;
  text-decoration: none;
}

a:hover {
  text-decoration: underline;
}

.empty-value {
  color: #94a3b8;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.action-button {
  min-height: 32px;
  padding: 0 10px;

  border: 0;
  border-radius: 8px;

  font-size: 11px;
  font-weight: 700;
}

.action-button.edit {
  background: #eff6ff;
  color: #1d4ed8;
}

.action-button.warning {
  background: #fff7ed;
  color: #c2410c;
}

.action-button.success {
  background: #f0fdf4;
  color: #15803d;
}

.action-button.danger {
  background: #fef2f2;
  color: #b91c1c;
}

.action-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.state-message {
  padding: 55px 20px;

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
  font-size: 13px;
}

.pagination-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.pagination-actions button {
  min-height: 35px;
  padding: 0 12px;

  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #ffffff;
  color: #334155;
}

.pagination-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.pagination-actions span {
  color: #64748b;
  font-size: 12px;
}

@media (max-width: 760px) {
  .page-header,
  .toolbar,
  .pagination {
    align-items: stretch;
    flex-direction: column;
  }

  .primary-button,
  .secondary-button,
  .search-wrapper {
    width: 100%;
  }

  .pagination-actions {
    justify-content: space-between;
  }
}
</style>