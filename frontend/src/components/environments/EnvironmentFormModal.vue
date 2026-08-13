<script setup lang="ts">
import { computed, reactive, watch } from 'vue'

import type {
  ApplicationResponse,
} from '@/types/application'

import type {
  CreateEnvironmentRequest,
  EnvironmentResponse,
  EnvironmentType,
  UpdateEnvironmentRequest,
} from '@/types/environment'

const props = defineProps<{
  open: boolean
  environment?: EnvironmentResponse | null
  applications: ApplicationResponse[]
  loading?: boolean
}>()

const emit = defineEmits<{
  close: []
  submit: [
    request:
      | CreateEnvironmentRequest
      | UpdateEnvironmentRequest,
  ]
}>()

const environmentTypes: EnvironmentType[] = [
  'DEVELOPMENT',
  'TEST',
  'STAGING',
  'PRODUCTION',
]

const form = reactive({
  name: '',
  description: '',
  type: 'DEVELOPMENT' as EnvironmentType,
  namespace: '',
  clusterUrl: '',
  applicationId: 0,
})

const validationError = reactive({
  message: '',
})

const isEditMode = computed(() => {
  return props.environment !== null
    && props.environment !== undefined
})

const modalTitle = computed(() => {
  return isEditMode.value
    ? 'Modifier l’environnement'
    : 'Nouvel environnement'
})

const submitLabel = computed(() => {
  if (props.loading) {
    return isEditMode.value
      ? 'Modification...'
      : 'Création...'
  }

  return isEditMode.value
    ? 'Enregistrer les modifications'
    : 'Créer l’environnement'
})

watch(
  () => [props.open, props.environment],
  () => {
    validationError.message = ''

    if (props.environment) {
      form.name = props.environment.name
      form.description =
        props.environment.description ?? ''
      form.type = props.environment.type
      form.namespace = props.environment.namespace
      form.clusterUrl =
        props.environment.clusterUrl ?? ''
      form.applicationId =
        props.environment.applicationId

      return
    }

    resetForm()
  },
  {
    immediate: true,
  },
)

function resetForm() {
  form.name = ''
  form.description = ''
  form.type = 'DEVELOPMENT'
  form.namespace = ''
  form.clusterUrl = ''
  form.applicationId = 0
}

function closeModal() {
  if (props.loading) {
    return
  }

  validationError.message = ''
  emit('close')
}

function submitForm() {
  validationError.message = ''

  const normalizedName = form.name.trim()
  const normalizedNamespace =
    form.namespace.trim()
  const normalizedClusterUrl =
    form.clusterUrl.trim()

  if (!form.applicationId) {
    validationError.message =
      'Veuillez sélectionner une application.'

    return
  }

  if (!normalizedName) {
    validationError.message =
      'Le nom de l’environnement est obligatoire.'

    return
  }

  if (!normalizedNamespace) {
    validationError.message =
      'Le namespace Kubernetes est obligatoire.'

    return
  }

  if (normalizedClusterUrl) {
    try {
      new URL(normalizedClusterUrl)
    } catch {
      validationError.message =
        'L’URL du cluster est invalide.'

      return
    }
  }

  emit('submit', {
    name: normalizedName,
    description: form.description.trim(),
    type: form.type,
    namespace: normalizedNamespace,
    clusterUrl: normalizedClusterUrl,
    applicationId: form.applicationId,
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
      <section
        class="modal-card"
        role="dialog"
        aria-modal="true"
        :aria-label="modalTitle"
      >
        <header class="modal-header">
          <div>
            <p class="eyebrow">
              Environnement
            </p>

            <h2>
              {{ modalTitle }}
            </h2>
          </div>

          <button
            type="button"
            class="close-button"
            aria-label="Fermer"
            :disabled="loading"
            @click="closeModal"
          >
            ×
          </button>
        </header>

        <form @submit.prevent="submitForm">
          <div class="form-group">
            <label for="application">
              Application
            </label>

            <select
              id="application"
              v-model.number="form.applicationId"
              :disabled="loading"
            >
              <option
                :value="0"
                disabled
              >
                Sélectionner une application
              </option>

              <option
                v-for="application in applications"
                :key="application.id"
                :value="application.id"
              >
                {{ application.name }}
              </option>
            </select>

            <small
              v-if="applications.length === 0"
              class="field-information"
            >
              Aucune application active disponible.
            </small>
          </div>

          <div class="form-group">
            <label for="environment-name">
              Nom
            </label>

            <input
              id="environment-name"
              v-model="form.name"
              type="text"
              maxlength="120"
              placeholder="Development"
              :disabled="loading"
            />
          </div>

          <div class="form-group">
            <label for="environment-description">
              Description
            </label>

            <textarea
              id="environment-description"
              v-model="form.description"
              rows="4"
              maxlength="500"
              placeholder="Environnement utilisé pour le développement"
              :disabled="loading"
            ></textarea>
          </div>

          <div class="form-group">
            <label for="environment-type">
              Type
            </label>

            <select
              id="environment-type"
              v-model="form.type"
              :disabled="loading"
            >
              <option
                v-for="type in environmentTypes"
                :key="type"
                :value="type"
              >
                {{ type }}
              </option>
            </select>
          </div>

          <div class="form-group">
            <label for="namespace">
              Namespace Kubernetes
            </label>

            <input
              id="namespace"
              v-model="form.namespace"
              type="text"
              placeholder="smartdeploy-dev"
              :disabled="loading"
            />
          </div>

          <div class="form-group">
            <label for="cluster-url">
              URL du cluster
            </label>

            <input
              id="cluster-url"
              v-model="form.clusterUrl"
              type="url"
              placeholder="https://dev.k8s.smartdeploy.local"
              :disabled="loading"
            />
          </div>

          <p
            v-if="validationError.message"
            class="validation-error"
          >
            {{ validationError.message }}
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
              :disabled="
                loading ||
                applications.length === 0
              "
            >
              {{ submitLabel }}
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
  background: rgb(15 23 42 / 55%);
  backdrop-filter: blur(3px);
}

.modal-card {
  width: min(100%, 580px);
  max-height: calc(100vh - 48px);
  overflow-y: auto;

  border: 1px solid #e2e8f0;
  border-radius: 18px;
  background: #ffffff;

  box-shadow:
    0 24px 70px rgb(15 23 42 / 25%);
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;

  padding: 24px 26px;
  border-bottom: 1px solid #e2e8f0;
}

.eyebrow {
  margin: 0 0 5px;

  color: #2563eb;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.09em;
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

  border: 1px solid #e2e8f0;
  border-radius: 9px;
  background: #ffffff;
  color: #64748b;

  font-size: 22px;
  line-height: 1;
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

input,
textarea,
select {
  width: 100%;
  padding: 12px 14px;

  border: 1px solid #cbd5e1;
  border-radius: 10px;
  outline: none;

  background: #ffffff;
  color: #0f172a;

  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

input,
select {
  min-height: 47px;
}

textarea {
  resize: vertical;
}

input:focus,
textarea:focus,
select:focus {
  border-color: #2563eb;

  box-shadow:
    0 0 0 4px rgb(37 99 235 / 10%);
}

input:disabled,
textarea:disabled,
select:disabled {
  cursor: not-allowed;
  background: #f8fafc;
  opacity: 0.75;
}

.field-information {
  color: #b45309;
  font-size: 11px;
}

.validation-error {
  margin: 0;
  padding: 11px 13px;

  border: 1px solid #fecaca;
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
  border: 1px solid #cbd5e1;
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
  opacity: 0.65;
}

@media (max-width: 600px) {
  .modal-overlay {
    padding: 12px;
  }

  .modal-header,
  form {
    padding-right: 18px;
    padding-left: 18px;
  }

  .modal-actions {
    flex-direction: column-reverse;
  }

  .cancel-button,
  .submit-button {
    width: 100%;
  }
}
</style>