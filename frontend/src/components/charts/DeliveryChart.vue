<script setup lang="ts">
import { computed, ref } from 'vue'

/*
 * Activité de livraison : barres empilées (réussis, échoués) par jour, et la courbe du taux de réussite
 * (axe de droite). Le volume et la qualité dans une seule image.
 */
const props = defineProps<{ days: { date: string; success: number; failed: number; other: number }[] }>()
const W = 720
const H = 230
const P = { left: 34, right: 40, top: 14, bottom: 26 }
const pw = W - P.left - P.right
const ph = H - P.top - P.bottom
const max = computed(() => Math.max(2, ...props.days.map(d => d.success + d.failed)))
const bw = computed(() => pw / Math.max(1, props.days.length))
const yBar = (v: number) => (v / max.value) * ph
const rate = (d: { success: number; failed: number }) => (d.success + d.failed ? (d.success / (d.success + d.failed)) * 100 : null)
const ratePts = computed(() => props.days.map((d, i) => {
  const r = rate(d)
  return r === null ? null : `${(P.left + i * bw.value + bw.value / 2).toFixed(1)},${(P.top + ph - (r / 100) * ph).toFixed(1)}`
}).filter(Boolean).join(' '))
const hover = ref<number | null>(null)
const every = computed(() => Math.ceil(props.days.length / 10))
const label = (iso: string) => new Date(iso).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })
const total = computed(() => props.days.reduce((n, d) => n + d.success + d.failed, 0))
</script>

<template>
  <div class="dc">
    <svg :viewBox="`0 0 ${W} ${H}`" class="dc-svg">
      <defs>
        <linearGradient id="dcOk" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#22c55e" /><stop offset="1" stop-color="#16a34a" /></linearGradient>
        <linearGradient id="dcKo" x1="0" x2="0" y1="0" y2="1"><stop offset="0" stop-color="#f87171" /><stop offset="1" stop-color="#dc2626" /></linearGradient>
      </defs>
      <g v-for="f in [0, 0.25, 0.5, 0.75, 1]" :key="f">
        <line :x1="P.left" :x2="W - P.right" :y1="P.top + ph - f * ph" :y2="P.top + ph - f * ph" class="dc-grid" />
        <text :x="P.left - 6" :y="P.top + ph - f * ph + 3.5" text-anchor="end" class="dc-txt">{{ Math.round(f * max) }}</text>
        <text :x="W - P.right + 6" :y="P.top + ph - f * ph + 3.5" class="dc-txt dc-rate">{{ f * 100 }} %</text>
      </g>
      <g v-for="(d, i) in days" :key="d.date" @mouseenter="hover = i" @mouseleave="hover = null">
        <rect :x="P.left + i * bw" :y="P.top" :width="bw" :height="ph" :fill="hover === i ? '#f1f5f9' : 'transparent'" />
        <rect :x="P.left + i * bw + bw * .18" :y="P.top + ph - yBar(d.success)" :width="bw * .64" :height="yBar(d.success)" rx="3" fill="url(#dcOk)" />
        <rect :x="P.left + i * bw + bw * .18" :y="P.top + ph - yBar(d.success + d.failed)" :width="bw * .64" :height="yBar(d.failed)" rx="3" fill="url(#dcKo)" />
        <text v-if="i % every === 0" :x="P.left + i * bw + bw / 2" :y="H - 8" text-anchor="middle" class="dc-txt">{{ label(d.date) }}</text>
      </g>
      <polyline v-if="ratePts" :points="ratePts" fill="none" stroke="#4f46e5" stroke-width="2.5" stroke-linejoin="round" stroke-linecap="round" />
      <template v-for="(d, i) in days" :key="`p${d.date}`">
        <circle v-if="rate(d) !== null" :cx="P.left + i * bw + bw / 2" :cy="P.top + ph - ((rate(d) ?? 0) / 100) * ph" r="3.5" fill="#fff" stroke="#4f46e5" stroke-width="2" />
      </template>
      <text v-if="!total" :x="W / 2" :y="H / 2" text-anchor="middle" class="dc-empty">Aucun déploiement sur la période</text>
    </svg>
    <div v-if="hover !== null && days[hover]" class="dc-tip">
      <strong>{{ label(days[hover]!.date) }}</strong>
      <span><i style="background:#16a34a" />{{ days[hover]!.success }} réussi(s)</span>
      <span><i style="background:#dc2626" />{{ days[hover]!.failed }} échoué(s)</span>
      <span v-if="rate(days[hover]!) !== null"><i style="background:#4f46e5" />{{ Math.round(rate(days[hover]!) ?? 0) }} % de réussite</span>
    </div>
    <div class="dc-legend"><span><i style="background:#16a34a" />Réussis</span><span><i style="background:#dc2626" />Échoués</span><span><i class="dc-line" />Taux de réussite</span></div>
  </div>
</template>

<style scoped>
.dc { position: relative; }
.dc-svg { display: block; width: 100%; height: auto; }
.dc-grid { stroke: #eef2f7; }
.dc-txt { fill: #94a3b8; font-size: 10.5px; }
.dc-rate { fill: #818cf8; }
.dc-empty { fill: #94a3b8; font-size: 13px; }
.dc-tip { position: absolute; top: 8px; left: 50%; display: flex; gap: 12px; padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 10px; background: rgba(255, 255, 255, .97); box-shadow: 0 10px 24px rgba(15, 23, 42, .12); font-size: 12.5px; transform: translateX(-50%); pointer-events: none; white-space: nowrap; }
.dc-tip span, .dc-legend span { display: inline-flex; align-items: center; gap: 6px; }
.dc-tip i, .dc-legend i { display: inline-block; width: 10px; height: 10px; border-radius: 3px; }
.dc-legend { display: flex; gap: 16px; margin-top: 6px; color: #64748b; font-size: 12.5px; }
.dc-legend .dc-line { width: 16px; height: 0; border-top: 3px solid #4f46e5; border-radius: 2px; }
</style>
