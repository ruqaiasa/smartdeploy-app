<script setup lang="ts">
import {
  computed,
  reactive,
  ref,
  watch,
} from 'vue'

import type {
  ApplicationResponse,
} from '@/types/application'

import type {
  CreateVersionRequest,
  UpdateVersionRequest,
  VersionResponse,
} from '@/types/version'

const props = defineProps<{
  open: boolean

  version?: VersionResponse | null

  applications: ApplicationResponse[]

  loading?: boolean
}>()

const emit = defineEmits<{
  close: []

  submit: [
    request:
      | CreateVersionRequest
      | UpdateVersionRequest,
  ]
}>()

const form = reactive({
  applicationId: 0,

  version: '',

  gitBranch: '',

  gitCommit: '',

  dockerImage: '',

  artifactPath: '',

  buildLogsUrl: '',
})

const validationError =
  ref('')

const isEditMode =
  computed(() => {
    return props.version !== null
      && props.version !== undefined
  })

const modalTitle =
  computed(() => {
    return isEditMode.value
      ? 'Modifier la version'
      : 'Nouvelle version'
  })

const submitLabel =
  computed(() => {
    if (props.loading) {
      return isEditMode.value
        ? 'Modification...'
        : 'Création...'
    }

    return isEditMode.value
      ? 'Enregistrer'
      : 'Créer la version'
  })

watch(
  () => [
    props.open,
    props.version,
  ],
  () => {
    validationError.value = ''

    if (props.version) {
      form.applicationId =
        props.version.applicationId

      form.version =
        props.version.version

      form.gitBranch =
        props.version.gitBranch

      form.gitCommit =
        props.version.gitCommit ?? ''

      form.dockerImage =
        props.version.dockerImage ?? ''

      form.artifactPath =
        props.version.artifactPath ?? ''

      form.buildLogsUrl =
        props.version.buildLogsUrl ?? ''

      return
    }

    resetForm()
  },
  {
    immediate: true,
  },
)

function resetForm() {
  form.applicationId = 0

  form.version = ''
  form.gitBranch = ''

  form.gitCommit = ''
  form.dockerImage = ''

  form.artifactPath = ''
  form.buildLogsUrl = ''
}

function closeModal() {
  if (props.loading) {
    return
  }

  validationError.value = ''

  emit('close')
}

function submitForm() {
  validationError.value = ''

  const version =
    form.version.trim()

  const gitBranch =
    form.gitBranch.trim()

  if (
    !isEditMode.value
    && !form.applicationId
  ) {
    validationError.value =
      'Veuillez sélectionner une application.'

    return
  }

  if (!version) {
    validationError.value =
      'Le numéro de version est obligatoire.'

    return
  }

  if (!gitBranch) {
    validationError.value =
      'La branche Git est obligatoire.'

    return
  }

  const commonData = {
    version,

    gitBranch,

    gitCommit:
      form.gitCommit.trim(),

    dockerImage:
      form.dockerImage.trim(),

    artifactPath:
      form.artifactPath.trim(),

    buildLogsUrl:
      form.buildLogsUrl.trim(),
  }

  if (isEditMode.value) {
    emit(
      'submit',
      commonData,
    )

    return
  }

  emit(
    'submit',
    {
      applicationId:
        form.applicationId,

      ...commonData,
    },
  )
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
              Version
            </p>

            <h2>
              {{ modalTitle }}
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
              :disabled="
                loading
                || isEditMode
              "
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

          <div class="form-row">
            <div class="form-group">
              <label>
                Version
              </label>

              <input
                v-model="form.version"
                type="text"
                maxlength="50"
                placeholder="1.0.0"
                :disabled="loading"
              />
            </div>

            <div class="form-group">
              <label>
                Branche Git
              </label>

              <input
                v-model="form.gitBranch"
                type="text"
                maxlength="150"
                placeholder="main"
                :disabled="loading"
              />
            </div>
          </div>

          <div class="form-group">
            <label>
              Commit Git
            </label>

            <input
              v-model="form.gitCommit"
              type="text"
              maxlength="100"
              placeholder="a83fd42..."
              :disabled="loading"
            />
          </div>

          <div class="form-group">
            <label>
              Image Docker
            </label>

            <input
              v-model="form.dockerImage"
              type="text"
              maxlength="500"
              placeholder="smartdeploy/backend:1.0.0"
              :disabled="loading"
            />
          </div>

          <div class="form-group">
            <label>
              Chemin de l’artefact
            </label>

            <input
              v-model="form.artifactPath"
              type="text"
              maxlength="500"
              placeholder="target/app.jar"
              :disabled="loading"
            />
          </div>

          <div class="form-group">
            <label>
              URL des logs
            </label>

            <input
              v-model="form.buildLogsUrl"
              type="url"
              maxlength="1000"
              placeholder="http://localhost:8080/job/..."
              :disabled="loading"
            />
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

  background:
    rgb(15 23 42 / 55%);

  backdrop-filter: blur(3px);
}

.modal-card {
  width: min(100%, 650px);

  max-height:
    calc(100vh - 48px);

  overflow-y: auto;

  border:
    1px solid #e2e8f0;

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

.modal-header h2 {
  margin: 0;

  color: #0f172a;

  font-size: 23px;
}

.eyebrow {
  margin: 0 0 5px;

  color: #2563eb;

  font-size: 11px;
  font-weight: 800;

  text-transform: uppercase;
}

.close-button {
  width: 36px;
  height: 36px;

  border:
    1px solid #e2e8f0;

  border-radius: 9px;

  background: #ffffff;

  font-size: 22px;
}

form {
  display: flex;
  flex-direction: column;

  gap: 18px;

  padding: 25px 26px;
}

.form-row {
  display: grid;

  grid-template-columns:
    repeat(2, 1fr);

  gap: 14px;
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
select {
  width: 100%;

  min-height: 47px;

  padding: 0 14px;

  border:
    1px solid #cbd5e1;

  border-radius: 10px;

  outline: none;

  background: #ffffff;

  color: #0f172a;
}

input:focus,
select:focus {
  border-color: #2563eb;

  box-shadow:
    0 0 0 4px
    rgb(37 99 235 / 10%);
}

input:disabled,
select:disabled {
  cursor: not-allowed;

  background: #f8fafc;

  opacity: 0.7;
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

  margin-top: 5px;
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

@media (max-width: 600px) {
  .form-row {
    grid-template-columns: 1fr;
  }
}
</style>