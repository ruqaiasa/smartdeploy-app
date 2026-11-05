<script setup lang="ts">
import { computed } from 'vue'
import type { DeploymentGuard, GuardLevel } from '@/types/guard'
import type { DeploymentResponse } from '@/types/deployment'

/*
 * Garde de déploiement : verdict du gardien, chronologie et preuves.
 * En cas de dégradation, propose le retour arrière vers le déploiement
 * précédent ; la décision reste humaine.
 */
const props = defineProps<{
  guard: DeploymentGuard
  deployment: DeploymentResponse
  canRollback: boolean
  busy?: boolean
}>()
const emit = defineEmits<{ close: []; rollback: [targetId: number] }>()

const STATUS_TITLES: Record<DeploymentGuard['status'], string> = {
  WATCHING: 'Surveillance en cours',
  HEALTHY: 'Déploiement validé',
  HEALTHY_WITH_WARNINGS: 'Déploiement validé avec réserves',
  DEGRADED: 'Dégradation détectée',
  INCONCLUSIVE: 'Surveillance non concluante',
  SUPERSEDED: 'Surveillance interrompue',
}
const LEVEL_LABELS: Record<GuardLevel, string> = { OK: 'Conforme', UNKNOWN: 'Non évalué', WARNING: 'Avertissement', CRITICAL: 'Critique' }
const ORDER: Record<GuardLevel, number> = { CRITICAL: 0, WARNING: 1, OK: 2, UNKNOWN: 3 }

const tone = computed(() => ({
  WATCHING: 'watching', HEALTHY: 'healthy', HEALTHY_WITH_WARNINGS: 'warning',
  DEGRADED: 'degraded', INCONCLUSIVE: 'neutral', SUPERSEDED: 'neutral',
}[props.guard.status]))

const checks = computed(() => [...props.guard.checks].sort((a, b) => ORDER[a.level] - ORDER[b.level]))

function time(value: string | null) {
  return value ? new Date(value).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) : '—'
}

const steps = computed(() => {
  const g = props.guard
  const list = [
    { label: 'Mise en service', detail: `Surveillance jusqu’à ${time(g.endsAt)}`, at: g.startedAt, done: true },
    { label: `${g.evaluations} évaluation${g.evaluations > 1 ? 's' : ''}`, detail: 'Une mesure toutes les 30 secondes', at: g.lastEvaluatedAt, done: g.evaluations > 0 },
    { label: 'Verdict', detail: STATUS_TITLES[g.status], at: g.decidedAt, done: g.status !== 'WATCHING' },
  ]
  if (g.status === 'DEGRADED' && g.rollbackTargetId) {
    list.push(g.autoRollbackDeploymentId
      ? { label: 'Retour automatique', detail: `Déploiement #${g.autoRollbackDeploymentId} vers #${g.rollbackTargetId}`, at: g.decidedAt, done: true }
      : { label: 'Retour arrière proposé', detail: `Vers le déploiement #${g.rollbackTargetId}`, at: g.decidedAt, done: true })
  }
  return list
})

function confirmRollback() {
  const target = props.guard.rollbackTargetId
  if (!target) return
  if (!window.confirm(`Revenir au déploiement #${target} sur ${props.deployment.environmentName} ?\n\nLes images de ce déploiement sont redéployées par leur empreinte, sans reconstruction.`)) return
  emit('rollback', target)
}
</script>

<template>
  <Teleport to="body">
    <div class="gp-overlay" @click.self="emit('close')">
      <section class="gp" role="dialog" aria-modal="true" aria-labelledby="gp-title">
        <header class="gp-header">
          <div>
            <p class="gp-eyebrow">Garde de déploiement</p>
            <h2 id="gp-title">{{ deployment.applicationName }} {{ deployment.version }} · {{ deployment.environmentName }}</h2>
            <p class="gp-sub">Déploiement #{{ deployment.id }} · surveillé pendant 10 minutes après sa mise en service</p>
          </div>
          <button type="button" class="gp-close" aria-label="Fermer" @click="emit('close')">
            <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M5 5l10 10M15 5 5 15" stroke="currentColor" stroke-width="2" stroke-linecap="round" /></svg>
          </button>
        </header>

        <div class="gp-verdict" :class="`is-${tone}`">
          <strong>{{ STATUS_TITLES[guard.status] }}</strong>
          <span>{{ guard.verdict }}</span>
          <div v-if="guard.autoRollbackDeploymentId" class="gp-verdict-actions">
            <span class="gp-auto">
              Retour arrière automatique lancé : déploiement #{{ guard.autoRollbackDeploymentId }}, vers le déploiement sain #{{ guard.rollbackTargetId }}.
            </span>
          </div>
          <div v-else-if="guard.status === 'DEGRADED' && guard.rollbackTargetId && canRollback" class="gp-verdict-actions">
            <button type="button" class="gp-btn gp-btn-danger" :disabled="busy" @click="confirmRollback">
              Revenir au déploiement #{{ guard.rollbackTargetId }}
            </button>
            <span class="gp-hint">La décision vous appartient : le gardien propose, il n’agit pas seul.</span>
          </div>
        </div>

        <ol class="gp-timeline" aria-label="Chronologie">
          <li v-for="step in steps" :key="step.label" :class="{ 'is-done': step.done }">
            <span class="gp-dot" aria-hidden="true" />
            <div>
              <strong>{{ step.label }}</strong>
              <span>{{ step.detail }}</span>
            </div>
            <time v-if="step.done">{{ time(step.at) }}</time>
          </li>
        </ol>

        <h3 class="gp-h3">Preuves de la dernière évaluation</h3>
        <p v-if="!checks.length" class="gp-muted">Première évaluation dans moins de 30 secondes.</p>
        <div v-else class="gp-table-wrap">
          <table class="gp-table">
            <thead>
              <tr><th scope="col">Contrôle</th><th scope="col">Mesure</th><th scope="col">Référence</th><th scope="col">Résultat</th></tr>
            </thead>
            <tbody>
              <tr v-for="check in checks" :key="check.key" :class="`is-${check.level.toLowerCase()}`">
                <td data-label="Contrôle">
                  <strong>{{ check.label }}</strong>
                  <small>{{ check.explanation }}</small>
                </td>
                <td data-label="Mesure" class="gp-mono">{{ check.observed ?? '—' }}</td>
                <td data-label="Référence" class="gp-mono">{{ check.reference ?? '—' }}</td>
                <td data-label="Résultat"><span class="gp-level">{{ LEVEL_LABELS[check.level] }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.gp-overlay {
  --ink: var(--color-gray-900, #111827); --muted: var(--color-gray-500, #6b7280); --line: var(--color-gray-200, #e5e7eb);
  position: fixed; inset: 0; z-index: 1100; display: grid; place-items: center; padding: 20px;
  background: rgba(15, 23, 42, .45);
}
.gp {
  display: flex; flex-direction: column; gap: 18px; width: min(860px, 100%); max-height: calc(100vh - 40px);
  overflow-y: auto; padding: 24px; border-radius: 14px; background: #fff; color: var(--ink);
  box-shadow: 0 24px 64px rgba(15, 23, 42, .25);
}
.gp-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.gp-eyebrow { margin: 0 0 4px; color: var(--color-primary-hover, #2563eb); font-size: 12px; font-weight: 600; letter-spacing: .04em; text-transform: uppercase; }
.gp-header h2 { margin: 0; font-size: 19px; }
.gp-sub { margin: 4px 0 0; color: var(--muted); font-size: 13px; }
.gp-close { display: grid; place-items: center; width: 32px; height: 32px; border: 0; border-radius: 8px; background: none; color: var(--muted); cursor: pointer; }
.gp-close:hover { background: var(--color-gray-100, #f3f4f6); color: var(--ink); }
.gp-verdict { display: flex; flex-direction: column; gap: 4px; padding: 14px 16px; border: 1px solid; border-left-width: 4px; border-radius: 10px; font-size: 14px; }
.gp-verdict strong { font-size: 15px; }
.gp-verdict.is-watching { border-color: #bfdbfe; background: #eff6ff; color: #1e3a8a; }
.gp-verdict.is-healthy { border-color: #a7f3d0; background: #ecfdf5; color: #065f46; }
.gp-verdict.is-warning { border-color: #fde68a; background: #fffbeb; color: #92400e; }
.gp-verdict.is-degraded { border-color: #fecaca; background: #fef2f2; color: #991b1b; }
.gp-verdict.is-neutral { border-color: var(--line); background: var(--color-gray-50, #f9fafb); color: var(--muted); }
.gp-verdict-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; margin-top: 8px; }
.gp-hint { font-size: 12.5px; opacity: .85; }
.gp-auto { padding: 6px 10px; border-radius: 8px; background: #fff; color: #1e3a8a; font-weight: 600; font-size: 13px; }
.gp-btn { padding: 8px 14px; border: 1px solid transparent; border-radius: 8px; font: inherit; font-size: 13.5px; font-weight: 600; cursor: pointer; }
.gp-btn-danger { background: #dc2626; color: #fff; }
.gp-btn-danger:hover:not(:disabled) { background: #b91c1c; }
.gp-btn:disabled { opacity: .6; cursor: not-allowed; }
.gp-btn:focus-visible { outline: 2px solid var(--color-primary-hover, #2563eb); outline-offset: 2px; }
.gp-timeline { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 10px; margin: 0; padding: 0; list-style: none; }
.gp-timeline li { display: flex; align-items: flex-start; gap: 8px; padding: 10px 12px; border: 1px solid var(--line); border-radius: 10px; color: var(--muted); font-size: 12.5px; }
.gp-timeline li > div { display: flex; flex: 1; flex-direction: column; gap: 2px; }
.gp-timeline li strong { color: var(--ink); font-size: 13px; }
.gp-timeline time { font-variant-numeric: tabular-nums; }
.gp-dot { flex-shrink: 0; width: 9px; height: 9px; margin-top: 4px; border: 2px solid var(--color-gray-300, #d1d5db); border-radius: 50%; }
.gp-timeline li.is-done .gp-dot { border-color: var(--color-primary-hover, #2563eb); background: var(--color-primary-hover, #2563eb); }
.gp-h3 { margin: 0; font-size: 15px; }
.gp-muted { margin: 0; color: var(--muted); font-size: 13px; }
.gp-table-wrap { overflow-x: auto; border: 1px solid var(--line); border-radius: 10px; }
.gp-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.gp-table th { padding: 9px 12px; background: var(--color-gray-50, #f9fafb); color: var(--muted); font-size: 12px; font-weight: 600; text-align: left; }
.gp-table td { padding: 10px 12px; border-top: 1px solid var(--line); vertical-align: top; }
.gp-table td small { display: block; margin-top: 2px; color: var(--muted); font-size: 12px; }
.gp-mono { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; white-space: nowrap; }
.gp-level { display: inline-block; padding: 2px 8px; border-radius: 999px; font-size: 12px; font-weight: 600; white-space: nowrap; }
tr.is-ok .gp-level { background: #ecfdf5; color: #047857; }
tr.is-unknown .gp-level { background: var(--color-gray-100, #f3f4f6); color: var(--muted); }
tr.is-warning .gp-level { background: #fffbeb; color: #b45309; }
tr.is-critical { background: #fef2f2; }
tr.is-critical .gp-level { background: #fee2e2; color: #b91c1c; }
@media (max-width: 640px) {
  .gp { padding: 18px; }
  .gp-table thead { display: none; }
  .gp-table tr { display: block; padding: 8px 4px; border-top: 1px solid var(--line); }
  .gp-table td { display: flex; justify-content: space-between; gap: 12px; padding: 4px 8px; border: 0; }
  .gp-table td::before { content: attr(data-label); color: var(--muted); font-size: 12px; }
  .gp-mono { white-space: normal; text-align: right; }
}
</style>
