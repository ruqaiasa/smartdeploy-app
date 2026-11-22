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

const auth = useAuthStore()
const workspace = ref<Workspace | null>(null)
const recent = ref<DeploymentResponse[]>([])
const todo = ref<{ link: string | null; text: string }[]>([])
const loading = ref(true)
const error = ref(false)
const firstName = computed(() => auth.currentUser?.firstName ?? '')
const failed = computed(() => recent.value.filter(d => d.status === 'FAILED').length)
const lastUpdated = ref<Date | null>(null)
const updatedLabel = computed(() => lastUpdated.value?.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }) ?? '')
const STATUS: Record<string, { label: string; tone: string }> = {
  AWAITING_APPROVAL: { label: 'À approuver', tone: 'approval' },
  PENDING: { label: 'En attente', tone: 'muted' },
  RUNNING: { label: 'En cours', tone: 'running' },
  SUCCESS: { label: 'Réussi', tone: 'ok' },
  FAILED: { label: 'Échec', tone: 'critical' },
  REJECTED: { label: 'Refusé', tone: 'critical' },
  CANCELLED: { label: 'Annulé', tone: 'muted' },
  ROLLED_BACK: { label: 'Remplacé', tone: 'muted' },
}
const status = (d: DeploymentResponse) => d.status === 'SUCCESS' && d.current
  ? { label: 'En service', tone: 'ok' }
  : STATUS[d.status] ?? { label: d.status, tone: 'muted' }
function relative(iso: string) {
  const h = (Date.now() - new Date(iso).getTime()) / 3600000
  if (!Number.isFinite(h) || h < 0) return 'Récent'
  return h < 1 ? 'À l’instant' : h < 24 ? `${Math.floor(h)} h` : `${Math.floor(h / 24)} j`
}
async function load() {
  loading.value = true
  error.value = false
  try {
    const [ws, deps, claims] = await Promise.all([
      getWorkspace(),
      getDeployments({ mine: true, size: 6 }),
      getClaims().catch(() => []),
    ])
    workspace.value = ws
    recent.value = deps?.content ?? []
    lastUpdated.value = new Date()
    todo.value = [
      ...claims.filter(c => c.status === 'WAITING_REQUESTER').map(c => ({ link: null, text: `Répondre : ${c.title}` })),
      ...claims.filter(c => c.status === 'RESOLVED').map(c => ({ link: null, text: `Confirmer : ${c.title}` })),
      ...recent.value.filter(d => d.status === 'FAILED').slice(0, 3).map(d => ({ link: `/deployments/${d.id}`, text: `Échec : ${d.applicationName}` })),
    ]
  } catch {
    error.value = true
  } finally {
    loading.value = false
  }
}
onMounted(() => { void load() })
</script>

<template>
  <section class="sddev" aria-label="Accueil développeur">
    <header class="sddev-head">
      <div class="sddev-intro">
        <span class="sddev-kicker">ESPACE DÉVELOPPEUR</span>
        <h1>Bonjour{{ firstName ? `, ${firstName}` : '' }}</h1>
        <p>Vos projets, vos demandes, vos priorités.</p>
      </div>
      <div class="sddev-head-actions">
        <button type="button" class="sddev-button sddev-secondary" :disabled="loading" @click="load">Actualiser</button>
        <RouterLink to="/deployments" class="sddev-button sddev-primary">Demander un déploiement</RouterLink>
      </div>
    </header>

    <p v-if="updatedLabel && !error" class="sddev-updated">Mis à jour à {{ updatedLabel }}</p>
    <div v-if="error" class="sddev-alert" role="alert">
      <span>Impossible de charger les données.</span>
      <button type="button" @click="load">Réessayer</button>
    </div>

    <div v-if="!loading && !error" class="sddev-signal" :class="{ 'is-alert': failed > 0 }" role="status">
      <span class="sddev-signal-title"><span class="sddev-signal-dot" aria-hidden="true"></span>
        {{ failed > 0 ? `${failed} déploiement${failed > 1 ? 's' : ''} en échec récent` : 'Aucun échec parmi les déploiements récents' }}
      </span>
      <RouterLink to="/deployments">Voir le suivi</RouterLink>
    </div>

    <div class="sddev-stats" :aria-busy="loading">
      <RouterLink to="/applications" class="sddev-stat">
        <div class="sddev-stat-top"><span>Applications</span></div>
        <strong>{{ loading || error ? '—' : (workspace?.applications ?? 0) }}</strong>
        <span class="sddev-stat-foot">Voir les applications</span>
      </RouterLink>
      <RouterLink to="/environments" class="sddev-stat">
        <div class="sddev-stat-top"><span>En service</span></div>
        <strong>{{ loading || error ? '—' : (workspace?.inService ?? 0) }}</strong>
        <span class="sddev-stat-foot">Voir les environnements</span>
      </RouterLink>
      <RouterLink to="/deployments" class="sddev-stat">
        <div class="sddev-stat-top"><span>En attente</span></div>
        <strong>{{ loading || error ? '—' : (workspace?.pendingRequests ?? 0) }}</strong>
        <span class="sddev-stat-foot">Suivre les demandes</span>
      </RouterLink>
      <RouterLink to="/deployments" class="sddev-stat">
        <div class="sddev-stat-top"><span>Échecs récents</span></div>
        <strong>{{ loading || error ? '—' : failed }}</strong>
        <span class="sddev-stat-foot">Consulter les détails</span>
      </RouterLink>
    </div>

    <div class="sddev-content-grid">
      <section class="sddev-panel sddev-activity">
        <header class="sddev-panel-head">
          <div><h2>Déploiements récents</h2><p>Vos six dernières demandes</p></div>
          <RouterLink to="/deployments" class="sddev-text-link">Tout voir</RouterLink>
        </header>
        <p v-if="loading" class="sddev-placeholder">Chargement…</p>
        <div v-else-if="error" class="sddev-empty">Données indisponibles.</div>
        <div v-else-if="!recent.length" class="sddev-empty"><strong>Aucun déploiement</strong><p>Vos déploiements apparaîtront ici.</p></div>
        <div v-else class="sddev-table-scroll">
          <table class="sddev-table">
            <thead><tr><th>Application</th><th>Environnement</th><th>Statut</th><th>Quand</th><th><span class="sddev-sr">Détails</span></th></tr></thead>
            <tbody><tr v-for="d in recent" :key="d.id">
              <td><RouterLink class="sddev-app-link" :to="`/deployments/${d.id}`">{{ d.applicationName }}</RouterLink><div class="sddev-version">{{ d.version.startsWith('v') ? d.version : `v${d.version}` }}</div></td>
              <td class="sddev-cell-muted">{{ d.environmentName }}</td>
              <td><span class="sddev-pill" :class="`is-${status(d).tone}`">{{ status(d).label }}</span></td>
              <td class="sddev-cell-muted sddev-nowrap">{{ relative(d.createdAt) }}</td>
              <td><RouterLink class="sddev-row-arrow" :to="`/deployments/${d.id}`" :aria-label="`Voir ${d.applicationName}`">Voir</RouterLink></td>
            </tr></tbody>
          </table>
        </div>
      </section>

      <aside class="sddev-panel sddev-priority">
        <header class="sddev-panel-head">
          <div><h2>À traiter</h2><p>Demandes à suivre</p></div>
          <span class="sddev-count">{{ loading || error ? '—' : todo.length }}</span>
        </header>
        <p v-if="loading" class="sddev-placeholder">Chargement…</p>
        <div v-else-if="error" class="sddev-empty">Données indisponibles.</div>
        <div v-else-if="!todo.length" class="sddev-empty sddev-empty-priority"><strong>Tout est à jour</strong><p>Aucune action requise.</p></div>
        <ul v-else class="sddev-todo"><li v-for="(t, i) in todo" :key="i">
          <RouterLink v-if="t.link" :to="t.link"><span>{{ t.text }}</span></RouterLink>
          <button v-else type="button" @click="openClaimsWidget()"><span>{{ t.text }}</span></button>
        </li></ul>
        <RouterLink to="/knowledge" class="sddev-help">Centre d’aide</RouterLink>
      </aside>
    </div>
    <section class="sddev-steps" aria-label="Parcours de déploiement">
      <div class="sddev-steps-intro"><h2>Votre parcours</h2><p>De l'application à la mise en service.</p></div>
      <div class="sddev-steps-links">
        <RouterLink to="/applications"><span class="sddev-step-number">01</span><span>Application</span></RouterLink>
        <RouterLink to="/versions"><span class="sddev-step-number">02</span><span>Version</span></RouterLink>
        <RouterLink to="/deployments"><span class="sddev-step-number">03</span><span>Déploiement</span></RouterLink>
      </div>
    </section>
  </section>
</template>

<style scoped>
.sddev{max-width:1290px;margin:0 auto;display:grid;gap:19px;min-width:0;color:#17243b}
.sddev *{box-sizing:border-box}
.sddev-head{display:flex;justify-content:space-between;align-items:center;gap:24px;padding:4px 1px 10px;border-bottom:1px solid #e5eaf1;background:transparent}
.sddev-intro{min-width:0}.sddev-kicker{display:block;margin-bottom:9px;font-size:10px;font-weight:800;letter-spacing:.12em;color:#64748b}
.sddev-head h1{margin:0!important;font-size:clamp(24px,2.2vw,30px)!important;font-weight:750!important;letter-spacing:-.035em!important;color:#17243b!important;line-height:1.3!important}
.sddev-head p{margin:5px 0 0!important;color:#64748b;font-size:13px;line-height:1.5}
.sddev-head-actions{display:flex;gap:9px;align-items:center;flex-wrap:wrap}
.sddev-button{min-height:39px;display:inline-flex;align-items:center;justify-content:center;padding:9px 14px;border-radius:8px;border:1px solid #dce3eb;font-size:12px;font-weight:700;line-height:1.25;text-decoration:none;white-space:nowrap;cursor:pointer}
.sddev-button:disabled{opacity:.6;cursor:wait}.sddev-secondary{background:#fff;color:#334155}.sddev-secondary:hover:not(:disabled){background:#f4f7fb}.sddev-primary{background:#3b82f6;border-color:#3b82f6;color:#fff}.sddev-primary:hover{background:#2563eb;border-color:#2563eb}
.sddev-updated{margin:-13px 1px -2px!important;color:#94a3b8;font-size:11px;text-align:right}
.sddev-alert{display:flex;align-items:center;justify-content:space-between;gap:12px;border-radius:9px;border:1px solid #fecaca;background:#fff7f7;padding:12px 15px;color:#991b1b;font-size:12px}.sddev-alert button{color:inherit;border:0;background:transparent;font-weight:700;text-decoration:underline;cursor:pointer}
.sddev-stats{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:13px}
.sddev-stat{min-width:0;min-height:127px;display:flex;flex-direction:column;gap:8px;padding:18px 19px 15px;border:1px solid #e4e9f1;border-radius:11px;background:#fff;color:#17243b;text-decoration:none;box-shadow:0 1px 4px rgba(15,23,42,.025)}
.sddev-stat:hover{border-color:#93c5fd;background:#fcfdff}.sddev-stat-top{color:#64748b;font-size:12px;font-weight:650;line-height:1.35}.sddev-stat strong{font-size:31px;font-weight:750;line-height:1.1;color:#0f172a;letter-spacing:-.04em}.sddev-stat-foot{margin-top:auto;font-size:11px;font-weight:600;color:#2563eb;line-height:1.4}
.sddev-content-grid{display:grid;grid-template-columns:minmax(0,1.85fr) minmax(270px,1fr);gap:14px;align-items:start;min-width:0}
.sddev-panel{background:#fff;border:1px solid #e4e9f1;border-radius:11px;min-width:0;overflow:hidden;box-shadow:0 1px 4px rgba(15,23,42,.025)}
.sddev-panel-head{padding:19px 20px 15px;border-bottom:1px solid #edf0f5;display:flex;align-items:center;justify-content:space-between;gap:12px}
.sddev-panel-head h2{margin:0!important;font-size:15px!important;color:#17243b!important;font-weight:750!important;letter-spacing:-.01em}.sddev-panel-head p{margin:4px 0 0!important;font-size:11px;color:#8692a3}
.sddev-text-link,.sddev-help{color:#2563eb;text-decoration:none;font-size:12px;font-weight:700;white-space:nowrap}.sddev-text-link:hover,.sddev-help:hover{text-decoration:underline}
.sddev-table-scroll{width:100%;min-width:0;overflow-x:auto}.sddev-table{border-collapse:collapse!important;width:100%;font-size:12px}.sddev-table th{text-align:left;background:#f8fafc!important;padding:12px 14px!important;color:#718096!important;font-size:10px!important;letter-spacing:.04em;text-transform:uppercase;font-weight:700!important;white-space:nowrap}.sddev-table td{padding:14px!important;border-top:1px solid #edf0f5;vertical-align:middle}.sddev-table tbody tr:hover td{background:#f8fbff}
.sddev-app-link{font-size:12px;font-weight:750;color:#17243b;text-decoration:none}.sddev-app-link:hover{color:#2563eb}.sddev-version{margin-top:3px;color:#94a3b8;font-size:11px}.sddev-cell-muted{color:#64748b}.sddev-nowrap{white-space:nowrap}.sddev-row-arrow{color:#2563eb;font-size:11px;font-weight:700;text-decoration:none}
.sddev-pill{display:inline-block;border-radius:6px;padding:5px 8px;background:#f1f5f9;color:#475569;font-size:11px;font-weight:700;white-space:nowrap}.sddev-pill.is-ok{background:#dcfce7;color:#166534}.sddev-pill.is-critical{background:#fee2e2;color:#991b1b}.sddev-pill.is-running{background:#dbeafe;color:#1d4ed8}.sddev-pill.is-approval{background:#fef3c7;color:#92400e}
.sddev-count{min-width:27px;height:27px;display:grid;place-items:center;border-radius:7px;background:#eff6ff;color:#1d4ed8;font-size:12px;font-weight:750}
.sddev-todo{list-style:none;margin:0;padding:14px 16px;display:grid;gap:8px}.sddev-todo li>*{width:100%;display:flex;justify-content:space-between;align-items:center;gap:10px;text-align:left;background:#f8fafc;border:1px solid #e9edf3;border-radius:8px;padding:12px;color:#334155;font-size:12px;font-weight:650;text-decoration:none;cursor:pointer}.sddev-todo li>*:hover{background:#eff6ff;border-color:#bfdbfe}.sddev-todo li>* span:first-child{overflow-wrap:anywhere}
.sddev-help{display:block;margin:0 18px;padding:15px 0;border-top:1px solid #edf0f5}
.sddev-placeholder{padding:21px!important;font-size:12px;color:#64748b}.sddev-empty{padding:28px 18px;text-align:center;color:#64748b;font-size:12px}.sddev-empty strong{display:block;color:#334155;font-size:13px}.sddev-empty p{margin-top:5px!important;color:#64748b}.sddev-empty-priority{padding:29px 18px}
.sddev-steps{display:flex;justify-content:space-between;align-items:center;gap:18px;border:1px solid #e4e9f1;border-radius:11px;background:#fff;padding:17px 21px;min-width:0}.sddev-steps-intro h2{font-size:14px!important;font-weight:750!important;margin:0!important}.sddev-steps-intro p{font-size:11px;color:#64748b;margin:4px 0 0!important}.sddev-steps-links{display:flex;flex-wrap:wrap;gap:9px}.sddev-steps-links a{display:inline-flex;align-items:center;gap:9px;border:1px solid #e4e9f1;border-radius:8px;padding:9px 12px;text-decoration:none;color:#334155;font-size:12px;font-weight:650}.sddev-steps-links a:hover{border-color:#93c5fd;background:#f8fbff}.sddev-step-number{font-size:10px;color:#2563eb;font-weight:800}
.sddev-sr{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap}
@media(max-width:1050px){.sddev-stats{grid-template-columns:repeat(2,minmax(0,1fr))}.sddev-content-grid{grid-template-columns:1fr}.sddev-steps{align-items:flex-start;flex-direction:column}}
@media(max-width:620px){.sddev{gap:15px}.sddev-head{align-items:flex-start;flex-direction:column}.sddev-head-actions{width:100%}.sddev-head-actions>*{flex:1}.sddev-stats{gap:10px}.sddev-stat{padding:15px 13px;min-height:120px}.sddev-stat strong{font-size:28px}.sddev-table{min-width:600px}.sddev-panel-head{padding:16px}}
</style>
