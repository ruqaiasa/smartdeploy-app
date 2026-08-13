<script setup lang="ts">
import { computed, reactive, watch } from 'vue'

import type {
  ApplicationResponse,
  CreateApplicationRequest,
  Technology,
  UpdateApplicationRequest,
} from '@/types/application'

const props = defineProps<{
  open: boolean
  application?: ApplicationResponse | null
  loading?: boolean
}>()

const emit = defineEmits<{
  close: []
  submit: [
    request:
      | CreateApplicationRequest
      | UpdateApplicationRequest,
  ]
}>()

const technologies: Technology[] = [
  'SPRING_BOOT',
  'ANGULAR',
  'VUE',
  'REACT',
  'NODE_JS',
  'OTHER',
]

const form = reactive({
  name: '',
  description: '',
  repositoryUrl: '',
  technology: 'SPRING_BOOT' as Technology,
})

const validationError = reactive({
  message: '',
})

const isEditMode = computed(() => {
  return props.application !== null &&
    props.application !== undefined
})

const modalTitle = computed(() => {
  return isEditMode.value
    ? 'Modifier l’application'
    : 'Nouvelle application'
})

const submitLabel = computed(() => {
  if (props.loading) {
    return isEditMode.value
      ? 'Modification...'
      : 'Création...'
  }

  return isEditMode.value
    ? 'Enregistrer les modifications'
    : 'Créer l’application'
})

watch(
  () => [props.open, props.application],
  () => {
    validationError.message = ''

    if (props.application) {
      form.name = props.application.name
      form.description =
        props.application.description ?? ''
      form.repositoryUrl =
        props.application.repositoryUrl
      form.technology =
        props.application.technology

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
  form.repositoryUrl = ''
  form.technology = 'SPRING_BOOT'
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
  const normalizedRepositoryUrl =
    form.repositoryUrl.trim()

  if (!normalizedName) {
    validationError.message =
      'Le nom de l’application est obligatoire.'

    return
  }

  if (!normalizedRepositoryUrl) {
    validationError.message =
      'L’URL du dépôt Git est obligatoire.'

    return
  }

  try {
    new URL(normalizedRepositoryUrl)
  } catch {
    validationError.message =
      'L’URL du dépôt Git est invalide.'

    return
  }

  emit('submit', {
    name: normalizedName,
    description: form.description.trim(),
    repositoryUrl: normalizedRepositoryUrl,
    technology: form.technology,
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
              Application
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
            <label for="application-name">
              Nom
            </label>

            <input
              id="application-name"
              v-model="form.name"
              type="text"
              maxlength="120"
              placeholder="SmartDeploy Backend"
              :disabled="loading"
            />
          </div>

          <div class="form-group">
            <label for="application-description">
              Description
            </label>

            <textarea
              id="application-description"
              v-model="form.description"
              rows="4"
              maxlength="500"
              placeholder="Description de l’application"
              :disabled="loading"
            ></textarea>
          </div>

          <div class="form-group">
            <label for="repository-url">
              URL du repository
            </label>

            <input
              id="repository-url"
              v-model="form.repositoryUrl"
              type="url"
              placeholder="https://github.com/company/project"
              :disabled="loading"
            />
          </div>

          <div class="form-group">
            <label for="technology">
              Technologie
            </label>

            <select
              id="technology"
              v-model="form.technology"
              :disabled="loading"
            >
              <option
                v-for="technology in technologies"
                :key="technology"
                :value="technology"
              >
                {{ technology }}
              </option>
            </select>
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
              :disabled="loading"
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

  box-shadow: 0 24px 70px rgb(15 23 42 / 25%);
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
  box-shadow: 0 0 0 4px rgb(37 99 235 / 10%);
}

input:disabled,
textarea:disabled,
select:disabled {
  cursor: not-allowed;
  background: #f8fafc;
  opacity: 0.75;
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