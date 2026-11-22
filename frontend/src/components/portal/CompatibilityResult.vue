<script setup lang="ts">
import { computed } from 'vue'
import type { CompatibilityReport } from '@/types/portal'

const props = defineProps<{ report: CompatibilityReport; compact?: boolean }>()

const STATUS_LABEL = { OK: 'Conforme', INFO: 'Information', WARNING: 'À améliorer', ERROR: 'Bloquant' } as const
const tone = computed(() => props.report.verdict === 'READY' ? 'ok' : props.report.verdict === 'ALMOST_READY' ? 'warning' : 'critical')
/* Les points bloquants et à améliorer d’abord ; en mode compact, les points conformes sont résumés. */
const ordered = computed(() => {
  const order = { ERROR: 0, WARNING: 1, INFO: 2, OK: 3 }
  return [...props.report.checks].sort((a, b) => order[a.status] - order[b.status])
})
const shown = computed(() => props.compact ? ordered.value.filter(c => c.status !== 'OK') : ordered.value)
const okCount = computed(() => props.report.checks.filter(c => c.status === 'OK').length)
</script>

<template>
  <div class="cr">
    <div class="cr-verdict" :class="`is-${tone}`">
      <strong>{{ report.verdictLabel }}</strong>
      <span v-if="report.architectureLabel">Architecture conseillée : <b>{{ report.architectureLabel }}</b></span>
    </div>
    <ul class="cr-checks">
      <li v-for="c in shown" :key="c.id" :class="`is-${c.status.toLowerCase()}`">
        <span class="cr-badge">{{ STATUS_LABEL[c.status] }}</span>
        <div>
          <strong>{{ c.label }}</strong>
          <p>{{ c.detail }}</p>
          <p v-if="c.fix" class="cr-fix">Comment corriger : {{ c.fix }}</p>
        </div>
      </li>
    </ul>
    <p v-if="compact && okCount" class="cr-ok">{{ okCount }} point{{ okCount > 1 ? 's' : '' }} conforme{{ okCount > 1 ? 's' : '' }}.</p>
  </div>
</template>

<style scoped>
.cr { display: flex; flex-direction: column; gap: 8px; }
.cr-verdict { display: flex; flex-direction: column; gap: 3px; padding: 11px 14px; border: 1px solid; border-left-width: 5px; border-radius: 10px; font-size: 13.5px; }
.cr-verdict strong { font-size: 15px; }
.cr-verdict.is-ok { border-color: #bbf7d0; border-left-color: #16a34a; background: #f0fdf4; color: #14532d; }
.cr-verdict.is-warning { border-color: #fde68a; border-left-color: #d97706; background: #fffbeb; color: #78350f; }
.cr-verdict.is-critical { border-color: #fecaca; border-left-color: #dc2626; background: #fef2f2; color: #7f1d1d; }
.cr-checks { display: flex; flex-direction: column; gap: 6px; margin: 0; padding: 0; list-style: none; }
.cr-checks li { display: flex; gap: 10px; padding: 8px 10px; border-radius: 8px; background: #f9fafb; }
.cr-checks p { margin: 2px 0 0; color: #374151; font-size: 13px; }
.cr-fix { color: #1e3a8a !important; font-weight: 500; }
.cr-badge { flex: 0 0 auto; align-self: flex-start; min-width: 88px; padding: 2px 8px; border-radius: 999px; font-size: 11px; font-weight: 700; text-align: center; }
.is-ok .cr-badge { background: #dcfce7; color: #166534; }
.is-info .cr-badge { background: #e0e7ff; color: #3730a3; }
.is-warning .cr-badge { background: #ffedd5; color: #9a3412; }
.is-error .cr-badge { background: #fee2e2; color: #991b1b; }
.cr-ok { margin: 0; color: #166534; font-size: 13px; }
</style>
