<script setup lang="ts">
import {
  computed,
  onMounted,
  reactive,
  ref,
  watch,
} from 'vue'

import {
  getVersionsByApplication,
} from '@/api/versions.api'

import type {
  ApplicationResponse,
} from '@/types/application'

import type {
  EnvironmentResponse,
} from '@/types/environment'

import type {
  VersionResponse,
} from '@/types/version'

import type {
  CreateDeploymentRequest,
} from '@/types/deployment'

const props = defineProps<{
  open: boolean
  applications: ApplicationResponse[]
  environments: EnvironmentResponse[]
  loading?: boolean
}>()

const emit = defineEmits<{
  close: []
  submit: [request: CreateDeploymentRequest]
}>()

const form = reactive({
  applicationId: 0,
  versionId: 0,
  environmentId: 0,
})

const versions =
  ref<VersionResponse[]>([])

const loadingVersions =
  ref(false)

const validationError =
  ref('')

const filteredEnvironments =
  computed(() => {
    if (!form.applicationId) {
      return []
    }

    return props.environments.filter(
      (environment) =>
        environment.applicationId
        === form.applicationId
        && environment.active,
    )
  })

watch(
  () => props.open,
  (open) => {
    if (open) {
      resetForm()
    }
  },
)

watch(
  () => form.applicationId,
  async (applicationId) => {
    form.versionId = 0
    form.environmentId = 0
    versions.value = []

    if (!applicationId) {
      return
    }

    loadingVersions.value = true

    try {
      versions.value =
        await getVersionsByApplication(
          applicationId,
        )
    } finally {
      loadingVersions.value = false
    }
  },
)

function resetForm() {
  validationError.value = ''

  form.applicationId = 0
  form.versionId = 0
  form.environmentId = 0

  versions.value = []
}

function closeModal() {
  if (props.loading) {
    return
  }

  emit('close')
}

function submitForm() {
  validationError.value = ''

  if (!form.applicationId) {
    validationError.value =
      'Veuillez sélectionner une application.'

    return
  }

  if (!form.versionId) {
    validationError.value =
      'Veuillez sélectionner une version.'

    return
  }

  if (!form.environmentId) {
    validationError.value =
      'Veuillez sélectionner un environnement.'

    return
  }

  emit('submit', {
    versionId: form.versionId,
    environmentId: form.environmentId,
  })
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="modal-overlay"
      @click.self="closeModal"
    >
      <section class="modal-card">
        <header class="modal-header">
          <div>
            <p class="eyebrow">
              Déploiement
            </p>

            <h2>
              Nouveau déploiement
            </h2>
          </div>

          <button
            type="button"
            class="close-button"
            :disabled="loading"
            @click="closeModal"
          >
            ×
          </button>
        </header>

        <form
          @submit.prevent="submitForm"
        >
          <div class="form-group">
            <label>
              Application
            </label>

            <select
              v-model.number="
                form.applicationId
              "
              :disabled="loading"
            >
              <option
                :value="0"
                disabled
              >
                Sélectionner une application
              </option>

              <option
                v-for="
                  application
                  in applications
                "
                :key="application.id"
                :value="application.id"
              >
                {{ application.name }}
              </option>
            </select>
          </div>

          <div class="form-group">
            <label>
              Version
            </label>

            <select
              v-model.number="
                form.versionId
              "
              :disabled="
                loading
                || loadingVersions
                || !form.applicationId
              "
            >
              <option
                :value="0"
                disabled
              >
                {{
                  loadingVersions
                    ? 'Chargement...'
                    : 'Sélectionner une version'
                }}
              </option>

              <option
                v-for="version in versions"
                :key="version.id"
                :value="version.id"
              >
                {{ version.version }}
                — {{ version.buildStatus }}
              </option>
            </select>

            <small
              v-if="
                form.applicationId
                && !loadingVersions
                && versions.length === 0
              "
              class="field-info"
            >
              Aucune version disponible
              pour cette application.
            </small>
          </div>

          <div class="form-group">
            <label>
              Environnement
            </label>

            <select
              v-model.number="
                form.environmentId
              "
              :disabled="
                loading
                || !form.applicationId
              "
            >
              <option
                :value="0"
                disabled
              >
                Sélectionner un environnement
              </option>

              <option
                v-for="
                  environment
                  in filteredEnvironments
                "
                :key="environment.id"
                :value="environment.id"
              >
                {{ environment.name }}
                — {{ environment.type }}
              </option>
            </select>

            <small
              v-if="
                form.applicationId
                && filteredEnvironments.length === 0
              "
              class="field-info"
            >
              Aucun environnement actif
              pour cette application.
            </small>
          </div>

          <p
            v-if="validationError"
            class="validation-error"
          >
            {{ validationError }}
          </p>

          <footer class="modal-actions">
            <button
              type="button"
              class="cancel-button"
              :disabled="loading"
              @click="closeModal"
            >
              Annuler
            </button>

            <button
              type="submit"
              class="submit-button"
              :disabled="loading"
            >
              {{
                loading
                  ? 'Déploiement...'
                  : 'Déployer'
              }}
            </button>
          </footer>
        </form>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;

  display: grid;
  place-items: center;

  padding: 24px;

  background:
    rgb(15 23 42 / 55%);

  backdrop-filter: blur(3px);
}

.modal-card {
  width: min(100%, 560px);

  border: 1px solid #e2e8f0;
  border-radius: 18px;

  background: #ffffff;

  box-shadow:
    0 24px 70px
    rgb(15 23 42 / 25%);
}

.modal-header {
  display: flex;

  align-items: flex-start;
  justify-content: space-between;

  padding: 24px 26px;

  border-bottom:
    1px solid #e2e8f0;
}

.eyebrow {
  margin: 0 0 5px;

  color: #2563eb;

  font-size: 11px;
  font-weight: 800;

  text-transform: uppercase;
}

.modal-header h2 {
  margin: 0;

  color: #0f172a;

  font-size: 23px;
}

.close-button {
  width: 36px;
  height: 36px;

  border:
    1px solid #e2e8f0;

  border-radius: 9px;

  background: #ffffff;

  color: #64748b;

  font-size: 22px;
}

form {
  display: flex;
  flex-direction: column;

  gap: 19px;

  padding: 25px 26px 27px;
}

.form-group {
  display: flex;
  flex-direction: column;

  gap: 8px;
}

label {
  color: #334155;

  font-size: 13px;
  font-weight: 700;
}

select {
  width: 100%;

  min-height: 47px;

  padding: 0 14px;

  border: 1px solid #cbd5e1;
  border-radius: 10px;

  outline: none;

  background: #ffffff;
  color: #0f172a;
}

select:focus {
  border-color: #2563eb;

  box-shadow:
    0 0 0 4px
    rgb(37 99 235 / 10%);
}

select:disabled {
  cursor: not-allowed;

  background: #f8fafc;

  opacity: 0.7;
}

.field-info {
  color: #b45309;

  font-size: 11px;
}

.validation-error {
  margin: 0;

  padding: 11px 13px;

  border:
    1px solid #fecaca;

  border-radius: 9px;

  background: #fef2f2;

  color: #b91c1c;

  font-size: 12px;
}

.modal-actions {
  display: flex;

  justify-content: flex-end;

  gap: 11px;

  margin-top: 4px;
}

.cancel-button,
.submit-button {
  min-height: 42px;

  padding: 0 16px;

  border-radius: 10px;

  font-weight: 700;
}

.cancel-button {
  border:
    1px solid #cbd5e1;

  background: #ffffff;

  color: #334155;
}

.submit-button {
  border: 0;

  background: #2563eb;

  color: #ffffff;
}

.submit-button:hover:not(:disabled) {
  background: #1d4ed8;
}

button:disabled {
  cursor: not-allowed;

  opacity: 0.6;
}
</style>