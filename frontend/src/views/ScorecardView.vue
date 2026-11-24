<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getScorecard } from '@/api/portal.api'
import type { Scorecard } from '@/types/portal'

/* Fiche d’excellence : une note de A à E sur cinq axes, et quoi faire pour progresser. */
const route = useRoute()
const card = ref<Scorecard | null>(null)
const error = ref('')
const loading = ref(false)

async function load(refresh = false) {
  loading.value = true
  try { card.value = await getScorecard(Number(route.params.id), refresh); error.value = '' }
  catch (exception) {
    error.value = (exception as { response?: { status?: number } }).response?.status === 403
      ? 'Cette application ne vous appartient pas.' : 'La fiche n’a pas pu être calculée.'
  } finally { loading.value = false }
}
onMounted(() => load())
watch(() => route.params.id, () => load())
const computedAt = computed(() => card.value ? new Date(card.value.computedAt).toLocaleTimeString('fr-FR') : '')

const delta = computed(() => card.value?.score !== null && card.value?.previousScore !== null && card.value
  ? (card.value.score ?? 0) - (card.value.previousScore ?? 0) : null)
const AXIS_ICON: Record<string, string> = { QUALITY: 'Q', SECURITY: 'S', RELIABILITY: 'F', SPEED: 'R', OPERATIONS: 'E' }
const AXIS_LABEL: Record<string, string> = { QUALITY: 'Qualité', SECURITY: 'Sécurité', RELIABILITY: 'Fiabilité', SPEED: 'Rapidité', OPERATIONS: 'Exploitation' }
function tone(score: number | null) {
  if (score === null) return 'none'
  return score >= 85 ? 'a' : score >= 70 ? 'b' : score >= 55 ? 'c' : score >= 40 ? 'd' : 'e'
}
const byAxis = computed(() => {
  const groups: Record<string, Scorecard['criteria']> = {}
  for (const c of card.value?.criteria ?? []) (groups[c.axis] ??= []).push(c)
  return groups
})
</script>

<template>
  <section class="sc">
    <RouterLink to="/applications" class="sc-back">← Applications</RouterLink>
    <p v-if="error" class="sc-error" role="alert">{{ error }}</p>
    <p v-if="loading && !card" class="sc-muted">Calcul de la fiche…</p>

    <template v-if="card">
      <header class="sc-hero">
        <div class="sc-grade" :class="`is-${card.grade.toLowerCase()}`">{{ card.grade }}</div>
        <div class="sc-hero-text">
          <p class="sc-kicker">Fiche d’excellence</p>
          <h1>{{ card.applicationName }}</h1>
          <p>
            <strong>{{ card.score ?? '—' }}</strong> / 100
            <span v-if="delta !== null && delta !== 0" class="sc-delta" :class="delta > 0 ? 'is-up' : 'is-down'">{{ delta > 0 ? '+' : '' }}{{ delta }}</span>
          </p>
        </div>
        <div class="sc-refresh-box">
          <button type="button" class="sc-refresh" :disabled="loading" @click="load(true)">{{ loading ? 'Calcul…' : 'Recalculer' }}</button>
          <small>Calculée à {{ computedAt }}</small>
        </div>
      </header>

      <div class="sc-axes">
        <div v-for="a in card.axes" :key="a.key" class="sc-axis">
          <div class="sc-axis-top"><span class="sc-axis-icon">{{ AXIS_ICON[a.key] }}</span><strong>{{ a.label }}</strong><span class="sc-axis-score">{{ a.score ?? '—' }}</span></div>
          <div class="sc-bar"><div class="sc-fill" :class="`is-${tone(a.score)}`" :style="{ width: `${a.score ?? 0}%` }" /></div>
        </div>
      </div>

      <section v-if="card.recommendations.length" class="sc-card">
        <h2>Pour progresser</h2>
        <ol class="sc-reco">
          <li v-for="r in card.recommendations" :key="r.label">
            <span class="sc-points">{{ AXIS_LABEL[r.axis] }} · {{ r.points }} pts</span>
            <div><strong>{{ r.label }}</strong><p>{{ r.recommendation }}</p></div>
          </li>
        </ol>
      </section>
      <p v-else class="sc-perfect">Tous les critères mesurables sont remplis.</p>

      <section class="sc-card">
        <h2>Détail des critères</h2>
        <div v-for="a in card.axes" :key="a.key" class="sc-group">
          <h3>{{ a.label }}</h3>
          <ul>
            <li v-for="c in byAxis[a.key] ?? []" :key="c.label">
              <span class="sc-state" :class="c.passed === null ? 'is-unknown' : c.passed ? 'is-ok' : 'is-ko'">{{ c.passed === null ? '?' : c.passed ? '✓' : '✗' }}</span>
              <span class="sc-crit">{{ c.label }}</span>
              <span class="sc-detail">{{ c.detail }}</span>
              <span class="sc-pts">{{ c.points }} pts</span>
            </li>
          </ul>
        </div>
      </section>
    </template>
  </section>
</template>

<style scoped>
.sc { display: flex; flex-direction: column; gap: 14px; max-width: 1080px; }
.sc-back { color: #1d4ed8; font-size: 13.5px; font-weight: 600; text-decoration: none; }
.sc-error { margin: 0; padding: 10px 14px; border: 1px solid #fecaca; border-radius: 10px; background: #fef2f2; color: #991b1b; }
.sc-muted { color: #6b7280; }
.sc-hero { display: flex; flex-wrap: wrap; align-items: center; gap: 18px; padding: 20px 24px; border-radius: 18px; background: linear-gradient(135deg, #0f172a, #1e3a8a); color: #fff; }
.sc-grade { display: grid; place-items: center; width: 76px; height: 76px; border-radius: 20px; background: #fff; font-size: 40px; font-weight: 900; }
.sc-grade.is-a { color: #15803d; } .sc-grade.is-b { color: #65a30d; } .sc-grade.is-c { color: #ca8a04; } .sc-grade.is-d { color: #ea580c; } .sc-grade.is-e { color: #dc2626; }
.sc-hero-text { flex: 1; }
.sc-kicker { margin: 0; font-size: 12px; font-weight: 700; letter-spacing: .06em; opacity: .8; text-transform: uppercase; }
.sc-hero h1 { margin: 2px 0; font-size: 24px; }
.sc-hero p { margin: 0; }
.sc-hero strong { font-size: 22px; }
.sc-delta { margin-left: 8px; padding: 1px 8px; border-radius: 999px; font-size: 13px; font-weight: 800; }
.sc-delta.is-up { background: #dcfce7; color: #166534; } .sc-delta.is-down { background: #fee2e2; color: #991b1b; }
.sc-refresh-box { display: flex; flex-direction: column; align-items: flex-end; gap: 4px; }
.sc-refresh-box small { font-size: 12px; opacity: .8; }
.sc-refresh { padding: 8px 14px; border: 0; border-radius: 10px; background: rgba(255, 255, 255, .16); color: #fff; font: inherit; font-weight: 700; cursor: pointer; }
.sc-axes { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; }
@media (max-width: 900px) { .sc-axes { grid-template-columns: 1fr 1fr; } }
.sc-axis { display: flex; flex-direction: column; gap: 8px; padding: 14px; border: 1px solid #e0e7ff; border-radius: 14px; background: #fff; }
.sc-axis-top { display: flex; align-items: center; gap: 8px; font-size: 14px; }
.sc-axis-icon { display: grid; place-items: center; width: 26px; height: 26px; border-radius: 8px; background: #e0e7ff; color: #1e40af; font-size: 12px; font-weight: 800; }
.sc-axis-score { margin-left: auto; font-size: 18px; font-weight: 800; }
.sc-bar { height: 8px; overflow: hidden; border-radius: 999px; background: #f3f4f6; }
.sc-fill { height: 100%; border-radius: 999px; }
.sc-fill.is-a { background: #16a34a; } .sc-fill.is-b { background: #84cc16; } .sc-fill.is-c { background: #eab308; } .sc-fill.is-d { background: #f97316; } .sc-fill.is-e { background: #dc2626; }
.sc-card { display: flex; flex-direction: column; gap: 10px; padding: 16px 18px; border: 1px solid #e0e7ff; border-radius: 14px; background: #fff; }
.sc-card h2 { margin: 0; font-size: 16px; }
.sc-reco { display: flex; flex-direction: column; gap: 8px; margin: 0; padding: 0; list-style: none; }
.sc-reco li { display: flex; align-items: flex-start; gap: 12px; padding: 10px 12px; border-radius: 10px; background: #f8faff; }
.sc-reco p { margin: 2px 0 0; color: #4b5563; font-size: 13.5px; }
.sc-points { flex: 0 0 auto; white-space: nowrap; padding: 3px 9px; border-radius: 999px; background: #dcfce7; color: #166534; font-size: 12.5px; font-weight: 800; }
.sc-perfect { margin: 0; padding: 14px; border-radius: 12px; background: #f0fdf4; color: #166534; font-weight: 700; }
.sc-group h3 { margin: 8px 0 4px; color: #1e3a8a; font-size: 14px; }
.sc-group ul { display: flex; flex-direction: column; margin: 0; padding: 0; list-style: none; }
.sc-group li { display: grid; grid-template-columns: 26px minmax(0, 1.4fr) minmax(0, 1.6fr) 60px; align-items: center; gap: 8px; padding: 7px 0; border-top: 1px solid #eef2ff; font-size: 13.5px; }
.sc-state { display: grid; place-items: center; width: 22px; height: 22px; border-radius: 999px; font-size: 12px; font-weight: 800; }
.sc-state.is-ok { background: #dcfce7; color: #166534; } .sc-state.is-ko { background: #fee2e2; color: #991b1b; } .sc-state.is-unknown { background: #f3f4f6; color: #6b7280; }
.sc-detail { color: #6b7280; }
.sc-pts { color: #9ca3af; font-size: 12px; text-align: right; }
</style>
