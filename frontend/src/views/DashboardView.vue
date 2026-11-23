<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import DashboardTrends from '@/components/dashboard/DashboardTrends.vue'
import DoraPanel from '@/components/dashboard/DoraPanel.vue'
import StorageHealthCard from '@/components/dashboard/StorageHealthCard.vue'
import DataProtectionCard from '@/components/dashboard/DataProtectionCard.vue'
import { getPendingApprovalsCount } from '@/api/deployments.api'
import { useDashboardStore } from '@/stores/dashboard'
import { useAuthStore } from '@/stores/auth'

/*
 * Tableau de bord, organisé du plus important au plus détaillé :
 * l'état général (tout va bien ? que faut-il faire ?), les chiffres clés,
 * l'activité récente, puis la performance de livraison et la santé du socle.
 */
const dashboardStore = useDashboardStore()
const auth = useAuthStore()
const canApprove = computed(() => auth.hasRole('ADMIN', 'DEVOPS'))
const pendingApprovals = ref<number | null>(null)
const refreshedAt = ref<Date | null>(null)

const dashboard = computed(() => dashboardStore.dashboard)
const recentDeployments = computed(() => dashboard.value?.recentDeployments ?? [])
const recentFailures = computed(() => recentDeployments.value.filter(d => d.status === 'FAILED'))

/* Chiffres clés */
const kpis = computed(() => {
  const d = dashboard.value
  if (!d) return []
  return [
    { key: 'apps', label: 'Applications', value: d.totalApplications, detail: `${d.activeApplications} active${d.activeApplications > 1 ? 's' : ''}`, to: '/applications', tone: 'blue',
      icon: 'M4 5h12v4H4zM4 11h12v4H4z' },
    { key: 'envs', label: 'Environnements', value: d.totalEnvironments, detail: `${d.activeEnvironments} actif${d.activeEnvironments > 1 ? 's' : ''}`, to: '/environments', tone: 'teal',
      icon: 'M10 3l7 4-7 4-7-4zM3 11l7 4 7-4' },
    { key: 'deps', label: 'Déploiements', value: d.totalDeployments, detail: `${d.successfulDeployments} réussis · ${d.failedDeployments} échoués`, to: '/deployments', tone: 'violet',
      icon: 'M10 16V5M5.5 9.5 10 5l4.5 4.5' },
  ]
})
const successRate = computed(() => Math.round((dashboard.value?.successRate ?? 0) * 10) / 10)

/* Anneau « Résultats des déploiements » : réussis, échoués, autres (annulés, refusés, remplacés…) */
const ring = computed(() => {
  const d = dashboard.value
  const total = d?.totalDeployments ?? 0
  const ok = d?.successfulDeployments ?? 0
  const ko = d?.failedDeployments ?? 0
  const other = Math.max(0, total - ok - ko)
  const circumference = 2 * Math.PI * 42
  const part = (n: number) => (total ? (n / total) * circumference : 0)
  return {
    total, ok, ko, other, circumference,
    segments: [
      { key: 'ok', length: part(ok), offset: 0, color: '#16a34a' },
      { key: 'ko', length: part(ko), offset: part(ok), color: '#dc2626' },
      { key: 'other', length: part(other), offset: part(ok) + part(ko), color: '#cbd5e1' },
    ],
  }
})
const rateTone = computed(() => successRate.value >= 85 ? 'ok' : successRate.value >= 60 ? 'warning' : 'critical')

/* Ce qui demande une action, pour le bandeau d'état et la colonne « À traiter » */
const actions = computed(() => {
  const list: { key: string; tone: string; text: string; to: string }[] = []
  const alerts = dashboard.value?.openAlerts ?? 0
  if (alerts > 0) list.push({ key: 'alerts', tone: 'critical', text: `${alerts} alerte${alerts > 1 ? 's' : ''} ouverte${alerts > 1 ? 's' : ''}`, to: '/alerts' })
  if (pendingApprovals.value) list.push({ key: 'approvals', tone: 'approval', text: `${pendingApprovals.value} mise${pendingApprovals.value > 1 ? 's' : ''} en production à approuver`, to: '/deployments' })
  const lastFailure = recentFailures.value[0]
  if (lastFailure) list.push({ key: 'failure', tone: 'warning', text: `Échec récent : ${lastFailure.applicationName} ${lastFailure.version} (${lastFailure.environmentName})`, to: '/deployments' })
  return list
})

const STATUS: Record<string, { label: string; tone: string }> = {
  SUCCESS: { label: 'Réussi', tone: 'ok' },
  FAILED: { label: 'Échoué', tone: 'critical' },
  RUNNING: { label: 'En cours', tone: 'running' },
  PENDING: { label: 'En attente', tone: 'muted' },
  CANCELLED: { label: 'Annulé', tone: 'muted' },
  ROLLED_BACK: { label: 'Remplacé', tone: 'muted' },
  AWAITING_APPROVAL: { label: 'À approuver', tone: 'approval' },
  REJECTED: { label: 'Refusé', tone: 'critical' },
}
const status = (s: string) => STATUS[s] ?? { label: s, tone: 'muted' }

function relative(iso: string) {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  if (minutes < 1) return 'à l’instant'
  if (minutes < 60) return `il y a ${minutes} min`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `il y a ${hours} h`
  const days = Math.round(hours / 24)
  return days < 30 ? `il y a ${days} j` : new Date(iso).toLocaleDateString('fr-FR')
}
const refreshedLabel = computed(() => refreshedAt.value
  ? refreshedAt.value.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })
  : '')

async function load() {
  await dashboardStore.loadDashboard()
  if (canApprove.value) {
    try { pendingApprovals.value = await getPendingApprovalsCount() } catch { pendingApprovals.value = null }
  }
  refreshedAt.value = new Date()
}
async function refresh() {
  await dashboardStore.refresh()
  if (canApprove.value) {
    try { pendingApprovals.value = await getPendingApprovalsCount() } catch { pendingApprovals.value = null }
  }
  refreshedAt.value = new Date()
}

onMounted(load)
</script>

<template>
  <section class="db">
    <!-- En-tête -->
    <header class="db-header">
      <div>
        <h1>Tableau de bord</h1>
        <p class="db-sub">
          Vue d’ensemble de la plateforme
          <template v-if="refreshedLabel"> · mis à jour à {{ refreshedLabel }}</template>
        </p>
      </div>
      <div class="db-header-actions">
        <button type="button" class="db-btn" :disabled="dashboardStore.loading" @click="refresh">Actualiser</button>
        <RouterLink to="/deployments" class="db-btn db-btn-primary">Déploiements</RouterLink>
      </div>
    </header>

    <div v-if="dashboardStore.loading && !dashboard" class="db-state">Chargement du tableau de bord…</div>
    <div v-else-if="dashboardStore.errorMessage" class="db-state is-error">
      <span>{{ dashboardStore.errorMessage }}</span>
      <button type="button" class="db-btn" @click="refresh">Réessayer</button>
    </div>

    <template v-else-if="dashboard">
      <!-- Bandeau d'état : tout va bien, ou ce qui demande une action -->
      <div v-if="!actions.length" class="db-banner is-ok" role="status">
        <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><circle cx="10" cy="10" r="9" fill="currentColor" opacity=".15" /><path d="M6 10.5l2.5 2.5L14 7.5" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
        <span><strong>Tout est opérationnel.</strong> Aucune alerte ouverte et aucune action en attente.</span>
      </div>
      <div v-else class="db-banner is-attention" role="status">
        <svg viewBox="0 0 20 20" width="18" height="18" aria-hidden="true"><circle cx="10" cy="10" r="9" fill="currentColor" opacity=".15" /><path d="M10 5.5v5.5M10 14.2v.3" stroke="currentColor" stroke-width="2" stroke-linecap="round" /></svg>
        <strong>À traiter :</strong>
        <RouterLink v-for="a in actions" :key="a.key" :to="a.to" class="db-pill" :class="`is-${a.tone}`">{{ a.text }}</RouterLink>
      </div>

      <!-- Chiffres clés -->
      <div class="db-kpis">
        <RouterLink v-for="k in kpis" :key="k.key" :to="k.to" class="db-kpi">
          <span class="db-kpi-icon" :class="`is-${k.tone}`" aria-hidden="true">
            <svg viewBox="0 0 20 20" width="18" height="18"><path :d="k.icon" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </span>
          <span class="db-kpi-label">{{ k.label }}</span>
          <strong class="db-kpi-value">{{ k.value }}</strong>
          <span class="db-kpi-detail">{{ k.detail }}</span>
        </RouterLink>
        <div class="db-kpi">
          <span class="db-kpi-icon is-green" aria-hidden="true">
            <svg viewBox="0 0 20 20" width="18" height="18"><path d="M4.5 10.5l3.5 3.5 7.5-8" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" /></svg>
          </span>
          <span class="db-kpi-label">Taux de réussite</span>
          <strong class="db-kpi-value">{{ successRate.toLocaleString('fr-FR') }} %</strong>
          <span class="db-bar" :class="`is-${rateTone}`"><span :style="{ width: `${Math.min(100, successRate)}%` }" /></span>
          <span class="db-kpi-detail">des déploiements terminés</span>
        </div>
      </div>

      <!-- Activité récente et actions -->
      <div class="db-main">
        <section class="db-card">
          <header class="db-card-head">
            <div>
              <h2>Déploiements récents</h2>
              <p>Les dernières opérations enregistrées</p>
            </div>
            <RouterLink to="/deployments" class="db-link">Tout voir</RouterLink>
          </header>
          <p v-if="!recentDeployments.length" class="db-empty">Aucun déploiement pour le moment.</p>
          <div v-else class="db-table-wrap">
            <table class="db-table">
              <thead>
                <tr><th>Application</th><th>Version</th><th>Environnement</th><th>Statut</th><th class="db-right">Date</th></tr>
              </thead>
              <tbody>
                <tr v-for="d in recentDeployments" :key="d.id">
                  <td>
                    <RouterLink :to="`/applications/${d.applicationId}`" class="db-app">{{ d.applicationName }}</RouterLink>
                    <span class="db-id">#{{ d.id }}</span>
                  </td>
                  <td><span class="db-version">{{ d.version }}</span></td>
                  <td>{{ d.environmentName }}</td>
                  <td><span class="db-status" :class="`is-${status(d.status).tone}`"><span class="db-dot" :class="`is-${status(d.status).tone}`" />{{ status(d.status).label }}</span></td>
                  <td class="db-right db-when" :title="new Date(d.createdAt).toLocaleString('fr-FR')">{{ relative(d.createdAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <aside class="db-side">
          <section class="db-card">
            <header class="db-card-head"><h2>À traiter</h2></header>
            <ul class="db-todo">
              <li>
                <RouterLink to="/alerts" class="db-todo-row">
                  <span>Alertes ouvertes</span>
                  <span class="db-count" :class="dashboard.openAlerts ? 'is-critical' : 'is-zero'">{{ dashboard.openAlerts }}</span>
                </RouterLink>
              </li>
              <li v-if="canApprove">
                <RouterLink to="/deployments" class="db-todo-row">
                  <span>Mises en production à approuver</span>
                  <span class="db-count" :class="pendingApprovals ? 'is-approval' : 'is-zero'">{{ pendingApprovals ?? '–' }}</span>
                </RouterLink>
              </li>
              <li>
                <RouterLink to="/deployments" class="db-todo-row">
                  <span>Échecs parmi les derniers déploiements</span>
                  <span class="db-count" :class="recentFailures.length ? 'is-warning' : 'is-zero'">{{ recentFailures.length }}</span>
                </RouterLink>
              </li>
            </ul>
          </section>

          <section class="db-card">
            <header class="db-card-head"><h2>Résultats des déploiements</h2></header>
            <div class="db-ring">
              <svg viewBox="0 0 100 100" width="132" height="132" role="img" :aria-label="`${ring.ok} réussis, ${ring.ko} échoués, ${ring.other} autres`">
                <circle cx="50" cy="50" r="42" fill="none" stroke="#f1f5f9" stroke-width="11" />
                <circle v-for="seg in ring.segments" :key="seg.key" cx="50" cy="50" r="42" fill="none" :stroke="seg.color" stroke-width="11"
                        :stroke-dasharray="`${seg.length} ${ring.circumference - seg.length}`" :stroke-dashoffset="-seg.offset" transform="rotate(-90 50 50)" />
                <text x="50" y="47" text-anchor="middle" class="db-ring-value">{{ ring.total }}</text>
                <text x="50" y="61" text-anchor="middle" class="db-ring-label">déploiements</text>
              </svg>
              <ul class="db-legend">
                <li><span class="db-sq" style="background:#16a34a" />Réussis <strong>{{ ring.ok }}</strong></li>
                <li><span class="db-sq" style="background:#dc2626" />Échoués <strong>{{ ring.ko }}</strong></li>
                <li><span class="db-sq" style="background:#cbd5e1" />Autres <strong>{{ ring.other }}</strong></li>
              </ul>
            </div>
          </section>
        </aside>
      </div>
    </template>

    <!-- Tendances -->
    <section class="db-section">
      <h2 class="db-section-title">Tendances</h2>
      <DashboardTrends />
    </section>

    <!-- Performance de livraison -->
    <section class="db-section">
      <h2 class="db-section-title">Performance de livraison</h2>
      <DoraPanel />
    </section>

    <!-- Socle -->
    <section class="db-section">
      <h2 class="db-section-title">Infrastructure et données</h2>
      <div class="db-grid-2">
        <DataProtectionCard />
        <StorageHealthCard />
      </div>
    </section>
  </section>
</template>

<style scoped>
.db { --ink: #111827; --muted: #6b7280; --line: #e5e7eb; --card: #fff;
  display: flex; flex-direction: column; gap: 20px; color: var(--ink); }
.db-header { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 12px; }
.db-header h1 { margin: 0; font-size: 26px; font-weight: 700; letter-spacing: -.01em; }
.db-sub { margin: 4px 0 0; color: var(--muted); font-size: 14px; }
.db-header-actions { display: flex; gap: 8px; }
.db-btn { display: inline-flex; align-items: center; padding: 8px 14px; border: 1px solid var(--line); border-radius: 8px; background: #fff; color: var(--ink); font: inherit; font-size: 14px; font-weight: 600; text-decoration: none; cursor: pointer; }
.db-btn:hover:not(:disabled) { background: #f9fafb; }
.db-btn:disabled { opacity: .6; cursor: wait; }
.db-btn-primary { border-color: #1e3a8a; background: #1e3a8a; color: #fff; }
.db-btn-primary:hover { background: #1e40af !important; }

.db-state { padding: 16px; border: 1px solid var(--line); border-radius: 12px; background: var(--card); color: var(--muted); }
.db-state.is-error { display: flex; align-items: center; justify-content: space-between; gap: 12px; border-color: #fecaca; background: #fef2f2; color: #991b1b; }

.db-banner { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; padding: 12px 16px; border: 1px solid; border-radius: 12px; font-size: 14px; }
.db-banner.is-ok { border-color: #bbf7d0; background: #f0fdf4; color: #166534; }
.db-banner.is-attention { border-color: #fde68a; background: #fffbeb; color: #92400e; }
.db-pill { padding: 3px 10px; border-radius: 999px; font-size: 13px; font-weight: 600; text-decoration: none; }
.db-pill:hover { filter: brightness(.96); text-decoration: underline; }
.db-pill.is-critical { background: #fee2e2; color: #991b1b; }
.db-pill.is-approval { background: #ede9fe; color: #5b21b6; }
.db-pill.is-warning { background: #ffedd5; color: #9a3412; }

.db-kpis { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 14px; }
.db-kpi { display: flex; flex-direction: column; gap: 4px; padding: 16px 18px; border: 1px solid var(--line); border-radius: 12px; background: var(--card); color: inherit; text-decoration: none; transition: border-color .15s, box-shadow .15s; }
a.db-kpi:hover { border-color: #c7d2fe; box-shadow: 0 2px 10px rgba(30, 58, 138, .07); }
.db-kpi { position: relative; padding-top: 18px; }
.db-kpi-icon { display: grid; place-items: center; width: 34px; height: 34px; margin-bottom: 6px; border-radius: 9px; }
.db-kpi-icon.is-blue { background: #e0e7ff; color: #3730a3; }
.db-kpi-icon.is-teal { background: #ccfbf1; color: #0f766e; }
.db-kpi-icon.is-violet { background: #ede9fe; color: #6d28d9; }
.db-kpi-icon.is-green { background: #dcfce7; color: #15803d; }
.db-kpi-label { color: var(--muted); font-size: 13px; font-weight: 600; }
.db-kpi-value { font-size: 28px; font-weight: 700; line-height: 1.1; letter-spacing: -.01em; }
.db-kpi-detail { color: var(--muted); font-size: 12.5px; }
.db-bar { display: block; height: 6px; margin: 4px 0 2px; border-radius: 999px; background: #eef0f3; overflow: hidden; }
.db-bar span { display: block; height: 100%; border-radius: 999px; }
.db-bar.is-ok span { background: #16a34a; }
.db-bar.is-warning span { background: #d97706; }
.db-bar.is-critical span { background: #dc2626; }

.db-main { display: grid; grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr); gap: 16px; align-items: start; }
@media (max-width: 1000px) { .db-main { grid-template-columns: 1fr; } }
.db-side { display: flex; flex-direction: column; gap: 16px; }
.db-card { padding: 16px 18px; border: 1px solid var(--line); border-radius: 12px; background: var(--card); }
.db-card-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; margin-bottom: 10px; }
.db-card-head h2 { margin: 0; font-size: 16px; }
.db-card-head p { margin: 2px 0 0; color: var(--muted); font-size: 13px; }
.db-link { color: #1e40af; font-size: 13.5px; font-weight: 600; text-decoration: none; }
.db-link:hover { text-decoration: underline; }
.db-empty { margin: 0; color: var(--muted); font-size: 14px; }

.db-table-wrap { overflow-x: auto; margin: 0 -4px; }
.db-table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
.db-table th { padding: 8px 10px; border-bottom: 1px solid var(--line); color: var(--muted); font-size: 12px; font-weight: 600; letter-spacing: .03em; text-align: left; text-transform: uppercase; white-space: nowrap; }
.db-table td { padding: 11px 10px; border-bottom: 1px solid #f1f2f4; vertical-align: middle; }
.db-table tbody tr:last-child td { border-bottom: 0; }
.db-table tbody tr:hover td { background: #fafbff; }
.db-table .db-right { text-align: right; }
.db-app { color: var(--ink); font-weight: 600; text-decoration: none; }
.db-app:hover { color: #1e40af; text-decoration: underline; }
.db-id { margin-left: 6px; color: var(--muted); font-size: 12px; }
.db-version { padding: 1px 7px; border-radius: 5px; background: #f3f4f6; color: #374151; font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; white-space: nowrap; }
.db-status { display: inline-flex; align-items: center; gap: 6px; padding: 2px 10px 2px 8px; border-radius: 999px; font-size: 12px; font-weight: 600; white-space: nowrap; }
.db-dot { width: 7px; height: 7px; border-radius: 999px; }
.db-when { color: var(--muted); font-size: 12.5px; white-space: nowrap; }
.is-ok.db-dot { background: #16a34a; } .is-ok.db-status { background: #dcfce7; color: #166534; }
.is-critical.db-dot { background: #dc2626; } .is-critical.db-status { background: #fee2e2; color: #991b1b; }
.is-running.db-dot { background: #2563eb; } .is-running.db-status { background: #dbeafe; color: #1e40af; }
.is-approval.db-dot { background: #7c3aed; } .is-approval.db-status { background: #ede9fe; color: #5b21b6; }
.is-muted.db-dot { background: #9ca3af; } .is-muted.db-status { background: #f3f4f6; color: #4b5563; }

.db-todo { margin: 0; padding: 0; list-style: none; }
.db-todo-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding: 9px 2px; border-top: 1px solid #f1f2f4; color: var(--ink); font-size: 14px; text-decoration: none; }
.db-todo li:first-child .db-todo-row { border-top: 0; }
.db-todo-row:hover span:first-child { color: #1e40af; text-decoration: underline; }
.db-count { min-width: 28px; padding: 1px 8px; border-radius: 999px; font-size: 13px; font-weight: 700; text-align: center; }
.db-count.is-zero { background: #f3f4f6; color: #6b7280; }
.db-count.is-critical { background: #fee2e2; color: #991b1b; }
.db-count.is-approval { background: #ede9fe; color: #5b21b6; }
.db-count.is-warning { background: #ffedd5; color: #9a3412; }
.db-ring { display: flex; align-items: center; gap: 16px; }
.db-ring-value { fill: #111827; font-size: 18px; font-weight: 700; }
.db-ring-label { fill: #6b7280; font-size: 8px; }
.db-legend { display: flex; flex-direction: column; gap: 8px; margin: 0; padding: 0; list-style: none; font-size: 13.5px; }
.db-legend li { display: flex; align-items: center; gap: 8px; }
.db-legend strong { margin-left: auto; padding-left: 12px; }
.db-sq { width: 10px; height: 10px; border-radius: 3px; }
.db-section { display: flex; flex-direction: column; gap: 10px; }
.db-section-title { margin: 6px 0 0; color: #374151; font-size: 13px; font-weight: 700; letter-spacing: .05em; text-transform: uppercase; }
.db-grid-2 { display: grid; grid-template-columns: repeat(auto-fit, minmax(340px, 1fr)); gap: 16px; align-items: start; }
</style>
