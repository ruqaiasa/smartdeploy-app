<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { abortCanary, approveDeployment, cancelDeployment, getCanary, getDeploymentById, getPromotion, promoteCanary, rejectDeployment, type CanaryView } from '@/api/deployments.api'
import { getIncidentAnalysis } from '@/api/incident.api'
import { getDeploymentMetrics } from '@/api/monitoring.api'
import type { ApplicationMetrics } from '@/types/monitoring'
import { getInsight, getLogs, getPods, getProgress, sendAnalysisFeedback } from '@/api/portal.api'
import { useAuthStore } from '@/stores/auth'
import DeploymentDiscussion from '@/components/deployments/DeploymentDiscussion.vue'
import ApprovalDialog from '@/components/deployments/ApprovalDialog.vue'
import EnvironmentCharts from '@/components/charts/EnvironmentCharts.vue'
import ProposeMeetingDialog from '@/components/meetings/ProposeMeetingDialog.vue'
import { openClaimsWidget } from '@/components/developer/claimsWidget'
import type { DeploymentResponse } from '@/types/deployment'
import type { IncidentAnalysis } from '@/types/incident'
import type { AnalysisInsight, DeploymentProgress, LogExcerpt, PodSummary } from '@/types/portal'

/*
 * Fiche de déploiement : tout ce que le développeur doit savoir, sans demander —
 * la progression réelle, l’explication d’un échec, l’adresse, les pods, les journaux et le fil.
 */
const route = useRoute()
const auth = useAuthStore()
const id = computed(() => Number(route.params.id))

const deployment = ref<DeploymentResponse | null>(null)
const progress = ref<DeploymentProgress | null>(null)
const analysis = ref<IncidentAnalysis | null>(null)
const insight = ref<AnalysisInsight | null>(null)
const pods = ref<PodSummary[]>([])
const metrics = ref<ApplicationMetrics | null>(null)
/* Déploiement progressif (canary) */
const canary = ref<CanaryView | null>(null)
const meetingOpen = ref(false)
const canaryBusy = ref(false)
const canaryRouter = useRouter()
async function promoteCanaryNow() {
  if (!window.confirm('Promouvoir : le pod canary est retiré et toute l’application passe à cette version (images réutilisées). Continuer ?')) return
  canaryBusy.value = true
  try { const created = await promoteCanary(id.value); await canaryRouter.push(`/deployments/${created.id}`) }
  catch (exception) { decisionError.value = (exception as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Promotion impossible.' }
  finally { canaryBusy.value = false }
}
async function abortCanaryNow() {
  const reason = window.prompt('Abandonner le déploiement progressif : pourquoi ?', 'Comportement anormal observé')
  if (reason === null) return
  canaryBusy.value = true
  try { await abortCanary(id.value, reason); await load() } finally { canaryBusy.value = false }
}
/* Étape suivante : la promotion vers l’environnement suivant, si la version y est prête. */
const promotion = ref<{ targetEnvironmentName: string; targetEnvironmentType: string } | null>(null)
const error = ref('')
let timer: ReturnType<typeof setInterval> | null = null

const OPEN = ['PENDING', 'RUNNING', 'AWAITING_APPROVAL']
const isOpen = computed(() => deployment.value !== null && OPEN.includes(deployment.value.status))

async function load() {
  try {
    const [d, p] = await Promise.all([getDeploymentById(id.value), getProgress(id.value)])
    deployment.value = d
    progress.value = p
    error.value = ''
    if (d.status === 'FAILED' && !analysis.value) {
      analysis.value = await getIncidentAnalysis(id.value).catch(() => null)
      insight.value = await getInsight(id.value).catch(() => null)
    }
    if (d.status === 'SUCCESS' || d.status === 'FAILED') pods.value = await getPods(id.value).catch(() => [])
    if (d.status === 'SUCCESS') metrics.value = await getDeploymentMetrics(id.value).catch(() => null)
    canary.value = await getCanary(id.value).catch(() => null)
    promotion.value = d.status === 'SUCCESS' && d.current ? await getPromotion(id.value).catch(() => null) : null
  } catch (exception) {
    const status = (exception as { response?: { status?: number } }).response?.status
    error.value = status === 403 ? 'Ce déploiement concerne une application qui ne vous appartient pas.' : 'Ce déploiement n’a pas pu être chargé.'
  }
}
function schedule() {
  if (timer) clearInterval(timer)
  timer = isOpen.value ? setInterval(load, 5000) : null
}
onMounted(async () => { await load(); schedule() })
watch(isOpen, schedule)
watch(id, async () => { analysis.value = null; await load(); schedule() })
onBeforeUnmount(() => { if (timer) clearInterval(timer) })

/* Journaux */
const selectedPod = ref('')
const previous = ref(false)
const errorsOnly = ref(false)
const logs = ref<LogExcerpt | null>(null)
const logsError = ref('')
const logsLoading = ref(false)
async function loadLogs() {
  if (!selectedPod.value) return
  logsLoading.value = true
  logsError.value = ''
  try { logs.value = await getLogs(id.value, selectedPod.value, previous.value, errorsOnly.value) }
  catch (exception) { logs.value = null; logsError.value = (exception as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Journaux indisponibles.' }
  finally { logsLoading.value = false }
}
watch(pods, list => { if (!selectedPod.value && list.length) { selectedPod.value = (list.find(p => p.component === 'Serveur') ?? list[0])!.name; void loadLogs() } })

/* Décision et retrait */
const canDecide = computed(() => deployment.value?.status === 'AWAITING_APPROVAL' && auth.hasRole('DEVOPS', 'ADMIN')
  && deployment.value.requestedById !== auth.currentUser?.id)
const canWithdraw = computed(() => deployment.value?.status === 'AWAITING_APPROVAL' && deployment.value.requestedById === auth.currentUser?.id)
const deciding = ref(false)
const decisionError = ref('')
const decisionOpen = ref(false)
async function decide(kind: 'approve' | 'reject', comment: string) {
  deciding.value = true
  decisionError.value = ''
  try {
    await (kind === 'approve' ? approveDeployment(id.value, comment) : rejectDeployment(id.value, comment))
    decisionOpen.value = false
    await load()
  } catch (exception) {
    decisionError.value = (exception as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'La décision n’a pas pu être enregistrée.'
  } finally { deciding.value = false }
}
async function withdraw() {
  if (!window.confirm('Retirer cette demande ?')) return
  await cancelDeployment(id.value).catch(() => undefined)
  await load()
}

/* Affichage */
const STATUS: Record<string, { label: string; tone: string }> = {
  AWAITING_APPROVAL: { label: 'En attente d’accord', tone: 'approval' }, PENDING: { label: 'En file', tone: 'muted' },
  RUNNING: { label: 'En cours', tone: 'running' }, SUCCESS: { label: 'Réussi', tone: 'ok' }, FAILED: { label: 'Échoué', tone: 'critical' },
  REJECTED: { label: 'Refusé', tone: 'critical' }, CANCELLED: { label: 'Annulé', tone: 'muted' }, ROLLED_BACK: { label: 'Remplacé', tone: 'muted' },
}
const status = computed(() => STATUS[deployment.value?.status ?? ''] ?? { label: deployment.value?.status ?? '', tone: 'muted' })
function minutes(seconds: number | null) {
  if (seconds === null) return ''
  if (seconds < 60) return `${seconds} s`
  return `${Math.floor(seconds / 60)} min ${String(seconds % 60).padStart(2, '0')} s`
}
const progressLine = computed(() => {
  const p = progress.value
  if (!p) return ''
  if (p.status === 'RUNNING') {
    const step = p.currentStage ? `Étape ${p.currentStageIndex + 1} sur ${p.totalStages} : ${p.currentStage}` : 'Démarrage du pipeline'
    const rest = p.estimatedRemainingSeconds !== null ? ` · ~${Math.max(1, Math.round(p.estimatedRemainingSeconds / 60))} min` : ''
    return step + rest
  }
  if (p.status === 'SUCCESS') return `Terminé en ${minutes(p.elapsedSeconds)}`
  if (p.status === 'FAILED') return p.currentStage ? `Arrêté à l’étape ${p.currentStageIndex + 1} : ${p.currentStage}` : 'Arrêté'
  if (p.status === 'AWAITING_APPROVAL') return 'En attente de validation'
  if (p.status === 'PENDING') return p.pendingReason ?? 'En file d’attente'
  return ''
})
const CONFIDENCE: Record<string, string> = { HIGH: 'élevée', MEDIUM: 'moyenne', LOW: 'faible' }
const HEALTH: Record<string, string> = { HEALTHY: 'Saine', WARNING: 'À surveiller', CRITICAL: 'Critique', UNKNOWN: 'Inconnue' }

/* Analyste v2 : retour de l’équipe sur la justesse de l’analyse */
const isTeam = computed(() => auth.hasRole('DEVOPS', 'ADMIN'))
const isDeveloperUser = computed(() => auth.hasRole('DEVELOPER') && !auth.hasRole('VIEWER', 'DEVOPS', 'ADMIN'))
const feedbackSent = ref('')
async function feedback(verdict: 'CORRECT' | 'PARTIAL' | 'WRONG') {
  const actual = verdict === 'CORRECT' ? '' : (window.prompt('Quelle était la vraie cause ?') ?? '')
  try { await sendAnalysisFeedback(id.value, verdict, actual); feedbackSent.value = verdict } catch { feedbackSent.value = '' }
}
const FEEDBACK_LABEL: Record<string, string> = { CORRECT: 'juste', PARTIAL: 'partielle', WRONG: 'fausse' }
const reportLink = computed(() => deployment.value
  ? `/claims?new=1&applicationId=${deployment.value.applicationId}&deploymentId=${deployment.value.id}` : '/claims?new=1')
</script>

<template>
  <section class="fd">
    <RouterLink to="/deployments" class="fd-back">← Déploiements</RouterLink>
    <p v-if="error" class="fd-error" role="alert">{{ error }}</p>

    <template v-if="deployment">
      <!-- En-tête -->
      <header class="fd-head">
        <div>
          <p class="fd-kicker">Déploiement #{{ deployment.id }}</p>
          <h1>{{ deployment.applicationName }} <code>{{ deployment.version }}</code></h1>
          <p class="fd-meta">
            {{ deployment.environmentName }} · demandé par {{ deployment.requestedByEmail }}
            <template v-if="deployment.approvedBy"> · décision de {{ deployment.approvedBy }}</template>
          </p>
        </div>
        <div class="fd-head-actions">
          <span class="fd-status" :class="`is-${status.tone}`">{{ status.label }}</span>
          <button v-if="canDecide" type="button" class="fd-btn fd-btn-primary" @click="decisionOpen = true">Décider</button>
          <button v-if="canWithdraw" type="button" class="fd-btn" @click="withdraw">Retirer</button>
          <button v-if="isDeveloperUser" type="button" class="fd-btn" @click="openClaimsWidget({ applicationId: deployment.applicationId, deploymentId: deployment.id })">Signaler</button>
          <RouterLink v-else :to="reportLink" class="fd-btn">Signaler</RouterLink>
          <RouterLink v-if="deployment.status === 'FAILED' || deployment.status === 'ROLLED_BACK'" :to="`/rapport/${deployment.id}`" class="fd-btn" title="Post-mortem généré automatiquement">Rapport d’incident</RouterLink>
          <button type="button" class="fd-btn" title="Proposer des créneaux de visioconférence" @click="meetingOpen = true">Planifier une visio</button>
        </div>
      </header>

      <blockquote v-if="deployment.requestMessage" class="fd-message">{{ deployment.requestMessage }}</blockquote>

      <!-- Progression -->
      <section v-if="progress" class="fd-card">
        <h2>Progression</h2>
        <ol class="fd-phases">
          <li v-for="(p, i) in progress.phases" :key="p.key" :class="`is-${p.status.toLowerCase()}`">
            <span class="fd-dot">{{ p.status === 'DONE' ? '✓' : p.status === 'FAILED' ? '!' : i + 1 }}</span>
            <span>{{ p.label }}</span>
          </li>
        </ol>
        <p class="fd-progress-line">{{ progressLine }}</p>
        <p v-if="progress.source === 'unavailable'" class="fd-muted">Jenkins injoignable.</p>
      </section>

      <!-- Déploiement progressif (canary) -->
      <section v-if="canary" class="fd-card fd-canary" :class="`is-${canary.state.toLowerCase()}`">
        <header class="fd-canary-head">
          <h2>Déploiement progressif (canary)</h2>
          <span class="fd-status" :class="canary.state === 'OBSERVING' ? 'is-running' : canary.state === 'PROMOTED' ? 'is-ok' : 'is-muted'">
            {{ canary.state === 'OBSERVING' ? (canary.active ? 'En observation' : 'En préparation') : canary.state === 'PROMOTED' ? 'Promu' : 'Abandonné' }}
          </span>
        </header>
        <template v-if="canary.state === 'OBSERVING' && canary.active">
          <p>Un pod de cette version reçoit environ <strong>{{ canary.trafficPercent }} %</strong> du trafic, à côté de {{ canary.stableReplicas }} pod(s) de la version stable. Le gardien l’observe : s’il la juge dégradée, il abandonne le canary automatiquement.</p>
          <div class="fd-traffic" :aria-label="`${canary.trafficPercent} % vers le canary`">
            <span class="fd-traffic-stable" :style="{ width: `${100 - canary.trafficPercent}%` }">Version stable · {{ 100 - canary.trafficPercent }} %</span>
            <span class="fd-traffic-canary" :style="{ width: `${canary.trafficPercent}%` }">Canary · {{ canary.trafficPercent }} %</span>
          </div>
          <div v-if="isTeam" class="fd-canary-actions">
            <button type="button" class="fd-btn fd-btn-primary" :disabled="canaryBusy" @click="promoteCanaryNow">Promouvoir</button>
            <button type="button" class="fd-btn" :disabled="canaryBusy" @click="abortCanaryNow">Abandonner</button>
          </div>
        </template>
        <p v-else-if="canary.state === 'OBSERVING'">Le pod canary sera lancé à la fin du pipeline, à côté de la version en service.</p>
        <p v-else class="fd-muted">{{ canary.note }}</p>
      </section>

      <!-- Étape suivante : la promotion -->
      <section v-if="promotion" class="fd-card fd-next">
        <div>
          <h2>Étape suivante</h2>
          <p v-if="promotion.targetEnvironmentType === 'PRODUCTION'">Cette version est validée : vous pouvez demander sa <strong>mise en production</strong>. Un DevOps examinera la demande et réalisera la promotion, avec les mêmes images.</p>
          <p v-else>Cette version peut passer vers <strong>{{ promotion.targetEnvironmentName }}</strong>, avec les mêmes images (sans reconstruction).</p>
        </div>
        <RouterLink :to="{ path: '/deployments', query: { promote: String(deployment.id) } }" class="fd-btn fd-btn-primary">
          {{ promotion.targetEnvironmentType === 'PRODUCTION' ? 'Demander la mise en production' : `Promouvoir vers ${promotion.targetEnvironmentName}` }}
        </RouterLink>
      </section>

      <!-- Explication d'un échec -->
      <section v-if="deployment.status === 'FAILED'" class="fd-card fd-failure">
        <h2>Cause</h2>
        <template v-if="analysis">
          <p class="fd-cause"><strong>{{ analysis.title }}</strong> <span class="fd-muted">· confiance {{ CONFIDENCE[insight?.learning?.calibratedConfidence ?? analysis.confidence] ?? analysis.confidence }}</span></p>
          <p v-if="insight?.learning && insight.learning.reviews" class="fd-learning">{{ insight.learning.note }}</p>
          <p>{{ analysis.explanation }}</p>
          <h3>À faire</h3>
          <p>{{ analysis.action }}</p>
          <pre v-if="analysis.decisiveLine" class="fd-decisive">{{ analysis.decisiveLine }}</pre>
          <div v-if="insight && insight.kind !== 'NONE'" class="fd-insight" :class="`is-${insight.kind.toLowerCase()}`">
            <h3>Changement de code</h3>
            <p>{{ insight.verdict }}</p>
            <p v-if="insight.baseVersion" class="fd-muted">Depuis {{ insight.baseVersion }}<template v-if="insight.changedFiles.length"> · {{ insight.changedFiles.length }} fichier(s)</template></p>
            <ul v-if="insight.commits.length" class="fd-commits">
              <li v-for="c in insight.commits" :key="c.sha"><code>{{ c.sha.slice(0, 7) }}</code> {{ c.message }} <span class="fd-muted">· {{ c.author }}</span></li>
            </ul>
          </div>
          <div v-if="insight?.knownFix" class="fd-fix">
            <h3>Ce qui a marché la dernière fois</h3>
            <p>{{ insight.knownFix.resolution }}</p>
            <p class="fd-muted">
              Incident #{{ insight.knownFix.deploymentId }} ({{ insight.knownFix.version }}, {{ insight.knownFix.environmentName }}), similaire à {{ insight.knownFix.similarity }} %
              <template v-if="insight.knownFix.confirmed"> · cause confirmée par l’équipe</template>
            </p>
          </div>
          <div v-if="insight?.suggestions.length" class="fd-kb">
            <h3>Solutions connues</h3>
            <p v-for="a in insight.suggestions" :key="a.id"><strong>{{ a.title }}</strong> : {{ a.solution }}</p>
          </div>
          <div v-if="isTeam" class="fd-feedback">
            <template v-if="feedbackSent || insight?.feedback">Avis : {{ FEEDBACK_LABEL[feedbackSent || insight?.feedback || ''] }}</template>
            <template v-else>
              Analyse :
              <button type="button" class="fd-chip" @click="feedback('CORRECT')">Juste</button>
              <button type="button" class="fd-chip" @click="feedback('PARTIAL')">Partielle</button>
              <button type="button" class="fd-chip" @click="feedback('WRONG')">Fausse</button>
            </template>
          </div>
          <div v-if="analysis.similar?.length" class="fd-similar">
            <h3>Déjà vu</h3>
            <p v-for="s in analysis.similar" :key="s.deploymentId" class="fd-muted">
              #{{ s.deploymentId }} ({{ s.version }}, {{ s.environmentName }}) · {{ s.similarity }} % · {{ s.resolution }}
            </p>
          </div>
        </template>
        <p v-else class="fd-muted">{{ deployment.errorMessage ?? 'Analyse en cours…' }}</p>
      </section>

      <!-- Accès et santé -->
      <section v-if="deployment.status === 'SUCCESS' || pods.length" class="fd-card">
        <h2>Application</h2>
        <div v-if="metrics" class="fd-health">
          <div :class="`is-${(metrics.healthStatus ?? '').toLowerCase()}`"><span>Santé</span><strong>{{ HEALTH[metrics.healthStatus] ?? metrics.healthStatus }}</strong></div>
          <div><span>Pods prêts</span><strong>{{ metrics.runningPods < 0 ? '—' : metrics.runningPods }} / {{ metrics.desiredPods }}</strong></div>
          <div><span>Processeur</span><strong>{{ metrics.cpuUsagePercent.toFixed(1) }} %</strong></div>
          <div><span>Mémoire</span><strong>{{ Math.round(metrics.memoryUsageMb) }} Mo</strong></div>
          <div><span>Redémarrages</span><strong>{{ metrics.restartCount }}</strong></div>
        </div>
        <p v-if="progress?.applicationUrl">Adresse : <a :href="progress.applicationUrl" target="_blank" rel="noopener">{{ progress.applicationUrl }}</a></p>
        <div v-else class="fd-access">
          <p><strong>Pas d’adresse publique</strong> : l’application n’est joignable qu’à l’intérieur du cluster (aucun nom d’hôte saisi dans l’assistant).</p>
          <template v-if="progress?.accessService">
            <p class="fd-muted">Pour l’ouvrir quand même, sur la machine du cluster :</p>
            <pre class="fd-decisive">kubectl -n {{ progress.namespace }} port-forward --address 0.0.0.0 svc/{{ progress.accessService }} 8090:{{ progress.accessPort ?? 8080 }}</pre>
            <p class="fd-muted">puis ouvrir l’adresse de cette machine sur le port 8090.</p>
          </template>
        </div>
        <table v-if="pods.length" class="fd-table">
          <thead><tr><th>Composant</th><th>Pod</th><th>Prêt</th><th>Redémarrages</th><th>Dernier arrêt</th></tr></thead>
          <tbody>
            <tr v-for="p in pods" :key="p.name">
              <td>{{ p.component }}</td><td><code>{{ p.name }}</code></td>
              <td><span class="fd-status" :class="p.ready ? 'is-ok' : 'is-critical'">{{ p.ready ? 'Oui' : 'Non' }}</span></td>
              <td>{{ p.restarts }}</td><td class="fd-muted">{{ p.lastTermination ?? '—' }}</td>
            </tr>
          </tbody>
        </table>
        <p v-if="deployment.builtCommit" class="fd-muted">Commit construit : <code>{{ deployment.builtCommit.slice(0, 12) }}</code></p>
      </section>

      <!-- Courbes de l'environnement -->
      <section v-if="deployment.status === 'SUCCESS' && deployment.environmentId" class="fd-card">
        <h2>Courbes</h2>
        <EnvironmentCharts :environment-id="deployment.environmentId" compact />
      </section>

      <!-- Journaux -->
      <section v-if="pods.length" class="fd-card">
        <h2>Journaux</h2>
        <div class="fd-logbar">
          <select v-model="selectedPod" aria-label="Pod" @change="loadLogs">
            <option v-for="p in pods" :key="p.name" :value="p.name">{{ p.component }} · {{ p.name }}</option>
          </select>
          <label><input v-model="errorsOnly" type="checkbox" @change="loadLogs" /> Erreurs seulement</label>
          <label><input v-model="previous" type="checkbox" @change="loadLogs" /> Avant redémarrage</label>
          <button type="button" class="fd-btn" :disabled="logsLoading" @click="loadLogs">Actualiser</button>
        </div>
        <p v-if="logsError" class="fd-muted">{{ logsError }}</p>
        <pre v-else-if="logs" class="fd-logs">{{ logs.lines.length ? logs.lines.join('\n') : 'Aucune ligne.' }}</pre>
        <p class="fd-muted">Secrets masqués.</p>
      </section>

      <!-- Fil de la demande -->
      <div id="discussion"><DeploymentDiscussion :deployment-id="deployment.id" /></div>

      <ProposeMeetingDialog :open="meetingOpen" :deployment-id="deployment.id" :default-topic="`Déploiement #${deployment.id} : ${deployment.applicationName} ${deployment.version}`"
        @close="meetingOpen = false" @proposed="meetingOpen = false; canaryRouter.push('/meetings')" />

      <ApprovalDialog :open="decisionOpen" :deployment="deployment" :loading="deciding" :error="decisionError"
        @close="decisionOpen = false" @approve="decide('approve', $event)" @reject="decide('reject', $event)" />
    </template>
  </section>
</template>

<style scoped>
.fd { display: flex; flex-direction: column; gap: 14px; max-width: 1080px; }
.fd-back { color: #1e40af; font-size: 13.5px; font-weight: 600; text-decoration: none; }
.fd-error { margin: 0; padding: 10px 14px; border: 1px solid #fecaca; border-radius: 10px; background: #fef2f2; color: #991b1b; }
.fd-head { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 12px; }
.fd-kicker { margin: 0; color: #4338ca; font-size: 12px; font-weight: 700; letter-spacing: .05em; text-transform: uppercase; }
.fd-head h1 { margin: 2px 0; font-size: 24px; }
.fd-head h1 code { padding: 1px 8px; border-radius: 6px; background: #f3f4f6; font-size: 17px; }
.fd-meta { margin: 0; color: #6b7280; font-size: 13.5px; }
.fd-head-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.fd-btn { padding: 8px 14px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #111827; font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; }
.fd-btn-primary { border-color: #1e3a8a; background: #1e3a8a; color: #fff; }
.fd-btn:disabled { opacity: .5; cursor: wait; }
.fd-status { display: inline-block; padding: 3px 10px; border-radius: 999px; font-size: 12.5px; font-weight: 700; }
.is-ok.fd-status { background: #dcfce7; color: #166534; }
.is-critical.fd-status { background: #fee2e2; color: #991b1b; }
.is-running.fd-status { background: #dbeafe; color: #1e40af; }
.is-approval.fd-status { background: #ede9fe; color: #5b21b6; }
.is-muted.fd-status { background: #f3f4f6; color: #4b5563; }
.fd-message { margin: 0; padding: 10px 14px; border-left: 4px solid #7c3aed; border-radius: 8px; background: #f5f3ff; color: #312e81; font-size: 14px; }
.fd-card { display: flex; flex-direction: column; gap: 8px; padding: 16px 18px; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; }
.fd-card h2 { margin: 0; font-size: 16px; }
.fd-card h3 { margin: 6px 0 0; font-size: 14px; }
.fd-card p { margin: 0; font-size: 14px; }
.fd-muted { color: #6b7280; font-size: 13px !important; }
.fd-phases { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; margin: 4px 0 0; padding: 0; list-style: none; }
.fd-phases li { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 10px 6px; border-radius: 10px; background: #f9fafb; color: #6b7280; font-size: 12.5px; font-weight: 600; text-align: center; }
.fd-dot { display: grid; place-items: center; width: 26px; height: 26px; border-radius: 999px; background: #e5e7eb; color: #4b5563; font-size: 12.5px; }
.fd-phases .is-done { background: #f0fdf4; color: #166534; }
.fd-phases .is-done .fd-dot { background: #16a34a; color: #fff; }
.fd-phases .is-current { background: #eff6ff; color: #1e40af; }
.fd-phases .is-current .fd-dot { background: #2563eb; color: #fff; }
.fd-phases .is-failed { background: #fef2f2; color: #991b1b; }
.fd-phases .is-failed .fd-dot { background: #dc2626; color: #fff; }
.fd-progress-line { color: #374151; font-weight: 600; }
.fd-failure { border-color: #fecaca; }
.fd-cause strong { color: #991b1b; }
.fd-decisive, .fd-logs { margin: 0; padding: 10px 12px; border-radius: 8px; background: #0f172a; color: #e2e8f0; font-size: 12.5px; white-space: pre-wrap; word-break: break-word; }
.fd-logs { max-height: 360px; overflow: auto; }
.fd-table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
.fd-table th { padding: 7px 8px; border-bottom: 1px solid #e5e7eb; color: #6b7280; font-size: 12px; text-align: left; text-transform: uppercase; }
.fd-table td { padding: 8px; border-bottom: 1px solid #f1f2f4; }
.fd-logbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; font-size: 13.5px; }
.fd-logbar select { padding: 7px 9px; border: 1px solid #e5e7eb; border-radius: 8px; font: inherit; font-size: 13.5px; }
@media (max-width: 760px) { .fd-phases { grid-template-columns: 1fr; } }
a.fd-btn { text-decoration: none; }
.fd-canary { border-color: #c7d2fe; }
.fd-canary-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.fd-traffic { display: flex; overflow: hidden; height: 30px; border-radius: 10px; font-size: 12px; font-weight: 800; color: #fff; }
.fd-traffic span { display: flex; align-items: center; justify-content: center; white-space: nowrap; overflow: hidden; }
.fd-traffic-stable { background: linear-gradient(90deg, #16a34a, #22c55e); }
.fd-traffic-canary { background: linear-gradient(90deg, #f59e0b, #f97316); }
.fd-canary-actions { display: flex; gap: 8px; }
.fd-next { flex-direction: row; flex-wrap: wrap; align-items: center; justify-content: space-between; border-color: #c7d2fe; background: #f8faff; }
.fd-next p { max-width: 640px; }
.fd-learning { padding: 6px 10px; border-radius: 8px; background: #f5f3ff; color: #5b21b6; font-size: 13px !important; }
.fd-fix { display: flex; flex-direction: column; gap: 4px; padding: 10px 12px; border-left: 4px solid #16a34a; border-radius: 10px; background: #f0fdf4; }
.fd-health { display: grid; grid-template-columns: repeat(5, 1fr); gap: 8px; }
.fd-health div { display: flex; flex-direction: column; gap: 2px; padding: 10px 12px; border-radius: 10px; background: #f9fafb; }
.fd-health span { color: #6b7280; font-size: 12px; }
.fd-health strong { font-size: 16px; }
.fd-health .is-healthy { background: #f0fdf4; color: #166534; }
.fd-health .is-warning { background: #fffbeb; color: #92400e; }
.fd-health .is-critical { background: #fef2f2; color: #991b1b; }
@media (max-width: 760px) { .fd-health { grid-template-columns: 1fr 1fr; } }
.fd-insight { display: flex; flex-direction: column; gap: 4px; padding: 10px 12px; border-radius: 10px; background: #f8fafc; border-left: 4px solid #94a3b8; }
.fd-insight.is-code { background: #fef2f2; border-left-color: #dc2626; }
.fd-insight.is-environment { background: #fffbeb; border-left-color: #d97706; }
.fd-commits { margin: 2px 0 0; padding-left: 18px; font-size: 13px; }
.fd-kb { display: flex; flex-direction: column; gap: 4px; padding: 10px 12px; border-radius: 10px; background: #f0fdf4; }
.fd-feedback { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; color: #374151; font-size: 13.5px; }
.fd-chip { padding: 3px 12px; border: 1px solid #e5e7eb; border-radius: 999px; background: #fff; font: inherit; font-size: 13px; font-weight: 600; cursor: pointer; }
</style>
