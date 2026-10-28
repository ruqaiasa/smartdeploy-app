<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { getVersionsByApplication } from '@/api/versions.api'
import { listKubernetesSecrets, createKubernetesSecret, type KubernetesSecretSummary } from '@/api/kubernetesSecrets.api'
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
  backendBuildContext: '.',
  backendDockerfilePath: 'Dockerfile',
  frontendBuildContext: '.',
  frontendDockerfilePath: 'Dockerfile',
  frontendRepositoryUrl: '',
  frontendGitBranch: 'main',
  buildStrategy: 'MAVEN' as 'MAVEN' | 'DOCKER_ONLY',
  sonarMode: 'MAVEN' as 'MAVEN' | 'CLI' | 'SKIP',
  runBackendTests: false,
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
const secrets = ref<KubernetesSecretSummary[]>([])
const loadingSecrets = ref(false)
const creatingSecret = ref(false)
const secretError = ref('')
const secretMode = ref<'EXISTING' | 'NEW'>('EXISTING')
const newSecret = reactive({ name: '', key: 'password', password: '' })
let secretRequestSequence = 0
const selectedSecret = computed(() => secrets.value.find(s => s.name === form.passwordSecretName))
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
const dockerTag = computed(() => {
  const version = selectedVersion.value

  const rawTag = String(
    version?.version || version?.id || 'latest'
  ).trim()

  return rawTag
    .replace(/[^a-zA-Z0-9_.-]/g, '-')
    .replace(/^[.-]+/, '') || 'latest'
})

const backendImage = computed(() => {
  const existingImage =
    selectedVersion.value?.backendDockerImage?.trim()
    || selectedVersion.value?.dockerImages?.backend?.trim()
    || selectedVersion.value?.dockerImage?.trim()

  if (existingImage) return existingImage

  const appName = k8sName(selectedApplication.value?.name || '')

  if (!appName) return ''

  return `localhost:5000/${appName}-backend:${dockerTag.value}`
})

const frontendImage = computed(() => {
  const existingImage =
    selectedVersion.value?.frontendDockerImage?.trim()
    || selectedVersion.value?.dockerImages?.frontend?.trim()

  if (existingImage) return existingImage

  const appName = k8sName(selectedApplication.value?.name || '')

  if (!appName) return ''

  return `localhost:5000/${appName}-frontend:${dockerTag.value}`
})
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
  secretRequestSequence++
  secrets.value = []
  loadingSecrets.value = false
  secretError.value = ''
  secretMode.value = 'EXISTING'
  Object.assign(newSecret, { name: '', key: 'password', password: '' })
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
watch(() => form.environmentId, async environmentId => {
  const sequence = ++secretRequestSequence
  secrets.value = []
  form.passwordSecretName = ''
  form.passwordSecretKey = ''
  secretError.value = ''
  if (!environmentId || !props.open) {
    loadingSecrets.value = false
    return
  }
  loadingSecrets.value = true
  try {
    const result = await listKubernetesSecrets(environmentId)
    if (sequence === secretRequestSequence && props.open) secrets.value = result
  } catch {
    if (sequence === secretRequestSequence && props.open) {
      secretError.value = 'Impossible de charger les Secrets Kubernetes.'
    }
  } finally {
    if (sequence === secretRequestSequence) loadingSecrets.value = false
  }
})

watch(() => form.passwordSecretName, () => {
  const keys = selectedSecret.value?.keys ?? []
  form.passwordSecretKey = keys.includes('password') ? 'password' : (keys[0] ?? '')
})

async function handleCreateSecret() {
  secretError.value = ''
  const environmentId = form.environmentId
  const name = newSecret.name.trim()
  const key = newSecret.key.trim()
  const password = newSecret.password
  if (!environmentId || !selectedEnvironment.value) {
    secretError.value = 'Sélectionnez un environnement valide.'
    return
  }
  if (!validK8sName(name) || !key || !password) {
    secretError.value = 'Renseignez un nom Kubernetes valide, une clé et un mot de passe.'
    return
  }
  if (secrets.value.some(s => s.name === name)) {
    secretError.value = 'Ce Secret existe déjà. Sélectionnez-le dans la liste.'
    return
  }
  creatingSecret.value = true
  try {
    const created = await createKubernetesSecret({ environmentId, name, key, password })
    if (props.open && form.environmentId === environmentId) {
      secrets.value = [...secrets.value.filter(s => s.name !== created.name), created]
      secretMode.value = 'EXISTING'
      form.passwordSecretName = created.name
      form.passwordSecretKey = created.keys.includes(key) ? key : (created.keys[0] ?? '')
      Object.assign(newSecret, { name: '', key: 'password', password: '' })
    }
  } catch {
    secretError.value = 'Impossible de créer le Secret Kubernetes. Vérifiez les droits et le namespace.'
  } finally {
    creatingSecret.value = false
  }
}

watch(secretMode, mode => {
  if (mode === 'NEW') {
    form.passwordSecretName = ''
    form.passwordSecretKey = ''
  }
  secretError.value = ''
})

function closeModal() { if (!props.loading && !creatingSecret.value) emit('close') }
function submitForm() {
  validationError.value = ''
  if (!selectedApplication.value || !selectedVersion.value || !selectedEnvironment.value) {
    validationError.value = 'Sélectionnez une application, une version et un environnement valides.'
    return
  }
 if (!selectedVersion.value) {
  validationError.value =
    'Veuillez sélectionner une version valide.'
  return
}
  if (!validK8sName(namespace.value) || !validK8sName(backendName.value)
      || (isFullStack.value && !validK8sName(frontendName.value))) {
    validationError.value = 'Le namespace ou le nom généré est invalide. Vérifiez la configuration de l’application et de l’environnement.'
    return
  }
 if (!backendImage.value) {
  validationError.value =
    'Impossible de déterminer le nom de l’image Docker backend.'
  return
}

if (isFullStack.value && !frontendImage.value) {
  validationError.value =
    'Impossible de déterminer le nom de l’image Docker frontend.'
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
  if (creatingSecret.value || loadingSecrets.value || secretMode.value === 'NEW') {
    validationError.value = 'Terminez la configuration du Secret avant de déployer.'
    return
  }
  if (!selectedSecret.value || !selectedSecret.value.keys.includes(form.passwordSecretKey)) {
    validationError.value = 'Sélectionnez un Secret existant et une clé valide dans cet environnement.'
    return
  }
  if (form.buildStrategy === 'DOCKER_ONLY' && form.sonarMode === 'MAVEN') {
    validationError.value = 'Sonar Maven nécessite un build Maven ; utilisez CLI ou SKIP.'
    return
  }
  const kubernetesConfig: KubernetesApplicationRequest = {
    namespace: namespace.value,
    frontend: isFullStack.value ? {
      name: frontendName.value, image: frontendImage.value,
      replicas: form.frontendReplicas, containerPort: frontendPort.value,
      repositoryUrl: form.frontendRepositoryUrl.trim() || selectedApplication.value?.repositoryUrl || '',
      gitBranch: form.frontendGitBranch.trim() || selectedVersion.value?.gitBranch || 'main',
      buildContext: form.frontendBuildContext.trim() || '.',
      dockerfilePath: form.frontendDockerfilePath.trim() || 'Dockerfile',
    } : null,
    backend: {
      name: backendName.value, image: backendImage.value,
      replicas: form.backendReplicas, containerPort: backendPort.value,
      buildContext: form.backendBuildContext.trim() || '.',
      dockerfilePath: form.backendDockerfilePath.trim() || 'Dockerfile',
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
    cicd: {
      buildStrategy: form.buildStrategy,
      sonarMode: form.sonarMode,
      runBackendTests: form.runBackendTests,
    },
  }
  emit('submit', { versionId: form.versionId, environmentId: form.environmentId, kubernetesConfig })
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="modal-overlay" @click.self="closeModal">
      <section class="modal-card" role="dialog" aria-modal="true" aria-labelledby="deploy-title">
        <header class="modal-header">
          <div class="header-copy">
            <h2 id="deploy-title">Nouveau déploiement</h2>
          </div>
          <button type="button" class="close-button" :disabled="loading || creatingSecret" aria-label="Fermer" @click="closeModal">×</button>
        </header>

        <form @submit.prevent="submitForm">
          <!-- 01 / Application -->
          <section class="section-card">
            <div class="section-heading"><span class="step-number">01</span><div><h3>Application &amp; environnement</h3><p>Choisissez ce que vous souhaitez déployer.</p></div></div>
            <div class="fields-grid">
              <div class="form-group"><label for="sd-app">Application <span class="required">*</span></label>
                <select id="sd-app" v-model.number="form.applicationId" :disabled="loading" required><option :value="0" disabled>Sélectionner une application</option><option v-for="app in applications" :key="app.id" :value="app.id">{{ app.name }}</option></select>
              </div>
              <div class="form-group"><label for="sd-version">Version <span class="required">*</span></label>
                <select id="sd-version" v-model.number="form.versionId" :disabled="loading || loadingVersions || !form.applicationId" required><option :value="0" disabled>{{ loadingVersions ? 'Chargement...' : 'Sélectionner une version' }}</option><option v-for="version in versions" :key="version.id" :value="version.id" :disabled="!(['PENDING', 'SUCCESS'].includes(version.buildStatus))">{{ version.version }} — {{ version.buildStatus }}</option></select>
                <small v-if="versions.some(v => v.buildStatus === 'PENDING')" class="field-info">Les versions PENDING peuvent être sélectionnées pour leur premier build Jenkins. Le backend doit également autoriser ce statut.</small>
                <small v-if="loadError" class="validation-error">{{ loadError }}</small><small v-else-if="form.applicationId && !loadingVersions && versions.length === 0" class="field-info">Aucune version disponible.</small>
              </div>
              <div class="form-group full-width"><label for="sd-env">Environnement <span class="required">*</span></label>
                <select id="sd-env" v-model.number="form.environmentId" :disabled="loading || !form.applicationId" required><option :value="0" disabled>Sélectionner un environnement</option><option v-for="env in filteredEnvironments" :key="env.id" :value="env.id">{{ env.name }} — {{ env.type }}</option></select>
                <small v-if="form.applicationId && !filteredEnvironments.length" class="field-info">Aucun environnement actif.</small>
              </div>
            </div>
          </section>

          <!-- 02 / CI/CD -->
          <section class="section-card">
            <div class="section-heading"><span class="step-number">02</span><div><h3>Pipeline CI/CD</h3><p>Définissez la stratégie de build et les contrôles qualité.</p></div></div>
            <div class="fields-grid">
              <div class="form-group"><label for="sd-build">Stratégie de build</label><select id="sd-build" v-model="form.buildStrategy"><option value="MAVEN">Maven (Jenkins)</option><option value="DOCKER_ONLY">Docker-only</option></select></div>
              <div class="form-group"><label for="sd-sonar">Analyse SonarQube</label><select id="sd-sonar" v-model="form.sonarMode"><option value="MAVEN">Maven</option><option value="CLI">Scanner CLI</option><option value="SKIP">Désactivée</option></select></div>
              <label class="checkbox-row full-width"><input v-model="form.runBackendTests" type="checkbox" /><span><strong>Exécuter les tests Maven</strong><small>Vérifier les tests pendant le pipeline Jenkins</small></span></label>
              <div class="form-group"><label for="sd-back-context">Contexte backend</label><input id="sd-back-context" v-model="form.backendBuildContext" placeholder="backend ou ." /></div>
              <div class="form-group"><label for="sd-back-dockerfile">Dockerfile backend</label><input id="sd-back-dockerfile" v-model="form.backendDockerfilePath" placeholder="Dockerfile" /></div>
            </div>
            <div v-if="isFullStack" class="subsection"><div class="subsection-title">Configuration du frontend</div><div class="fields-grid">
              <div class="form-group full-width"><label for="sd-front-repo">Dépôt Git frontend</label><input id="sd-front-repo" v-model="form.frontendRepositoryUrl" placeholder="Vide = dépôt principal" /></div>
              <div class="form-group"><label for="sd-front-branch">Branche frontend</label><input id="sd-front-branch" v-model="form.frontendGitBranch" placeholder="main" /></div>
              <div class="form-group"><label for="sd-front-context">Contexte frontend</label><input id="sd-front-context" v-model="form.frontendBuildContext" placeholder="frontend ou ." /></div>
              <div class="form-group full-width"><label for="sd-front-dockerfile">Dockerfile frontend</label><input id="sd-front-dockerfile" v-model="form.frontendDockerfilePath" placeholder="Dockerfile" /></div>
            </div></div>
          </section>

          <!-- 03 / Kubernetes -->
          <section class="section-card">
            <div class="section-heading"><span class="step-number">03</span><div><h3>Déploiement Kubernetes</h3><p>Choisissez l'architecture et les ressources du cluster.</p></div></div>
            <div class="form-group"><label for="sd-arch">Architecture de déploiement</label><select id="sd-arch" v-model="form.architecture" :disabled="loading"><option value="BACKEND">Backend + PostgreSQL externe</option><option value="FULLSTACK_EXTERNAL">Full Stack + PostgreSQL externe</option><option value="FULLSTACK_MANAGED">Full Stack + PostgreSQL sur K3s</option></select></div>
            <div v-if="selectedEnvironment || selectedVersion" class="info-panel">
              <div v-if="selectedEnvironment" class="info-line"><span>Namespace</span><code>{{ namespace }}</code></div>
              <div v-if="selectedVersion" class="info-line"><span>Image backend</span><code>{{ backendImage || 'Non disponible' }}</code></div>
              <div v-if="isFullStack && selectedVersion" class="info-line"><span>Image frontend</span><code>{{ frontendImage || 'Non disponible' }}</code></div>
            </div>
            <div class="subsection-title">Mise à l'échelle</div>
            <div class="fields-grid"><div class="form-group"><label for="sd-back-replicas">Réplicas backend</label><input id="sd-back-replicas" v-model.number="form.backendReplicas" type="number" min="1" max="10" required /></div><div v-if="isFullStack" class="form-group"><label for="sd-front-replicas">Réplicas frontend</label><input id="sd-front-replicas" v-model.number="form.frontendReplicas" type="number" min="1" max="10" required /></div></div>
          </section>

          <!-- 04 / PostgreSQL -->
          <section class="section-card">
            <div class="section-heading"><span class="step-number">04</span><div><h3>Base de données PostgreSQL</h3><p>{{ isManaged ? 'Base gérée dans K3s avec stockage persistant.' : 'Connexion à une instance PostgreSQL externe.' }}</p></div><span class="status-pill">{{ isManaged ? 'MANAGED' : 'EXTERNAL' }}</span></div>
            <div class="fields-grid">
              <div class="form-group"><label for="sd-db">Nom de la base <span class="required">*</span></label><input id="sd-db" v-model.trim="form.databaseName" placeholder="ecommerce" required /></div>
              <div class="form-group"><label for="sd-user">Utilisateur <span class="required">*</span></label><input id="sd-user" v-model.trim="form.databaseUser" placeholder="ecommerce_user" required /></div>
              <div v-if="!isManaged" class="form-group full-width"><label for="sd-host">Hôte PostgreSQL externe</label><input id="sd-host" v-model.trim="form.externalHost" placeholder="postgres.internal" required /></div>
              <div class="form-group"><label for="sd-db-port">Port PostgreSQL</label><input id="sd-db-port" v-model.number="form.databasePort" type="number" min="1" max="65535" required /></div>
              <div v-if="isManaged" class="form-group"><label for="sd-storage">Stockage PVC (Gi)</label><input id="sd-storage" v-model.number="form.storageGi" type="number" min="1" required /></div>
            </div>
            <div class="subsection secret-section"><div class="subsection-title">Sécurité · Kubernetes Secrets</div>
              <div class="form-group"><label for="sd-secret-mode">Configuration du Secret</label><select id="sd-secret-mode" v-model="secretMode" :disabled="loading || creatingSecret"><option value="EXISTING">Utiliser un Secret existant</option><option value="NEW">Créer un nouveau Secret</option></select></div>
              <template v-if="secretMode === 'EXISTING'"><div class="fields-grid">
                <div class="form-group"><label for="sd-secret">Secret Kubernetes</label><select id="sd-secret" v-model="form.passwordSecretName" :disabled="loading || loadingSecrets || !form.environmentId" required><option value="" disabled>{{ loadingSecrets ? 'Chargement...' : 'Sélectionner un Secret' }}</option><option v-for="secret in secrets" :key="secret.name" :value="secret.name">{{ secret.name }}</option></select></div>
                <div class="form-group"><label for="sd-secret-key">Clé du Secret</label><select id="sd-secret-key" v-model="form.passwordSecretKey" :disabled="loading || !selectedSecret" required><option value="" disabled>Sélectionner une clé</option><option v-for="key in selectedSecret?.keys ?? []" :key="key" :value="key">{{ key }}</option></select></div>
              </div><small v-if="form.environmentId && !loadingSecrets && secrets.length === 0" class="field-info">Aucun Secret disponible. Vous pouvez en créer un.</small></template>
              <template v-else><div class="fields-grid">
                <div class="form-group"><label for="sd-new-secret-name">Nom du Secret</label><input id="sd-new-secret-name" v-model.trim="newSecret.name" placeholder="ecommerce-db-secret" :disabled="creatingSecret" /></div>
                <div class="form-group"><label for="sd-new-secret-key">Clé</label><input id="sd-new-secret-key" v-model.trim="newSecret.key" placeholder="password" :disabled="creatingSecret" /></div>
                <div class="form-group full-width"><label for="sd-new-secret-password">Mot de passe PostgreSQL</label><input id="sd-new-secret-password" v-model="newSecret.password" type="password" autocomplete="new-password" placeholder="Mot de passe" :disabled="creatingSecret" /></div>
              </div><button type="button" class="secondary-button" :disabled="creatingSecret || !form.environmentId || loading" @click="handleCreateSecret">{{ creatingSecret ? 'Création...' : '+ Créer le Secret dans K3s' }}</button></template>
              <p v-if="secretError" class="validation-error" role="alert">{{ secretError }}</p>
              <p class="security-note">Le mot de passe reste dans Kubernetes Secret : les manifests utilisent uniquement <code>secretKeyRef</code>.</p>
            </div>
          </section>
          <p v-if="validationError" class="validation-error" role="alert">{{ validationError }}</p>
          <footer class="modal-actions"><span class="footer-hint">Les paramètres seront transmis à Jenkins et Kubernetes.</span><div class="action-buttons"><button type="button" class="cancel-button" :disabled="loading || creatingSecret" @click="closeModal">Annuler</button><button type="submit" class="submit-button" :disabled="loading || loadingVersions || loadingSecrets || creatingSecret || secretMode === 'NEW'">{{ loading ? 'Déploiement en cours...' : 'Lancer le déploiement →' }}</button></div></footer>
        </form>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.modal-overlay{position:fixed;inset:0;z-index:1000;display:grid;place-items:center;padding:24px;background:rgba(15,23,42,.64);backdrop-filter:blur(6px)}
.modal-card{width:min(100%,840px);max-height:min(94vh,1000px);overflow-y:auto;overscroll-behavior:contain;border:1px solid #e2e8f0;border-radius:22px;background:#f8fafc;box-shadow:0 32px 90px rgba(15,23,42,.35);scrollbar-width:thin}
.modal-header{display:flex;align-items:flex-start;justify-content:space-between;gap:18px;padding:29px 34px 25px;background:linear-gradient(115deg,#0f172a,#1e3a8a);color:white}
.header-copy{min-width:0}.eyebrow{display:flex;align-items:center;gap:9px;margin-bottom:12px;color:#bfdbfe;font-size:11px;font-weight:800;letter-spacing:1.7px}.eyebrow-dot{width:8px;height:8px;border-radius:50%;background:#60a5fa;box-shadow:0 0 0 4px rgba(96,165,250,.18)}
.modal-header h2{margin:0;font-size:27px;font-weight:800;letter-spacing:-.6px}.modal-header p{margin:9px 0 0;color:#cbd5e1;font-size:13px;line-height:1.6}.close-button{flex-shrink:0;width:36px;height:36px;border:1px solid rgba(255,255,255,.22);border-radius:10px;background:rgba(255,255,255,.1);color:#fff;font-size:23px;cursor:pointer}
form{display:flex;flex-direction:column;gap:18px;padding:25px 30px 28px}.section-card{display:flex;flex-direction:column;gap:19px;padding:23px 25px;background:white;border:1px solid #e2e8f0;border-radius:16px;box-shadow:0 3px 15px rgba(15,23,42,.025)}
.section-heading{display:flex;align-items:center;gap:14px;min-width:0;padding-bottom:17px;border-bottom:1px solid #eef2f7}.section-heading>div{flex:1;min-width:0}.section-heading h3{margin:0;color:#0f172a;font-size:16px;font-weight:800;letter-spacing:-.2px}.section-heading p{margin:5px 0 0;color:#64748b;font-size:12px;line-height:1.5}.step-number{display:grid;place-items:center;flex-shrink:0;width:39px;height:39px;border-radius:12px;background:#eff6ff;color:#2563eb;font-size:13px;font-weight:900}
.fields-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:17px}.full-width{grid-column:1/-1}.form-group{display:flex;flex-direction:column;gap:8px;min-width:0}.form-group label{color:#334155;font-size:12px;font-weight:700}.required{color:#ef4444}
.form-group select,.form-group input{box-sizing:border-box;width:100%;min-width:0;height:44px;padding:0 13px;border:1px solid #cbd5e1;border-radius:10px;outline:none;background:#fff;color:#0f172a;font:inherit;font-size:13px;transition:border-color .2s,box-shadow .2s}.form-group select:focus,.form-group input:focus{border-color:#3b82f6;box-shadow:0 0 0 4px rgba(59,130,246,.1)}.form-group select:disabled,.form-group input:disabled{background:#f1f5f9;color:#94a3b8;cursor:not-allowed}.form-group input::placeholder{color:#94a3b8}
.checkbox-row{display:flex;align-items:center;gap:12px;padding:13px 15px;border:1px solid #e2e8f0;border-radius:11px;background:#f8fafc;cursor:pointer}.checkbox-row input{width:17px;height:17px;accent-color:#2563eb}.checkbox-row span{display:flex;flex-direction:column;gap:3px}.checkbox-row strong{font-size:12px;color:#334155}.checkbox-row small{font-size:11px;color:#64748b}
.subsection{display:flex;flex-direction:column;gap:15px;padding:19px;border:1px solid #e2e8f0;border-radius:12px;background:#f8fafc}.subsection-title{color:#334155;font-size:13px;font-weight:800}.info-panel{display:grid;gap:9px;padding:15px 17px;border:1px solid #bfdbfe;border-radius:12px;background:#eff6ff}.info-line{display:flex;justify-content:space-between;align-items:flex-start;gap:16px;color:#475569;font-size:12px}.info-line code{max-width:70%;overflow-wrap:anywhere;text-align:right;color:#1d4ed8;font-size:11px;font-weight:700}.status-pill{flex-shrink:0;padding:6px 9px;border-radius:999px;background:#dcfce7;color:#166534;font-size:10px;font-weight:900;letter-spacing:.4px}
.security-note{margin:0;color:#64748b;font-size:11px;line-height:1.6}.security-note code{color:#334155}.field-info{color:#b45309;font-size:11px}.validation-error{margin:0;padding:11px 13px;border:1px solid #fecaca;border-radius:9px;background:#fef2f2;color:#b91c1c;font-size:12px}.secondary-button{align-self:flex-start;padding:11px 15px;border:1px solid #bfdbfe;border-radius:9px;background:#eff6ff;color:#1d4ed8;font-weight:800;cursor:pointer}
.modal-actions{display:flex;justify-content:space-between;align-items:center;gap:16px;padding:18px 3px 0;border-top:1px solid #e2e8f0}.footer-hint{color:#64748b;font-size:11px;line-height:1.5}.action-buttons{display:flex;gap:10px;flex-shrink:0}.cancel-button,.submit-button{min-height:44px;padding:0 18px;border-radius:10px;font-size:12px;font-weight:800;cursor:pointer}.cancel-button{border:1px solid #cbd5e1;background:white;color:#334155}.submit-button{border:1px solid #2563eb;background:#2563eb;color:white;box-shadow:0 4px 12px rgba(37,99,235,.2)}.submit-button:hover:not(:disabled){background:#1d4ed8}button:disabled{opacity:.55;cursor:not-allowed}
@media(max-width:640px){.modal-overlay{padding:10px;align-items:center}.modal-card{max-height:96vh;border-radius:15px}.modal-header{padding:23px 19px}.modal-header h2{font-size:22px}form{padding:16px 13px;gap:12px}.section-card{padding:17px 15px}.fields-grid{grid-template-columns:1fr;gap:14px}.full-width{grid-column:auto}.section-heading{align-items:flex-start}.status-pill{font-size:9px}.modal-actions{align-items:stretch;flex-direction:column}.action-buttons{width:100%}.action-buttons button{flex:1}.info-line{flex-direction:column;gap:5px}.info-line code{max-width:100%;text-align:left}}
</style>
