<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getConfidenceScore } from '@/api/confidence.api'
import type { ConfidenceScore } from '@/types/confidence'

/*
 * Score de confiance affiché avant le lancement d'un déploiement :
 * 100 points moins des pénalités, chacune justifiée par l'historique.
 */
const props = defineProps<{ versionId: number; environmentId: number }>()

const score = ref<ConfidenceScore | null>(null)
const failed = ref(false)

async function load() {
  score.value = null
  failed.value = false
  if (!props.versionId || !props.environmentId) return
  try {
    score.value = await getConfidenceScore(props.versionId, props.environmentId)
  } catch {
    failed.value = true
  }
}

watch(() => [props.versionId, props.environmentId], load, { immediate: true })

const tone = computed(() => ({ HIGH: 'high', MEDIUM: 'medium', LOW: 'low' }[score.value?.level ?? 'HIGH']))
</script>

<template>
  <section v-if="score" class="cs" :class="`is-${tone}`" aria-label="Score de confiance">
    <div class="cs-head">
      <div class="cs-gauge" role="img" :aria-label="`Score de confiance : ${score.score} sur 100`">
        <strong>{{ score.score }}</strong><span>/100</span>
      </div>
      <div class="cs-text">
        <p class="cs-eyebrow">Score de confiance</p>
        <strong class="cs-level">{{ score.levelLabel }}</strong>
        <span class="cs-advice">{{ score.advice }}</span>
      </div>
    </div>
    <ul v-if="score.factors.length" class="cs-factors">
      <li v-for="factor in score.factors" :key="factor.label">
        <span class="cs-points">{{ factor.points }}</span>
        <span><strong>{{ factor.label }}</strong> — {{ factor.explanation }}</span>
      </li>
    </ul>
    <p v-else class="cs-none">Aucun signal de risque dans l’historique de cette version et de cet environnement.</p>
  </section>
  <p v-else-if="failed" class="cs-unavailable">Score de confiance indisponible pour le moment.</p>
</template>

<style scoped>
.cs { display: flex; flex-direction: column; gap: 10px; margin-bottom: 14px; padding: 14px 16px; border: 1px solid; border-left-width: 4px; border-radius: 10px; }
.cs.is-high { border-color: #a7f3d0; background: #ecfdf5; color: #065f46; }
.cs.is-medium { border-color: #fde68a; background: #fffbeb; color: #92400e; }
.cs.is-low { border-color: #fecaca; background: #fef2f2; color: #991b1b; }
.cs-head { display: flex; align-items: center; gap: 14px; }
.cs-gauge { display: flex; flex-shrink: 0; align-items: baseline; justify-content: center; gap: 1px; width: 64px; height: 64px; padding-top: 18px; box-sizing: border-box; border: 3px solid currentColor; border-radius: 50%; background: #fff; }
.cs-gauge strong { font-size: 20px; line-height: 1; }
.cs-gauge span { font-size: 11px; opacity: .8; }
.cs-text { display: flex; flex-direction: column; gap: 2px; }
.cs-eyebrow { margin: 0; font-size: 11.5px; font-weight: 600; letter-spacing: .04em; text-transform: uppercase; opacity: .85; }
.cs-level { font-size: 16px; }
.cs-advice { font-size: 13px; }
.cs-factors { display: flex; flex-direction: column; gap: 6px; margin: 0; padding: 0; list-style: none; font-size: 13px; }
.cs-factors li { display: flex; align-items: flex-start; gap: 8px; }
.cs-points { flex-shrink: 0; min-width: 34px; padding: 1px 6px; border-radius: 999px; background: #fff; font-weight: 700; font-size: 12px; text-align: center; }
.cs-none, .cs-unavailable { margin: 0 0 12px; font-size: 13px; }
.cs-unavailable { color: var(--color-gray-500, #6b7280); }
</style>
