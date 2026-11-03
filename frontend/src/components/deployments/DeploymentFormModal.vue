<script setup lang="ts">
/**
 * Formulaire « Nouveau déploiement ».
 *
 * Principe : l'utilisateur ne renseigne que ce qui change d'un déploiement
 * à l'autre (quoi, où, architecture, réplicas, base de données). Tout ce que
 * SmartDeploy peut déduire (namespace, noms Kubernetes, images, Secret) est
 * calculé et affiché au récapitulatif ; les réglages techniques rarement
 * modifiés sont regroupés dans « Options avancées », préremplis.
 *
 * La requête envoyée au backend (CreateDeploymentRequest) est inchangée.
 */
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

/** Clé unique utilisée dans les Secrets créés par SmartDeploy. */
const DEFAULT_SECRET_KEY = 'password'
const DEFAULTS = {
  backendPort: 8080,
  frontendPort: 80,
  replicas: 2,
  databasePort: 5432,
  storageGi: 5,
} as const

const wizardSteps = ['Application', 'Architecture', 'Base de données', 'Vérification'] as const
const currentStep = ref(1)
const showAdvanced = ref(false)

const form = reactive({
  applicationId: 0,
  versionId: 0,
  environmentId: 0,
  architecture: 'BACKEND' as Architecture,
  frontendReplicas: DEFAULTS.replicas as number,
  backendReplicas: DEFAULTS.replicas as number,
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
  passwordSecretKey: DEFAULT_SECRET_KEY,
  externalHost: '',
  databasePort: DEFAULTS.databasePort as number,
  storageGi: DEFAULTS.storageGi as number,
})
const ports = reactive({ backend: DEFAULTS.backendPort as number, frontend: DEFAULTS.frontendPort as number })

const versions = ref<VersionResponse[]>([])
const loadingVersions = ref(false)
const loadError = ref('')
const validationError = ref('')
let versionRequestSequence = 0

/*
 * Secret PostgreSQL.
 * NEW      : SmartDeploy crée « <application>-db-secret » (clé « password »)
 *            avec le mot de passe saisi ; c'est le cas par défaut.
 * EXISTING : un Secret déjà présent dans le namespace est réutilisé
 *            (sélection automatique s'il porte le nom par défaut).
 */
const secrets = ref<KubernetesSecretSummary[]>([])
const loadingSecrets = ref(false)
const creatingSecret = ref(false)
const secretError = ref('')
const secretMode = ref<'EXISTING' | 'NEW'>('NEW')
const newSecret = reactive({ name: '', password: '' })
let secretRequestSequence = 0
const selectedSecret = computed(() => secrets.value.find(s => s.name === form.passwordSecretName))

/* ---------------------------------------------------------------
 * Sélections et valeurs déduites
 * --------------------------------------------------------------- */
const selectedApplication = computed(() => props.applications.find(a => a.id === form.applicationId))
const selectedVersion = computed(() =>
  versions.value.find(v => v.id === form.versionId) as VersionWithImages | undefined,
)
const filteredEnvironments = computed(() =>
  props.environments.filter(e => e.applicationId === form.applicationId && e.active),
)
const selectedEnvironment = computed(() => filteredEnvironments.value.find(e => e.id === form.environmentId))
const isFullStack = computed(() => form.architecture !== 'BACKEND')
const isManaged = computed(() => form.architecture === 'FULLSTACK_MANAGED')

const architectureLabel = computed(() => ({
  BACKEND: 'Backend seul, base PostgreSQL existante',
  FULLSTACK_EXTERNAL: 'Frontend et backend, base PostgreSQL existante',
  FULLSTACK_MANAGED: 'Frontend et backend, PostgreSQL créé dans le cluster',
})[form.architecture])
const totalPods = computed(() =>
  form.backendReplicas + (isFullStack.value ? form.frontendReplicas : 0) + (isManaged.value ? 1 : 0),
)

function k8sName(value: string): string {
  return value.toLowerCase().normalize('NFKD').replace(/[\u0300-\u036f]/g, '')
    .replace(/[^a-z0-9-]/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '')
}
/** Identifiant PostgreSQL déduit du nom de l'application (ex. « E-Commerce » → « e_commerce »). */
function databaseIdentifier(value: string): string {
  const id = k8sName(value).replace(/-/g, '_')
  return /^[a-z_]/.test(id) ? id : `db_${id}`
}
const appSlug = computed(() => k8sName(selectedApplication.value?.name || ''))
const backendName = computed(() => `${appSlug.value}-backend`)
const frontendName = computed(() => `${appSlug.value}-frontend`)
const defaultSecretName = computed(() => `${appSlug.value || 'application'}-db-secret`)
const namespace = computed(() => selectedEnvironment.value?.namespace?.trim() || '')

const dockerTag = computed(() => {
  const version = selectedVersion.value
  const rawTag = String(version?.version || version?.id || 'latest').trim()
  // Même règle que le Jenkinsfile : minuscules, caractères sûrs uniquement.
  return rawTag.toLowerCase().replace(/[^a-z0-9_.-]/g, '-').replace(/^[.-]+/, '') || 'latest'
})
/** Une image déjà construite est référencée par empreinte : on ne la reconstruit pas sous ce nom. */
const isBuildableImage = (image?: string | null) => !!image?.trim() && !image.includes('@sha256:')
const backendImage = computed(() => {
  const existingImage = [
    selectedVersion.value?.backendDockerImage,
    selectedVersion.value?.dockerImages?.backend,
    selectedVersion.value?.dockerImage,
  ].find(isBuildableImage)?.trim()
  if (existingImage) return existingImage
  return appSlug.value ? `localhost:5000/${appSlug.value}-backend:${dockerTag.value}` : ''
})
const frontendImage = computed(() => {
  const existingImage = [
    selectedVersion.value?.frontendDockerImage,
    selectedVersion.value?.dockerImages?.frontend,
  ].find(isBuildableImage)?.trim()
  if (existingImage) return existingImage
  return appSlug.value ? `localhost:5000/${appSlug.value}-frontend:${dockerTag.value}` : ''
})
const pipelineSummary = computed(() => {
  const steps = [form.buildStrategy === 'MAVEN' ? 'build Maven' : 'build Docker']
  if (form.runBackendTests) steps.push('tests')
  if (form.sonarMode !== 'SKIP') steps.push(`analyse SonarQube (${form.sonarMode === 'MAVEN' ? 'Maven' : 'CLI'})`)
  steps.push('scan de sécurité Trivy')
  const text = steps.join(', ')
  return text.charAt(0).toUpperCase() + text.slice(1)
})
const secretSummary = computed(() =>
  secretMode.value === 'NEW'
    ? `${newSecret.name || defaultSecretName.value} (créé au lancement)`
    : `${form.passwordSecretName || 'À sélectionner'} (existant)`,
)

const validK8sName = (value: string) => value.length <= 63 && /^[a-z0-9]([-a-z0-9]*[a-z0-9])?$/.test(value)
const validRelativePath = (value: string) =>
  !!value.trim() && /^[a-zA-Z0-9._/-]+$/.test(value) && !value.startsWith('/') && !value.includes('..')
const validPort = (n: number) => Number.isInteger(n) && n >= 1 && n <= 65535
const validReplicas = (n: number) => Number.isInteger(n) && n >= 1 && n <= 10

function readableKubernetesError(error: unknown, fallback: string): string {
  const err = error as { response?: { status?: number; data?: { detail?: string; message?: string } } }
  const status = err?.response?.status
  const detail = err?.response?.data?.detail || err?.response?.data?.message
  if (status === 403) return 'Accès refusé (403) : le backend SmartDeploy doit disposer des droits Kubernetes nécessaires.'
  if (status === 401) return 'Session expirée (401) : reconnectez-vous à SmartDeploy.'
  if (status === 409) return 'Ce Secret existe déjà dans le namespace : sélectionnez-le dans les options avancées.'
  return detail || `${fallback}${status ? ` (HTTP ${status})` : ''}.`
}

/* ---------------------------------------------------------------
 * Initialisation et préremplissage
 * --------------------------------------------------------------- */
function resetForm() {
  currentStep.value = 1
  showAdvanced.value = false
  versionRequestSequence++
  secretRequestSequence++
  versions.value = []
  secrets.value = []
  loadingVersions.value = false
  loadingSecrets.value = false
  secretError.value = ''
  secretMode.value = 'NEW'
  validationError.value = ''
  loadError.value = ''
  Object.assign(ports, { backend: DEFAULTS.backendPort, frontend: DEFAULTS.frontendPort })
  Object.assign(newSecret, { name: '', password: '' })
  Object.assign(form, {
    applicationId: 0, versionId: 0, environmentId: 0,
    architecture: 'BACKEND', frontendReplicas: DEFAULTS.replicas, backendReplicas: DEFAULTS.replicas,
    backendBuildContext: '.', backendDockerfilePath: 'Dockerfile',
    frontendBuildContext: '.', frontendDockerfilePath: 'Dockerfile',
    frontendRepositoryUrl: '', frontendGitBranch: 'main',
    buildStrategy: 'MAVEN', sonarMode: 'MAVEN', runBackendTests: false,
    databaseName: '', databaseUser: '', passwordSecretName: '', passwordSecretKey: DEFAULT_SECRET_KEY,
    externalHost: '', databasePort: DEFAULTS.databasePort, storageGi: DEFAULTS.storageGi,
  })
}
watch(() => props.open, open => { if (open) resetForm() })

watch(() => form.applicationId, async applicationId => {
  const sequence = ++versionRequestSequence
  form.versionId = 0
  form.environmentId = 0
  versions.value = []
  loadError.value = ''

  // Valeurs par défaut déduites de l'application (modifiables).
  const app = props.applications.find(a => a.id === applicationId)
  const dbId = databaseIdentifier(app?.name || '')
  form.frontendRepositoryUrl = app?.repositoryUrl || ''
  form.databaseName = app ? dbId : ''
  form.databaseUser = app ? `${dbId}_user` : ''
  newSecret.name = `${k8sName(app?.name || '') || 'application'}-db-secret`

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

watch(() => form.versionId, versionId => {
  const version = versions.value.find(v => v.id === versionId)
  if (version?.gitBranch) form.frontendGitBranch = version.gitBranch
})

watch(() => form.environmentId, async environmentId => {
  const sequence = ++secretRequestSequence
  secrets.value = []
  form.passwordSecretName = ''
  form.passwordSecretKey = DEFAULT_SECRET_KEY
  secretMode.value = 'NEW'
  secretError.value = ''
  if (!environmentId || !props.open) { loadingSecrets.value = false; return }
  loadingSecrets.value = true
  try {
    const result = await listKubernetesSecrets(environmentId)
    if (sequence !== secretRequestSequence || !props.open) return
    secrets.value = result
    // Réutilisation automatique du Secret par défaut s'il existe déjà.
    const existing = result.find(s => s.name === defaultSecretName.value)
    if (existing) selectExistingSecret(existing.name)
  } catch (error: unknown) {
    if (sequence === secretRequestSequence && props.open) {
      secretError.value = readableKubernetesError(error, 'Impossible de lister les Secrets Kubernetes')
    }
  } finally {
    if (sequence === secretRequestSequence) loadingSecrets.value = false
  }
})

function selectExistingSecret(name: string) {
  secretMode.value = 'EXISTING'
  form.passwordSecretName = name
  const keys = secrets.value.find(s => s.name === name)?.keys ?? []
  form.passwordSecretKey = keys.includes(DEFAULT_SECRET_KEY) ? DEFAULT_SECRET_KEY : (keys[0] ?? '')
}
function onSecretChoice(event: Event) {
  const value = (event.target as HTMLSelectElement).value
  secretError.value = ''
  if (value) {
    selectExistingSecret(value)
  } else {
    secretMode.value = 'NEW'
    form.passwordSecretName = ''
    form.passwordSecretKey = DEFAULT_SECRET_KEY
  }
}

watch(() => form.buildStrategy, strategy => {
  if (strategy === 'DOCKER_ONLY') {
    form.runBackendTests = false
    if (form.sonarMode === 'MAVEN') form.sonarMode = 'CLI'
  }
})

async function createSecretIfNeeded(): Promise<boolean> {
  if (secretMode.value !== 'NEW') return true
  secretError.value = ''
  const environmentId = form.environmentId
  const name = newSecret.name.trim()
  if (secrets.value.some(s => s.name === name)) {
    // Créé entre-temps (autre onglet, essai précédent) : on le réutilise.
    selectExistingSecret(name)
    return true
  }
  creatingSecret.value = true
  try {
    const created = await createKubernetesSecret({
      environmentId, name, key: DEFAULT_SECRET_KEY, password: newSecret.password,
    })
    if (!props.open || form.environmentId !== environmentId) return false
    secrets.value = [...secrets.value.filter(s => s.name !== created.name), created]
    selectExistingSecret(created.name)
    newSecret.password = ''
    return true
  } catch (error: unknown) {
    secretError.value = readableKubernetesError(error, 'Impossible de créer le Secret Kubernetes')
    return false
  } finally {
    creatingSecret.value = false
  }
}

/* ---------------------------------------------------------------
 * Validation par étape
 * --------------------------------------------------------------- */
function validateWizardStep(step: number): string {
  if (step === 1) {
    if (!selectedApplication.value || !selectedVersion.value || !selectedEnvironment.value)
      return 'Sélectionnez une application, une version et un environnement.'
    if (!['PENDING', 'SUCCESS'].includes(selectedVersion.value.buildStatus))
      return 'Cette version n’est pas déployable : seul PENDING ou SUCCESS est autorisé.'
    if (!selectedApplication.value.repositoryUrl?.trim())
      return 'Le dépôt Git de l’application n’est pas configuré : complétez la fiche application.'
    if (!validK8sName(namespace.value))
      return 'Le namespace de cet environnement est invalide : corrigez la fiche environnement.'
  }
  if (step === 2) {
    if (!validK8sName(backendName.value) || (isFullStack.value && !validK8sName(frontendName.value)))
      return 'Le nom de l’application ne permet pas de générer des noms Kubernetes valides.'
    if (!backendImage.value || (isFullStack.value && !frontendImage.value))
      return 'Impossible de déterminer les images Docker de cette version.'
    if (![form.backendReplicas, ...(isFullStack.value ? [form.frontendReplicas] : [])].every(validReplicas))
      return 'Le nombre de réplicas doit être compris entre 1 et 10.'
  }
  if (step === 3) {
    if (!form.databaseName.trim() || !form.databaseUser.trim())
      return 'Indiquez le nom de la base et l’utilisateur PostgreSQL.'
    if (!isManaged.value && !form.externalHost.trim())
      return 'Indiquez l’adresse du serveur PostgreSQL externe.'
    if (!validPort(form.databasePort))
      return 'Le port PostgreSQL doit être compris entre 1 et 65535.'
    if (loadingSecrets.value)
      return 'Chargement des Secrets en cours, réessayez dans un instant.'
    if (secretMode.value === 'NEW' && !newSecret.password)
      return 'Saisissez le mot de passe PostgreSQL.'
    if (secretMode.value === 'EXISTING'
        && (!selectedSecret.value || !selectedSecret.value.keys.includes(form.passwordSecretKey)))
      return 'Le Secret sélectionné est introuvable ou ne contient pas de mot de passe.'
  }
  if (step === 4) {
    if (![ports.backend, ...(isFullStack.value ? [ports.frontend] : [])].every(validPort))
      return 'Options avancées : les ports des conteneurs doivent être compris entre 1 et 65535.'
    if (![form.backendBuildContext, form.backendDockerfilePath,
      ...(isFullStack.value ? [form.frontendBuildContext, form.frontendDockerfilePath] : [])].every(validRelativePath))
      return 'Options avancées : les chemins Docker doivent être relatifs au dépôt, sans « .. ».'
    if (isFullStack.value && (!form.frontendRepositoryUrl.trim() || !form.frontendGitBranch.trim()))
      return 'Options avancées : indiquez le dépôt Git et la branche du frontend.'
    if (form.buildStrategy === 'DOCKER_ONLY' && form.sonarMode === 'MAVEN')
      return 'Options avancées : l’analyse SonarQube Maven nécessite le build Maven.'
    if (isManaged.value && (!Number.isInteger(form.storageGi) || form.storageGi < 1))
      return 'Options avancées : le stockage PostgreSQL doit être d’au moins 1 Gi.'
    if (secretMode.value === 'NEW' && !validK8sName(newSecret.name.trim()))
      return 'Options avancées : le nom du Secret n’est pas un nom Kubernetes valide.'
  }
  return ''
}

function nextStep() {
  validationError.value = ''
  const error = validateWizardStep(currentStep.value)
  if (error) { validationError.value = error; return }
  if (currentStep.value < wizardSteps.length) currentStep.value++
}
function previousStep() {
  validationError.value = ''
  if (currentStep.value > 1) currentStep.value--
}
function navigateStep(target: number) {
  if (target < currentStep.value && target >= 1) {
    currentStep.value = target
    validationError.value = ''
  }
}
function closeModal() { if (!props.loading && !creatingSecret.value) emit('close') }

/* ---------------------------------------------------------------
 * Soumission : requête identique à la version précédente
 * --------------------------------------------------------------- */
async function submitForm() {
  validationError.value = ''
  // Entrée dans un champ intermédiaire ne doit jamais déclencher Jenkins.
  if (currentStep.value !== wizardSteps.length) { nextStep(); return }

  for (let step = 1; step <= wizardSteps.length; step++) {
    const error = validateWizardStep(step)
    if (error) {
      currentStep.value = step
      if (step === 4) showAdvanced.value = true
      validationError.value = error
      return
    }
  }
  if (creatingSecret.value || loadingSecrets.value) {
    validationError.value = 'Une opération sur les Secrets est en cours.'
    return
  }
  if (!(await createSecretIfNeeded())) {
    currentStep.value = 3
    validationError.value = secretError.value || 'La création du Secret a échoué.'
    return
  }
  if (!selectedSecret.value || !selectedSecret.value.keys.includes(form.passwordSecretKey)) {
    currentStep.value = 3
    validationError.value = 'Le Secret PostgreSQL est introuvable dans cet environnement.'
    return
  }

  const kubernetesConfig: KubernetesApplicationRequest = {
    namespace: namespace.value,
    frontend: isFullStack.value ? {
      name: frontendName.value, image: frontendImage.value,
      replicas: form.frontendReplicas, containerPort: ports.frontend,
      repositoryUrl: form.frontendRepositoryUrl.trim() || selectedApplication.value?.repositoryUrl || '',
      gitBranch: form.frontendGitBranch.trim() || selectedVersion.value?.gitBranch || 'main',
      buildContext: form.frontendBuildContext.trim() || '.',
      dockerfilePath: form.frontendDockerfilePath.trim() || 'Dockerfile',
    } : null,
    backend: {
      name: backendName.value, image: backendImage.value,
      replicas: form.backendReplicas, containerPort: ports.backend,
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
    <div v-if="open" class="dfm-overlay" @click.self="closeModal">
      <section class="dfm" role="dialog" aria-modal="true" aria-labelledby="dfm-title">
        <header class="dfm-header">
          <div>
            <h2 id="dfm-title">Nouveau déploiement</h2>
            <p>Les noms, les images et le namespace sont calculés automatiquement.</p>
          </div>
          <button type="button" class="dfm-close" :disabled="loading || creatingSecret" aria-label="Fermer" @click="closeModal">
            <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><path d="M5 5l10 10M15 5L5 15" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" /></svg>
          </button>
        </header>

        <nav class="dfm-steps" aria-label="Étapes du déploiement">
          <button v-for="(label, index) in wizardSteps" :key="label" type="button" class="dfm-step"
            :class="{ 'is-current': currentStep === index + 1, 'is-done': currentStep > index + 1 }"
            :disabled="loading || creatingSecret || index + 1 >= currentStep"
            :aria-current="currentStep === index + 1 ? 'step' : undefined"
            @click="navigateStep(index + 1)">
            <span class="dfm-step-bar" aria-hidden="true" />
            <span class="dfm-step-label">{{ label }}</span>
          </button>
        </nav>

        <form class="dfm-body" novalidate @submit.prevent="submitForm">
          <p v-if="validationError" class="dfm-alert" role="alert">{{ validationError }}</p>

          <!-- Étape 1 -->
          <section v-if="currentStep === 1" class="dfm-section">
            <h3>Quoi déployer, et où ?</h3>
            <div class="dfm-grid">
              <div class="dfm-field dfm-wide"><label for="sd-app">Application</label>
                <select id="sd-app" v-model.number="form.applicationId" :disabled="loading">
                  <option :value="0" disabled>Choisir une application</option>
                  <option v-for="app in applications" :key="app.id" :value="app.id">{{ app.name }}</option>
                </select>
              </div>
              <div class="dfm-field"><label for="sd-version">Version</label>
                <select id="sd-version" v-model.number="form.versionId" :disabled="loading || loadingVersions || !form.applicationId">
                  <option :value="0" disabled>{{ loadingVersions ? 'Chargement…' : 'Choisir une version' }}</option>
                  <option v-for="version in versions" :key="version.id" :value="version.id" :disabled="!['PENDING', 'SUCCESS'].includes(version.buildStatus)">{{ version.version }} ({{ version.buildStatus }})</option>
                </select>
                <small v-if="loadError" class="dfm-error">{{ loadError }}</small>
                <small v-else-if="form.applicationId && !loadingVersions && versions.length === 0" class="dfm-warn">Aucune version. Créez-en une depuis la fiche de l’application.</small>
              </div>
              <div class="dfm-field"><label for="sd-env">Environnement</label>
                <select id="sd-env" v-model.number="form.environmentId" :disabled="loading || !form.applicationId">
                  <option :value="0" disabled>Choisir un environnement</option>
                  <option v-for="env in filteredEnvironments" :key="env.id" :value="env.id">{{ env.name }} ({{ env.type }})</option>
                </select>
                <small v-if="form.applicationId && !filteredEnvironments.length" class="dfm-warn">Aucun environnement actif pour cette application.</small>
              </div>
            </div>
            <dl v-if="selectedApplication" class="dfm-facts">
              <div><dt>Dépôt Git</dt><dd><code>{{ selectedApplication.repositoryUrl || 'Non configuré' }}</code></dd></div>
              <div v-if="selectedVersion"><dt>Branche</dt><dd><code>{{ selectedVersion.gitBranch || 'Non renseignée' }}</code><span v-if="selectedVersion.gitCommit" class="dfm-muted"> commit {{ selectedVersion.gitCommit.slice(0, 8) }}</span></dd></div>
              <div v-if="selectedEnvironment"><dt>Namespace</dt><dd><code>{{ namespace }}</code></dd></div>
            </dl>
          </section>

          <!-- Étape 2 -->
          <section v-if="currentStep === 2" class="dfm-section">
            <h3>Architecture</h3>
            <fieldset class="dfm-choices" :disabled="loading">
              <legend class="dfm-sr">Composants à déployer</legend>
              <label class="dfm-choice" :class="{ 'is-selected': form.architecture === 'BACKEND' }">
                <input v-model="form.architecture" type="radio" value="BACKEND" />
                <strong>Backend seul</strong>
                <span>Une API reliée à une base PostgreSQL existante.</span>
              </label>
              <label class="dfm-choice" :class="{ 'is-selected': form.architecture === 'FULLSTACK_EXTERNAL' }">
                <input v-model="form.architecture" type="radio" value="FULLSTACK_EXTERNAL" />
                <strong>Frontend et backend</strong>
                <span>Les deux applications, reliées à une base PostgreSQL existante.</span>
              </label>
              <label class="dfm-choice" :class="{ 'is-selected': form.architecture === 'FULLSTACK_MANAGED' }">
                <input v-model="form.architecture" type="radio" value="FULLSTACK_MANAGED" />
                <strong>Frontend, backend et base</strong>
                <span>PostgreSQL est créé dans le cluster, avec un volume persistant.</span>
              </label>
            </fieldset>
            <h4>Nombre d’instances</h4>
            <div class="dfm-grid">
              <div class="dfm-field"><label for="sd-back-replicas">Backend</label><input id="sd-back-replicas" v-model.number="form.backendReplicas" type="number" min="1" max="10" /></div>
              <div v-if="isFullStack" class="dfm-field"><label for="sd-front-replicas">Frontend</label><input id="sd-front-replicas" v-model.number="form.frontendReplicas" type="number" min="1" max="10" /></div>
            </div>
            <p class="dfm-help">{{ totalPods }} pod(s) seront créés{{ isManaged ? ', dont 1 pour PostgreSQL' : '' }}.</p>
          </section>

          <!-- Étape 3 -->
          <section v-if="currentStep === 3" class="dfm-section">
            <h3>Base de données</h3>
            <p class="dfm-lead">{{ isManaged ? 'SmartDeploy crée PostgreSQL 16 dans le cluster. Choisissez les identifiants de la nouvelle base.' : 'Indiquez comment joindre votre serveur PostgreSQL existant.' }}</p>
            <div class="dfm-grid">
              <template v-if="!isManaged">
                <div class="dfm-field"><label for="sd-host">Adresse du serveur</label><input id="sd-host" v-model.trim="form.externalHost" placeholder="192.168.1.20 ou db.exemple.com" /></div>
                <div class="dfm-field"><label for="sd-db-port">Port</label><input id="sd-db-port" v-model.number="form.databasePort" type="number" min="1" max="65535" /></div>
              </template>
              <div class="dfm-field"><label for="sd-db">Nom de la base</label><input id="sd-db" v-model.trim="form.databaseName" /></div>
              <div class="dfm-field"><label for="sd-user">Utilisateur</label><input id="sd-user" v-model.trim="form.databaseUser" /></div>
              <div v-if="secretMode === 'NEW'" class="dfm-field dfm-wide">
                <label for="sd-db-password">Mot de passe</label>
                <input id="sd-db-password" v-model="newSecret.password" type="password" autocomplete="new-password" :disabled="creatingSecret" />
                <small class="dfm-help">Conservé dans le Secret Kubernetes « {{ newSecret.name }} ». Il n’apparaît jamais dans les manifests.</small>
              </div>
              <p v-else class="dfm-note dfm-wide">Le mot de passe est déjà enregistré dans le Secret « {{ form.passwordSecretName }} ». Il sera réutilisé.</p>
            </div>
            <small v-if="loadingSecrets" class="dfm-help">Recherche des Secrets existants…</small>
            <p v-if="secretError" class="dfm-alert" role="alert">{{ secretError }}</p>
          </section>

          <!-- Étape 4 : options avancées, puis récapitulatif -->
          <template v-if="currentStep === 4 && selectedApplication && selectedEnvironment">
            <section class="dfm-section dfm-advanced">
              <button type="button" class="dfm-disclosure" :aria-expanded="showAdvanced" @click="showAdvanced = !showAdvanced">
                <svg viewBox="0 0 20 20" width="14" height="14" aria-hidden="true" :class="{ 'is-open': showAdvanced }"><path d="M7 5l5 5-5 5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
                Options avancées
              </button>
              <p class="dfm-help">Réglages techniques transmis à Jenkins et à Kubernetes. Les valeurs proposées conviennent à la plupart des projets : ne les modifiez que si votre projet est organisé différemment.</p>

              <div v-if="showAdvanced" class="dfm-advanced-body">
                <div class="dfm-group">
                  <h4>Construction et contrôles</h4>
                  <p class="dfm-help">Comment Jenkins construit l’application avant de la déployer.</p>
                  <div class="dfm-grid">
                    <div class="dfm-field dfm-wide"><label for="sd-build">Méthode de construction</label>
                      <select id="sd-build" v-model="form.buildStrategy">
                        <option value="MAVEN">Compiler avec Maven, puis créer l’image Docker (projets Java / Spring Boot)</option>
                        <option value="DOCKER_ONLY">Laisser le Dockerfile tout faire (autres langages, Dockerfile multi-étapes)</option>
                      </select>
                    </div>
                    <div class="dfm-field dfm-wide"><label for="sd-sonar">Analyse de la qualité du code (SonarQube)</label>
                      <select id="sd-sonar" v-model="form.sonarMode">
                        <option value="MAVEN" :disabled="form.buildStrategy === 'DOCKER_ONLY'">Analyser avec Maven (recommandé pour Java)</option>
                        <option value="CLI">Analyser avec le scanner SonarQube (autres langages)</option>
                        <option value="SKIP">Ne pas analyser le code</option>
                      </select>
                      <small class="dfm-help">Si l’analyse est activée, le déploiement s’arrête quand le code ne respecte pas les règles de qualité.</small>
                    </div>
                    <label class="dfm-check dfm-wide">
                      <input v-model="form.runBackendTests" type="checkbox" :disabled="form.buildStrategy === 'DOCKER_ONLY'" />
                      <span><strong>Lancer les tests du backend</strong><small class="dfm-help dfm-block">À cocher si vos tests fonctionnent sans base de données. Jenkins ne fournit pas encore de base PostgreSQL pendant les tests.</small></span>
                    </label>
                  </div>
                </div>

                <div class="dfm-group">
                  <h4>Ports d’écoute</h4>
                  <p class="dfm-help">Le port sur lequel votre application écoute à l’intérieur de son conteneur. Kubernetes y envoie le trafic.</p>
                  <div class="dfm-grid">
                    <div class="dfm-field"><label for="sd-back-port">Backend</label><input id="sd-back-port" v-model.number="ports.backend" type="number" min="1" max="65535" /><small class="dfm-help">Spring Boot : 8080</small></div>
                    <div v-if="isFullStack" class="dfm-field"><label for="sd-front-port">Frontend</label><input id="sd-front-port" v-model.number="ports.frontend" type="number" min="1" max="65535" /><small class="dfm-help">Nginx : 80. Vérifiez le port déclaré dans votre Dockerfile.</small></div>
                  </div>
                </div>

                <div class="dfm-group">
                  <h4>Emplacement du code dans le dépôt Git</h4>
                  <p class="dfm-help">Où Jenkins trouve le code et les Dockerfiles. Avec « . », il cherche à la racine du dépôt, puis dans les dossiers <code>backend</code> et <code>frontend</code>.</p>
                  <div class="dfm-grid">
                    <div class="dfm-field"><label for="sd-back-context">Dossier du backend</label><input id="sd-back-context" v-model="form.backendBuildContext" placeholder="." /><small class="dfm-help">Le dossier qui contient le fichier pom.xml</small></div>
                    <div class="dfm-field"><label for="sd-back-dockerfile">Dockerfile du backend</label><input id="sd-back-dockerfile" v-model="form.backendDockerfilePath" placeholder="Dockerfile" /><small class="dfm-help">Son nom, dans le dossier du backend</small></div>
                    <template v-if="isFullStack">
                      <div class="dfm-field dfm-wide"><label for="sd-front-repo">Dépôt Git du frontend</label><input id="sd-front-repo" v-model="form.frontendRepositoryUrl" /><small class="dfm-help">Gardez l’adresse de l’application si le frontend est dans le même dépôt.</small></div>
                      <div class="dfm-field"><label for="sd-front-branch">Branche du frontend</label><input id="sd-front-branch" v-model="form.frontendGitBranch" /><small class="dfm-help">Par défaut, la branche de la version choisie</small></div>
                      <div class="dfm-field"><label for="sd-front-context">Dossier du frontend</label><input id="sd-front-context" v-model="form.frontendBuildContext" placeholder="." /><small class="dfm-help">Le dossier qui contient le package.json</small></div>
                      <div class="dfm-field dfm-wide"><label for="sd-front-dockerfile">Dockerfile du frontend</label><input id="sd-front-dockerfile" v-model="form.frontendDockerfilePath" placeholder="Dockerfile" /><small class="dfm-help">Son nom, dans le dossier du frontend</small></div>
                    </template>
                  </div>
                </div>

                <div class="dfm-group">
                  <h4>Base de données</h4>
                  <div class="dfm-grid">
                    <div v-if="isManaged" class="dfm-field"><label for="sd-storage">Espace disque de la base (Gi)</label><input id="sd-storage" v-model.number="form.storageGi" type="number" min="1" /><small class="dfm-help">Réservé dans le cluster pour les données PostgreSQL</small></div>
                    <div class="dfm-field" :class="{ 'dfm-wide': !isManaged }"><label for="sd-secret">Stockage du mot de passe</label>
                      <select id="sd-secret" :value="secretMode === 'EXISTING' ? form.passwordSecretName : ''" :disabled="loadingSecrets || creatingSecret" @change="onSecretChoice">
                        <option value="">Créer un nouveau Secret Kubernetes</option>
                        <option v-for="secret in secrets" :key="secret.name" :value="secret.name">Réutiliser le Secret « {{ secret.name }} »</option>
                      </select>
                      <small class="dfm-help">Un Secret Kubernetes garde le mot de passe hors du code et des manifests.</small>
                    </div>
                    <div v-if="secretMode === 'NEW'" class="dfm-field"><label for="sd-new-secret-name">Nom du nouveau Secret</label><input id="sd-new-secret-name" v-model.trim="newSecret.name" /></div>
                    <div v-if="secretMode === 'EXISTING' && selectedSecret && selectedSecret.keys.length > 1" class="dfm-field"><label for="sd-secret-key">Clé du mot de passe dans ce Secret</label>
                      <select id="sd-secret-key" v-model="form.passwordSecretKey"><option v-for="key in selectedSecret.keys" :key="key" :value="key">{{ key }}</option></select>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <section class="dfm-section">
              <h3>Vérification</h3>
              <p class="dfm-lead">Relisez ce que SmartDeploy va déployer, puis lancez le déploiement.</p>
              <dl class="dfm-summary">
                <div><dt>Application</dt><dd>{{ selectedApplication.name }}, version {{ selectedVersion?.version }}</dd></div>
                <div><dt>Environnement</dt><dd>{{ selectedEnvironment.name }} <code>{{ namespace }}</code></dd></div>
                <div><dt>Architecture</dt><dd>{{ architectureLabel }}</dd></div>
                <div><dt>Backend</dt><dd><code>{{ backendName }}</code>, {{ form.backendReplicas }} instance(s), port {{ ports.backend }}<br /><code class="dfm-muted">{{ backendImage }}</code></dd></div>
                <div v-if="isFullStack"><dt>Frontend</dt><dd><code>{{ frontendName }}</code>, {{ form.frontendReplicas }} instance(s), port {{ ports.frontend }}<br /><code class="dfm-muted">{{ frontendImage }}</code></dd></div>
                <div><dt>PostgreSQL</dt><dd>{{ isManaged ? `Dans le cluster, ${form.storageGi} Gi` : `${form.externalHost}:${form.databasePort}` }}, base <code>{{ form.databaseName }}</code>, utilisateur <code>{{ form.databaseUser }}</code></dd></div>
                <div><dt>Mot de passe</dt><dd>{{ secretSummary }}</dd></div>
                <div><dt>Pipeline</dt><dd>{{ pipelineSummary }}</dd></div>
              </dl>
            </section>
          </template>

          <footer class="dfm-footer">
            <span class="dfm-muted">Étape {{ currentStep }} sur {{ wizardSteps.length }}</span>
            <div class="dfm-actions">
              <button v-if="currentStep > 1" type="button" class="dfm-btn dfm-btn-ghost" :disabled="loading || creatingSecret" @click="previousStep">Retour</button>
              <button v-else type="button" class="dfm-btn dfm-btn-ghost" :disabled="loading || creatingSecret" @click="closeModal">Annuler</button>
              <button v-if="currentStep < wizardSteps.length" type="button" class="dfm-btn dfm-btn-primary" :disabled="loading || loadingVersions || creatingSecret" @click="nextStep">Continuer</button>
              <button v-else type="submit" class="dfm-btn dfm-btn-primary" :disabled="loading || loadingVersions || loadingSecrets || creatingSecret">
                {{ loading ? 'Déploiement en cours…' : creatingSecret ? 'Création du Secret…' : 'Lancer le déploiement' }}
              </button>
            </div>
          </footer>
        </form>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.dfm-overlay {
  --ink: var(--color-gray-900, #111827);
  --muted: var(--color-gray-500, #6b7280);
  --line: var(--color-gray-200, #e5e7eb);
  --soft: var(--color-gray-50, #f9fafb);
  --accent: var(--color-primary-hover, #2563eb);
  --accent-soft: var(--color-primary-light, #f0f6ff);
  --danger: #b91c1c;
  --warn: #92400e;

  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 24px 16px;
  background: rgba(17, 24, 39, 0.45);
}

.dfm {
  display: flex;
  flex-direction: column;
  width: min(100%, 720px);
  max-height: calc(100dvh - 48px);
  overflow: hidden;
  border-radius: 14px;
  background: #fff;
  color: var(--ink);
  box-shadow: 0 24px 64px rgba(17, 24, 39, 0.22);
  font-size: 14px;
  line-height: 1.5;
}

/* En-tête */
.dfm-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 24px 28px 0;
}
.dfm-header h2 { margin: 0; font-size: 20px; font-weight: 600; letter-spacing: -0.01em; }
.dfm-header p { margin: 4px 0 0; color: var(--muted); font-size: 13px; }
.dfm-close {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  margin: -4px -8px 0 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
}
.dfm-close:hover { background: var(--soft); color: var(--ink); }

/* Barre d'étapes segmentée */
.dfm-steps {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  padding: 20px 28px 4px;
}
.dfm-step {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  padding: 0;
  border: 0;
  background: none;
  color: var(--muted);
  font: inherit;
  font-size: 12px;
  text-align: left;
  cursor: default;
}
.dfm-step-bar { height: 3px; border-radius: 2px; background: var(--line); transition: background-color 0.2s ease; }
.dfm-step-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.dfm-step.is-done { cursor: pointer; color: var(--ink); }
.dfm-step.is-done .dfm-step-bar { background: var(--accent); }
.dfm-step.is-current { color: var(--accent); font-weight: 600; }
.dfm-step.is-current .dfm-step-bar { background: var(--accent); }
.dfm-step:disabled { opacity: 1; }

/* Corps */
.dfm-body {
  display: flex;
  flex-direction: column;
  gap: 28px;
  min-height: 0;
  overflow-y: auto;
  padding: 24px 28px 0;
}
.dfm-section { display: flex; flex-direction: column; gap: 16px; }
.dfm-section h3 { margin: 0; font-size: 16px; font-weight: 600; }
.dfm-section h4 { margin: 8px 0 0; font-size: 13px; font-weight: 600; }
.dfm-lead { margin: -8px 0 0; color: var(--muted); font-size: 13px; }

.dfm-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.dfm-wide { grid-column: 1 / -1; }
.dfm-field { display: flex; flex-direction: column; gap: 6px; min-width: 0; }
.dfm-field label { font-size: 13px; font-weight: 500; }
.dfm-field input,
.dfm-field select {
  width: 100%;
  height: 40px;
  padding: 0 12px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: #fff;
  color: var(--ink);
  font: inherit;
  font-size: 14px;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}
.dfm-field input:hover,
.dfm-field select:hover { border-color: var(--color-gray-300, #d1d5db); }
.dfm-field input:focus,
.dfm-field select:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15); }
.dfm-field input:disabled,
.dfm-field select:disabled { background: var(--soft); color: var(--muted); cursor: not-allowed; }

.dfm-help { margin: 0; color: var(--muted); font-size: 12px; }
.dfm-muted { color: var(--muted); }
.dfm-warn { color: var(--warn); font-size: 12px; }
.dfm-error { color: var(--danger); font-size: 12px; }
.dfm-note { margin: 0; padding: 12px 14px; border-radius: 8px; background: var(--soft); color: var(--ink); font-size: 13px; }
.dfm-alert { margin: 0; padding: 12px 14px; border: 1px solid #fecaca; border-radius: 8px; background: #fef2f2; color: var(--danger); font-size: 13px; }
code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; overflow-wrap: anywhere; }

/* Faits déduits (étape 1) */
.dfm-facts { display: grid; gap: 8px; margin: 0; padding: 14px 16px; border-radius: 10px; background: var(--soft); }
.dfm-facts div { display: grid; grid-template-columns: 120px minmax(0, 1fr); gap: 12px; }
.dfm-facts dt { color: var(--muted); font-size: 13px; }
.dfm-facts dd { margin: 0; font-size: 13px; }

/* Choix d'architecture */
.dfm-choices { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; margin: 0; padding: 0; border: 0; min-width: 0; }
.dfm-sr { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
.dfm-choice {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px;
  border: 1px solid var(--line);
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;
}
.dfm-choice:hover { border-color: var(--color-gray-300, #d1d5db); }
.dfm-choice input { position: absolute; opacity: 0; pointer-events: none; }
.dfm-choice strong { font-size: 14px; font-weight: 600; }
.dfm-choice span { color: var(--muted); font-size: 12.5px; line-height: 1.45; }
.dfm-choice.is-selected { border-color: var(--accent); background: var(--accent-soft); box-shadow: inset 0 0 0 1px var(--accent); }
.dfm-choice:has(input:focus-visible) { outline: 2px solid var(--accent); outline-offset: 2px; }

/* Récapitulatif */
.dfm-summary { margin: 0; border-top: 1px solid var(--line); }
.dfm-summary div { display: grid; grid-template-columns: 140px minmax(0, 1fr); gap: 16px; padding: 11px 0; border-bottom: 1px solid var(--line); }
.dfm-summary dt { color: var(--muted); font-size: 13px; }
.dfm-summary dd { margin: 0; font-size: 13.5px; line-height: 1.6; }

/* Options avancées */
.dfm-disclosure {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 4px 0;
  border: 0;
  background: none;
  color: var(--ink);
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
}
.dfm-disclosure .dfm-muted { font-size: 12.5px; font-weight: 400; }
.dfm-disclosure svg { transition: transform 0.15s ease; }
.dfm-disclosure svg.is-open { transform: rotate(90deg); }
.dfm-advanced { gap: 6px; padding: 16px 18px; border: 1px solid var(--line); border-radius: 10px; }
.dfm-advanced-body { display: flex; flex-direction: column; gap: 20px; margin-top: 10px; padding-top: 16px; border-top: 1px solid var(--line); }
.dfm-group { display: flex; flex-direction: column; gap: 10px; }
.dfm-group h4 { margin: 0; }
.dfm-group > .dfm-help { margin-top: -4px; }
.dfm-block { display: block; margin-top: 2px; }
.dfm-check { align-items: flex-start; }
.dfm-check input { margin-top: 2px; flex-shrink: 0; }
.dfm-check { display: flex; align-items: center; gap: 10px; font-size: 13px; cursor: pointer; }
.dfm-check input { width: 16px; height: 16px; accent-color: var(--accent); }

/* Pied */
.dfm-footer {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin: 4px -28px 0;
  padding: 16px 28px;
  border-top: 1px solid var(--line);
  background: #fff;
  font-size: 13px;
}
.dfm-actions { display: flex; gap: 8px; }
.dfm-btn {
  height: 40px;
  padding: 0 18px;
  border-radius: 8px;
  font: inherit;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease;
}
.dfm-btn-ghost { border: 1px solid var(--line); background: #fff; color: var(--ink); }
.dfm-btn-ghost:hover:not(:disabled) { background: var(--soft); }
.dfm-btn-primary { border: 1px solid var(--accent); background: var(--accent); color: #fff; }
.dfm-btn-primary:hover:not(:disabled) { background: #1d4ed8; border-color: #1d4ed8; }
.dfm-btn:disabled { opacity: 0.55; cursor: not-allowed; }
.dfm-btn:focus-visible,
.dfm-close:focus-visible,
.dfm-step:focus-visible,
.dfm-disclosure:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }

@media (max-width: 680px) {
  .dfm-overlay { padding: 0; place-items: end center; }
  .dfm { width: 100%; max-height: 100dvh; border-radius: 14px 14px 0 0; }
  .dfm-header, .dfm-steps, .dfm-body { padding-left: 18px; padding-right: 18px; }
  .dfm-footer { margin: 4px -18px 0; padding: 14px 18px; }
  .dfm-grid, .dfm-choices { grid-template-columns: 1fr; }
  .dfm-wide { grid-column: auto; }
  .dfm-step-label { font-size: 11px; }
  .dfm-facts div, .dfm-summary div { grid-template-columns: 1fr; gap: 2px; }
}

@media (prefers-reduced-motion: reduce) {
  .dfm *, .dfm *::before, .dfm *::after { transition: none !important; }
}
</style>
