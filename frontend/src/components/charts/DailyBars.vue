<script setup lang="ts">
import { computed, ref } from 'vue'

/* Histogramme empilé jour par jour (réussis, échoués, autres), en SVG, sans bibliothèque. */
const props = defineProps<{ days: { date: string; success: number; failed: number; other: number }[] }>()

const W = 640
const H = 170
const PAD = { left: 30, right: 8, top: 10, bottom: 22 }
const plotW = W - PAD.left - PAD.right
const plotH = H - PAD.top - PAD.bottom
const max = computed(() => Math.max(1, ...props.days.map(d => d.success + d.failed + d.other)))
const bw = computed(() => plotW / Math.max(1, props.days.length))
const y = (v: number) => (v / max.value) * plotH
const hover = ref<number | null>(null)
const label = (iso: string) => new Date(iso).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })
const every = computed(() => Math.ceil(props.days.length / 10))
</script>

<template>
  <div class="dbars">
    <svg :viewBox="`0 0 ${W} ${H}`" class="dbars-svg">
      <line v-for="f in [0, 0.5, 1]" :key="f" :x1="PAD.left" :x2="W - PAD.right" :y1="PAD.top + plotH - f * plotH" :y2="PAD.top + plotH - f * plotH" class="dbars-grid" />
      <text v-for="f in [0, 0.5, 1]" :key="`t${f}`" :x="PAD.left - 6" :y="PAD.top + plotH - f * plotH + 3.5" text-anchor="end" class="dbars-txt">{{ Math.round(f * max) }}</text>
      <g v-for="(d, i) in days" :key="d.date" @mouseenter="hover = i" @mouseleave="hover = null">
        <rect :x="PAD.left + i * bw + 2" :y="PAD.top" :width="Math.max(2, bw - 4)" :height="plotH" fill="transparent" />
        <rect :x="PAD.left + i * bw + 2" :y="PAD.top + plotH - y(d.success)" :width="Math.max(2, bw - 4)" :height="y(d.success)" rx="2" fill="#16a34a" />
        <rect :x="PAD.left + i * bw + 2" :y="PAD.top + plotH - y(d.success + d.failed)" :width="Math.max(2, bw - 4)" :height="y(d.failed)" rx="2" fill="#dc2626" />
        <rect :x="PAD.left + i * bw + 2" :y="PAD.top + plotH - y(d.success + d.failed + d.other)" :width="Math.max(2, bw - 4)" :height="y(d.other)" rx="2" fill="#a5b4fc" />
        <text v-if="i % every === 0" :x="PAD.left + i * bw + bw / 2" :y="H - 6" text-anchor="middle" class="dbars-txt">{{ label(d.date) }}</text>
      </g>
    </svg>
    <div v-if="hover !== null && days[hover]" class="dbars-tip">
      <strong>{{ label(days[hover]!.date) }}</strong>
      <span><i style="background:#16a34a" />Réussis : {{ days[hover]!.success }}</span>
      <span><i style="background:#dc2626" />Échoués : {{ days[hover]!.failed }}</span>
      <span><i style="background:#a5b4fc" />Autres : {{ days[hover]!.other }}</span>
    </div>
    <div class="dbars-legend"><span><i style="background:#16a34a" />Réussis</span><span><i style="background:#dc2626" />Échoués</span><span><i style="background:#a5b4fc" />En attente, annulés, refusés</span></div>
  </div>
</template>

<style scoped>
.dbars { position: relative; }
.dbars-svg { display: block; width: 100%; height: auto; }
.dbars-grid { stroke: #eef0f3; }
.dbars-txt { fill: #9ca3af; font-size: 10px; }
.dbars-tip { position: absolute; top: 4px; right: 10px; display: flex; flex-direction: column; gap: 2px; padding: 8px 10px; border: 1px solid #e5e7eb; border-radius: 10px; background: #fff; box-shadow: 0 8px 20px rgba(15, 23, 42, .1); font-size: 12px; pointer-events: none; }
.dbars-tip span, .dbars-legend span { display: inline-flex; align-items: center; gap: 6px; }
.dbars-tip i, .dbars-legend i { display: inline-block; width: 9px; height: 9px; border-radius: 3px; }
.dbars-legend { display: flex; flex-wrap: wrap; gap: 14px; margin-top: 4px; color: #6b7280; font-size: 12px; }
</style>
