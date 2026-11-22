<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getWorkspace, checkCompatibility } from '@/api/portal.api'
import { getDeployments } from '@/api/deployments.api'
import { getApplications } from '@/api/applications.api'
import { getEnvironments } from '@/api/environments.api'
import { useAuthStore } from '@/stores/auth'
import { useDeploymentsStore } from '@/stores/deployments'
import DeploymentFormModal from '@/components/deployments/DeploymentFormModal.vue'
import DeploymentDiscussion from '@/components/deployments/DeploymentDiscussion.vue'
import NewApplicationDialog from '@/components/portal/NewApplicationDialog.vue'
import NewVersionDialog from '@/components/portal/NewVersionDialog.vue'
import NewEnvironmentDialog from '@/components/portal/NewEnvironmentDialog.vue'
import CompatibilityResult from '@/components/portal/CompatibilityResult.vue'
import type { CompatibilityReport, Workspace, WorkspaceApplication } from '@/types/portal'
import type { ApplicationResponse } from '@/types/application'
import type { EnvironmentResponse } from '@/types/environment'
import type { CreateDeploymentRequest, DeploymentResponse } from '@/types/deployment'

/*
 * « Mon espace » : l’espace de travail de l’utilisateur, organisé autour de SES applications.
 * Tout s’y fait sans changer de page : enregistrer une application (avec le contrôle du dépôt),
 * créer une version, demander un déploiement, suivre et discuter ses demandes.
 */
const auth = useAuthStore()
const deploymentsStore = useDeploymentsStore()
const isDeveloper = computed(() => auth.hasRole('DEVELOPER') && !auth.hasRole('DEVOPS', 'ADMIN'))
const firstName = computed(() => auth.currentUser?.firstName ?? '')

const workspace = ref<Workspace | null>(null)
const requests = ref<DeploymentResponse[]>([])
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    const [ws, deps] = await Promise.all([getWorkspace(), getDeployments({ mine: true, size: 8 })])
    workspace.value = ws
    requests.value = deps.content
  } catch { workspace.value = { applications: 0, inService: 0, pendingRequests: 0, items: [] } }
  finally { loading.value = false }
}
onMounted(load)

/* Assistant de déploiement, ouvert ici, prérempli avec l’application choisie */
const wizardOpen = ref(false)
const wizardApp = ref<number | null>(null)
const wizardApps = ref<ApplicationResponse[]>([])
const wizardEnvs = ref<EnvironmentResponse[]>([])
async function openWizard(applicationId: number | null) {
  const [apps, envs] = await Promise.all([getApplications({ mine: true, size: 100 }), getEnvironments({ size: 100 })])
  wizardApps.value = apps.content
  wizardEnvs.value = envs.content
  wizardApp.value = applicationId
  wizardOpen.value = true
}
async function submitRequest(request: CreateDeploymentRequest) {
  try {
    await deploymentsStore.create(request)
    wizardOpen.value = false
    await load()
  } catch { /* message affiché par le magasin */ }
}

/* Nouvelle application, nouvelle version, contrôle d’un dépôt, discussion */
const newAppOpen = ref(false)
const versionFor = ref<WorkspaceApplication | null>(null)
const environmentFor = ref<WorkspaceApplication | null>(null)
const checking = ref<number | null>(null)
const reports = ref<Record<number, CompatibilityReport | string>>({})
async function checkRepository(app: WorkspaceApplication) {
  checking.value = app.id
  try { reports.value[app.id] = await checkCompatibility(app.repositoryUrl, app.latestVersions[0]?.branch ?? 'main') }
  catch (exception) { reports.value[app.id] = (exception as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Vérification impossible.' }
  finally { checking.value = null }
}
const discussion = ref<DeploymentResponse | null>(null)

/* Affichage */
const ENV_TYPE: Record<string, string> = { DEVELOPMENT: 'Dév.', TEST: 'Test', STAGING: 'Pré-prod.', PREPRODUCTION: 'Pré-prod.', PRODUCTION: 'Prod.' }
const LAST: Record<string, { label: string; tone: string }> = {
  AWAITING_APPROVAL: { label: 'À approuver', tone: 'approval' }, PENDING: { label: 'En file', tone: 'muted' }, RUNNING: { label: 'En cours', tone: 'running' },
  FAILED: { label: 'Dernier échec', tone: 'critical' }, REJECTED: { label: 'Refusée', tone: 'critical' }, CANCELLED: { label: 'Annulée', tone: 'muted' },
}
function requestStatus(d: DeploymentResponse) {
  if (d.status === 'SUCCESS') return d.current ? { label: 'En service', tone: 'ok' } : { label: 'Réussie', tone: 'muted' }
  if (d.status === 'ROLLED_BACK') return { label: 'Remplacée', tone: 'muted' }
  return LAST[d.status] ?? { label: d.status, tone: 'muted' }
}
function relative(iso: string | null) {
  if (!iso) return ''
  const h = (Date.now() - new Date(iso).getTime()) / 3_600_000
  return h < 1 ? 'il y a moins d’une heure' : h < 24 ? `il y a ${Math.round(h)} h` : `il y a ${Math.round(h / 24)} j`
}
</script>

<template>
  <section class="me">
    <header class="me-head">
      <div>
        <h1>{{ firstName ? `Bonjour ${firstName}` : 'Mon espace' }}</h1>
        <p>Vos applications, leurs environnements et vos demandes de déploiement, au même endroit.</p>
      </div>
      <div class="me-actions">
        <button type="button" class="me-btn" @click="newAppOpen = true">Nouvelle application</button>
        <button type="button" class="me-btn me-btn-primary" :disabled="!workspace?.items.length" @click="openWizard(null)">
          {{ isDeveloper ? 'Demander un déploiement' : 'Nouveau déploiement' }}
        </button>
      </div>
    </header>

    <div v-if="workspace" class="me-stats">
      <div><strong>{{ workspace.applications }}</strong><span>application{{ workspace.applications > 1 ? 's' : '' }}</span></div>
      <div><strong>{{ workspace.inService }}</strong><span>environnement{{ workspace.inService > 1 ? 's' : '' }} en service</span></div>
      <div :class="{ 'is-pending': workspace.pendingRequests }"><strong>{{ workspace.pendingRequests }}</strong><span>demande{{ workspace.pendingRequests > 1 ? 's' : '' }} en attente d’accord</span></div>
    </div>

    <p v-if="loading" class="me-muted">Chargement de votre espace…</p>

    <!-- Aucun application : premier pas -->
    <section v-else-if="!workspace?.items.length" class="me-empty">
      <h2>Commencez par enregistrer votre application</h2>
      <p>SmartDeploy vérifiera d’abord votre dépôt GitHub, puis vous pourrez créer une version et demander son déploiement.</p>
      <ol>
        <li><b>Vérifier</b> le dépôt</li><li><b>Enregistrer</b> l’application</li><li><b>Créer</b> une version</li><li><b>Demander</b> un déploiement</li>
      </ol>
      <button type="button" class="me-btn me-btn-primary" @click="newAppOpen = true">Enregistrer mon application</button>
    </section>

    <!-- Mes applications -->
    <template v-else>
      <h2 class="me-section">Mes applications</h2>
      <div class="me-apps">
        <article v-for="app in workspace!.items" :key="app.id" class="me-app">
          <header>
            <div>
              <h3>{{ app.name }} <span v-if="!app.active" class="me-tag">inactive</span></h3>
              <a :href="app.repositoryUrl" target="_blank" rel="noopener" class="me-repo">{{ app.repositoryUrl.replace('https://github.com/', '') }}</a>
            </div>
            <button type="button" class="me-link" :disabled="checking === app.id" @click="checkRepository(app)">
              {{ checking === app.id ? 'Analyse…' : 'Vérifier le dépôt' }}
            </button>
          </header>

          <ul v-if="app.environments.length" class="me-envs">
            <li v-for="env in app.environments" :key="env.id" :class="{ 'is-live': env.currentVersion }">
              <span class="me-env-type">{{ ENV_TYPE[env.type ?? ''] ?? env.type }}</span>
              <strong>{{ env.name }}</strong>
              <span v-if="env.currentVersion" class="me-live">{{ env.currentVersion }} en service</span>
              <span v-else class="me-muted">rien en service</span>
              <span v-if="env.lastStatus && LAST[env.lastStatus]" class="me-pill" :class="`is-${LAST[env.lastStatus]?.tone}`">
                {{ LAST[env.lastStatus]?.label }} · {{ env.lastVersion }}
              </span>
            </li>
          </ul>
          <p v-else class="me-muted">Aucun environnement : ajoutez-en un de développement pour commencer.</p>

          <p v-if="app.latestVersions.length" class="me-versions">
            Versions : <code v-for="v in app.latestVersions" :key="v.id" :title="v.pinned ? 'Figée sur son commit' : 'Pas encore construite'">{{ v.version }}</code>
          </p>
          <p v-else class="me-versions">Aucune version : créez-en une pour pouvoir demander un déploiement.</p>

          <div v-if="reports[app.id]" class="me-report">
            <p v-if="typeof reports[app.id] === 'string'" class="me-error">{{ reports[app.id] }}</p>
            <CompatibilityResult v-else :report="reports[app.id] as CompatibilityReport" compact />
          </div>

          <footer>
            <button type="button" class="me-btn" @click="environmentFor = app">Ajouter un environnement</button>
            <button type="button" class="me-btn" @click="versionFor = app">Nouvelle version</button>
            <button type="button" class="me-btn me-btn-primary" :disabled="!app.environments.length || !app.latestVersions.length" @click="openWizard(app.id)">
              {{ isDeveloper ? 'Demander un déploiement' : 'Déployer' }}
            </button>
          </footer>
        </article>
      </div>

      <!-- Mes demandes -->
      <h2 class="me-section">Mes demandes</h2>
      <section class="me-card">
        <p v-if="!requests.length" class="me-muted">Aucune demande pour l’instant.</p>
        <table v-else class="me-table">
          <thead><tr><th>Application</th><th>Environnement</th><th>État</th><th>Date</th><th /></tr></thead>
          <tbody>
            <tr v-for="d in requests" :key="d.id">
              <td><strong>{{ d.applicationName }}</strong> <code>{{ d.version }}</code><small> #{{ d.id }}</small></td>
              <td>{{ d.environmentName }}</td>
              <td><span class="me-pill" :class="`is-${requestStatus(d).tone}`">{{ requestStatus(d).label }}</span></td>
              <td class="me-muted">{{ relative(d.createdAt) }}</td>
              <td class="me-right"><button type="button" class="me-link" @click="discussion = d">Discussion</button></td>
            </tr>
          </tbody>
        </table>
      </section>
    </template>

    <NewApplicationDialog :open="newAppOpen" @close="newAppOpen = false" @created="load" />
    <NewEnvironmentDialog :open="environmentFor !== null" :application-id="environmentFor?.id ?? null"
      :application-name="environmentFor?.name ?? ''" :existing-types="(environmentFor?.environments ?? []).map(e => e.type ?? '')"
      @close="environmentFor = null" @created="load" />
    <NewVersionDialog :open="versionFor !== null" :application-id="versionFor?.id ?? null" :application-name="versionFor?.name ?? ''"
      @close="versionFor = null" @created="load" />
    <DeploymentFormModal :open="wizardOpen" :applications="wizardApps" :environments="wizardEnvs" :loading="deploymentsStore.actionLoading"
      :initial-application-id="wizardApp" @close="wizardOpen = false" @submit="submitRequest" />
    <p v-if="deploymentsStore.errorMessage && wizardOpen" class="me-error me-floating">{{ deploymentsStore.errorMessage }}</p>

    <div v-if="discussion" class="me-backdrop" @click.self="discussion = null">
      <div class="me-dialog" role="dialog" aria-modal="true">
        <header>
          <strong>#{{ discussion.id }} · {{ discussion.applicationName }} {{ discussion.version }} · {{ discussion.environmentName }}</strong>
          <button type="button" class="me-close" aria-label="Fermer" @click="discussion = null">×</button>
        </header>
        <DeploymentDiscussion :deployment-id="discussion.id" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.me { display: flex; flex-direction: column; gap: 16px; }
.me-head { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 12px; }
.me-head h1 { margin: 0; font-size: 25px; }
.me-head p { margin: 4px 0 0; color: #6b7280; font-size: 14px; }
.me-actions { display: flex; gap: 8px; }
.me-btn { padding: 8px 14px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #111827; font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; }
.me-btn:disabled { opacity: .5; cursor: not-allowed; }
.me-btn-primary { border-color: #1e3a8a; background: #1e3a8a; color: #fff; }
.me-link { padding: 0; border: 0; background: none; color: #1e40af; font: inherit; font-size: 13.5px; font-weight: 600; cursor: pointer; }
.me-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; }
.me-stats div { display: flex; align-items: baseline; gap: 8px; padding: 14px 16px; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; }
.me-stats strong { font-size: 24px; }
.me-stats span { color: #6b7280; font-size: 13.5px; }
.me-stats .is-pending { border-color: #ddd6fe; background: #f5f3ff; }
.me-section { margin: 6px 0 -4px; color: #374151; font-size: 13px; font-weight: 700; letter-spacing: .05em; text-transform: uppercase; }
.me-muted { margin: 0; color: #6b7280; font-size: 13px; }
.me-empty { display: flex; flex-direction: column; align-items: flex-start; gap: 10px; padding: 24px; border: 1px dashed #c7d2fe; border-radius: 14px; background: #f8faff; }
.me-empty h2 { margin: 0; font-size: 18px; }
.me-empty p { margin: 0; color: #4b5563; }
.me-empty ol { display: flex; flex-wrap: wrap; gap: 8px 22px; margin: 0; padding-left: 18px; color: #374151; }
.me-apps { display: grid; grid-template-columns: repeat(auto-fill, minmax(380px, 1fr)); gap: 14px; }
.me-app { display: flex; flex-direction: column; gap: 10px; padding: 16px 18px; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; }
.me-app > header { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
.me-app h3 { margin: 0; font-size: 17px; }
.me-tag { padding: 1px 7px; border-radius: 999px; background: #f3f4f6; color: #6b7280; font-size: 11px; font-weight: 600; }
.me-repo { color: #6b7280; font-size: 12.5px; text-decoration: none; }
.me-repo:hover { color: #1e40af; text-decoration: underline; }
.me-envs { display: flex; flex-direction: column; gap: 6px; margin: 0; padding: 0; list-style: none; }
.me-envs li { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; padding: 8px 10px; border-radius: 8px; background: #f9fafb; font-size: 13.5px; }
.me-envs li.is-live { background: #f0fdf4; }
.me-env-type { min-width: 54px; padding: 1px 6px; border-radius: 5px; background: #e5e7eb; color: #374151; font-size: 11px; font-weight: 700; text-align: center; }
.me-live { color: #166534; font-weight: 600; }
.me-pill { margin-left: auto; padding: 2px 9px; border-radius: 999px; font-size: 12px; font-weight: 600; white-space: nowrap; }
.me-pill.is-approval { background: #ede9fe; color: #5b21b6; }
.me-pill.is-running { background: #dbeafe; color: #1e40af; }
.me-pill.is-critical { background: #fee2e2; color: #991b1b; }
.me-pill.is-ok { background: #dcfce7; color: #166534; }
.me-pill.is-muted { background: #f3f4f6; color: #4b5563; }
.me-versions { margin: 0; color: #6b7280; font-size: 13px; }
.me-versions code { margin-left: 6px; padding: 1px 6px; border-radius: 5px; background: #f3f4f6; color: #111827; font-size: 12px; }
.me-report { padding-top: 4px; border-top: 1px dashed #e5e7eb; }
.me-app > footer { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; margin-top: auto; }
.me-app > footer .me-btn { white-space: nowrap; padding: 7px 12px; font-size: 13.5px; }
.me-card { padding: 12px 16px; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; }
.me-table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
.me-table th { padding: 8px; border-bottom: 1px solid #e5e7eb; color: #6b7280; font-size: 12px; font-weight: 600; letter-spacing: .03em; text-align: left; text-transform: uppercase; }
.me-table td { padding: 10px 8px; border-bottom: 1px solid #f1f2f4; }
.me-table tbody tr:last-child td { border-bottom: 0; }
.me-table code { padding: 0 5px; border-radius: 4px; background: #f3f4f6; font-size: 12px; }
.me-table small { color: #9ca3af; }
.me-table .me-pill { margin-left: 0; }
.me-right { text-align: right; }
.me-error { margin: 0; padding: 9px 12px; border: 1px solid #fecaca; border-radius: 8px; background: #fef2f2; color: #991b1b; font-size: 13.5px; }
.me-floating { position: fixed; right: 20px; bottom: 20px; z-index: 70; }
.me-backdrop { position: fixed; inset: 0; z-index: 60; display: grid; place-items: center; padding: 16px; background: rgba(15, 23, 42, .45); }
.me-dialog { display: flex; flex-direction: column; gap: 10px; width: min(620px, 100%); padding: 16px 18px; border-radius: 14px; background: #fff; }
.me-dialog header { display: flex; justify-content: space-between; gap: 10px; }
.me-close { border: 0; background: none; color: #6b7280; font-size: 22px; cursor: pointer; }
</style>
