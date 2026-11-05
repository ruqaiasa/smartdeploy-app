<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getIncidentAnalysis, rerunIncidentAnalysis } from '@/api/incident.api'
import type { IncidentAnalysis } from '@/types/incident'
import type { DeploymentResponse } from '@/types/deployment'

/*
 * Analyse d'un incident : cause probable, niveau de confiance, action
 * proposée et preuves citées. L'analyse étant asynchrone, le panneau
 * attend son résultat quelques secondes s'il n'est pas encore disponible.
 */
const props = defineProps<{ deployment: DeploymentResponse; canRerun: boolean }>()
const emit = defineEmits<{ close: [] }>()

const analysis = ref<IncidentAnalysis | null>(null)
const loading = ref(true)
const rerunning = ref(false)
const error = ref('')
let attempts = 0
const isRecent = computed(() => {
  const end = props.deployment.finishedAt ?? props.deployment.updatedAt
  return !end || Date.now() - new Date(end).getTime() < 2 * 60_000
})
let timer: ReturnType<typeof setTimeout> | null = null

const CONFIDENCE: Record<IncidentAnalysis['confidence'], { label: string; tone: string }> = {
  HIGH: { label: 'Confiance élevée', tone: 'high' },
  MEDIUM: { label: 'Confiance moyenne', tone: 'medium' },
  LOW: { label: 'Confiance faible', tone: 'low' },
}
const TRIGGERS: Record<IncidentAnalysis['trigger'], string> = {
  PIPELINE: 'échec du pipeline',
  GUARD: 'dégradation détectée par le gardien',
  MANUAL: 'analyse relancée',
}

const confidence = computed(() => CONFIDENCE[analysis.value?.confidence ?? 'LOW'] ?? CONFIDENCE.LOW)
const triggerLabel = computed(() => (analysis.value && TRIGGERS[analysis.value.trigger]) || 'analyse')

/* Une preuve « Source : texte » est affichée avec sa source en étiquette. */
const evidence = computed(() => (analysis.value?.evidence ?? []).map(line => {
  const i = line.indexOf(' : ')
  return i > 0 ? { source: line.slice(0, i), text: line.slice(i + 3) } : { source: '', text: line }
}))

async function load() {
  try {
    analysis.value = await getIncidentAnalysis(props.deployment.id)
    error.value = ''
  } catch {
    error.value = 'Impossible de charger l’analyse.'
  }
  // Une analyse automatique n'est attendue que pour un incident récent
  // (fin du déploiement il y a moins de 2 minutes) ; sinon, on propose
  // directement de la lancer.
  if (!analysis.value && isRecent.value && attempts++ < 10) {
    timer = setTimeout(load, 3000)
    return
  }
  loading.value = false
}

async function rerun() {
  rerunning.value = true
  error.value = ''
  try {
    analysis.value = await rerunIncidentAnalysis(props.deployment.id)
  } catch {
    error.value = 'La nouvelle analyse a échoué.'
  } finally {
    rerunning.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => { if (timer) clearTimeout(timer) })
</script>

<template>
  <Teleport to="body">
    <div class="ia-overlay" @click.self="emit('close')">
      <section class="ia" role="dialog" aria-modal="true" aria-labelledby="ia-title">
        <header class="ia-header">
          <div>
            <p class="ia-eyebrow">Analyse de l’incident</p>
            <h2 id="ia-title">{{ deployment.applicationName }} {{ deployment.version }} · {{ deployment.environmentName }}</h2>
            <p class="ia-sub">
              Déploiement #{{ deployment.id }}
              <template v-if="analysis"> · {{ triggerLabel }}, analysé le {{ new Date(analysis.createdAt).toLocaleString('fr-FR') }}</template>
            </p>
          </div>
          <button type="button" class="ia-close" aria-label="Fermer" @click="emit('close')">
            <svg viewBox="0 0 20 20" width="16" height="16" aria-hidden="true"><path d="M5 5l10 10M15 5 5 15" stroke="currentColor" stroke-width="2" stroke-linecap="round" /></svg>
          </button>
        </header>

        <p v-if="loading" class="ia-muted">Analyse automatique en cours : collecte du log Jenkins, de l’état des pods et des événements Kubernetes…</p>
        <p v-else-if="!analysis && !rerunning" class="ia-muted">Ce déploiement n’a pas encore été analysé. Lancez l’analyse ci-dessous.</p>
        <p v-if="rerunning" class="ia-muted">Analyse en cours : collecte du log Jenkins, de l’état des pods et des événements Kubernetes…</p>

        <template v-if="analysis">
          <div class="ia-cause" :class="analysis.code === 'UNKNOWN' ? 'is-unknown' : 'is-found'">
            <div class="ia-cause-head">
              <strong>{{ analysis.title }}</strong>
              <span class="ia-confidence" :class="`is-${confidence.tone}`">{{ confidence.label }}</span>
            </div>
            <span>{{ analysis.explanation }}</span>
          </div>

          <div class="ia-action">
            <span class="ia-label">Action proposée</span>
            <span>{{ analysis.action }}</span>
          </div>

          <div>
            <h3 class="ia-h3">Preuves</h3>
            <ul class="ia-evidence">
              <li v-for="(line, index) in evidence" :key="index">
                <span v-if="line.source" class="ia-source">{{ line.source }}</span>
                <code>{{ line.text }}</code>
              </li>
            </ul>
          </div>

          <p v-if="analysis.alternatives?.length" class="ia-muted">
            Autres causes possibles : {{ analysis.alternatives.join(', ') }}.
          </p>
          <div v-if="analysis.notes?.length" class="ia-notes" role="note">
            <strong>Sources incomplètes</strong>
            <span v-for="note in analysis.notes" :key="note">{{ note }}</span>
          </div>
        </template>

        <p v-if="error" class="ia-error" role="alert">{{ error }}</p>

        <footer v-if="canRerun" class="ia-footer">
          <span class="ia-muted">Le diagnostic repose uniquement sur les preuves citées : aucune cause n’est supposée.</span>
          <button type="button" class="ia-btn" :disabled="rerunning" @click="rerun">
            {{ rerunning ? 'Analyse…' : analysis ? 'Relancer l’analyse' : 'Lancer l’analyse' }}
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<style scoped>
.ia-overlay {
  --ink: var(--color-gray-900, #111827); --muted: var(--color-gray-500, #6b7280); --line: var(--color-gray-200, #e5e7eb);
  position: fixed; inset: 0; z-index: 1100; display: grid; place-items: center; padding: 20px; background: rgba(15, 23, 42, .45);
}
.ia {
  display: flex; flex-direction: column; gap: 16px; width: min(820px, 100%); max-height: calc(100vh - 40px);
  overflow-y: auto; padding: 24px; border-radius: 14px; background: #fff; color: var(--ink); box-shadow: 0 24px 64px rgba(15, 23, 42, .25);
}
.ia-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.ia-eyebrow { margin: 0 0 4px; color: var(--color-primary-hover, #2563eb); font-size: 12px; font-weight: 600; letter-spacing: .04em; text-transform: uppercase; }
.ia-header h2 { margin: 0; font-size: 19px; }
.ia-sub { margin: 4px 0 0; color: var(--muted); font-size: 13px; }
.ia-close { display: grid; place-items: center; width: 32px; height: 32px; border: 0; border-radius: 8px; background: none; color: var(--muted); cursor: pointer; }
.ia-close:hover { background: var(--color-gray-100, #f3f4f6); color: var(--ink); }
.ia-cause { display: flex; flex-direction: column; gap: 6px; padding: 14px 16px; border: 1px solid; border-left-width: 4px; border-radius: 10px; font-size: 14px; }
.ia-cause.is-found { border-color: #fecaca; background: #fef2f2; color: #7f1d1d; }
.ia-cause.is-unknown { border-color: var(--line); background: var(--color-gray-50, #f9fafb); color: var(--ink); }
.ia-cause-head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; }
.ia-cause-head strong { font-size: 16px; }
.ia-confidence { padding: 2px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; }
.ia-confidence.is-high { background: #dcfce7; color: #166534; }
.ia-confidence.is-medium { background: #fef3c7; color: #92400e; }
.ia-confidence.is-low { background: var(--color-gray-100, #f3f4f6); color: var(--muted); }
.ia-action { display: flex; flex-direction: column; gap: 4px; padding: 12px 14px; border: 1px solid #bfdbfe; border-radius: 10px; background: #eff6ff; color: #1e3a8a; font-size: 14px; }
.ia-label { font-size: 12px; font-weight: 600; letter-spacing: .03em; text-transform: uppercase; }
.ia-h3 { margin: 0 0 8px; font-size: 15px; }
.ia-evidence { display: flex; flex-direction: column; gap: 6px; margin: 0; padding: 0; list-style: none; }
.ia-evidence li { display: flex; flex-direction: column; gap: 3px; padding: 8px 10px; border: 1px solid var(--line); border-radius: 8px; }
.ia-source { color: var(--muted); font-size: 11.5px; font-weight: 600; text-transform: uppercase; letter-spacing: .03em; }
.ia-evidence code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; white-space: pre-wrap; word-break: break-word; }
.ia-notes { display: flex; flex-direction: column; gap: 3px; padding: 10px 12px; border: 1px solid #fde68a; border-radius: 8px; background: #fffbeb; color: #92400e; font-size: 13px; }
.ia-muted { margin: 0; color: var(--muted); font-size: 13px; }
.ia-error { margin: 0; color: #b91c1c; font-size: 13px; }
.ia-footer { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; padding-top: 4px; border-top: 1px solid var(--line); }
.ia-btn { padding: 8px 14px; border: 1px solid var(--line); border-radius: 8px; background: #fff; color: var(--ink); font: inherit; font-size: 13.5px; font-weight: 600; cursor: pointer; }
.ia-btn:hover:not(:disabled) { background: var(--color-gray-50, #f9fafb); }
.ia-btn:disabled { opacity: .6; cursor: not-allowed; }
.ia-btn:focus-visible, .ia-close:focus-visible { outline: 2px solid var(--color-primary-hover, #2563eb); outline-offset: 2px; }
</style>
