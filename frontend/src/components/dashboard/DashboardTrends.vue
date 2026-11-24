<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import DailyBars from '@/components/charts/DailyBars.vue'
import { getInsights, type Insights, type Kpi } from '@/api/insights.api'

/* Tendances : l’activité jour par jour, les indicateurs comparés à la période précédente, les causes de panne, la santé des environnements. */
const days = ref(14)
const data = ref<Insights | null>(null)
async function load() { try { data.value = await getInsights(days.value) } catch { data.value = null } }
onMounted(load)
watch(days, load)

function value(k: Kpi) {
  if (k.value === null) return '—'
  const n = k.value.toLocaleString('fr-FR', { maximumFractionDigits: 1 })
  return k.unit === '%' ? `${n} %` : k.unit === 'min' ? `${n} min` : n
}
function trend(k: Kpi) {
  if (k.value === null || k.previous === null || k.previous === 0) return null
  const diff = k.value - k.previous
  if (Math.abs(diff) < 0.05) return { text: 'stable', tone: 'flat' }
  const better = k.higherIsBetter ? diff > 0 : diff < 0
  const pct = Math.round((Math.abs(diff) / Math.abs(k.previous)) * 100)
  return { text: `${diff > 0 ? '▲' : '▼'} ${pct} %`, tone: better ? 'good' : 'bad' }
}
const STATE: Record<string, { label: string; tone: string }> = {
  HEALTHY: { label: 'En service', tone: 'ok' }, ALERT: { label: 'Alerte', tone: 'critical' }, FAILED: { label: 'Dernier échec', tone: 'critical' },
  DEPLOYING: { label: 'Déploiement', tone: 'running' }, IDLE: { label: 'Arrêté', tone: 'muted' }, EMPTY: { label: 'Jamais déployé', tone: 'muted' },
}
/* Le taux de réussite et le volume sont en haut du tableau de bord ; le rétablissement est dans DORA. */
const PROCESS_KPIS = ['pipeline', 'approval']
const maxCause = (list: { count: number }[]) => Math.max(1, ...list.map(c => c.count))
</script>

<template>
  <div v-if="data" class="tr">
    <div class="tr-head">
      <p class="tr-hint">Le processus de livraison, comparé à la période précédente.</p>
      <div class="tr-range">
        <button v-for="d in [7, 14, 30]" :key="d" type="button" :class="{ 'is-active': days === d }" @click="days = d">{{ d }} jours</button>
      </div>
    </div>

    <div class="tr-kpis">
      <div v-for="k in data.kpis.filter(x => PROCESS_KPIS.includes(x.key))" :key="k.key" class="tr-kpi">
        <span>{{ k.label }}</span>
        <strong>{{ value(k) }}</strong>
        <small v-if="trend(k)" :class="`is-${trend(k)!.tone}`">{{ trend(k)!.text }} <em>vs période précédente</em></small>
        <small v-else class="is-flat">pas de comparaison</small>
      </div>
    </div>

    <div class="tr-grid">
      <section class="tr-card tr-wide">
        <h4>Déploiements par jour</h4>
        <DailyBars :days="data.daily" />
      </section>
      <section class="tr-card">
        <h4>Causes de panne les plus fréquentes</h4>
        <p v-if="!data.causes.length" class="tr-muted">Aucune panne analysée sur la période.</p>
        <ul v-else class="tr-causes">
          <li v-for="c in data.causes" :key="c.code">
            <span class="tr-cause-label">{{ c.title }}</span>
            <span class="tr-cause-bar"><i :style="{ width: `${(c.count / maxCause(data.causes)) * 100}%` }" /></span>
            <strong>{{ c.count }}</strong>
          </li>
        </ul>
      </section>
    </div>

    <section class="tr-card">
      <h4>Santé des environnements</h4>
      <div class="tr-envs">
        <RouterLink v-for="e in data.environments" :key="e.environmentId" :to="`/supervision?env=${e.environmentId}`" class="tr-env" :class="`is-${STATE[e.state]?.tone ?? 'muted'}`">
          <span class="tr-env-dot" />
          <span class="tr-env-body">
            <strong>{{ e.applicationName }}</strong>
            <small>{{ e.environmentName }}<template v-if="e.version"> · {{ e.version }}</template></small>
          </span>
          <span class="tr-env-state">{{ STATE[e.state]?.label ?? e.state }}<template v-if="e.openAlerts"> ({{ e.openAlerts }})</template></span>
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<style scoped>
.tr { display: flex; flex-direction: column; gap: 12px; }
.tr-head { display: flex; align-items: center; justify-content: space-between; }
.tr-hint { margin: 0; color: #6b7280; font-size: 13px; }
.tr-range { display: flex; gap: 4px; padding: 3px; border-radius: 10px; background: #f3f4f6; }
.tr-range button { padding: 5px 12px; border: 0; border-radius: 8px; background: none; font: inherit; font-size: 13px; font-weight: 700; color: #4b5563; cursor: pointer; }
.tr-range .is-active { background: #fff; color: #1e3a8a; box-shadow: 0 1px 3px rgba(15, 23, 42, .12); }
.tr-kpis { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.tr-kpi { display: flex; flex-direction: column; gap: 2px; padding: 12px 14px; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; }
.tr-kpi span { color: #6b7280; font-size: 12px; font-weight: 600; }
.tr-kpi strong { font-size: 22px; }
.tr-kpi small { font-size: 12px; font-weight: 700; }
.tr-kpi em { color: #9ca3af; font-style: normal; font-weight: 500; }
.is-good { color: #15803d; } .is-bad { color: #b91c1c; } .is-flat { color: #6b7280; }
.tr-grid { display: grid; grid-template-columns: 2fr 1fr; gap: 12px; }
@media (max-width: 1000px) { .tr-grid, .tr-kpis { grid-template-columns: 1fr 1fr; } }
.tr-card { padding: 14px 16px; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; }
.tr-card h4 { margin: 0 0 10px; font-size: 14px; color: #374151; }
.tr-muted { margin: 0; color: #6b7280; font-size: 13px; }
.tr-causes { display: flex; flex-direction: column; gap: 8px; margin: 0; padding: 0; list-style: none; }
.tr-causes li { display: grid; grid-template-columns: minmax(0, 1.3fr) 1fr 24px; align-items: center; gap: 8px; font-size: 13px; }
.tr-cause-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tr-cause-bar { height: 8px; border-radius: 999px; background: #f3f4f6; overflow: hidden; }
.tr-cause-bar i { display: block; height: 100%; border-radius: 999px; background: linear-gradient(90deg, #f97316, #dc2626); }
.tr-envs { display: grid; grid-template-columns: repeat(auto-fill, minmax(230px, 1fr)); gap: 8px; }
.tr-env { display: flex; align-items: center; gap: 10px; padding: 10px 12px; border: 1px solid #e5e7eb; border-radius: 10px; color: inherit; text-decoration: none; }
.tr-env:hover { border-color: #c7d2fe; background: #f8faff; }
.tr-env-dot { flex: 0 0 auto; width: 10px; height: 10px; border-radius: 999px; background: #9ca3af; }
.is-ok .tr-env-dot { background: #16a34a; } .is-critical .tr-env-dot { background: #dc2626; } .is-running .tr-env-dot { background: #2563eb; }
.tr-env-body { display: flex; flex: 1; flex-direction: column; min-width: 0; }
.tr-env-body small { overflow: hidden; color: #6b7280; text-overflow: ellipsis; white-space: nowrap; }
.tr-env-state { color: #4b5563; font-size: 12px; font-weight: 700; white-space: nowrap; }
.is-critical .tr-env-state { color: #b91c1c; }
</style>
