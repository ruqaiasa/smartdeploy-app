<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getOnlineApplications, getScorecards, getWorkspace, type OnlineApplication } from '@/api/portal.api'
import { getDeployments } from '@/api/deployments.api'
import { getClaims } from '@/api/claims.api'
import { useAuthStore } from '@/stores/auth'
import { openClaimsWidget } from '@/components/developer/claimsWidget'
import type { ScorecardSummary, Workspace } from '@/types/portal'
import type { DeploymentResponse } from '@/types/deployment'

/* Accueil du développeur : le parcours pour déployer, ce qui est en cours, ce qui est à faire, l’activité. */
const auth = useAuthStore()
const workspace = ref<Workspace | null>(null)
const recent = ref<DeploymentResponse[]>([])
const todo = ref<{ link: string | null; text: string }[]>([])
const scorecards = ref<ScorecardSummary[]>([])
const online = ref<OnlineApplication[]>([])

onMounted(async () => {
  const [ws, deps, claims] = await Promise.all([
    getWorkspace().catch(() => null), getDeployments({ mine: true, size: 8 }).catch(() => null), getClaims().catch(() => []),
  ])
  workspace.value = ws
  scorecards.value = await getScorecards().catch(() => [])
  online.value = await getOnlineApplications().catch(() => [])
  recent.value = deps?.content ?? []
  todo.value = [
    ...claims.filter(c => c.status === 'WAITING_REQUESTER').map(c => ({ link: null, text: `Répondre : ${c.title}` })),
    ...claims.filter(c => c.status === 'RESOLVED').map(c => ({ link: null, text: `Confirmer : ${c.title}` })),
    ...recent.value.filter(d => d.status === 'FAILED').slice(0, 3).map(d => ({ link: `/deployments/${d.id}`, text: `Échec : ${d.applicationName} ${d.version}` })),
  ]
})

const counts = computed(() => {
  const items = workspace.value?.items ?? []
  return {
    apps: items.length,
    envs: items.reduce((n, a) => n + a.environments.length, 0),
    versions: items.reduce((n, a) => n + a.latestVersions.length, 0),
    live: workspace.value?.inService ?? 0,
  }
})
const steps = computed(() => [
  { n: 1, label: 'Application', count: counts.value.apps, unit: 'application', to: '/applications', action: '+ Créer' },
  { n: 2, label: 'Environnement', count: counts.value.envs, unit: 'environnement', to: '/environments', action: '+ Créer' },
  { n: 3, label: 'Version', count: counts.value.versions, unit: 'version', to: '/versions', action: '+ Créer' },
  { n: 4, label: 'Déploiement', count: counts.value.live, unit: 'en service', to: '/deployments', action: 'Demander' },
])
const ongoing = computed(() => recent.value.filter(d => ['AWAITING_APPROVAL', 'PENDING', 'RUNNING'].includes(d.status)))

const ENV_LABEL: Record<string, string> = { DEVELOPMENT: 'Dév.', TEST: 'Test', STAGING: 'Préprod.', PRODUCTION: 'Prod.' }
const STATUS: Record<string, { label: string; tone: string }> = {
  AWAITING_APPROVAL: { label: 'À valider', tone: 'approval' }, PENDING: { label: 'En file', tone: 'muted' }, RUNNING: { label: 'En cours', tone: 'running' },
  SUCCESS: { label: 'Réussi', tone: 'ok' }, FAILED: { label: 'Échoué', tone: 'critical' }, REJECTED: { label: 'Refusé', tone: 'critical' },
  CANCELLED: { label: 'Annulé', tone: 'muted' }, ROLLED_BACK: { label: 'Remplacé', tone: 'muted' },
}
const status = (d: DeploymentResponse) => d.status === 'SUCCESS' && d.current ? { label: 'En service', tone: 'ok' } : STATUS[d.status] ?? { label: d.status, tone: 'muted' }
function relative(iso: string) {
  const m = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  return m < 60 ? `${m} min` : m < 1440 ? `${Math.round(m / 60)} h` : `${Math.round(m / 1440)} j`
}
const plural = (n: number, w: string) => w === 'en service' ? `${n} ${w}` : `${n} ${w}${n > 1 ? 's' : ''}`
</script>

<template>
  <section class="hm">
    <header class="hm-hero">
      <div>
        <h1>Bonjour {{ auth.currentUser?.firstName ?? '' }}</h1>
        <p>Votre espace de déploiement.</p>
      </div>
      <RouterLink to="/deployments" class="hm-cta">Demander un déploiement</RouterLink>
    </header>

    <!-- Le parcours pour déployer -->
    <ol class="hm-steps" aria-label="Pour déployer">
      <li v-for="s in steps" :key="s.n" :class="{ 'is-done': s.count > 0 }">
        <span class="hm-step-dot">{{ s.count > 0 ? '✓' : s.n }}</span>
        <div class="hm-step-body">
          <strong>{{ s.label }}</strong>
          <span>{{ plural(s.count, s.unit) }}</span>
        </div>
        <RouterLink :to="s.to" class="hm-step-action">{{ s.action }}</RouterLink>
      </li>
    </ol>

    <section v-if="online.length" class="hm-card">
      <header class="hm-card-head"><h2>Mes applications en ligne</h2><span class="hm-count">{{ online.length }}</span></header>
      <ul class="hm-online">
        <li v-for="o in online" :key="o.environmentId">
          <span class="hm-env-type" :class="`is-${(o.environmentType ?? '').toLowerCase()}`">{{ ENV_LABEL[o.environmentType ?? ''] ?? o.environmentType }}</span>
          <span class="hm-online-name"><strong>{{ o.applicationName }}</strong> <code>{{ o.version }}</code><small>{{ o.environmentName }}</small></span>
          <a v-if="o.url" :href="o.url" target="_blank" rel="noopener" class="hm-open">{{ o.url.replace('http://', '') }} ↗</a>
          <span v-else class="hm-noaddr">Pas d’adresse publique</span>
          <RouterLink :to="`/deployments/${o.deploymentId}`" class="hm-link">Fiche</RouterLink>
        </li>
      </ul>
    </section>

    <div class="hm-grid">
      <section class="hm-card">
        <header class="hm-card-head"><h2>En cours</h2><span class="hm-count">{{ ongoing.length }}</span></header>
        <p v-if="!ongoing.length" class="hm-empty">Aucun déploiement en cours.</p>
        <ul v-else class="hm-list">
          <li v-for="d in ongoing" :key="d.id">
            <RouterLink :to="`/deployments/${d.id}`">
              <span class="hm-name">{{ d.applicationName }} <code>{{ d.version }}</code></span>
              <span class="hm-sub">{{ d.environmentName }}</span>
              <span class="hm-pill" :class="`is-${status(d).tone}`">{{ status(d).label }}</span>
            </RouterLink>
          </li>
        </ul>
      </section>

      <section class="hm-card">
        <header class="hm-card-head"><h2>À faire</h2><span class="hm-count" :class="{ 'is-alert': todo.length }">{{ todo.length }}</span></header>
        <p v-if="!todo.length" class="hm-ok">✓ Tout est à jour</p>
        <ul v-else class="hm-list">
          <li v-for="(t, i) in todo" :key="i">
            <RouterLink v-if="t.link" :to="t.link" class="hm-todo">{{ t.text }}</RouterLink>
            <button v-else type="button" class="hm-todo" @click="openClaimsWidget()">{{ t.text }}</button>
          </li>
        </ul>
      </section>
    </div>

    <section v-if="scorecards.length" class="hm-card">
      <header class="hm-card-head"><h2>Excellence de mes applications</h2></header>
      <div class="hm-scores">
        <RouterLink v-for="c in scorecards" :key="c.applicationId" :to="`/applications/${c.applicationId}/excellence`" class="hm-score">
          <span class="hm-grade" :class="`is-${c.grade.toLowerCase()}`">{{ c.grade }}</span>
          <span><strong>{{ c.applicationName }}</strong><small>{{ c.score === null ? 'Voir la fiche' : `${c.score} / 100` }}</small></span>
        </RouterLink>
      </div>
    </section>

    <section class="hm-card">
      <header class="hm-card-head"><h2>Activité récente</h2><RouterLink to="/deployments" class="hm-link">Tout voir</RouterLink></header>
      <p v-if="!recent.length" class="hm-empty">Aucun déploiement.</p>
      <table v-else class="hm-table">
        <thead><tr><th>Application</th><th>Environnement</th><th>État</th><th class="hm-right">Il y a</th></tr></thead>
        <tbody>
          <tr v-for="d in recent" :key="d.id">
            <td><RouterLink :to="`/deployments/${d.id}`" class="hm-name">{{ d.applicationName }} <code>{{ d.version }}</code></RouterLink></td>
            <td class="hm-sub">{{ d.environmentName }}</td>
            <td><span class="hm-pill" :class="`is-${status(d).tone}`">{{ status(d).label }}</span></td>
            <td class="hm-right hm-sub">{{ relative(d.createdAt) }}</td>
          </tr>
        </tbody>
      </table>
    </section>
  </section>
</template>

<style scoped>
.hm-online { display: flex; flex-direction: column; margin: 0; padding: 0; list-style: none; }
.hm-online li { display: grid; grid-template-columns: 62px minmax(0, 1.2fr) minmax(0, 1.6fr) auto; align-items: center; gap: 12px; padding: 10px 4px; border-top: 1px solid #eef2ff; font-size: 14px; }
.hm-online li:first-child { border-top: 0; }
.hm-online-name { display: flex; flex-wrap: wrap; align-items: baseline; gap: 6px; min-width: 0; }
.hm-online-name code { padding: 0 6px; border-radius: 5px; background: #f3f4f6; font-size: 12px; }
.hm-online-name small { width: 100%; color: #6b7280; font-size: 12px; }
.hm-env-type { padding: 2px 8px; border-radius: 999px; background: #f3f4f6; color: #4b5563; font-size: 11.5px; font-weight: 800; text-align: center; }
.hm-env-type.is-production { background: #fee2e2; color: #991b1b; }
.hm-env-type.is-test { background: #fef3c7; color: #92400e; }
.hm-env-type.is-development { background: #dbeafe; color: #1e40af; }
.hm-open { overflow: hidden; padding: 6px 10px; border-radius: 9px; background: #eef2ff; color: #1e40af; font-size: 13px; font-weight: 700; text-decoration: none; text-overflow: ellipsis; white-space: nowrap; }
.hm-open:hover { background: #e0e7ff; }
.hm-noaddr { color: #9ca3af; font-size: 13px; }
@media (max-width: 760px) { .hm-online li { grid-template-columns: 1fr; } }
.hm-scores { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 10px; }
.hm-score { display: flex; align-items: center; gap: 12px; padding: 12px; border: 1px solid #e0e7ff; border-radius: 12px; color: inherit; text-decoration: none; }
.hm-score:hover { border-color: #c7d2fe; background: #f8faff; }
.hm-score span:last-child { display: flex; flex-direction: column; }
.hm-score small { color: #6b7280; }
.hm-grade { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 12px; background: #f3f4f6; color: #6b7280; font-size: 22px; font-weight: 900; }
.hm-grade.is-a { background: #dcfce7; color: #15803d; } .hm-grade.is-b { background: #ecfccb; color: #4d7c0f; } .hm-grade.is-c { background: #fef9c3; color: #a16207; } .hm-grade.is-d { background: #ffedd5; color: #c2410c; } .hm-grade.is-e { background: #fee2e2; color: #b91c1c; }
.hm { display: flex; flex-direction: column; gap: 16px; }
.hm-hero { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 14px; padding: 22px 26px; border-radius: 18px; background: linear-gradient(135deg, #1e3a8a, #4f46e5); color: #fff; }
.hm-hero h1 { margin: 0; font-size: 26px; }
.hm-hero p { margin: 4px 0 0; opacity: .85; }
.hm-cta { padding: 10px 16px; border-radius: 10px; background: #fff; color: #1e40af; font-size: 14px; font-weight: 800; text-decoration: none; }
.hm-steps { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin: 0; padding: 0; list-style: none; counter-reset: step; }
.hm-steps li { position: relative; display: flex; align-items: center; gap: 10px; padding: 14px; border: 1px solid #e0e7ff; border-radius: 14px; background: #fff; }
.hm-steps li.is-done { border-color: #bbf7d0; }
.hm-step-dot { display: grid; flex: 0 0 auto; place-items: center; width: 32px; height: 32px; border-radius: 999px; background: #e0e7ff; color: #1d4ed8; font-weight: 800; }
.is-done .hm-step-dot { background: #16a34a; color: #fff; }
.hm-step-body { display: flex; flex: 1; flex-direction: column; min-width: 0; }
.hm-step-body strong { font-size: 14.5px; }
.hm-step-body span { color: #6b7280; font-size: 12.5px; }
.hm-step-action { padding: 5px 10px; border-radius: 8px; background: #eef2ff; color: #1d4ed8; font-size: 12.5px; font-weight: 700; text-decoration: none; white-space: nowrap; }
.hm-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
@media (max-width: 900px) { .hm-grid, .hm-steps { grid-template-columns: 1fr; } }
.hm-card { display: flex; flex-direction: column; gap: 10px; padding: 16px 18px; border: 1px solid #e0e7ff; border-radius: 14px; background: #fff; }
.hm-card-head { display: flex; align-items: center; justify-content: space-between; }
.hm-card h2 { margin: 0; font-size: 16px; }
.hm-count { min-width: 26px; padding: 1px 8px; border-radius: 999px; background: #f3f4f6; color: #4b5563; font-size: 12.5px; font-weight: 700; text-align: center; }
.hm-count.is-alert { background: #e0e7ff; color: #1d4ed8; }
.hm-link { color: #1d4ed8; font-size: 13.5px; font-weight: 700; text-decoration: none; }
.hm-empty { margin: 0; color: #6b7280; font-size: 14px; }
.hm-ok { margin: 0; padding: 10px 12px; border-radius: 10px; background: #f0fdf4; color: #166534; font-weight: 700; font-size: 14px; }
.hm-list { display: flex; flex-direction: column; gap: 6px; margin: 0; padding: 0; list-style: none; }
.hm-list a:not(.hm-todo) { display: grid; grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr) auto; align-items: center; gap: 10px; padding: 9px 10px; border-radius: 10px; background: #f8faff; color: inherit; text-decoration: none; font-size: 14px; }
.hm-todo { display: block; box-sizing: border-box; width: 100%; padding: 9px 12px; border: 0; border-radius: 10px; background: #eef2ff; color: #1e3a8a; font: inherit; font-size: 14px; font-weight: 600; text-align: left; text-decoration: none; cursor: pointer; }
.hm-name { color: #111827; font-weight: 600; text-decoration: none; }
.hm-name code { padding: 0 6px; border-radius: 5px; background: #f3f4f6; font-size: 12px; }
.hm-sub { color: #6b7280; }
.hm-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.hm-table th { padding: 8px; border-bottom: 1px solid #e0e7ff; color: #6b7280; font-size: 12px; font-weight: 700; letter-spacing: .03em; text-align: left; text-transform: uppercase; }
.hm-table td { padding: 10px 8px; border-bottom: 1px solid #eef2ff; }
.hm-table tbody tr:last-child td { border-bottom: 0; }
.hm-right { text-align: right !important; }
.hm-pill { padding: 2px 9px; border-radius: 999px; font-size: 12px; font-weight: 700; white-space: nowrap; }
.hm-pill.is-approval { background: #e0e7ff; color: #1e40af; }
.hm-pill.is-running { background: #dbeafe; color: #1e40af; }
.hm-pill.is-ok { background: #dcfce7; color: #166534; }
.hm-pill.is-critical { background: #fee2e2; color: #991b1b; }
.hm-pill.is-muted { background: #f3f4f6; color: #4b5563; }
</style>
