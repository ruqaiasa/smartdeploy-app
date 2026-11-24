<script setup lang="ts">
import { computed } from 'vue'

/* Mini-courbe d’un indicateur (sans axes) : la tendance en un coup d’œil. */
const props = withDefaults(defineProps<{ values: (number | null)[]; color?: string; height?: number }>(), { color: '#2563eb', height: 36 })
const W = 120
const pts = computed(() => {
  const v = props.values.map(x => (x === null ? NaN : x))
  const finite = v.filter(x => Number.isFinite(x))
  if (finite.length < 2) return ''
  const min = Math.min(...finite)
  const max = Math.max(...finite)
  const span = max - min || 1
  return v.map((x, i) => Number.isFinite(x) ? `${((i / (v.length - 1)) * W).toFixed(1)},${(props.height - 3 - ((x - min) / span) * (props.height - 6)).toFixed(1)}` : null)
    .filter(Boolean).join(' ')
})
</script>

<template>
  <svg :viewBox="`0 0 ${W} ${height}`" class="spark" preserveAspectRatio="none" aria-hidden="true">
    <polyline v-if="pts" :points="`0,${height} ${pts} ${W},${height}`" :fill="color" fill-opacity=".1" stroke="none" />
    <polyline v-if="pts" :points="pts" fill="none" :stroke="color" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke" />
    <line v-else x1="0" :y1="height - 4" :x2="W" :y2="height - 4" stroke="#e5e7eb" stroke-width="2" stroke-dasharray="4 4" />
  </svg>
</template>

<style scoped>
.spark { display: block; width: 100%; height: 36px; }
</style>
