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

const CONFIDENCE: Record<IncidentAnalysis['confidence'], { label: string; tone: string; hint: string }> = {
  HIGH: { label: 'Confiance élevée', tone: 'high', hint: 'Une signature connue a été trouvée dans les preuves.' },
  MEDIUM: { label: 'Confiance moyenne', tone: 'medium', hint: 'Plusieurs indices concordent, sans signature certaine.' },
  LOW: { label: 'Confiance faible', tone: 'low', hint: 'Peu de preuves disponibles : à confirmer avec la première analyse ou les logs.' },
}
const TRIGGERS: Record<IncidentAnalysis['trigger'], string> = {
  PIPELINE: 'échec du pipeline',
  GUARD: 'dégradation détectée par le gardien',
  MANUAL: 'analyse relancée',
}

const confidence = computed(() => CONFIDENCE[analysis.value?.confidence ?? 'LOW'] ?? CONFIDENCE.LOW)
const triggerLabel = computed(() => (analysis.value && TRIGGERS[analysis.value.trigger]) || 'analyse')

/* Preuves regroupées par source (« Gardien : … », « Événement Kubernetes : … »), dans leur ordre d'apparition. */
function grouper(lines: string[] | undefined) {
  const groups = new Map<string, string[]>()
  for (const line of lines ?? []) {
    const i = line.indexOf(' : ')
    const source = i > 0 ? line.slice(0, i) : 'Autres preuves'
    const text = i > 0 ? line.slice(i + 3) : line
    groups.set(source, [...(groups.get(source) ?? []), text])
  }
  return [...groups].map(([source, items]) => ({ source, items }))
}
const evidenceGroups = computed(() => grouper(analysis.value?.evidence))
const initialEvidenceGroups = computed(() => grouper(analysis.value?.initial?.evidence))
const evidenceSummary = computed(() => evidenceGroups.value.map(g => g.source).join(', '))

/*
 * Faits et ligne décisive à montrer : ceux de la dernière analyse, sinon ceux de la première
 * (une analyse relancée après le remplacement des pods n'a souvent plus les logs).
 */
const facts = computed(() => analysis.value?.facts?.length ? analysis.value.facts : (analysis.value?.initial?.facts ?? []))
const factsFromInitial = computed(() => !analysis.value?.facts?.length && !!analysis.value?.initial?.facts?.length)
const decisiveLine = computed(() => analysis.value?.decisiveLine || analysis.value?.initial?.decisiveLine || '')
const decisiveFromInitial = computed(() => !analysis.value?.decisiveLine && !!analysis.value?.initial?.decisiveLine)
const hasWhereToLook = computed(() => facts.value.length > 0 || !!decisiveLine.value)

/* La première analyse est ouverte d'un clic depuis l'étape 2 quand elle contient plus de preuves. */
const showInitial = ref(false)
function openInitial() {
  showInitial.value = true
  setTimeout(() => document.getElementById('ia-initial')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 50)
}

const copied = ref(false)
async function copyDecisive() {
  try {
    await navigator.clipboard.writeText(decisiveLine.value)
    copied.value = true
    setTimeout(() => { copied.value = false }, 1500)
  } catch {
    copied.value = false
  }
}

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

/* Mémoire des incidents : date courte et durée lisible */
function dateCourte(iso: string) {
  return new Date(iso).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })
}
function duree(minutes: number) {
  if (minutes < 60) return `${minutes} min`
  const h = Math.floor(minutes / 60)
  return minutes % 60 ? `${h} h ${minutes % 60} min` : `${h} h`
}
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
              <template v-if="analysis"> · analysé le {{ new Date(analysis.createdAt).toLocaleString('fr-FR') }}</template>
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
          <div class="ia-chips">
            <span class="ia-chip">{{ triggerLabel }}</span>
            <span class="ia-confidence" :class="`is-${confidence.tone}`" :title="confidence.hint">{{ confidence.label }}</span>
            <span class="ia-chip-hint">{{ confidence.hint }}</span>
          </div>

          <!-- 1. Que s'est-il passé ? -->
          <section class="ia-step">
            <h3 class="ia-step-title"><span class="ia-num">1</span>Ce qui s’est passé</h3>
            <div class="ia-cause" :class="analysis.code === 'UNKNOWN' ? 'is-unknown' : 'is-found'">
              <strong>{{ analysis.title }}</strong>
              <span>{{ analysis.explanation }}</span>
              <span v-if="analysis.alternatives?.length" class="ia-alt">Autres causes possibles : {{ analysis.alternatives.join(', ') }}.</span>
            </div>
          </section>

          <!-- 2. Que dois-je faire ? -->
          <section class="ia-step">
            <h3 class="ia-step-title"><span class="ia-num">2</span>Ce qu’il faut faire</h3>
            <div class="ia-action">
              <span>{{ analysis.action }}</span>
              <button v-if="analysis.initial" type="button" class="ia-link" @click="openInitial">
                Voir la première analyse, plus complète
              </button>
            </div>
            <div v-if="analysis.notes?.length" class="ia-notes" role="note">
              <strong>Sources incomplètes</strong>
              <span v-for="note in analysis.notes" :key="note">{{ note }}</span>
            </div>
          </section>

          <!-- 3. Où regarder ? -->
          <section v-if="hasWhereToLook" class="ia-step">
            <h3 class="ia-step-title">
              <span class="ia-num">3</span>Où regarder
              <span v-if="factsFromInitial || decisiveFromInitial" class="ia-from-initial">d’après la première analyse</span>
            </h3>
            <div v-if="facts.length" class="ia-facts">
              <div v-for="fact in facts" :key="fact.label" class="ia-fact">
                <span>{{ fact.label }}</span>
                <strong>{{ fact.value }}</strong>
              </div>
            </div>
            <div v-if="decisiveLine" class="ia-decisive">
              <div class="ia-decisive-head">
                <span class="ia-label">Ligne décisive du log</span>
                <button type="button" class="ia-copy" @click="copyDecisive">{{ copied ? 'Copiée' : 'Copier' }}</button>
              </div>
              <code>{{ decisiveLine }}</code>
            </div>
          </section>

          <!-- 4. Est-ce déjà arrivé ? -->
          <section class="ia-step">
            <h3 class="ia-step-title"><span class="ia-num">{{ hasWhereToLook ? 4 : 3 }}</span>Déjà vu ?</h3>
            <ul v-if="analysis.similar?.length" class="ia-memory">
              <li v-for="s in analysis.similar" :key="s.deploymentId">
                <div class="ia-memory-head">
                  <strong>#{{ s.deploymentId }} · {{ s.version }}</strong>
                  <span class="ia-muted">{{ s.environmentName }} · {{ dateCourte(s.at) }}</span>
                  <span class="ia-score" :class="s.similarity >= 80 ? 'is-high' : 'is-mid'">{{ s.similarity }} % similaire</span>
                </div>
                <p class="ia-memory-fix" :class="{ 'is-open': s.resolutionMinutes === null }">
                  {{ s.resolution }}<template v-if="s.resolutionMinutes !== null">, en {{ duree(s.resolutionMinutes) }}</template>
                </p>
                <p v-if="s.sharedTerms.length" class="ia-memory-why">Points communs : {{ s.sharedTerms.join(', ') }}</p>
              </li>
            </ul>
            <p v-else class="ia-muted">Aucun incident similaire dans l’historique : c’est la première fois que cette panne est observée.</p>
          </section>

          <!-- Détails techniques, repliés par défaut -->
          <details v-if="evidenceGroups.length" class="ia-more">
            <summary>Preuves de l’analyse ({{ analysis.evidence.length }}) <span class="ia-muted">· {{ evidenceSummary }}</span></summary>
            <div v-for="group in evidenceGroups" :key="group.source" class="ia-group">
              <span class="ia-source">{{ group.source }} ({{ group.items.length }})</span>
              <ul class="ia-evidence">
                <li v-for="(line, index) in group.items" :key="index"><code>{{ line }}</code></li>
              </ul>
            </div>
          </details>

          <details v-if="analysis.initial" id="ia-initial" class="ia-more" :open="showInitial" @toggle="showInitial = ($event.target as HTMLDetailsElement).open">
            <summary>
              Première analyse, au moment de l’incident
              <span class="ia-muted">· {{ new Date(analysis.initial.createdAt).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) }} · {{ analysis.initial.title }}</span>
            </summary>
            <p class="ia-muted">Les pods remplacés depuis ont emporté leurs logs : cette première analyse contient les preuves les plus complètes.</p>
            <div v-for="group in initialEvidenceGroups" :key="group.source" class="ia-group">
              <span class="ia-source">{{ group.source }} ({{ group.items.length }})</span>
              <ul class="ia-evidence">
                <li v-for="(line, index) in group.items" :key="index"><code>{{ line }}</code></li>
              </ul>
            </div>
          </details>
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
.ia-chips { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: -6px; }
.ia-chip { padding: 2px 10px; border-radius: 999px; background: var(--color-gray-100, #f3f4f6); color: var(--muted); font-size: 12px; font-weight: 600; }
.ia-chip-hint { color: var(--muted); font-size: 12.5px; }
.ia-confidence { padding: 2px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; cursor: help; }
.ia-confidence.is-high { background: #dcfce7; color: #166534; }
.ia-confidence.is-medium { background: #fef3c7; color: #92400e; }
.ia-confidence.is-low { background: var(--color-gray-100, #f3f4f6); color: var(--muted); }
.ia-step { display: flex; flex-direction: column; gap: 8px; }
.ia-step-title { display: flex; align-items: center; gap: 8px; margin: 0; font-size: 14.5px; }
.ia-num { display: grid; place-items: center; width: 22px; height: 22px; border-radius: 999px; background: #1e3a8a; color: #fff; font-size: 12px; font-weight: 700; }
.ia-from-initial { padding: 1px 8px; border-radius: 999px; background: #fef3c7; color: #92400e; font-size: 11.5px; font-weight: 600; }
.ia-cause { display: flex; flex-direction: column; gap: 6px; padding: 14px 16px; border: 1px solid; border-left-width: 4px; border-radius: 10px; font-size: 14px; }
.ia-cause strong { font-size: 16px; }
.ia-cause.is-found { border-color: #fecaca; background: #fef2f2; color: #7f1d1d; }
.ia-cause.is-unknown { border-color: var(--line); background: var(--color-gray-50, #f9fafb); color: var(--ink); }
.ia-alt { font-size: 13px; opacity: .85; }
.ia-action { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; padding: 12px 14px; border: 1px solid #bfdbfe; border-left: 4px solid #2563eb; border-radius: 10px; background: #eff6ff; color: #1e3a8a; font-size: 14px; font-weight: 500; }
.ia-link { padding: 0; border: 0; background: none; color: #1d4ed8; font: inherit; font-size: 13px; font-weight: 600; text-decoration: underline; cursor: pointer; }
.ia-facts { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 8px; }
.ia-fact { display: flex; flex-direction: column; gap: 2px; padding: 8px 10px; border: 1px solid var(--line); border-radius: 8px; background: var(--color-gray-50, #f9fafb); }
.ia-fact span { color: var(--muted); font-size: 12px; }
.ia-fact strong { font-size: 13.5px; word-break: break-word; }
.ia-decisive { display: flex; flex-direction: column; gap: 6px; padding: 10px 12px; border: 1px solid #fecaca; border-left: 4px solid #dc2626; border-radius: 8px; background: #fef2f2; }
.ia-decisive-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.ia-decisive code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12.5px; color: #7f1d1d; white-space: pre-wrap; word-break: break-word; }
.ia-copy { padding: 2px 10px; border: 1px solid #fecaca; border-radius: 6px; background: #fff; color: #991b1b; font: inherit; font-size: 12px; font-weight: 600; cursor: pointer; }
.ia-memory { display: flex; flex-direction: column; gap: 8px; margin: 0; padding: 0; list-style: none; }
.ia-memory li { padding: 10px 12px; border: 1px solid #ddd6fe; border-radius: 10px; background: #f5f3ff; }
.ia-memory-head { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; font-size: 13.5px; }
.ia-score { margin-left: auto; padding: 1px 8px; border-radius: 999px; font-size: 12px; font-weight: 700; }
.ia-score.is-high { background: #ede9fe; color: #5b21b6; }
.ia-score.is-mid { background: #f3f4f6; color: #4b5563; }
.ia-memory-fix { margin: 6px 0 2px; font-size: 13.5px; font-weight: 600; color: #166534; }
.ia-memory-fix.is-open { color: #92400e; }
.ia-memory-why { margin: 0; color: var(--muted); font-size: 12.5px; }
.ia-more { padding: 10px 12px; border: 1px solid var(--line); border-radius: 8px; font-size: 13.5px; }
.ia-more summary { cursor: pointer; font-weight: 600; }
.ia-more[open] summary { margin-bottom: 8px; }
.ia-group { display: flex; flex-direction: column; gap: 4px; margin-top: 8px; }
.ia-label { font-size: 12px; font-weight: 600; letter-spacing: .03em; text-transform: uppercase; }
.ia-evidence { display: flex; flex-direction: column; gap: 4px; margin: 0; padding: 0; list-style: none; }
.ia-evidence li { padding: 6px 10px; border-radius: 6px; background: var(--color-gray-50, #f9fafb); }
.ia-source { color: var(--muted); font-size: 11.5px; font-weight: 600; text-transform: uppercase; letter-spacing: .03em; }
.ia-evidence code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; font-size: 12px; white-space: pre-wrap; word-break: break-word; }
.ia-notes { display: flex; flex-direction: column; gap: 3px; padding: 10px 12px; border: 1px solid #fde68a; border-radius: 8px; background: #fffbeb; color: #92400e; font-size: 13px; }
.ia-muted { margin: 0; color: var(--muted); font-size: 13px; }
.ia-error { margin: 0; color: #b91c1c; font-size: 13px; }
.ia-footer { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; padding-top: 4px; border-top: 1px solid var(--line); }
.ia-btn { padding: 8px 14px; border: 1px solid var(--line); border-radius: 8px; background: #fff; color: var(--ink); font: inherit; font-size: 13.5px; font-weight: 600; cursor: pointer; }
.ia-btn:hover:not(:disabled) { background: var(--color-gray-50, #f9fafb); }
.ia-btn:disabled { opacity: .6; cursor: not-allowed; }
.ia-btn:focus-visible, .ia-close:focus-visible { outline: 2px solid var(--color-primary-hover, #2563eb); outline-offset: 2px; }
</style>
