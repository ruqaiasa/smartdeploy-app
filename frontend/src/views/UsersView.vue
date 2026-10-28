<script setup lang="ts">
import {
  computed,
  onMounted,
  ref,
} from 'vue'

import {
  createUser,
  deleteUser,
  getUsers,
  restoreUser,
  updateUserRole,
  updateUserStatus,
} from '@/api/users.api'

import type {
  UserResponse,
} from '@/types/user'

import type {
  UserRole,
} from '@/types/auth'

const users = ref<UserResponse[]>([])

const loading = ref(false)
const actionLoading = ref(false)

const errorMessage =
  ref<string | null>(null)

const search = ref('')

const page = ref(0)
const size = ref(10)

const totalElements = ref(0)
const totalPages = ref(0)

const showCreateForm = ref(false)

const firstName = ref('')
const lastName = ref('')
const email = ref('')
const password = ref('')

const selectedRole =
  ref<UserRole>('VIEWER')

const roles: UserRole[] = [
  'VIEWER',
  'DEVELOPER',
  'DEVOPS',
  'ADMIN',
]

const hasPreviousPage =
  computed(() => page.value > 0)

const hasNextPage =
  computed(() =>
    page.value + 1 < totalPages.value,
  )

async function loadUsers() {
  loading.value = true
  errorMessage.value = null

  try {
    const response = await getUsers({
      page: page.value,
      size: size.value,
      search:
        search.value.trim()
        || undefined,
    })

    users.value = response.content
    totalElements.value =
      response.totalElements

    totalPages.value =
      response.totalPages
  } catch {
    errorMessage.value =
      'Impossible de charger les utilisateurs.'
  } finally {
    loading.value = false
  }
}

async function submitSearch() {
  page.value = 0
  await loadUsers()
}

async function previousPage() {
  if (!hasPreviousPage.value) {
    return
  }

  page.value -= 1
  await loadUsers()
}

async function nextPage() {
  if (!hasNextPage.value) {
    return
  }

  page.value += 1
  await loadUsers()
}

function resetCreateForm() {
  firstName.value = ''
  lastName.value = ''
  email.value = ''
  password.value = ''
  selectedRole.value = 'VIEWER'
}

async function submitCreateUser() {
  actionLoading.value = true
  errorMessage.value = null

  try {
    await createUser({
      firstName:
        firstName.value.trim(),

      lastName:
        lastName.value.trim(),

      email:
        email.value.trim(),

      password:
        password.value,

      role:
        selectedRole.value,
    })

    resetCreateForm()
    showCreateForm.value = false

    page.value = 0

    await loadUsers()
  } catch {
    errorMessage.value =
      'Impossible de créer cet utilisateur.'
  } finally {
    actionLoading.value = false
  }
}

async function changeRole(
  user: UserResponse,
  event: Event,
) {
  const target =
    event.target as HTMLSelectElement

  const newRole =
    target.value as UserRole

  if (newRole === user.role) {
    return
  }

  actionLoading.value = true
  errorMessage.value = null

  try {
    const updated =
      await updateUserRole(
        user.id,
        {
          role: newRole,
        },
      )

    Object.assign(
      user,
      updated,
    )
  } catch {
    target.value = user.role

    errorMessage.value =
      'Impossible de modifier le rôle.'
  } finally {
    actionLoading.value = false
  }
}

async function toggleStatus(
  user: UserResponse,
) {
  actionLoading.value = true
  errorMessage.value = null

  try {
    const updated =
      await updateUserStatus(
        user.id,
        {
          enabled:
            !user.enabled,
        },
      )

    Object.assign(
      user,
      updated,
    )
  } catch {
    errorMessage.value =
      'Impossible de modifier le statut.'
  } finally {
    actionLoading.value = false
  }
}

async function removeUser(
  user: UserResponse,
) {
  const confirmed =
    window.confirm(
      `Supprimer ${user.firstName} ${user.lastName} ?`,
    )

  if (!confirmed) {
    return
  }

  actionLoading.value = true
  errorMessage.value = null

  try {
    await deleteUser(user.id)

    await loadUsers()
  } catch {
    errorMessage.value =
      'Impossible de supprimer cet utilisateur.'
  } finally {
    actionLoading.value = false
  }
}

async function restore(
  user: UserResponse,
) {
  actionLoading.value = true
  errorMessage.value = null

  try {
    const updated =
      await restoreUser(user.id)

    Object.assign(
      user,
      updated,
    )
  } catch {
    errorMessage.value =
      'Impossible de restaurer cet utilisateur.'
  } finally {
    actionLoading.value = false
  }
}

function formatDate(
  value: string,
) {
  return new Intl.DateTimeFormat(
    'fr-FR',
    {
      dateStyle: 'medium',
      timeStyle: 'short',
    },
  ).format(
    new Date(value),
  )
}

onMounted(loadUsers)
</script>

<template>
  <section class="users-page">
    <header class="page-header">
      <div>
        <h2>Utilisateurs</h2>

        <p>
          Administration des comptes et
          des rôles SmartDeploy.
        </p>
      </div>

      <button
        type="button"
        class="primary-button"
        @click="
          showCreateForm =
            !showCreateForm
        "
      >
        + Nouvel utilisateur
      </button>
    </header>

    <div
      v-if="errorMessage"
      class="error-message"
    >
      {{ errorMessage }}
    </div>

    <section
      v-if="showCreateForm"
      class="panel create-panel"
    >
      <div class="panel-header">
        <div>
          <h3>
            Créer un utilisateur
          </h3>

          <p>
            Le compte sera immédiatement
            disponible après création.
          </p>
        </div>
      </div>

      <form
        class="create-form"
        @submit.prevent="
          submitCreateUser
        "
      >
        <label>
          <span>Prénom</span>

          <input
            v-model="firstName"
            required
            maxlength="100"
          />
        </label>

        <label>
          <span>Nom</span>

          <input
            v-model="lastName"
            required
            maxlength="100"
          />
        </label>

        <label>
          <span>Email</span>

          <input
            v-model="email"
            type="email"
            required
            maxlength="180"
          />
        </label>

        <label>
          <span>Mot de passe</span>

          <input
            v-model="password"
            type="password"
            required
            minlength="8"
          />
        </label>

        <label>
          <span>Rôle</span>

          <select
            v-model="selectedRole"
          >
            <option
              v-for="role in roles"
              :key="role"
              :value="role"
            >
              {{ role }}
            </option>
          </select>
        </label>

        <div class="form-actions">
          <button
            type="button"
            class="secondary-button"
            :disabled="actionLoading"
            @click="
              showCreateForm = false
            "
          >
            Annuler
          </button>

          <button
            type="submit"
            class="primary-button"
            :disabled="actionLoading"
          >
            {{
              actionLoading
                ? 'Création...'
                : 'Créer'
            }}
          </button>
        </div>
      </form>
    </section>

    <section class="panel">
      <div class="toolbar">
        <form
          class="search-form"
          @submit.prevent="
            submitSearch
          "
        >
          <input
            v-model="search"
            type="search"
            placeholder="Rechercher par nom ou email..."
          />

          <button
            type="submit"
            class="secondary-button"
          >
            Rechercher
          </button>
        </form>

        <span class="total">
          {{ totalElements }}
          utilisateur(s)
        </span>
      </div>

      <div
        v-if="loading"
        class="state-message"
      >
        Chargement...
      </div>

      <div
        v-else-if="
          users.length === 0
        "
        class="state-message"
      >
        Aucun utilisateur trouvé.
      </div>

      <div
        v-else
        class="table-wrapper"
      >
        <table>
          <thead>
            <tr>
              <th>Utilisateur</th>
              <th>Email</th>
              <th>Rôle</th>
              <th>Statut</th>
              <th>Créé le</th>
              <th>Actions</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="user in users"
              :key="user.id"
            >
              <td>
                <div class="user-cell">
                  <div class="avatar">
                    {{
                      user.firstName
                        .charAt(0)
                        .toUpperCase()
                    }}{{
                      user.lastName
                        .charAt(0)
                        .toUpperCase()
                    }}
                  </div>

                  <div>
                    <strong>
                      {{
                        user.firstName
                      }}
                      {{
                        user.lastName
                      }}
                    </strong>

                    <small>
                      ID #{{ user.id }}
                    </small>
                  </div>
                </div>
              </td>

              <td>
                {{ user.email }}
              </td>

              <td>
                <select
                  class="role-select"
                  :value="user.role"
                  :disabled="
                    actionLoading
                    || user.deleted
                  "
                  @change="
                    changeRole(
                      user,
                      $event,
                    )
                  "
                >
                  <option
                    v-for="role in roles"
                    :key="role"
                    :value="role"
                  >
                    {{ role }}
                  </option>
                </select>
              </td>

              <td>
                <span
                  v-if="user.deleted"
                  class="status-badge deleted"
                >
                  Supprimé
                </span>

                <span
                  v-else
                  class="status-badge"
                  :class="{
                    active:
                      user.enabled,
                    inactive:
                      !user.enabled,
                  }"
                >
                  {{
                    user.enabled
                      ? 'Actif'
                      : 'Désactivé'
                  }}
                </span>
              </td>

              <td>
                {{
                  formatDate(
                    user.createdAt,
                  )
                }}
              </td>

              <td>
                <div class="actions">
                  <button
                    v-if="!user.deleted"
                    type="button"
                    class="action-button"
                    :class="
                      user.enabled
                        ? 'warning'
                        : 'success'
                    "
                    :disabled="
                      actionLoading
                    "
                    @click="
                      toggleStatus(user)
                    "
                  >
                    {{
                      user.enabled
                        ? 'Désactiver'
                        : 'Activer'
                    }}
                  </button>

                  <button
                    v-if="!user.deleted"
                    type="button"
                    class="action-button danger"
                    :disabled="
                      actionLoading
                    "
                    @click="
                      removeUser(user)
                    "
                  >
                    Supprimer
                  </button>

                  <button
                    v-else
                    type="button"
                    class="action-button success"
                    :disabled="
                      actionLoading
                    "
                    @click="
                      restore(user)
                    "
                  >
                    Restaurer
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer class="pagination">
        <button
          type="button"
          class="secondary-button"
          :disabled="
            !hasPreviousPage
            || loading
          "
          @click="previousPage"
        >
          Précédent
        </button>

        <span>
          Page
          {{ page + 1 }}
          sur
          {{
            Math.max(
              totalPages,
              1,
            )
          }}
        </span>

        <button
          type="button"
          class="secondary-button"
          :disabled="
            !hasNextPage
            || loading
          "
          @click="nextPage"
        >
          Suivant
        </button>
      </footer>
    </section>
  </section>
</template>

<style scoped>
.users-page {
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

.page-header h2,
.panel h3 {
  margin: 0;
  color: #0f172a;
}

.page-header h2 {
  font-size: 26px;
}

.page-header p,
.panel-header p {
  margin: 7px 0 0;
  color: #64748b;
}

.panel {
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 14px;
  background: #ffffff;
  box-shadow:
    0 5px 18px
    rgba(15, 23, 42, 0.04);
}

.create-panel {
  padding: 22px;
}

.create-form {
  display: grid;
  grid-template-columns:
    repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 22px;
}

.create-form label {
  display: flex;
  flex-direction: column;
  gap: 7px;
}

.create-form label span {
  color: #334155;
  font-size: 13px;
  font-weight: 700;
}

input,
select {
  min-height: 42px;
  box-sizing: border-box;
  padding: 0 12px;
  border: 1px solid #cbd5e1;
  border-radius: 9px;
  outline: none;
  background: #ffffff;
  color: #0f172a;
  font: inherit;
}

input:focus,
select:focus {
  border-color: #2563eb;
}

.form-actions {
  display: flex;
  align-items: end;
  justify-content: flex-end;
  gap: 10px;
}

.primary-button,
.secondary-button,
.action-button {
  border-radius: 9px;
  cursor: pointer;
  font: inherit;
  font-weight: 700;
}

.primary-button,
.secondary-button {
  min-height: 42px;
  padding: 0 16px;
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

button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.error-message {
  padding: 13px 16px;
  border: 1px solid #fecaca;
  border-radius: 10px;
  background: #fef2f2;
  color: #b91c1c;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 20px;
  border-bottom: 1px solid #e2e8f0;
}

.search-form {
  display: flex;
  width: min(520px, 100%);
  gap: 10px;
}

.search-form input {
  flex: 1;
}

.total {
  color: #64748b;
  font-size: 13px;
  font-weight: 600;
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
  padding: 15px 18px;
  border-bottom: 1px solid #e2e8f0;
  text-align: left;
  vertical-align: middle;
}

th {
  background: #f8fafc;
  color: #64748b;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.45px;
  text-transform: uppercase;
}

td {
  color: #334155;
  font-size: 14px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 11px;
}

.user-cell > div:last-child {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.user-cell small {
  color: #94a3b8;
}

.avatar {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 10px;
  background: #eff6ff;
  color: #2563eb;
  font-size: 12px;
  font-weight: 800;
}

.role-select {
  min-height: 36px;
  padding: 0 9px;
  font-size: 13px;
  font-weight: 700;
}

.status-badge {
  display: inline-flex;
  padding: 5px 9px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
}

.status-badge.active {
  background: #dcfce7;
  color: #15803d;
}

.status-badge.inactive {
  background: #fef3c7;
  color: #b45309;
}

.status-badge.deleted {
  background: #fee2e2;
  color: #b91c1c;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.action-button {
  min-height: 34px;
  padding: 0 10px;
  border: 1px solid #cbd5e1;
  background: #ffffff;
  color: #334155;
  font-size: 12px;
}

.action-button.warning {
  border-color: #fde68a;
  background: #fffbeb;
  color: #b45309;
}

.action-button.success {
  border-color: #bbf7d0;
  background: #f0fdf4;
  color: #15803d;
}

.action-button.danger {
  border-color: #fecaca;
  background: #fef2f2;
  color: #dc2626;
}

.state-message {
  padding: 45px 20px;
  color: #64748b;
  text-align: center;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 14px;
  padding: 16px 20px;
}

.pagination span {
  color: #64748b;
  font-size: 13px;
}

@media (max-width: 850px) {
  .page-header,
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .create-form {
    grid-template-columns: 1fr;
  }

  .search-form {
    width: 100%;
  }
}
</style>