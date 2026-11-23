<script setup lang="ts">
import { computed, ref } from 'vue'

/*
 * Courbe temporelle en SVG, sans bibliothèque : une ou plusieurs séries, une ligne de limite facultative,
 * et des marqueurs verticaux (déploiements, alertes). Au survol, les valeurs à l’instant pointé.
 */
export interface ChartSeries { name: string; color: string; points: { timestamp: number; value: number }[]; area?: boolean }
export interface ChartMarker { at: number; label: string; tone: string; kind: string }

const props = withDefaults(defineProps<{
  series: ChartSeries[]
  from: number
  to: number
  unit?: string
  limit?: number | null
  limitLabel?: string
  markers?: ChartMarker[]
  height?: number
  decimals?: number
}>(), { unit: '', limit: null, limitLabel: 'Limite', markers: () => [], height: 180, decimals: 0 })

const W = 640
const PAD = { left: 46, right: 12, top: 12, bottom: 24 }
const H = computed(() => props.height)
const plotW = W - PAD.left - PAD.right
const plotH = computed(() => H.value - PAD.top - PAD.bottom)

const maxY = computed(() => {
  let m = props.limit ?? 0
  for (const s of props.series) for (const p of s.points) m = Math.max(m, p.value)
  if (m <= 0) return 1
  const mag = Math.pow(10, Math.floor(Math.log10(m)))
  return Math.ceil((m * 1.1) / mag) * mag
})
const x = (t: number) => PAD.left + ((t - props.from) / Math.max(1, props.to - props.from)) * plotW
const y = (v: number) => PAD.top + plotH.value - (v / maxY.value) * plotH.value

function path(s: ChartSeries) {
  return s.points.map((p, i) => `${i ? 'L' : 'M'}${x(p.timestamp).toFixed(1)},${y(p.value).toFixed(1)}`).join(' ')
}
function area(s: ChartSeries) {
  if (!s.points.length) return ''
  const first = s.points[0]!
  const last = s.points[s.points.length - 1]!
  return `${path(s)} L${x(last.timestamp).toFixed(1)},${y(0)} L${x(first.timestamp).toFixed(1)},${y(0)} Z`
}
const yTicks = computed(() => [0, 0.25, 0.5, 0.75, 1].map(f => f * maxY.value))
const xTicks = computed(() => {
  const n = 5
  return Array.from({ length: n + 1 }, (_, i) => props.from + ((props.to - props.from) * i) / n)
})
function clock(t: number) {
  const d = new Date(t * 1000)
  return props.to - props.from > 86400 ? d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' }) + ' ' + d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })
    : d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })
}
function fmt(v: number) {
  return v.toLocaleString('fr-FR', { maximumFractionDigits: props.decimals }) + (props.unit ? ` ${props.unit}` : '')
}
const empty = computed(() => props.series.every(s => s.points.length === 0))

/* Survol */
const hoverT = ref<number | null>(null)
const svg = ref<SVGSVGElement | null>(null)
function move(event: MouseEvent) {
  const box = svg.value?.getBoundingClientRect()
  if (!box) return
  const px = ((event.clientX - box.left) / box.width) * W
  if (px < PAD.left || px > W - PAD.right) { hoverT.value = null; return }
  hoverT.value = props.from + ((px - PAD.left) / plotW) * (props.to - props.from)
}
const hoverValues = computed(() => {
  const t = hoverT.value
  if (t === null) return []
  return props.series.map(s => {
    let best: { timestamp: number; value: number } | null = null
    for (const p of s.points) if (!best || Math.abs(p.timestamp - t) < Math.abs(best.timestamp - t)) best = p
    return { name: s.name, color: s.color, value: best?.value ?? null }
  })
})
const nearMarker = computed(() => {
  const t = hoverT.value
  if (t === null) return null
  const tolerance = (props.to - props.from) / 60
  return props.markers.find(m => Math.abs(m.at - t) < tolerance) ?? null
})
</script>

<template>
  <div class="tc">
    <svg ref="svg" :viewBox="`0 0 ${W} ${H}`" class="tc-svg" role="img" @mousemove="move" @mouseleave="hoverT = null">
      <g class="tc-grid">
        <g v-for="v in yTicks" :key="v">
          <line :x1="PAD.left" :x2="W - PAD.right" :y1="y(v)" :y2="y(v)" />
          <text :x="PAD.left - 6" :y="y(v) + 3.5" text-anchor="end">{{ v.toLocaleString('fr-FR', { maximumFractionDigits: 1 }) }}</text>
        </g>
        <text v-for="t in xTicks" :key="t" :x="x(t)" :y="H - 6" text-anchor="middle">{{ clock(t) }}</text>
      </g>
      <g v-for="m in markers" :key="`${m.kind}-${m.at}-${m.label}`" class="tc-marker" :class="`is-${m.tone} is-${m.kind.toLowerCase()}`">
        <line :x1="x(m.at)" :x2="x(m.at)" :y1="PAD.top" :y2="PAD.top + plotH" />
        <circle :cx="x(m.at)" :cy="PAD.top + 4" r="4" />
      </g>
      <g v-for="s in series" :key="s.name">
        <path v-if="s.area" :d="area(s)" :fill="s.color" fill-opacity=".12" />
        <path :d="path(s)" fill="none" :stroke="s.color" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" />
      </g>
      <g v-if="limit !== null && limit > 0" class="tc-limit">
        <line :x1="PAD.left" :x2="W - PAD.right" :y1="y(limit)" :y2="y(limit)" />
        <text :x="W - PAD.right - 4" :y="y(limit) - 4" text-anchor="end">{{ limitLabel }} {{ fmt(limit) }}</text>
      </g>
      <line v-if="hoverT !== null" class="tc-cross" :x1="x(hoverT)" :x2="x(hoverT)" :y1="PAD.top" :y2="PAD.top + plotH" />
      <text v-if="empty" :x="W / 2" :y="H / 2" text-anchor="middle" class="tc-empty">Aucune mesure sur la période</text>
    </svg>
    <div v-if="hoverT !== null && !empty" class="tc-tip">
      <strong>{{ clock(hoverT) }}</strong>
      <span v-for="h in hoverValues" :key="h.name"><i :style="{ background: h.color }" />{{ h.name }} : {{ h.value === null ? '—' : fmt(h.value) }}</span>
      <span v-if="nearMarker" class="tc-tip-marker">{{ nearMarker.kind === 'DEPLOYMENT' ? 'Déploiement' : 'Alerte' }} : {{ nearMarker.label }}</span>
    </div>
  </div>
</template>

<style scoped>
.tc { position: relative; }
.tc-svg { display: block; width: 100%; height: auto; }
.tc-grid line { stroke: #eef0f3; }
.tc-grid text { fill: #9ca3af; font-size: 10px; }
.tc-limit line { stroke: #dc2626; stroke-dasharray: 5 4; stroke-width: 1.2; }
.tc-limit text { fill: #dc2626; font-size: 10px; font-weight: 700; }
.tc-marker line { stroke-width: 1.3; stroke-dasharray: 3 3; }
.tc-marker.is-deployment line, .tc-marker.is-deployment circle { stroke: #4f46e5; fill: #4f46e5; }
.tc-marker.is-deployment.is-critical line, .tc-marker.is-deployment.is-critical circle { stroke: #dc2626; fill: #dc2626; }
.tc-marker.is-deployment.is-ok circle { fill: #16a34a; stroke: #16a34a; }
.tc-marker.is-alert line { stroke: #f59e0b; }
.tc-marker.is-alert circle { fill: #f59e0b; stroke: #f59e0b; }
.tc-cross { stroke: #94a3b8; stroke-width: 1; }
.tc-empty { fill: #9ca3af; font-size: 13px; }
.tc-tip { position: absolute; top: 6px; right: 14px; display: flex; flex-direction: column; gap: 2px; padding: 8px 10px; border: 1px solid #e5e7eb; border-radius: 10px; background: rgba(255, 255, 255, .96); box-shadow: 0 8px 20px rgba(15, 23, 42, .1); font-size: 12px; pointer-events: none; }
.tc-tip span { display: flex; align-items: center; gap: 6px; }
.tc-tip i { display: inline-block; width: 9px; height: 9px; border-radius: 3px; }
.tc-tip-marker { color: #4f46e5; font-weight: 700; }
</style>
