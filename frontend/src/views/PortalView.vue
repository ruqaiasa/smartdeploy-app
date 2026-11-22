<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getWorkspace } from '@/api/portal.api'
import { getDeployments } from '@/api/deployments.api'
import { getClaims } from '@/api/claims.api'
import { useAuthStore } from '@/stores/auth'
import { openClaimsWidget } from '@/components/developer/claimsWidget'
import type { Workspace } from '@/types/portal'
import type { DeploymentResponse } from '@/types/deployment'

// Le portail ne modifie aucune donnée et utilise les API déjà présentes.
const auth = useAuthStore()
const workspace = ref<Workspace | null>(null)
const deployments = ref<DeploymentResponse[]>([])
const tasks = ref<Array<{ id: string; title: string; detail: string; to?: string; kind: 'claim' | 'deployment' }>>([])
const loading = ref(false)
const error = ref('')
const updatedAt = ref<Date | null>(null)
const firstName = computed(() => auth.currentUser?.firstName?.trim() || '')
const pending = computed(() => workspace.value?.pendingRequests ?? 0)
const recentFailures = computed(() => deployments.value.filter(d => d.status === 'FAILED').length)
const statusMap: Record<string, { label: string; tone: string }> = {
  AWAITING_APPROVAL: { label: 'À valider', tone: 'pending' },
  PENDING: { label: 'En attente', tone: 'neutral' },
  RUNNING: { label: 'En cours', tone: 'active' },
  SUCCESS: { label: 'Réussi', tone: 'success' },
  FAILED: { label: 'Échec', tone: 'danger' },
  REJECTED: { label: 'Refusé', tone: 'danger' },
  CANCELLED: { label: 'Annulé', tone: 'neutral' },
  ROLLED_BACK: { label: 'Remplacé', tone: 'neutral' },
}
function displayStatus(d: DeploymentResponse) {
  return d.status === 'SUCCESS' && d.current
    ? { label: 'En service', tone: 'success' }
    : statusMap[d.status] ?? { label: d.status, tone: 'neutral' }
}
function displayDate(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString('fr-FR', { day: '2-digit', month: 'short' })
}
async function load() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  try {
    const [ws, deps, claims] = await Promise.all([
      getWorkspace(),
      getDeployments({ mine: true, size: 6 }),
      getClaims().catch(() => []),
    ])
    workspace.value = ws
    deployments.value = deps?.content ?? []
    const claimTasks = claims
      .filter(c => c.status === 'WAITING_REQUESTER' || c.status === 'RESOLVED')
      .map(c => ({
        id: `claim-${c.id}`,
        title: c.title,
        detail: c.status === 'WAITING_REQUESTER' ? 'Réponse attendue' : 'Confirmation attendue',
        kind: 'claim' as const,
      }))
    const failedTasks = deployments.value
      .filter(d => d.status === 'FAILED')
      .slice(0, 3)
      .map(d => ({
        id: `deployment-${d.id}`,
        title: d.applicationName,
        detail: `Déploiement échoué · ${d.environmentName}`,
        to: `/deployments/${d.id}`,
        kind: 'deployment' as const,
      }))
    tasks.value = [...claimTasks, ...failedTasks].slice(0, 5)
    updatedAt.value = new Date()
  } catch {
    error.value = 'Données indisponibles.'
  } finally {
    loading.value = false
  }
}
onMounted(() => { void load() })
</script>

<template>
  <section class="developer-home" aria-label="Accueil développeur">
    <header class="developer-home__header">
      <div class="developer-home__heading">
        <span class="developer-home__eyebrow">MON ESPACE</span>
        <h1>Bonjour{{ firstName ? `, ${firstName}` : '' }}</h1>
        <p>Retrouvez vos applications et suivez vos demandes.</p>
      </div>
      <div class="developer-home__header-actions">
        <button type="button" class="developer-home__button developer-home__button--outline" :disabled="loading" @click="load">
          {{ loading ? 'Actualisation…' : 'Actualiser' }}
        </button>
        <RouterLink to="/deployments" class="developer-home__button developer-home__button--primary">
          <svg viewBox="0 0 20 20" aria-hidden="true"><path d="M10 4v12M4 10h12" /></svg>
          Demander un déploiement
        </RouterLink>
      </div>
    </header>

    <div v-if="error" class="developer-home__error" role="alert">
      <span>{{ error }}</span>
      <button type="button" @click="load">Réessayer</button>
    </div>

    <section class="developer-home__overview" aria-label="Vue d’ensemble" :aria-busy="loading">
      <RouterLink to="/applications" class="developer-home__metric">
        <span class="developer-home__metric-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24"><rect x="3" y="5" width="18" height="14" rx="2" /><path d="M3 10h18" /></svg>
        </span>
        <span class="developer-home__metric-content"><span>Applications</span><strong>{{ loading || error ? '—' : (workspace?.applications ?? 0) }}</strong></span>
        <span class="developer-home__metric-arrow" aria-hidden="true">›</span>
      </RouterLink>
      <RouterLink to="/environments" class="developer-home__metric">
        <span class="developer-home__metric-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24"><path d="M4 7h16v10H4zM8 11h.01M12 11h.01M16 11h.01M7 17v3M17 17v3" /></svg>
        </span>
        <span class="developer-home__metric-content"><span>En service</span><strong>{{ loading || error ? '—' : (workspace?.inService ?? 0) }}</strong></span>
        <span class="developer-home__metric-arrow" aria-hidden="true">›</span>
      </RouterLink>
      <RouterLink to="/deployments" class="developer-home__metric" :class="{ 'developer-home__metric--attention': pending > 0 }">
        <span class="developer-home__metric-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></svg>
        </span>
        <span class="developer-home__metric-content"><span>Demandes en attente</span><strong>{{ loading || error ? '—' : pending }}</strong></span>
        <span class="developer-home__metric-arrow" aria-hidden="true">›</span>
      </RouterLink>
    </section>

    <div class="developer-home__body">
      <section class="developer-home__panel developer-home__panel--activity">
        <div class="developer-home__panel-head">
          <div><h2>Déploiements récents</h2><p>Vos dernières opérations</p></div>
          <RouterLink to="/deployments" class="developer-home__text-link">Tout voir</RouterLink>
        </div>
        <p v-if="loading" class="developer-home__state">Chargement…</p>
        <p v-else-if="error" class="developer-home__state">Données indisponibles.</p>
        <div v-else-if="!deployments.length" class="developer-home__empty">
          <strong>Aucun déploiement</strong>
          <p>Votre historique apparaîtra ici.</p>
          <RouterLink to="/deployments">Commencer</RouterLink>
        </div>
        <div v-else class="developer-home__table-wrap">
          <table class="developer-home__table">
            <thead><tr><th>Application</th><th>Environnement</th><th>Statut</th><th>Date</th><th><span class="developer-home__sr">Ouvrir</span></th></tr></thead>
            <tbody>
              <tr v-for="d in deployments" :key="d.id">
                <td><RouterLink :to="`/deployments/${d.id}`" class="developer-home__app">{{ d.applicationName }}</RouterLink><span class="developer-home__version">{{ d.version }}</span></td>
                <td class="developer-home__muted">{{ d.environmentName }}</td>
                <td><span class="developer-home__status" :class="`developer-home__status--${displayStatus(d).tone}`">{{ displayStatus(d).label }}</span></td>
                <td class="developer-home__muted developer-home__date">{{ displayDate(d.createdAt) }}</td>
                <td><RouterLink :to="`/deployments/${d.id}`" class="developer-home__open" :aria-label="`Voir le déploiement de ${d.applicationName}`">Voir</RouterLink></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>

      <aside class="developer-home__panel developer-home__panel--tasks">
        <div class="developer-home__panel-head">
          <div><h2>À traiter</h2><p>Uniquement les actions utiles</p></div>
          <span v-if="!loading && !error && tasks.length" class="developer-home__task-count">{{ tasks.length }}</span>
        </div>
        <p v-if="loading" class="developer-home__state">Chargement…</p>
        <p v-else-if="error" class="developer-home__state">Données indisponibles.</p>
        <div v-else-if="!tasks.length" class="developer-home__quiet">
          <span class="developer-home__quiet-icon" aria-hidden="true"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/></svg></span>
          <strong>Tout est à jour</strong>
          <p>Aucune action nécessaire.</p>
        </div>
        <ul v-else class="developer-home__task-list">
          <li v-for="task in tasks" :key="task.id">
            <RouterLink v-if="task.to" :to="task.to" class="developer-home__task">
              <span class="developer-home__task-indicator developer-home__task-indicator--danger" aria-hidden="true" />
              <span class="developer-home__task-copy"><strong>{{ task.title }}</strong><small>{{ task.detail }}</small></span>
              <span class="developer-home__task-chevron" aria-hidden="true">›</span>
            </RouterLink>
            <button v-else type="button" class="developer-home__task" @click="openClaimsWidget()">
              <span class="developer-home__task-indicator" aria-hidden="true" />
              <span class="developer-home__task-copy"><strong>{{ task.title }}</strong><small>{{ task.detail }}</small></span>
              <span class="developer-home__task-chevron" aria-hidden="true">›</span>
            </button>
          </li>
        </ul>
        <div class="developer-home__panel-footer"><RouterLink to="/knowledge" class="developer-home__text-link">Besoin d’aide ?</RouterLink></div>
      </aside>
    </div>
    <footer v-if="updatedAt && !error" class="developer-home__updated">
      Mis à jour à {{ updatedAt.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }) }}
      <span v-if="recentFailures > 0"> · {{ recentFailures }} échec{{ recentFailures > 1 ? 's' : '' }} dans les dernières opérations</span>
    </footer>
  </section>
</template>

<style scoped>
.developer-home{--ink:#14213a;--muted:#718096;--line:#e3e9f2;--primary:#2563eb;display:grid;gap:22px;min-width:0;max-width:1320px;margin:auto;color:var(--ink);font-size:14px}
.developer-home *{box-sizing:border-box}
.developer-home__header{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:6px 0 3px}
.developer-home__eyebrow{color:#64748b;font-size:10px;font-weight:800;letter-spacing:.15em}
.developer-home__heading h1{margin:7px 0 0!important;color:var(--ink)!important;font-size:clamp(25px,2.2vw,30px)!important;letter-spacing:-.035em;font-weight:750!important;line-height:1.2!important}
.developer-home__heading p{margin:8px 0 0!important;color:var(--muted);font-size:13px}
.developer-home__header-actions{display:flex;gap:9px;align-items:center;flex-wrap:wrap}
.developer-home__button{display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:40px;padding:9px 14px;border-radius:10px;font:inherit;font-size:12px;font-weight:700;text-decoration:none;cursor:pointer;white-space:nowrap;transition:background .18s,border-color .18s,transform .18s}
.developer-home__button svg{width:16px;height:16px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round}
.developer-home__button--outline{color:#344256;border:1px solid #d7e0ed;background:#fff}
.developer-home__button--outline:hover:not(:disabled){background:#f1f5f9}
.developer-home__button--primary{background:#2563eb;border:1px solid #2563eb;color:#fff}
.developer-home__button--primary:hover{background:#1d4ed8;border-color:#1d4ed8}
.developer-home__button:disabled{opacity:.5;cursor:wait}
.developer-home__overview{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}
.developer-home__metric{display:flex;align-items:center;gap:16px;min-width:0;min-height:110px;padding:19px 20px;background:#fff;border:1px solid var(--line);border-radius:14px;color:inherit;text-decoration:none;box-shadow:0 5px 18px rgba(20,33,58,.025);transition:box-shadow .18s,border-color .18s,transform .18s}
.developer-home__metric:hover{border-color:#bfd1f5;box-shadow:0 9px 24px rgba(20,33,58,.07);transform:translateY(-1px)}
.developer-home__metric-icon{display:grid;place-items:center;flex:0 0 43px;width:43px;height:43px;border-radius:12px;background:#edf4ff;color:#2563eb}
.developer-home__metric-icon svg{width:21px;height:21px;fill:none;stroke:currentColor;stroke-width:1.65;stroke-linecap:round;stroke-linejoin:round}
.developer-home__metric-content{display:flex;flex-direction:column;gap:4px;min-width:0}
.developer-home__metric-content>span{font-size:12px;color:#69788f;font-weight:600}
.developer-home__metric-content strong{font-size:30px;line-height:1.12;letter-spacing:-.04em;color:#14213a;font-weight:760}
.developer-home__metric-arrow{margin-left:auto;color:#a7b4c7;font-size:22px;line-height:1}
.developer-home__metric--attention .developer-home__metric-icon{background:#fff5e6;color:#c47a0b}
.developer-home__body{display:grid;grid-template-columns:minmax(0,1.85fr) minmax(290px,1fr);gap:16px;align-items:start;min-width:0}
.developer-home__panel{min-width:0;overflow:hidden;background:#fff;border:1px solid var(--line);border-radius:14px;box-shadow:0 5px 18px rgba(20,33,58,.025)}
.developer-home__panel-head{display:flex;justify-content:space-between;align-items:center;gap:14px;padding:20px 21px 17px;border-bottom:1px solid #eef2f7}
.developer-home__panel-head h2{font-size:16px!important;line-height:1.25!important;margin:0!important;font-weight:740!important;color:#18253d!important}
.developer-home__panel-head p{font-size:12px;margin:5px 0 0!important;color:#8792a5}
.developer-home__text-link{font-size:12px;font-weight:700;color:#2563eb;text-decoration:none;white-space:nowrap}
.developer-home__text-link:hover{text-decoration:underline}
.developer-home__table-wrap{width:100%;overflow-x:auto}
.developer-home__table{width:100%;border-collapse:collapse;min-width:550px;font-size:12px;text-align:left}
.developer-home__table th{padding:13px 16px!important;background:#f8fafc;color:#7a879a;font-size:10px;font-weight:750;text-transform:uppercase;letter-spacing:.055em;white-space:nowrap;border-bottom:1px solid #eaf0f6}
.developer-home__table td{padding:15px 16px!important;border-bottom:1px solid #f0f3f7;vertical-align:middle;color:#35445b}
.developer-home__table tr:last-child td{border-bottom:none}
.developer-home__table tbody tr:hover td{background:#f9fbff}
.developer-home__app{display:block;max-width:200px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#1e293b;text-decoration:none;font-weight:750}
.developer-home__app:hover{color:#2563eb}
.developer-home__version{display:block;margin-top:4px;font-size:11px;color:#92a0b2}
.developer-home__muted{color:#64748b!important}
.developer-home__date{white-space:nowrap}
.developer-home__open{color:#2563eb;font-weight:700;font-size:12px;text-decoration:none}
.developer-home__open:hover{text-decoration:underline}
.developer-home__status{display:inline-flex;align-items:center;padding:5px 9px;border-radius:7px;background:#f1f5f9;color:#475569;white-space:nowrap;font-size:11px;font-weight:700}
.developer-home__status--success{background:#e8f8f0;color:#167450}
.developer-home__status--danger{background:#fff0ef;color:#b42318}
.developer-home__status--active{background:#e9f2ff;color:#245ac7}
.developer-home__status--pending{background:#fff4df;color:#9f650c}
.developer-home__task-count{padding:4px 9px;border-radius:8px;color:#8b5b11;background:#fff3da;font-size:12px;font-weight:750}
.developer-home__task-list{padding:9px 12px 10px;margin:0;list-style:none}
.developer-home__task-list li+li{border-top:1px solid #edf1f6}
.developer-home__task{width:100%;display:flex;align-items:center;gap:10px;min-width:0;padding:13px 9px;text-align:left;border:0;border-radius:8px;background:transparent;color:inherit;text-decoration:none;cursor:pointer;font:inherit}
.developer-home__task:hover{background:#f7faff}
.developer-home__task-indicator{flex:0 0 8px;width:8px;height:8px;background:#e7a83b;border-radius:50%}
.developer-home__task-indicator--danger{background:#e25c58}
.developer-home__task-copy{flex:1;min-width:0;display:flex;flex-direction:column;gap:4px}
.developer-home__task-copy strong{font-size:12px;font-weight:750;white-space:nowrap;text-overflow:ellipsis;overflow:hidden}
.developer-home__task-copy small{font-size:11px;color:#758399;white-space:nowrap;text-overflow:ellipsis;overflow:hidden}
.developer-home__task-chevron{font-size:22px;color:#a2aec0}
.developer-home__panel-footer{padding:14px 20px;border-top:1px solid #edf1f7}
.developer-home__quiet{padding:44px 18px;text-align:center;display:flex;align-items:center;flex-direction:column;gap:8px}
.developer-home__quiet-icon{width:42px;height:42px;display:grid;place-items:center;border-radius:50%;background:#eaf8f0;color:#188459;margin-bottom:4px}
.developer-home__quiet-icon svg{width:22px;height:22px;fill:none;stroke:currentColor;stroke-width:1.8;stroke-linecap:round;stroke-linejoin:round}
.developer-home__quiet strong{font-size:13px;color:#344256}
.developer-home__quiet p{font-size:12px;color:#8792a5}
.developer-home__state,.developer-home__empty{padding:30px 20px;color:#79879a;font-size:13px}
.developer-home__empty{text-align:center}.developer-home__empty strong{display:block;color:#26354b;margin-bottom:6px}.developer-home__empty a{display:inline-block;margin-top:12px;color:#2563eb;font-weight:700;text-decoration:none}
.developer-home__error{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:12px 16px;border:1px solid #fecaca;border-radius:10px;background:#fff7f7;color:#9d2323}
.developer-home__error button{border:0;background:transparent;color:inherit;font-weight:700;cursor:pointer;text-decoration:underline}
.developer-home__updated{font-size:11px;color:#9aa5b6;text-align:right;margin-top:-8px}
.developer-home__sr{position:absolute;height:1px;width:1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap}
.developer-home :is(a,button):focus-visible{outline:3px solid #93c5fd;outline-offset:3px}
@media(max-width:1050px){.developer-home__body{grid-template-columns:1fr}.developer-home__overview{grid-template-columns:repeat(3,minmax(0,1fr))}}
@media(max-width:720px){.developer-home{gap:16px}.developer-home__header{align-items:flex-start;flex-direction:column}.developer-home__header-actions{width:100%}.developer-home__header-actions>*{flex:1}.developer-home__overview{grid-template-columns:1fr}.developer-home__metric{min-height:85px;padding:14px 16px}.developer-home__metric-content strong{font-size:26px}.developer-home__panel-head{padding:17px}.developer-home__updated{text-align:left}}
@media(prefers-reduced-motion:reduce){.developer-home__metric,.developer-home__button{transition:none}}
</style>
