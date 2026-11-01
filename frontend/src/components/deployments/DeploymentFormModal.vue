<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { getVersionsByApplication } from '@/api/versions.api'
import type { ApplicationResponse } from '@/types/application'
import type { EnvironmentResponse } from '@/types/environment'
import type { VersionResponse } from '@/types/version'
import type { CreateDeploymentRequest, KubernetesApplicationRequest } from '@/types/deployment'

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

type Architecture = 'BACKEND' | 'FULLSTACK_EXTERNAL' | 'FULLSTACK_MANAGED'
type VersionWithImages = VersionResponse & {
  frontendDockerImage?: string | null
  backendDockerImage?: string | null
  dockerImages?: { frontend?: string | null; backend?: string | null } | null
}
type ApplicationWithPorts = ApplicationResponse & {
  frontendPort?: number | null
  backendPort?: number | null
}

const form = reactive({
  applicationId: 0,
  versionId: 0,
  environmentId: 0,
  architecture: 'BACKEND' as Architecture,
  frontendReplicas: 2,
  backendReplicas: 2,
  databaseName: '',
  databaseUser: '',
  passwordSecretName: '',
  passwordSecretKey: 'password',
  externalHost: '',
  databasePort: 5432,
  storageGi: 5,
})
const versions = ref<VersionResponse[]>([])
const loadingVersions = ref(false)
const validationError = ref('')
const loadError = ref('')
let versionRequestSequence = 0

const selectedApplication = computed(() =>
  props.applications.find(a => a.id === form.applicationId) as ApplicationWithPorts | undefined,
)
const selectedVersion = computed(() =>
  versions.value.find(v => v.id === form.versionId) as VersionWithImages | undefined,
)
const filteredEnvironments = computed(() =>
  props.environments.filter(e => e.applicationId === form.applicationId && e.active),
)
const selectedEnvironment = computed(() =>
  filteredEnvironments.value.find(e => e.id === form.environmentId),
)
const isFullStack = computed(() => form.architecture !== 'BACKEND')
const isManaged = computed(() => form.architecture === 'FULLSTACK_MANAGED')
const backendImage = computed(() =>
  selectedVersion.value?.backendDockerImage?.trim()
  || selectedVersion.value?.dockerImages?.backend?.trim()
  || selectedVersion.value?.dockerImage?.trim()
  || '',
)
const frontendImage = computed(() =>
  selectedVersion.value?.frontendDockerImage?.trim()
  || selectedVersion.value?.dockerImages?.frontend?.trim()
  || '',
)
const backendPort = computed(() => selectedApplication.value?.backendPort ?? 8080)
const frontendPort = computed(() => selectedApplication.value?.frontendPort ?? 80)

function k8sName(value: string): string {
  return value.toLowerCase().normalize('NFKD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9-]/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '')
}
const backendName = computed(() => `${k8sName(selectedApplication.value?.name || '')}-backend`)
const frontendName = computed(() => `${k8sName(selectedApplication.value?.name || '')}-frontend`)
const namespace = computed(() => selectedEnvironment.value?.namespace?.trim() || '')
const validK8sName = (value: string) =>
  value.length <= 63 && /^[a-z0-9]([-a-z0-9]*[a-z0-9])?$/.test(value)

function resetForm() {
  versionRequestSequence++
  Object.assign(form, {
    applicationId: 0, versionId: 0, environmentId: 0,
    architecture: 'BACKEND', frontendReplicas: 2, backendReplicas: 2,
    databaseName: '', databaseUser: '', passwordSecretName: '',
    passwordSecretKey: 'password', externalHost: '', databasePort: 5432, storageGi: 5,
  })
  versions.value = []
  validationError.value = ''
  loadError.value = ''
  loadingVersions.value = false
}
watch(() => props.open, open => { if (open) resetForm() })
watch(() => form.applicationId, async applicationId => {
  const sequence = ++versionRequestSequence
  form.versionId = 0
  form.environmentId = 0
  versions.value = []
  loadError.value = ''
  if (!applicationId) { loadingVersions.value = false; return }
  loadingVersions.value = true
  try {
    const result = await getVersionsByApplication(applicationId)
    if (sequence === versionRequestSequence) versions.value = result
  } catch {
    if (sequence === versionRequestSequence) loadError.value = 'Impossible de charger les versions.'
  } finally {
    if (sequence === versionRequestSequence) loadingVersions.value = false
  }
})
function closeModal() { if (!props.loading) emit('close') }
function submitForm() {
  validationError.value = ''
  if (!selectedApplication.value || !selectedVersion.value || !selectedEnvironment.value) {
    validationError.value = 'Sélectionnez une application, une version et un environnement valides.'
    return
  }
  if (selectedVersion.value.buildStatus !== 'SUCCESS') {
    validationError.value = 'Seule une version avec un build SUCCESS peut être déployée.'
    return
  }
  if (!validK8sName(namespace.value) || !validK8sName(backendName.value)
      || (isFullStack.value && !validK8sName(frontendName.value))) {
    validationError.value = 'Le namespace ou le nom généré est invalide. Vérifiez la configuration de l’application et de l’environnement.'
    return
  }
  if (!backendImage.value || (isFullStack.value && !frontendImage.value)) {
    validationError.value = isFullStack.value
      ? 'Les images frontend et backend doivent être enregistrées dans la version avant le déploiement Full Stack.'
      : 'Aucune image Docker backend n’est enregistrée pour cette version.'
    return
  }
  if (![form.backendReplicas, ...(isFullStack.value ? [form.frontendReplicas] : [])]
    .every(n => Number.isInteger(n) && n >= 1 && n <= 10)) {
    validationError.value = 'Le nombre de replicas doit être compris entre 1 et 10.'
    return
  }
  if (![backendPort.value, ...(isFullStack.value ? [frontendPort.value] : []), form.databasePort]
    .every(n => Number.isInteger(n) && n >= 1 && n <= 65535)) {
    validationError.value = 'Les ports configurés sont invalides.'
    return
  }
  if (!isManaged.value && !form.externalHost.trim()) {
    validationError.value = 'Renseignez l’hôte de la base PostgreSQL externe.'
    return
  }
  if (isManaged.value && (!Number.isInteger(form.storageGi) || form.storageGi < 1)) {
    validationError.value = 'Le stockage PostgreSQL doit être supérieur ou égal à 1 Gi.'
    return
  }
  if (![form.databaseName, form.databaseUser, form.passwordSecretName, form.passwordSecretKey]
    .every(v => v.trim())) {
    validationError.value = 'Complétez la configuration PostgreSQL et les références du Secret.'
    return
  }
  const kubernetesConfig: KubernetesApplicationRequest = {
    namespace: namespace.value,
    frontend: isFullStack.value ? {
      name: frontendName.value, image: frontendImage.value,
      replicas: form.frontendReplicas, containerPort: frontendPort.value,
    } : null,
    backend: {
      name: backendName.value, image: backendImage.value,
      replicas: form.backendReplicas, containerPort: backendPort.value,
    },
    database: {
      mode: isManaged.value ? 'MANAGED' : 'EXTERNAL',
      databaseName: form.databaseName.trim(), username: form.databaseUser.trim(),
      passwordSecretName: form.passwordSecretName.trim(),
      passwordSecretKey: form.passwordSecretKey.trim(),
      externalHost: isManaged.value ? null : form.externalHost.trim(),
      port: form.databasePort, storageGi: form.storageGi,
    },
    ingress: null,
  }
  emit('submit', { versionId: form.versionId, environmentId: form.environmentId, kubernetesConfig })
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="modal-overlay" @click.self="closeModal">
      <section class="modal-card" role="dialog" aria-modal="true" aria-labelledby="deploy-title">
        <header class="modal-header">
          <div><p class="eyebrow">Déploiement</p><h2 id="deploy-title">Nouveau déploiement</h2></div>
          <button type="button" class="close-button" :disabled="loading" aria-label="Fermer" @click="closeModal">×</button>
        </header>
        <form @submit.prevent="submitForm">
          <div class="form-group">
            <label for="sd-app">Application</label>
            <select id="sd-app" v-model.number="form.applicationId" :disabled="loading" required>
              <option :value="0" disabled>Sélectionner une application</option>
              <option v-for="app in applications" :key="app.id" :value="app.id">{{ app.name }}</option>
            </select>
          </div>
          <div class="form-group">
            <label for="sd-version">Version</label>
            <select id="sd-version" v-model.number="form.versionId" :disabled="loading || loadingVersions || !form.applicationId" required>
              <option :value="0" disabled>{{ loadingVersions ? 'Chargement...' : 'Sélectionner une version' }}</option>
              <option v-for="version in versions" :key="version.id" :value="version.id" :disabled="version.buildStatus !== 'SUCCESS'">
                {{ version.version }} — {{ version.buildStatus }}
              </option>
            </select>
            <small v-if="loadError" class="validation-error">{{ loadError }}</small>
            <small v-else-if="form.applicationId && !loadingVersions && versions.length === 0" class="field-info">Aucune version disponible.</small>
          </div>
          <div class="form-group">
            <label for="sd-env">Environnement</label>
            <select id="sd-env" v-model.number="form.environmentId" :disabled="loading || !form.applicationId" required>
              <option :value="0" disabled>Sélectionner un environnement</option>
              <option v-for="env in filteredEnvironments" :key="env.id" :value="env.id">{{ env.name }} — {{ env.type }}</option>
            </select>
            <small v-if="form.applicationId && !filteredEnvironments.length" class="field-info">Aucun environnement actif.</small>
          </div>
          <div class="k8s-section">
            <h3>Configuration Kubernetes</h3>
            <div class="form-group">
              <label for="sd-arch">Architecture</label>
              <select id="sd-arch" v-model="form.architecture" :disabled="loading">
                <option value="BACKEND">Backend + PostgreSQL externe</option>
                <option value="FULLSTACK_EXTERNAL">Full Stack + PostgreSQL externe</option>
                <option value="FULLSTACK_MANAGED">Full Stack + PostgreSQL sur K3s</option>
              </select>
            </div>
            <p v-if="selectedEnvironment" class="technical-info">Namespace : <strong>{{ namespace }}</strong> (environnement)</p>
            <p v-if="selectedVersion" class="technical-info">Image backend : <strong>{{ backendImage || 'Non disponible' }}</strong></p>
            <p v-if="isFullStack && selectedVersion" class="technical-info">Image frontend : <strong>{{ frontendImage || 'Non disponible : métadonnée à ajouter au backend' }}</strong></p>
            <div class="k8s-grid">
              <div class="form-group"><label for="sd-back-replicas">Replicas backend</label><input id="sd-back-replicas" v-model.number="form.backendReplicas" type="number" min="1" max="10" required /></div>
              <div v-if="isFullStack" class="form-group"><label for="sd-front-replicas">Replicas frontend</label><input id="sd-front-replicas" v-model.number="form.frontendReplicas" type="number" min="1" max="10" required /></div>
            </div>
            <h4>PostgreSQL — {{ isManaged ? 'sur K3s' : 'externe' }}</h4>
            <div class="k8s-grid">
              <div class="form-group"><label for="sd-db">Base de données</label><input id="sd-db" v-model.trim="form.databaseName" required /></div>
              <div class="form-group"><label for="sd-user">Utilisateur</label><input id="sd-user" v-model.trim="form.databaseUser" required /></div>
            </div>
            <div v-if="!isManaged" class="form-group"><label for="sd-host">Hôte PostgreSQL externe</label><input id="sd-host" v-model.trim="form.externalHost" placeholder="postgres.internal" required /></div>
            <div class="k8s-grid">
              <div class="form-group"><label for="sd-db-port">Port PostgreSQL</label><input id="sd-db-port" v-model.number="form.databasePort" type="number" min="1" max="65535" required /></div>
              <div v-if="isManaged" class="form-group"><label for="sd-storage">Stockage PVC (Gi)</label><input id="sd-storage" v-model.number="form.storageGi" type="number" min="1" required /></div>
            </div>
            <div class="k8s-grid">
              <div class="form-group"><label for="sd-secret">Secret Kubernetes existant</label><input id="sd-secret" v-model.trim="form.passwordSecretName" required /></div>
              <div class="form-group"><label for="sd-secret-key">Clé du Secret</label><input id="sd-secret-key" v-model.trim="form.passwordSecretKey" required /></div>
            </div>
            <small class="field-info">Le Secret doit exister dans le namespace sélectionné. Aucun mot de passe en clair n'est envoyé.</small>
          </div>
          <p v-if="validationError" class="validation-error" role="alert">{{ validationError }}</p>
          <footer class="modal-actions">
            <button type="button" class="cancel-button" :disabled="loading" @click="closeModal">Annuler</button>
            <button type="submit" class="submit-button" :disabled="loading || loadingVersions">{{ loading ? 'Déploiement...' : 'Déployer' }}</button>
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

.k8s-section { display: grid; gap: 14px; padding-top: 12px; border-top: 1px solid #e2e8f0; }
.k8s-section h3, .k8s-section h4 { margin: 8px 0 0; }
.k8s-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.k8s-section input { width: 100%; min-height: 43px; box-sizing: border-box; padding: 0 12px; border: 1px solid #cbd5e1; border-radius: 9px; }
.modal-card { max-height: 90vh; overflow-y: auto; }
</style>