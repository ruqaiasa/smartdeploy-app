<script setup lang="ts">
import {
  computed,
  onMounted,
  ref,
} from 'vue'

import { useRouter } from 'vue-router'

import ApplicationFormModal from '@/components/applications/ApplicationFormModal.vue'

import { useApplicationsStore } from '@/stores/applications'
import { useAuthStore } from '@/stores/auth'

import type {
  ApplicationResponse,
  CreateApplicationRequest,
  UpdateApplicationRequest,
} from '@/types/application'

const router = useRouter()

const applicationsStore =
  useApplicationsStore()

const authStore = useAuthStore()

const canManageApplications = computed(() =>
  authStore.hasRole('DEVELOPER', 'DEVOPS', 'ADMIN'),
)

const search = ref('')

const modalOpen = ref(false)

const selectedApplication =
  ref<ApplicationResponse | null>(null)

/* ==========================================================
 *                       RECHERCHE
 * ==========================================================
 */

const filteredApplications =
  computed(() => {
    const normalizedSearch =
      search.value
        .trim()
        .toLowerCase()

    if (!normalizedSearch) {
      return applicationsStore.applications
    }

    return applicationsStore
      .applications
      .filter((application) => {
        const description =
          application.description
            ?.toLowerCase() ?? ''

        return (
          application.name
            .toLowerCase()
            .includes(normalizedSearch)
          ||
          description.includes(
            normalizedSearch,
          )
          ||
          application.technology
            .toLowerCase()
            .includes(normalizedSearch)
          ||
          application.repositoryUrl
            .toLowerCase()
            .includes(normalizedSearch)
        )
      })
  })

/* ==========================================================
 *                       INITIALISATION
 * ==========================================================
 */

onMounted(async () => {
  await applicationsStore
    .loadApplications()
})

/* ==========================================================
 *                      VOIR PLUS
 * ==========================================================
 */

function viewApplicationDetails(
  id: number,
) {
  router.push({
    name: 'application-details',

    params: {
      id,
    },
  })
}

/* ==========================================================
 *                       MODAL CREATE
 * ==========================================================
 */

function openCreateModal() {
  selectedApplication.value = null
  modalOpen.value = true
}

/* ==========================================================
 *                       MODAL UPDATE
 * ==========================================================
 */

function openEditModal(
  application: ApplicationResponse,
) {
  selectedApplication.value =
    application

  modalOpen.value = true
}

function closeModal() {
  if (
    applicationsStore.actionLoading
  ) {
    return
  }

  modalOpen.value = false
  selectedApplication.value = null
}

/* ==========================================================
 *                      CREATE / UPDATE
 * ==========================================================
 */

async function submitApplication(
  request:
    | CreateApplicationRequest
    | UpdateApplicationRequest,
) {
  try {
    if (selectedApplication.value) {
      await applicationsStore.update(
        selectedApplication.value.id,
        request,
      )
    } else {
      await applicationsStore.create(
        request as CreateApplicationRequest,
      )
    }

    closeModal()
  } catch {
    // Le store contient déjà
    // le message retourné par le backend.
  }
}

/* ==========================================================
 *                         PAGINATION
 * ==========================================================
 */

async function previousPage() {
  if (
    applicationsStore.page === 0
    ||
    applicationsStore.loading
  ) {
    return
  }

  applicationsStore.page -= 1

  await applicationsStore
    .loadApplications()
}

async function nextPage() {
  if (
    applicationsStore.page + 1
      >= applicationsStore.totalPages
    ||
    applicationsStore.loading
  ) {
    return
  }

  applicationsStore.page += 1

  await applicationsStore
    .loadApplications()
}

/* ==========================================================
 *                         ACTIVATE
 * ==========================================================
 */

async function activate(
  id: number,
) {
  try {
    await applicationsStore.activate(id)
  } catch {
    // Erreur gérée par le store.
  }
}

/* ==========================================================
 *                       DEACTIVATE
 * ==========================================================
 */

async function deactivate(
  id: number,
) {
  try {
    await applicationsStore.deactivate(id)
  } catch {
    // Erreur gérée par le store.
  }
}

/* ==========================================================
 *                          DELETE
 * ==========================================================
 */

async function remove(
  id: number,
) {
  const confirmed =
    window.confirm(
      'Voulez-vous vraiment supprimer cette application ?',
    )

  if (!confirmed) {
    return
  }

  try {
    await applicationsStore.remove(id)
  } catch {
    // Erreur gérée par le store.
  }
}

/* ==========================================================
 *                          DATE
 * ==========================================================
 */

function formatDate(
  date: string,
) {
  return new Date(date)
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
  <section class="applications-page">
    <!-- ======================================================
                         HEADER
    ======================================================= -->

    <div class="page-header">
      <div>
        <h2>
          Applications
        </h2>

        <p>
          Gérez les applications disponibles
          dans SmartDeploy.
        </p>
      </div>

      <button
        v-if="canManageApplications"
        type="button"
        class="primary-button"
        :disabled="
          applicationsStore.actionLoading
        "
        @click="openCreateModal"
      >
        + Nouvelle application
      </button>
    </div>

    <!-- ======================================================
                         TOOLBAR
    ======================================================= -->

    <section class="toolbar">
      <div class="search-wrapper">
        <input
          v-model="search"
          type="search"
          placeholder="Rechercher une application..."
        />
      </div>

      <button
        type="button"
        class="secondary-button"
        :disabled="
          applicationsStore.loading
          ||
          applicationsStore.actionLoading
        "
        @click="
          applicationsStore.refresh()
        "
      >
        Rafraîchir
      </button>
    </section>

    <!-- ======================================================
                         ERREUR
    ======================================================= -->

    <p
      v-if="
        applicationsStore.errorMessage
      "
      class="error-message"
    >
      {{
        applicationsStore.errorMessage
      }}
    </p>

    <!-- ======================================================
                         TABLEAU
    ======================================================= -->

    <section class="table-card">
      <div
        v-if="
          applicationsStore.loading
        "
        class="state-message"
      >
        Chargement des applications...
      </div>

      <div
        v-else-if="
          filteredApplications.length === 0
        "
        class="state-message"
      >
        Aucune application trouvée.
      </div>

      <div
        v-else
        class="table-wrapper"
      >
        <table>
          <thead>
            <tr>
              <th>Nom</th>
              <th>Technologie</th>
              <th>Repository</th>
              <th>État</th>
              <th>Créée le</th>
              <th>Actions</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="
                application
                in filteredApplications
              "
              :key="application.id"
            >
              <!-- NOM -->

              <td>
                <div
                  class="application-name"
                >
                  <strong>
                    {{ application.name }}
                  </strong>

                  <small>
                    {{
                      application.description
                      || 'Aucune description'
                    }}
                  </small>
                </div>
              </td>

              <!-- TECHNOLOGIE -->

              <td>
                <span
                  class="technology-badge"
                >
                  {{
                    application.technology
                  }}
                </span>
              </td>

              <!-- REPOSITORY -->

              <td>
                <a
                  :href="
                    application.repositoryUrl
                  "
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Voir le dépôt
                </a>
              </td>

              <!-- STATUT -->

              <td>
                <span
                  class="status-badge"
                  :class="{
                    active:
                      application.active,

                    inactive:
                      !application.active,
                  }"
                >
                  {{
                    application.active
                      ? 'Active'
                      : 'Inactive'
                  }}
                </span>
              </td>

              <!-- DATE -->

              <td>
                {{
                  formatDate(
                    application.createdAt,
                  )
                }}
              </td>

              <!-- ACTIONS -->

              <td>
                <div class="actions">

                  <!-- VOIR PLUS -->

                  <button
                    type="button"
                    class="
                      action-button
                      details
                    "
                    @click="
                      viewApplicationDetails(
                        application.id,
                      )
                    "
                  >
                    Voir plus
                  </button>

                  <!-- MODIFIER -->

                  <button
                    v-if="canManageApplications"
                    type="button"
                    class="
                      action-button
                      edit
                    "
                    :disabled="
                      applicationsStore
                        .actionLoading
                    "
                    @click="
                      openEditModal(
                        application,
                      )
                    "
                  >
                    Modifier
                  </button>

                  <!-- DÉSACTIVER -->

                  <button
                    v-if="
                      canManageApplications
                      && application.active
                    "
                    type="button"
                    class="
                      action-button
                      warning
                    "
                    :disabled="
                      applicationsStore
                        .actionLoading
                    "
                    @click="
                      deactivate(
                        application.id,
                      )
                    "
                  >
                    Désactiver
                  </button>

                  <!-- ACTIVER -->

                  <button
                    v-else-if="canManageApplications"
                    type="button"
                    class="
                      action-button
                      success
                    "
                    :disabled="
                      applicationsStore
                        .actionLoading
                    "
                    @click="
                      activate(
                        application.id,
                      )
                    "
                  >
                    Activer
                  </button>

                  <!-- SUPPRIMER -->

                  <button
                    v-if="canManageApplications"
                    type="button"
                    class="
                      action-button
                      danger
                    "
                    :disabled="
                      applicationsStore
                        .actionLoading
                    "
                    @click="
                      remove(
                        application.id,
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

    <!-- ======================================================
                         PAGINATION
    ======================================================= -->

    <footer class="pagination">
      <p>
        {{
          applicationsStore.totalElements
        }}
        application(s)
      </p>

      <div
        class="pagination-actions"
      >
        <button
          type="button"
          :disabled="
            applicationsStore.page === 0
            ||
            applicationsStore.loading
            ||
            applicationsStore.actionLoading
          "
          @click="previousPage"
        >
          Précédent
        </button>

        <span>
          Page
          {{
            applicationsStore.page + 1
          }}

          sur

          {{
            Math.max(
              applicationsStore.totalPages,
              1,
            )
          }}
        </span>

        <button
          type="button"
          :disabled="
            applicationsStore.page + 1
              >= applicationsStore.totalPages
            ||
            applicationsStore.loading
            ||
            applicationsStore.actionLoading
          "
          @click="nextPage"
        >
          Suivant
        </button>
      </div>
    </footer>

    <!-- ======================================================
                         MODAL
    ======================================================= -->

    <ApplicationFormModal
      :open="modalOpen"
      :application="
        selectedApplication
      "
      :loading="
        applicationsStore.actionLoading
      "
      @close="closeModal"
      @submit="submitApplication"
    />
  </section>
</template>

<style scoped>
.applications-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

/* ==========================================================
 *                         HEADER
 * ==========================================================
 */

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

/* ==========================================================
 *                         BUTTONS
 * ==========================================================
 */

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

/* ==========================================================
 *                         TOOLBAR
 * ==========================================================
 */

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
  color: #0f172a;
}

.search-wrapper input:focus {
  border-color: #2563eb;

  box-shadow:
    0 0 0 4px
    rgb(37 99 235 / 10%);
}

/* ==========================================================
 *                          ERROR
 * ==========================================================
 */

.error-message {
  margin: 0;
  padding: 12px 14px;

  border: 1px solid #fecaca;
  border-radius: 10px;

  background: #fef2f2;
  color: #b91c1c;
}

/* ==========================================================
 *                         TABLE
 * ==========================================================
 */

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
  padding: 16px 20px;

  border-bottom:
    1px solid #f1f5f9;

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

/* ==========================================================
 *                     APPLICATION NAME
 * ==========================================================
 */

.application-name {
  display: flex;
  min-width: 180px;

  flex-direction: column;
}

.application-name strong {
  color: #0f172a;
}

.application-name small {
  margin-top: 4px;

  color: #64748b;
}

/* ==========================================================
 *                         BADGES
 * ==========================================================
 */

.technology-badge,
.status-badge {
  display: inline-flex;

  padding: 5px 9px;

  border-radius: 999px;

  font-size: 10px;
  font-weight: 800;
}

.technology-badge {
  background: #eff6ff;
  color: #1d4ed8;
}

.status-badge.active {
  background: #dcfce7;
  color: #15803d;
}

.status-badge.inactive {
  background: #f1f5f9;
  color: #64748b;
}

/* ==========================================================
 *                          LINKS
 * ==========================================================
 */

a {
  color: #2563eb;

  font-weight: 700;
  text-decoration: none;
}

a:hover {
  text-decoration: underline;
}

/* ==========================================================
 *                         ACTIONS
 * ==========================================================
 */

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

.action-button.details {
  background: #f5f3ff;
  color: #6d28d9;
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

.action-button:hover:not(:disabled) {
  filter: brightness(0.97);
}

.action-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

/* ==========================================================
 *                         STATES
 * ==========================================================
 */

.state-message {
  padding: 55px 20px;

  color: #64748b;

  text-align: center;
}

/* ==========================================================
 *                       PAGINATION
 * ==========================================================
 */

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

/* ==========================================================
 *                       RESPONSIVE
 * ==========================================================
 */

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