<script setup lang="ts">
import { computed } from 'vue'

/* Jauge circulaire : une part en pourcentage, colorée selon des seuils. */
const props = withDefaults(defineProps<{ value: number | null; label: string; sub?: string; warn?: number; critical?: number }>(), { sub: '', warn: 75, critical: 90 })
const R = 42
const C = 2 * Math.PI * R
const pct = computed(() => Math.max(0, Math.min(100, props.value ?? 0)))
const color = computed(() => props.value === null ? '#cbd5e1' : pct.value >= props.critical ? '#dc2626' : pct.value >= props.warn ? '#f59e0b' : '#16a34a')
</script>

<template>
  <div class="dg">
    <svg viewBox="0 0 110 110" class="dg-svg" role="img" :aria-label="`${label} : ${value ?? '—'} %`">
      <circle cx="55" cy="55" :r="R" fill="none" stroke="#eef2f7" stroke-width="11" />
      <circle cx="55" cy="55" :r="R" fill="none" :stroke="color" stroke-width="11" stroke-linecap="round"
        :stroke-dasharray="`${(pct / 100) * C} ${C}`" transform="rotate(-90 55 55)" class="dg-arc" />
      <text x="55" y="53" text-anchor="middle" class="dg-value">{{ value === null ? '—' : Math.round(value) }}<tspan v-if="value !== null" class="dg-unit"> %</tspan></text>
      <text x="55" y="70" text-anchor="middle" class="dg-label">{{ label }}</text>
    </svg>
    <small v-if="sub" class="dg-sub">{{ sub }}</small>
  </div>
</template>

<style scoped>
.dg { display: flex; flex-direction: column; align-items: center; gap: 2px; }
.dg-svg { width: 132px; height: 132px; }
.dg-arc { transition: stroke-dasharray .6s ease; }
.dg-value { fill: #0f172a; font-size: 22px; font-weight: 800; }
.dg-unit { font-size: 12px; font-weight: 700; fill: #64748b; }
.dg-label { fill: #64748b; font-size: 9.5px; font-weight: 700; letter-spacing: .04em; text-transform: uppercase; }
.dg-sub { color: #64748b; font-size: 12px; text-align: center; }
</style>
