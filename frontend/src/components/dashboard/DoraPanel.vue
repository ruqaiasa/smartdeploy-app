<script setup lang="ts">
import { ref, watch } from 'vue'
import { getDoraReport } from '@/api/dora.api'
import type { DoraLevel, DoraReport } from '@/types/dora'

/*
 * Les quatre indicateurs DORA de la plateforme : fréquence de déploiement,
 * délai de livraison, taux d'échec des changements, temps de rétablissement.
 */
const days = ref(30)
const excludeBench = ref(false)
const report = ref<DoraReport | null>(null)
const failed = ref(false)

const LEVELS: Record<DoraLevel, string> = { ELITE: 'Élite', HIGH: 'Haute', MEDIUM: 'Moyenne', LOW: 'Basse', NONE: 'Sans donnée' }

async function load() {
  failed.value = false
  try {
    report.value = await getDoraReport(days.value, excludeBench.value)
  } catch {
    failed.value = true
  }
}
watch([days, excludeBench], load, { immediate: true })

function format(value: number | null, unit: string) {
  if (value === null) return '—'
  const number = (n: number) => n.toLocaleString('fr-FR', { maximumFractionDigits: 1 })
  if (unit.startsWith('minutes')) return value >= 120 ? `${number(value / 60)} h` : `${number(value)} min`
  if (unit === '%') return `${number(value)} %`
  return number(value)
}
function caption(unit: string) {
  if (unit.includes('médiane')) return 'médiane'
  return unit === '%' ? '' : unit
}
</script>

<template>
  <section class="dora" aria-labelledby="dora-title">
    <header class="dora-head">
      <div>
        <h2 id="dora-title">Indicateurs DORA</h2>
        <p class="dora-sub">Performance de livraison, calculée à partir de l’historique des déploiements et des verdicts du gardien.</p>
      </div>
      <div class="dora-controls">
        <label class="dora-check">
          <input v-model="excludeBench" type="checkbox" />
          Exclure le banc d’essai
        </label>
        <select v-model.number="days" aria-label="Période">
          <option :value="7">7 jours</option>
          <option :value="30">30 jours</option>
          <option :value="90">90 jours</option>
        </select>
      </div>
    </header>

    <p v-if="failed" class="dora-muted">Indicateurs indisponibles pour le moment.</p>
    <div v-else-if="report" class="dora-grid">
      <article v-for="metric in report.metrics" :key="metric.key" class="dora-tile" :title="metric.definition">
        <span class="dora-label">{{ metric.label }}</span>
        <strong class="dora-value">{{ format(metric.value, metric.unit) }}</strong>
        <span class="dora-unit">{{ caption(metric.unit) }}</span>
        <span class="dora-level" :class="`is-${metric.level.toLowerCase()}`">{{ LEVELS[metric.level] }}</span>
        <span class="dora-detail">{{ metric.detail }}</span>
      </article>
    </div>
    <ul v-if="report" class="dora-notes">
      <li v-for="note in report.notes" :key="note">{{ note }}</li>
    </ul>
  </section>
</template>

<style scoped>
.dora { display: flex; flex-direction: column; gap: 14px; margin-bottom: 24px; padding: 18px 20px; border: 1px solid var(--color-gray-200, #e5e7eb); border-radius: 12px; background: #fff; }
.dora-head { display: flex; flex-wrap: wrap; align-items: flex-start; justify-content: space-between; gap: 12px; }
.dora-head h2 { margin: 0; font-size: 17px; }
.dora-sub { margin: 4px 0 0; color: var(--color-gray-500, #6b7280); font-size: 13px; }
.dora-controls { display: flex; align-items: center; gap: 12px; font-size: 13px; }
.dora-controls select { padding: 6px 8px; border: 1px solid var(--color-gray-200, #e5e7eb); border-radius: 8px; background: #fff; font: inherit; }
.dora-check { display: inline-flex; align-items: center; gap: 6px; cursor: pointer; }
.dora-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(190px, 1fr)); gap: 12px; }
.dora-tile { display: flex; flex-direction: column; gap: 4px; padding: 14px; border: 1px solid var(--color-gray-200, #e5e7eb); border-radius: 10px; background: var(--color-gray-50, #f9fafb); cursor: help; }
.dora-label { color: var(--color-gray-600, #4b5563); font-size: 12.5px; font-weight: 600; }
.dora-value { font-size: 24px; line-height: 1.2; }
.dora-unit { min-height: 16px; color: var(--color-gray-500, #6b7280); font-size: 12px; }
.dora-level { align-self: flex-start; padding: 2px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; }
.dora-level.is-elite { background: #dcfce7; color: #166534; }
.dora-level.is-high { background: #dbeafe; color: #1e40af; }
.dora-level.is-medium { background: #fef3c7; color: #92400e; }
.dora-level.is-low { background: #fee2e2; color: #991b1b; }
.dora-level.is-none { background: var(--color-gray-100, #f3f4f6); color: var(--color-gray-500, #6b7280); }
.dora-detail { color: var(--color-gray-500, #6b7280); font-size: 12px; }
.dora-notes { display: flex; flex-direction: column; gap: 2px; margin: 0; padding-left: 18px; color: var(--color-gray-500, #6b7280); font-size: 12px; }
.dora-muted { margin: 0; color: var(--color-gray-500, #6b7280); font-size: 13px; }
</style>
