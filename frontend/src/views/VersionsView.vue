<script setup lang="ts">
import {
  computed,
  onMounted,
  ref,
} from 'vue'

import VersionFormModal from '@/components/versions/VersionFormModal.vue'

import {
  useApplicationsStore,
} from '@/stores/applications'

import {
  useVersionsStore,
} from '@/stores/versions'

import { useAuthStore } from '@/stores/auth'

import type {
  CreateVersionRequest,
  UpdateVersionRequest,
  VersionResponse,
} from '@/types/version'

const versionsStore =
  useVersionsStore()

const authStore = useAuthStore()

const canManageVersions = computed(() =>
  authStore.hasRole('DEVELOPER', 'DEVOPS', 'ADMIN'),
)

const applicationsStore =
  useApplicationsStore()

const modalOpen = ref(false)

const selectedVersion =
  ref<VersionResponse | null>(null)

onMounted(async () => {
  await Promise.all([
    versionsStore.loadVersions(),

    applicationsStore.loadApplications({
      page: 0,
      size: 100,
      active: true,
    }),
  ])
})

function openCreateModal() {
  selectedVersion.value = null
  modalOpen.value = true
}

function openEditModal(
  version: VersionResponse,
) {
  selectedVersion.value = version
  modalOpen.value = true
}

function closeModal() {
  if (versionsStore.actionLoading) {
    return
  }

  modalOpen.value = false
  selectedVersion.value = null
}

async function submitVersion(
  request:
    | CreateVersionRequest
    | UpdateVersionRequest,
) {
  try {
    if (selectedVersion.value) {
      await versionsStore.update(
        selectedVersion.value.id,
        request as UpdateVersionRequest,
      )
    } else {
      await versionsStore.create(
        request as CreateVersionRequest,
      )
    }

    closeModal()
  } catch {
    // Le store gère déjà le message d'erreur.
  }
}

async function removeVersion(
  id: number,
) {
  const confirmed =
    window.confirm(
      'Voulez-vous vraiment supprimer cette version ?',
    )

  if (!confirmed) {
    return
  }

  try {
    await versionsStore.remove(id)
  } catch {
    // Le store gère déjà le message d'erreur.
  }
}

async function previousPage() {
  if (
    versionsStore.page === 0
    || versionsStore.loading
  ) {
    return
  }

  versionsStore.page -= 1

  await versionsStore.loadVersions()
}

async function nextPage() {
  if (
    versionsStore.page + 1
      >= versionsStore.totalPages
    || versionsStore.loading
  ) {
    return
  }

  versionsStore.page += 1

  await versionsStore.loadVersions()
}

async function applyFilters() {
  versionsStore.page = 0

  await versionsStore.loadVersions()
}

async function resetFilters() {
  versionsStore.search = ''
  versionsStore.buildStatus = null
  versionsStore.applicationId = null
  versionsStore.page = 0

  await versionsStore.loadVersions()
}

function formatDate(
  value: string,
) {
  return new Date(value)
    .toLocaleDateString(
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
  <section class="versions-page">
    <header class="page-header">
      <div>
        <h2>
          Versions
        </h2>

        <p>
          Gérez les versions et les résultats
          des builds de vos applications.
        </p>
      </div>

      <button
        v-if="canManageVersions"
        type="button"
        class="primary-button"
        :disabled="versionsStore.actionLoading"
        @click="openCreateModal"
      >
        + Nouvelle version
      </button>
    </header>

    <section class="toolbar">
      <input
        v-model="versionsStore.search"
        type="search"
        placeholder="Version, branche, commit, application..."
        @keyup.enter="applyFilters"
      />

      <select
        v-model="versionsStore.applicationId"
        @change="applyFilters"
      >
        <option :value="null">
          Toutes les applications
        </option>

        <option
          v-for="application in applicationsStore.applications"
          :key="application.id"
          :value="application.id"
        >
          {{ application.name }}
        </option>
      </select>

      <select
        v-model="versionsStore.buildStatus"
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
      </select>

      <button
        type="button"
        class="secondary-button"
        :disabled="versionsStore.loading"
        @click="applyFilters"
      >
        Rechercher
      </button>

      <button
        type="button"
        class="secondary-button"
        :disabled="versionsStore.loading"
        @click="resetFilters"
      >
        Réinitialiser
      </button>
    </section>

    <p
      v-if="versionsStore.errorMessage"
      class="error-message"
    >
      {{ versionsStore.errorMessage }}
    </p>

    <section class="table-card">
      <div
        v-if="versionsStore.loading"
        class="state-message"
      >
        Chargement des versions...
      </div>

      <div
        v-else-if="
          versionsStore.versions.length === 0
        "
        class="state-message"
      >
        Aucune version trouvée.
      </div>

      <div
        v-else
        class="table-wrapper"
      >
        <table>
          <thead>
            <tr>
              <th>Version</th>
              <th>Application</th>
              <th>Git</th>
              <th>Image Docker</th>
              <th>Build</th>
              <th>Build #</th>
              <th>Créée le</th>
              <th>Actions</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="version in versionsStore.versions"
              :key="version.id"
            >
              <td>
                <strong class="version-number">
                  {{ version.version }}
                </strong>
              </td>

              <td>
                <strong>
                  {{ version.applicationName }}
                </strong>
              </td>

              <td>
                <div class="git-info">
                  <strong>
                    {{ version.gitBranch }}
                  </strong>

                  <small>
                    {{
                      version.gitCommit
                        || 'Commit non défini'
                    }}
                  </small>
                </div>
              </td>

              <td>
                <code
                  v-if="version.dockerImage"
                >
                  {{ version.dockerImage }}
                </code>

                <span
                  v-else
                  class="empty-value"
                >
                  Non définie
                </span>
              </td>

              <td>
                <span
                  class="status-badge"
                  :class="
                    version.buildStatus.toLowerCase()
                  "
                >
                  {{ version.buildStatus }}
                </span>
              </td>

              <td>
                {{
                  version.buildNumber
                    ?? '—'
                }}
              </td>

              <td>
                {{
                  formatDate(
                    version.createdAt,
                  )
                }}
              </td>

              <td>
                <div class="actions">
                  <button
                    v-if="canManageVersions"
                    type="button"
                    class="action-button edit"
                    :disabled="versionsStore.actionLoading"
                    @click="openEditModal(version)"
                  >
                    Modifier
                  </button>

                  <a
                    v-if="version.buildLogsUrl"
                    :href="version.buildLogsUrl"
                    target="_blank"
                    rel="noopener noreferrer"
                    class="action-button logs"
                  >
                    Logs
                  </a>

                  <button
                    v-if="canManageVersions"
                    type="button"
                    class="action-button danger"
                    :disabled="versionsStore.actionLoading"
                    @click="removeVersion(version.id)"
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
        {{ versionsStore.totalElements }}
        version(s)
      </p>

      <div class="pagination-actions">
        <button
          type="button"
          :disabled="
            versionsStore.page === 0
            || versionsStore.loading
            || versionsStore.actionLoading
          "
          @click="previousPage"
        >
          Précédent
        </button>

        <span>
          Page
          {{ versionsStore.page + 1 }}
          sur
          {{
            Math.max(
              versionsStore.totalPages,
              1,
            )
          }}
        </span>

        <button
          type="button"
          :disabled="
            versionsStore.page + 1
              >= versionsStore.totalPages
            || versionsStore.loading
            || versionsStore.actionLoading
          "
          @click="nextPage"
        >
          Suivant
        </button>
      </div>
    </footer>

    <VersionFormModal
      :open="modalOpen"
      :version="selectedVersion"
      :applications="
        applicationsStore.applications
      "
      :loading="
        versionsStore.actionLoading
      "
      @close="closeModal"
      @submit="submitVersion"
    />
  </section>
</template>

<style scoped>
.versions-page {
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

.primary-button:hover:not(:disabled) {
  background: #1d4ed8;
}

.secondary-button {
  border: 1px solid #cbd5e1;

  background: #ffffff;
  color: #334155;
}

.secondary-button:hover:not(:disabled) {
  background: #f8fafc;
}

.primary-button:disabled,
.secondary-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
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

  outline: none;

  background: #ffffff;
  color: #0f172a;
}

.toolbar input:focus,
.toolbar select:focus {
  border-color: #2563eb;

  box-shadow:
    0 0 0 4px
    rgb(37 99 235 / 10%);
}

.error-message {
  margin: 0;
  padding: 11px 13px;

  border: 1px solid #fecaca;
  border-radius: 9px;

  background: #fef2f2;
  color: #b91c1c;
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
  padding: 15px 17px;

  border-bottom:
    1px solid #f1f5f9;

  text-align: left;
  vertical-align: middle;
}

th {
  color: #64748b;

  font-size: 10px;
  letter-spacing: 0.04em;

  text-transform: uppercase;
}

td {
  color: #334155;

  font-size: 12px;
}

.version-number {
  color: #2563eb;

  font-size: 13px;
}

.git-info {
  display: flex;

  flex-direction: column;

  gap: 4px;
}

.git-info strong {
  color: #334155;
}

.git-info small {
  color: #94a3b8;
}

code {
  display: block;

  max-width: 210px;

  overflow: hidden;

  padding: 5px 7px;

  border-radius: 6px;

  background: #f1f5f9;
  color: #334155;

  text-overflow: ellipsis;
  white-space: nowrap;
}

.empty-value {
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

.action-button.edit {
  background: #eff6ff;
  color: #1d4ed8;
}

.action-button.logs {
  display: inline-flex;

  align-items: center;

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
  color: #334155;
}

.pagination-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
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

  .primary-button {
    width: 100%;
  }
}
</style>