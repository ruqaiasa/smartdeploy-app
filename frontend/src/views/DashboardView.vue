<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import TrendSparkline from '@/components/charts/TrendSparkline.vue'
import DonutGauge from '@/components/charts/DonutGauge.vue'
import DeliveryChart from '@/components/charts/DeliveryChart.vue'
import TimeChart from '@/components/charts/TimeChart.vue'
import StorageHealthCard from '@/components/dashboard/StorageHealthCard.vue'
import DataProtectionCard from '@/components/dashboard/DataProtectionCard.vue'
import { getPendingApprovalsCount } from '@/api/deployments.api'
import { getDoraReport } from '@/api/dora.api'
import { getClusterCapacity, getInsights, type ClusterCapacity, type Insights } from '@/api/insights.api'
import { useDashboardStore } from '@/stores/dashboard'
import { useAuthStore } from '@/stores/auth'
import type { DoraReport } from '@/types/dora'

/*
 * Tableau de bord, organisé autour de quatre questions :
 * tout va bien ? livre-t-on bien ? où ça casse ? la plateforme tient-elle la charge ?
 */
const dashboardStore = useDashboardStore()
const auth = useAuthStore()
const canApprove = computed(() => auth.hasRole('ADMIN', 'DEVOPS'))
const days = ref(14)
const insights = ref<Insights | null>(null)
const dora = ref<DoraReport | null>(null)
const cluster = ref<ClusterCapacity | null>(null)
const pendingApprovals = ref<number | null>(null)
const refreshedAt = ref<Date | null>(null)
const loading = ref(false)
const dashboard = computed(() => dashboardStore.dashboard)

async function load() {
  loading.value = true
  const [, ins, dr, cl] = await Promise.all([
    dashboardStore.loadDashboard(),
    getInsights(days.value).catch(() => null),
    getDoraReport(days.value, false).catch(() => null),
    getClusterCapacity(24).catch(() => null),
  ])
  insights.value = ins
  dora.value = dr
  cluster.value = cl
  if (canApprove.value) pendingApprovals.value = await getPendingApprovalsCount().catch(() => null)
  refreshedAt.value = new Date()
  loading.value = false
}
onMounted(load)
watch(days, load)

/* ---------- Tout va bien ? ---------- */
const actions = computed(() => {
  const list: { tone: string; text: string; to: string }[] = []
  const alerts = dashboard.value?.openAlerts ?? 0
  if (alerts) list.push({ tone: 'critical', text: `${alerts} alerte${alerts > 1 ? 's' : ''} ouverte${alerts > 1 ? 's' : ''}`, to: '/alerts' })
  if (pendingApprovals.value) list.push({ tone: 'approval', text: `${pendingApprovals.value} demande${pendingApprovals.value > 1 ? 's' : ''} à approuver`, to: '/console' })
  const failing = insights.value?.environments.filter(e => e.state === 'FAILED').length ?? 0
  if (failing) list.push({ tone: 'warning', text: `${failing} environnement${failing > 1 ? 's' : ''} en échec`, to: '/deployments' })
  const reserved = Math.max(cluster.value?.cpuRequested ?? 0, cluster.value?.memoryRequested ?? 0)
  if (reserved >= 90) list.push({ tone: 'warning', text: `Cluster réservé à ${Math.round(reserved)} %`, to: '/supervision' })
  return list
})

const daily = computed(() => insights.value?.daily ?? [])
const periodTotal = computed(() => daily.value.reduce((n, d) => n + d.success + d.failed, 0))
const periodRate = computed(() => {
  const ok = daily.value.reduce((n, d) => n + d.success, 0)
  return periodTotal.value ? Math.round((ok / periodTotal.value) * 1000) / 10 : null
})
const doraMetric = (key: string) => dora.value?.metrics.find(m => m.key === key) ?? null
const restore = computed(() => doraMetric('time_to_restore'))
const kpis = computed(() => [
  { label: 'Déploiements', value: String(periodTotal.value), sub: `sur ${days.value} jours`, spark: daily.value.map(d => d.success + d.failed), color: '#2563eb', to: '/deployments' },
  { label: 'Taux de réussite', value: periodRate.value === null ? '—' : `${periodRate.value.toLocaleString('fr-FR')} %`, sub: 'réussis / terminés',
    spark: daily.value.map(d => (d.success + d.failed ? (d.success / (d.success + d.failed)) * 100 : null)), color: '#16a34a', to: '/deployments' },
  { label: 'Temps de rétablissement', value: restore.value?.value == null ? '—' : `${restore.value.value.toLocaleString('fr-FR')} min`, sub: 'médiane, après un échec',
    spark: daily.value.map(d => d.failed), color: '#dc2626', to: '/deployments', level: restore.value?.level },
  { label: 'Alertes ouvertes', value: String(dashboard.value?.openAlerts ?? 0), sub: `${insights.value?.environments.filter(e => e.state === 'ALERT').length ?? 0} environnement(s) concerné(s)`,
    spark: [], color: '#f59e0b', to: '/alerts' },
])

/* ---------- Livre-t-on bien ? (DORA) ---------- */
const LEVEL: Record<string, { label: string; tone: string }> = {
  ELITE: { label: 'Élite', tone: 'elite' }, HIGH: { label: 'Haut', tone: 'high' }, MEDIUM: { label: 'Moyen', tone: 'medium' },
  LOW: { label: 'Faible', tone: 'low' }, NONE: { label: 'Sans donnée', tone: 'none' },
}
const doraTiles = computed(() => ['deployment_frequency', 'lead_time', 'change_failure_rate', 'time_to_restore']
  .map(doraMetric).filter((m): m is NonNullable<typeof m> => m !== null))
function doraValue(v: number | null, unit: string) {
  if (v === null) return '—'
  const n = v.toLocaleString('fr-FR')
  return unit === '%' ? `${n} %` : unit.startsWith('minutes') ? `${n} min` : unit === 'par semaine' ? `${n} / sem.` : n
}

/* ---------- Où ça casse ? ---------- */
const causes = computed(() => insights.value?.causes ?? [])
const causeMax = computed(() => Math.max(1, ...causes.value.map(c => c.count)))

/* ---------- La plateforme tient-elle ? ---------- */
const usageSeries = computed(() => cluster.value ? [
  { name: 'Processeur', color: '#4f46e5', points: cluster.value.cpuUsage, area: true },
  { name: 'Mémoire', color: '#0891b2', points: cluster.value.memoryUsage },
] : [])

/* Carte des environnements : applications × types */
const TYPES = [
  { key: 'DEVELOPMENT', label: 'Dév.' }, { key: 'TEST', label: 'Test' }, { key: 'STAGING', label: 'Préprod.' }, { key: 'PRODUCTION', label: 'Prod.' },
]
const STATE: Record<string, { label: string; tone: string }> = {
  HEALTHY: { label: 'En service', tone: 'ok' }, ALERT: { label: 'Alerte', tone: 'critical' }, FAILED: { label: 'Échec', tone: 'critical' },
  DEPLOYING: { label: 'Déploiement', tone: 'running' }, IDLE: { label: 'Arrêté', tone: 'muted' }, EMPTY: { label: 'Vide', tone: 'muted' },
}
const envTypes = computed(() => TYPES.filter(t => insights.value?.environments.some(e => e.type === t.key)))
const envApps = computed(() => [...new Set((insights.value?.environments ?? []).map(e => e.applicationName))].sort())
const envCell = (app: string, type: string) => insights.value?.environments.find(e => e.applicationName === app && e.type === type) ?? null

/* Activité récente */
const recent = computed(() => (dashboard.value?.recentDeployments ?? []).slice(0, 7))
const STATUS: Record<string, { label: string; tone: string }> = {
  SUCCESS: { label: 'Réussi', tone: 'ok' }, FAILED: { label: 'Échoué', tone: 'critical' }, RUNNING: { label: 'En cours', tone: 'running' },
  PENDING: { label: 'En attente', tone: 'muted' }, CANCELLED: { label: 'Annulé', tone: 'muted' }, ROLLED_BACK: { label: 'Remplacé', tone: 'muted' },
  AWAITING_APPROVAL: { label: 'À approuver', tone: 'approval' }, REJECTED: { label: 'Refusé', tone: 'critical' },
}
const status = (s: string) => STATUS[s] ?? { label: s, tone: 'muted' }
function relative(iso: string) {
  const m = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  return m < 1 ? 'à l’instant' : m < 60 ? `il y a ${m} min` : m < 1440 ? `il y a ${Math.round(m / 60)} h` : `il y a ${Math.round(m / 1440)} j`
}
const refreshedLabel = computed(() => refreshedAt.value?.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }) ?? '')
</script>

<template>
  <section class="dd">
    <header class="dd-head">
      <div>
        <h1>Tableau de bord</h1>
        <p>Mis à jour à {{ refreshedLabel }}</p>
      </div>
      <div class="dd-head-actions">
        <div class="dd-range" role="tablist">
          <button v-for="d in [7, 14, 30]" :key="d" type="button" :class="{ 'is-active': days === d }" @click="days = d">{{ d }} jours</button>
        </div>
        <button type="button" class="dd-btn" :disabled="loading" @click="load">{{ loading ? 'Actualisation…' : 'Actualiser' }}</button>
      </div>
    </header>

    <!-- 1. Tout va bien ? -->
    <div class="dd-status" :class="actions.length ? 'is-attention' : 'is-ok'">
      <div class="dd-status-icon" aria-hidden="true">
        <svg v-if="!actions.length" viewBox="0 0 24 24"><path d="M5 12.5l4.5 4.5L19 7.5" /></svg>
        <svg v-else viewBox="0 0 24 24"><path d="M12 8v5M12 16.5v.5" /><path d="M10.3 3.9 2.6 17.2A2 2 0 0 0 4.3 20h15.4a2 2 0 0 0 1.7-2.8L13.7 3.9a2 2 0 0 0-3.4 0z" /></svg>
      </div>
      <div class="dd-status-text">
        <strong>{{ actions.length ? `${actions.length} point${actions.length > 1 ? 's' : ''} à traiter` : 'Tout est opérationnel' }}</strong>
        <span>{{ actions.length ? 'Les éléments ci-contre demandent une action.' : 'Aucune alerte, aucun échec en cours, capacité suffisante.' }}</span>
      </div>
      <div class="dd-status-actions">
        <RouterLink v-for="a in actions" :key="a.text" :to="a.to" class="dd-chip" :class="`is-${a.tone}`">{{ a.text }}</RouterLink>
      </div>
    </div>

    <div class="dd-kpis">
      <RouterLink v-for="k in kpis" :key="k.label" :to="k.to" class="dd-kpi">
        <div class="dd-kpi-top">
          <span>{{ k.label }}</span>
          <em v-if="k.level" class="dd-level" :class="`is-${LEVEL[k.level]?.tone}`">{{ LEVEL[k.level]?.label }}</em>
        </div>
        <strong>{{ k.value }}</strong>
        <small>{{ k.sub }}</small>
        <TrendSparkline v-if="k.spark.length" :values="k.spark" :color="k.color" />
      </RouterLink>
    </div>

    <!-- 2. Livre-t-on bien ? -->
    <div class="dd-row dd-row-2-1">
      <section class="dd-card">
        <header class="dd-card-head"><div><h2>Activité de livraison</h2><p>Volume et qualité, jour par jour</p></div></header>
        <DeliveryChart :days="daily" />
      </section>
      <section class="dd-card">
        <header class="dd-card-head"><div><h2>Indicateurs DORA</h2><p>Référence de l’industrie pour la performance de livraison</p></div></header>
        <div class="dd-dora">
          <div v-for="m in doraTiles" :key="m.key" class="dd-dora-tile" :class="`is-${LEVEL[m.level]?.tone}`" :title="m.definition">
            <span>{{ m.label }}</span>
            <strong>{{ doraValue(m.value, m.unit) }}</strong>
            <em>{{ LEVEL[m.level]?.label }}</em>
          </div>
        </div>
      </section>
    </div>

    <!-- 3. Où ça casse ? -->
    <div class="dd-row dd-row-1-1">
      <section class="dd-card">
        <header class="dd-card-head"><div><h2>Où ça casse</h2><p>Causes trouvées par l’analyste sur la période</p></div></header>
        <p v-if="!causes.length" class="dd-empty">Aucune panne analysée sur la période.</p>
        <ul v-else class="dd-causes">
          <li v-for="c in causes" :key="c.code">
            <span class="dd-cause-label">{{ c.title }}</span>
            <span class="dd-cause-bar"><i :style="{ width: `${(c.count / causeMax) * 100}%` }" /></span>
            <strong>{{ c.count }}</strong>
          </li>
        </ul>
        <div class="dd-ai">
          <div><strong>{{ insights?.degradedVersions ?? 0 }}</strong><span>version(s) retirée(s) par le gardien</span></div>
          <div><strong>{{ insights?.analystPrecision ?? '—' }}<template v-if="insights?.analystPrecision !== null && insights?.analystPrecision !== undefined"> %</template></strong><span>précision de l’analyste ({{ insights?.analystReviews ?? 0 }} avis)</span></div>
        </div>
      </section>

      <!-- 4. La plateforme tient-elle ? -->
      <section class="dd-card">
        <header class="dd-card-head"><div><h2>Capacité du cluster</h2><p>Part réservée par les pods (au-delà de 90 %, les nouveaux pods ne démarrent plus)</p></div></header>
        <p v-if="cluster?.error" class="dd-empty">{{ cluster.error }}</p>
        <div v-else-if="cluster" class="dd-capacity">
          <div class="dd-gauges">
            <DonutGauge :value="cluster.cpuRequested" label="Processeur" :sub="cluster.cpuCores ? `réservé sur ${cluster.cpuCores.toLocaleString('fr-FR')} cœurs` : ''" />
            <DonutGauge :value="cluster.memoryRequested" label="Mémoire" :sub="cluster.memoryGb ? `réservée sur ${cluster.memoryGb.toLocaleString('fr-FR')} Go` : ''" />
          </div>
          <div class="dd-usage">
            <p class="dd-usage-title">Utilisation réelle sur 24 h <span v-if="cluster.runningPodsNow">· {{ cluster.runningPodsNow }} pods en service</span></p>
            <TimeChart :series="usageSeries" :from="cluster.from" :to="cluster.to" unit="%" :height="170" />
          </div>
        </div>
      </section>
    </div>

    <section v-if="envApps.length" class="dd-card">
      <header class="dd-card-head"><div><h2>Carte des environnements</h2><p>Chaque case : l’état et la version en service ; un clic ouvre ses courbes</p></div></header>
      <div class="dd-map" :style="{ gridTemplateColumns: `minmax(140px, 1fr) repeat(${envTypes.length}, minmax(120px, 1fr))` }">
        <span />
        <span v-for="t in envTypes" :key="t.key" class="dd-map-col">{{ t.label }}</span>
        <template v-for="app in envApps" :key="app">
          <span class="dd-map-app">{{ app }}</span>
          <template v-for="t in envTypes" :key="t.key">
            <RouterLink v-if="envCell(app, t.key)" :to="`/supervision?env=${envCell(app, t.key)!.environmentId}`" class="dd-map-cell" :class="`is-${STATE[envCell(app, t.key)!.state]?.tone ?? 'muted'}`">
              <strong>{{ envCell(app, t.key)!.version ?? '—' }}</strong>
              <span>{{ STATE[envCell(app, t.key)!.state]?.label }}<template v-if="envCell(app, t.key)!.openAlerts"> · {{ envCell(app, t.key)!.openAlerts }} alerte(s)</template></span>
            </RouterLink>
            <span v-else class="dd-map-cell is-none">—</span>
          </template>
        </template>
      </div>
    </section>

    <div class="dd-row dd-row-2-1">
      <section class="dd-card">
        <header class="dd-card-head"><div><h2>Activité récente</h2></div><RouterLink to="/deployments" class="dd-link">Tout voir</RouterLink></header>
        <p v-if="!recent.length" class="dd-empty">Aucun déploiement pour le moment.</p>
        <ol v-else class="dd-feed">
          <li v-for="d in recent" :key="d.id">
            <span class="dd-feed-dot" :class="`is-${status(d.status).tone}`" />
            <RouterLink :to="`/deployments/${d.id}`" class="dd-feed-main"><strong>{{ d.applicationName }}</strong> <code>{{ d.version }}</code> <span class="dd-feed-env">→ {{ d.environmentName }}</span></RouterLink>
            <span class="dd-pill" :class="`is-${status(d.status).tone}`">{{ status(d.status).label }}</span>
            <time>{{ relative(d.createdAt) }}</time>
          </li>
        </ol>
      </section>
      <section class="dd-card">
        <header class="dd-card-head"><div><h2>À traiter</h2></div></header>
        <p v-if="!actions.length" class="dd-ok">Rien à traiter.</p>
        <RouterLink v-for="a in actions" v-else :key="a.text" :to="a.to" class="dd-todo" :class="`is-${a.tone}`">{{ a.text }}</RouterLink>
      </section>
    </div>

    <section class="dd-section">
      <h2 class="dd-section-title">Infrastructure et données</h2>
      <div class="dd-row dd-row-1-1">
        <StorageHealthCard />
        <DataProtectionCard />
      </div>
    </section>
  </section>
</template>

<style scoped>
.dd { display: flex; flex-direction: column; gap: 16px; }
.dd-head { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 12px; }
.dd-head h1 { margin: 0; color: #0f172a; font-size: 26px; letter-spacing: -.01em; }
.dd-head p { margin: 2px 0 0; color: #64748b; font-size: 13.5px; }
.dd-head-actions { display: flex; align-items: center; gap: 10px; }
.dd-range { display: flex; gap: 3px; padding: 3px; border-radius: 10px; background: #eef2f7; }
.dd-range button { padding: 6px 12px; border: 0; border-radius: 8px; background: none; color: #475569; font: inherit; font-size: 13px; font-weight: 700; cursor: pointer; }
.dd-range .is-active { background: #fff; color: #1e3a8a; box-shadow: 0 1px 3px rgba(15, 23, 42, .12); }
.dd-btn { padding: 8px 14px; border: 1px solid #e2e8f0; border-radius: 10px; background: #fff; color: #0f172a; font: inherit; font-size: 13.5px; font-weight: 700; cursor: pointer; }
.dd-btn:disabled { opacity: .6; }

.dd-status { display: flex; flex-wrap: wrap; align-items: center; gap: 16px; padding: 18px 22px; border-radius: 18px; color: #fff; }
.dd-status.is-ok { background: linear-gradient(120deg, #065f46, #16a34a); }
.dd-status.is-attention { background: linear-gradient(120deg, #1e3a8a, #4f46e5); }
.dd-status-icon { display: grid; place-items: center; width: 48px; height: 48px; border-radius: 14px; background: rgba(255, 255, 255, .16); }
.dd-status-icon svg { width: 26px; height: 26px; fill: none; stroke: #fff; stroke-width: 2.2; stroke-linecap: round; stroke-linejoin: round; }
.dd-status-text { display: flex; flex: 1; flex-direction: column; min-width: 220px; }
.dd-status-text strong { font-size: 20px; }
.dd-status-text span { opacity: .85; font-size: 13.5px; }
.dd-status-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.dd-chip { padding: 7px 12px; border-radius: 999px; background: rgba(255, 255, 255, .95); color: #0f172a; font-size: 13px; font-weight: 700; text-decoration: none; }
.dd-chip.is-critical { color: #b91c1c; } .dd-chip.is-approval { color: #4338ca; } .dd-chip.is-warning { color: #b45309; }

.dd-kpis { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; }
.dd-kpi { display: flex; flex-direction: column; gap: 2px; padding: 14px 16px 10px; border: 1px solid #e2e8f0; border-radius: 16px; background: #fff; color: inherit; text-decoration: none; transition: box-shadow .15s, transform .15s; }
.dd-kpi:hover { box-shadow: 0 10px 24px rgba(15, 23, 42, .08); transform: translateY(-1px); }
.dd-kpi-top { display: flex; align-items: center; justify-content: space-between; color: #64748b; font-size: 12.5px; font-weight: 700; }
.dd-kpi strong { color: #0f172a; font-size: 28px; letter-spacing: -.02em; }
.dd-kpi small { color: #94a3b8; font-size: 12px; }

.dd-row { display: grid; gap: 16px; }
.dd-row-2-1 { grid-template-columns: minmax(0, 2fr) minmax(0, 1fr); }
.dd-row-1-1 { grid-template-columns: 1fr 1fr; }
@media (max-width: 1100px) { .dd-row-2-1, .dd-row-1-1, .dd-kpis { grid-template-columns: 1fr; } }
.dd-card { display: flex; flex-direction: column; gap: 12px; padding: 18px 20px; border: 1px solid #e2e8f0; border-radius: 18px; background: #fff; }
.dd-card-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; }
.dd-card-head h2 { margin: 0; color: #0f172a; font-size: 16px; }
.dd-card-head p { margin: 2px 0 0; color: #64748b; font-size: 12.5px; }
.dd-link { color: #1d4ed8; font-size: 13px; font-weight: 700; text-decoration: none; }
.dd-empty { margin: 0; color: #64748b; font-size: 13.5px; }
.dd-ok { margin: 0; padding: 12px; border-radius: 12px; background: #f0fdf4; color: #166534; font-weight: 700; }

.dd-level, .dd-dora-tile em { padding: 1px 8px; border-radius: 999px; font-size: 11px; font-style: normal; font-weight: 800; }
.is-elite.dd-level, .is-elite em { background: #dcfce7; color: #166534; }
.is-high.dd-level, .is-high em { background: #ecfccb; color: #3f6212; }
.is-medium.dd-level, .is-medium em { background: #fef3c7; color: #92400e; }
.is-low.dd-level, .is-low em { background: #fee2e2; color: #991b1b; }
.is-none.dd-level, .is-none em { background: #f1f5f9; color: #64748b; }
.dd-dora { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.dd-dora-tile { display: flex; flex-direction: column; gap: 4px; padding: 12px; border-radius: 14px; background: #f8fafc; border-left: 4px solid #cbd5e1; cursor: help; }
.dd-dora-tile.is-elite { border-left-color: #16a34a; } .dd-dora-tile.is-high { border-left-color: #84cc16; }
.dd-dora-tile.is-medium { border-left-color: #f59e0b; } .dd-dora-tile.is-low { border-left-color: #dc2626; }
.dd-dora-tile span { color: #64748b; font-size: 12px; font-weight: 700; }
.dd-dora-tile strong { color: #0f172a; font-size: 20px; }
.dd-dora-tile em { align-self: flex-start; }

.dd-causes { display: flex; flex-direction: column; gap: 9px; margin: 0; padding: 0; list-style: none; }
.dd-causes li { display: grid; grid-template-columns: minmax(0, 1.2fr) 1fr 28px; align-items: center; gap: 10px; font-size: 13.5px; }
.dd-cause-label { overflow: hidden; color: #334155; text-overflow: ellipsis; white-space: nowrap; }
.dd-cause-bar { height: 10px; overflow: hidden; border-radius: 999px; background: #f1f5f9; }
.dd-cause-bar i { display: block; height: 100%; border-radius: 999px; background: linear-gradient(90deg, #fb923c, #dc2626); }
.dd-causes strong { text-align: right; }
.dd-ai { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: auto; }
.dd-ai div { display: flex; flex-direction: column; padding: 12px; border-radius: 14px; background: linear-gradient(135deg, #eef2ff, #f8fafc); }
.dd-ai strong { color: #312e81; font-size: 22px; }
.dd-ai span { color: #64748b; font-size: 12.5px; }

.dd-capacity { display: flex; flex-direction: column; gap: 10px; }
.dd-gauges { display: flex; justify-content: center; gap: 40px; }
.dd-usage-title { margin: 0 0 4px; color: #475569; font-size: 12.5px; font-weight: 700; }

.dd-map { display: grid; gap: 8px; align-items: stretch; }
.dd-map-col { color: #64748b; font-size: 12px; font-weight: 800; letter-spacing: .04em; text-align: center; text-transform: uppercase; }
.dd-map-app { display: flex; align-items: center; color: #0f172a; font-size: 14px; font-weight: 700; }
.dd-map-cell { display: flex; flex-direction: column; gap: 2px; padding: 10px 12px; border-radius: 12px; color: inherit; text-decoration: none; border: 1px solid transparent; }
.dd-map-cell strong { font-size: 15px; }
.dd-map-cell span { font-size: 12px; }
.dd-map-cell.is-ok { background: #f0fdf4; border-color: #bbf7d0; color: #166534; }
.dd-map-cell.is-critical { background: #fef2f2; border-color: #fecaca; color: #991b1b; }
.dd-map-cell.is-running { background: #eff6ff; border-color: #bfdbfe; color: #1e40af; }
.dd-map-cell.is-muted { background: #f8fafc; border-color: #e2e8f0; color: #64748b; }
.dd-map-cell.is-none { align-items: center; justify-content: center; background: repeating-linear-gradient(45deg, #fafafa, #fafafa 6px, #f4f4f5 6px, #f4f4f5 12px); color: #cbd5e1; }
.dd-map-cell:not(.is-none):hover { box-shadow: 0 6px 16px rgba(15, 23, 42, .08); }

.dd-feed { display: flex; flex-direction: column; margin: 0; padding: 0; list-style: none; }
.dd-feed li { display: grid; grid-template-columns: 14px minmax(0, 1fr) auto 90px; align-items: center; gap: 10px; padding: 9px 2px; border-top: 1px solid #f1f5f9; font-size: 13.5px; }
.dd-feed li:first-child { border-top: 0; }
.dd-feed-dot { width: 10px; height: 10px; border-radius: 999px; background: #cbd5e1; box-shadow: 0 0 0 3px #f1f5f9; }
.dd-feed-dot.is-ok { background: #16a34a; } .dd-feed-dot.is-critical { background: #dc2626; } .dd-feed-dot.is-running { background: #2563eb; } .dd-feed-dot.is-approval { background: #6366f1; }
.dd-feed-main { overflow: hidden; color: #0f172a; text-decoration: none; text-overflow: ellipsis; white-space: nowrap; }
.dd-feed-main code { padding: 0 6px; border-radius: 5px; background: #f1f5f9; font-size: 12px; }
.dd-feed-env { color: #64748b; }
.dd-feed time { color: #94a3b8; font-size: 12px; text-align: right; }
.dd-pill { padding: 2px 9px; border-radius: 999px; font-size: 12px; font-weight: 700; white-space: nowrap; }
.dd-pill.is-ok { background: #dcfce7; color: #166534; } .dd-pill.is-critical { background: #fee2e2; color: #991b1b; }
.dd-pill.is-running { background: #dbeafe; color: #1e40af; } .dd-pill.is-approval { background: #e0e7ff; color: #3730a3; } .dd-pill.is-muted { background: #f1f5f9; color: #475569; }
.dd-todo { display: block; padding: 10px 12px; border-radius: 12px; font-size: 13.5px; font-weight: 700; text-decoration: none; }
.dd-todo.is-critical { background: #fef2f2; color: #991b1b; } .dd-todo.is-approval { background: #eef2ff; color: #3730a3; } .dd-todo.is-warning { background: #fffbeb; color: #92400e; }
.dd-section { display: flex; flex-direction: column; gap: 10px; }
.dd-section-title { margin: 4px 0 0; color: #475569; font-size: 12.5px; font-weight: 800; letter-spacing: .06em; text-transform: uppercase; }
</style>
