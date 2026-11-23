<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import TimeChart from '@/components/charts/TimeChart.vue'
import { getEnvironmentSeries, type EnvironmentSeries } from '@/api/insights.api'

/* Les courbes d’un environnement, avec les déploiements et les alertes de la période. */
const props = withDefaults(defineProps<{ environmentId: number; compact?: boolean }>(), { compact: false })
const hours = ref(1)
const data = ref<EnvironmentSeries | null>(null)
const loading = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

async function load() {
  loading.value = true
  try { data.value = await getEnvironmentSeries(props.environmentId, hours.value) } catch { data.value = null } finally { loading.value = false }
}
onMounted(() => { void load(); timer = setInterval(load, 30000) })
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
watch(() => [props.environmentId, hours.value], load)

const markers = computed(() => data.value?.markers.map(m => ({ at: m.at, label: m.label, tone: m.tone, kind: m.kind })) ?? [])
const last = (p: { value: number }[] | undefined) => p && p.length ? p[p.length - 1]!.value : null
const peak = (p: { value: number }[] | undefined) => p && p.length ? Math.max(...p.map(x => x.value)) : null
const tiles = computed(() => {
  const d = data.value
  if (!d) return []
  const cpu = last(d.cpu)
  const mem = last(d.memory)
  const restarts = d.restarts.reduce((n, p) => n + p.value, 0)
  return [
    { label: 'Processeur', value: cpu === null ? '—' : `${Math.round(cpu)} m`, sub: d.cpuLimit ? `${Math.round(((cpu ?? 0) / d.cpuLimit) * 100)} % de la limite` : `pic ${Math.round(peak(d.cpu) ?? 0)} m`, tone: d.cpuLimit && (cpu ?? 0) / d.cpuLimit > 0.8 ? 'warn' : 'ok' },
    { label: 'Mémoire', value: mem === null ? '—' : `${Math.round(mem)} Mo`, sub: d.memoryLimit ? `${Math.round(((mem ?? 0) / d.memoryLimit) * 100)} % de la limite` : `pic ${Math.round(peak(d.memory) ?? 0)} Mo`, tone: d.memoryLimit && (mem ?? 0) / d.memoryLimit > 0.85 ? 'warn' : 'ok' },
    { label: 'Pods prêts', value: `${last(d.readyPods) ?? '—'} / ${last(d.desiredPods) ?? '—'}`, sub: 'disponibles / attendus', tone: (last(d.readyPods) ?? 0) < (last(d.desiredPods) ?? 0) ? 'warn' : 'ok' },
    { label: 'Redémarrages', value: `${Math.round(restarts)}`, sub: `sur ${hours.value} h`, tone: restarts > 0 ? 'warn' : 'ok' },
  ]
})
</script>

<template>
  <div class="ec">
    <div class="ec-bar">
      <div class="ec-range" role="tablist">
        <button v-for="h in [1, 6, 24]" :key="h" type="button" :class="{ 'is-active': hours === h }" @click="hours = h">{{ h }} h</button>
      </div>
      <span class="ec-legend"><i class="is-dep" />Déploiement <i class="is-alert" />Alerte <i class="is-limit" />Limite</span>
    </div>
    <p v-if="data?.error" class="ec-error">{{ data.error }}</p>
    <div v-if="data && !compact" class="ec-tiles">
      <div v-for="t in tiles" :key="t.label" :class="`is-${t.tone}`"><span>{{ t.label }}</span><strong>{{ t.value }}</strong><small>{{ t.sub }}</small></div>
    </div>
    <div v-if="data" class="ec-grid" :class="{ 'is-compact': compact }">
      <section>
        <h4>Processeur (millicœurs)</h4>
        <TimeChart :series="[{ name: 'Processeur', color: '#4f46e5', points: data.cpu, area: true }]" :from="data.from" :to="data.to" unit="m" :limit="data.cpuLimit" :markers="markers" :height="compact ? 150 : 180" />
      </section>
      <section>
        <h4>Mémoire (Mo)</h4>
        <TimeChart :series="[{ name: 'Mémoire', color: '#0891b2', points: data.memory, area: true }]" :from="data.from" :to="data.to" unit="Mo" :limit="data.memoryLimit" :markers="markers" :height="compact ? 150 : 180" />
      </section>
      <section v-if="!compact">
        <h4>Pods prêts</h4>
        <TimeChart :series="[{ name: 'Attendus', color: '#cbd5e1', points: data.desiredPods }, { name: 'Prêts', color: '#16a34a', points: data.readyPods, area: true }]" :from="data.from" :to="data.to" :markers="markers" />
      </section>
      <section v-if="!compact">
        <h4>Redémarrages</h4>
        <TimeChart :series="[{ name: 'Redémarrages', color: '#dc2626', points: data.restarts, area: true }]" :from="data.from" :to="data.to" :markers="markers" />
      </section>
    </div>
    <p v-else-if="loading" class="ec-muted">Chargement des courbes…</p>
  </div>
</template>

<style scoped>
.ec { display: flex; flex-direction: column; gap: 12px; }
.ec-bar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; }
.ec-range { display: flex; gap: 4px; padding: 3px; border-radius: 10px; background: #f3f4f6; }
.ec-range button { padding: 5px 12px; border: 0; border-radius: 8px; background: none; font: inherit; font-size: 13px; font-weight: 700; color: #4b5563; cursor: pointer; }
.ec-range .is-active { background: #fff; color: #1e3a8a; box-shadow: 0 1px 3px rgba(15, 23, 42, .12); }
.ec-legend { display: flex; align-items: center; gap: 6px; color: #6b7280; font-size: 12px; }
.ec-legend i { display: inline-block; width: 14px; height: 0; margin-left: 8px; border-top: 2px dashed; }
.ec-legend .is-dep { border-color: #4f46e5; } .ec-legend .is-alert { border-color: #f59e0b; } .ec-legend .is-limit { border-color: #dc2626; }
.ec-error { margin: 0; padding: 8px 12px; border-radius: 8px; background: #fef2f2; color: #991b1b; font-size: 13px; }
.ec-tiles { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.ec-tiles div { display: flex; flex-direction: column; gap: 1px; padding: 12px 14px; border: 1px solid #e5e7eb; border-left: 4px solid #16a34a; border-radius: 12px; background: #fff; }
.ec-tiles div.is-warn { border-left-color: #f59e0b; }
.ec-tiles span { color: #6b7280; font-size: 12px; font-weight: 600; }
.ec-tiles strong { font-size: 20px; }
.ec-tiles small { color: #9ca3af; font-size: 12px; }
.ec-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.ec-grid section { padding: 12px 14px; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; }
.ec-grid h4 { margin: 0 0 6px; color: #374151; font-size: 13px; }
@media (max-width: 900px) { .ec-grid, .ec-tiles { grid-template-columns: 1fr; } }
.ec-muted { color: #6b7280; font-size: 13px; }
</style>
