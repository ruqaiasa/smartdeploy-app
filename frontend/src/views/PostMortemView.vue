<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { getPostMortem, type PostMortem } from '@/api/portal.api'

/* Post-mortem automatique : un rapport d’incident lisible, imprimable en PDF. */
const route = useRoute()
const report = ref<PostMortem | null>(null)
const error = ref('')

onMounted(async () => {
  try { report.value = await getPostMortem(Number(route.params.id)) }
  catch (exception) {
    const s = (exception as { response?: { status?: number } }).response?.status
    error.value = s === 404 ? 'Ce déploiement n’a pas connu d’incident.' : s === 403 ? 'Ce déploiement ne vous appartient pas.' : 'Le rapport n’a pas pu être généré.'
  }
})
const when = (iso: string | null) => iso ? new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'medium' }) : '—'
const hour = (iso: string) => new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
const CONF: Record<string, string> = { HIGH: 'élevée', MEDIUM: 'moyenne', LOW: 'faible' }
const sevTone = computed(() => report.value?.severity === 'Majeure' ? 'critical' : report.value?.severity === 'Mineure' ? 'warning' : 'low')
function print() { window.print() }
</script>

<template>
  <main class="pm">
    <nav class="pm-bar">
      <RouterLink :to="`/deployments/${route.params.id}`">← Fiche du déploiement</RouterLink>
      <button v-if="report" type="button" @click="print">Imprimer ou enregistrer en PDF</button>
    </nav>
    <p v-if="error" class="pm-error">{{ error }}</p>

    <article v-if="report" class="pm-doc">
      <header class="pm-head">
        <p class="pm-brand">SmartDeploy · Rapport d’incident</p>
        <h1>{{ report.applicationName }} {{ report.version }}</h1>
        <div class="pm-tags">
          <span class="pm-sev" :class="`is-${sevTone}`">Gravité : {{ report.severity }}</span>
          <span>{{ report.kind }}</span>
          <span>{{ report.environmentName }}</span>
          <span>Déploiement #{{ report.deploymentId }}</span>
        </div>
      </header>

      <section class="pm-summary">{{ report.summary }}</section>

      <div class="pm-metrics">
        <div><span>Début de l’incident</span><strong>{{ when(report.impactStart ?? report.detectedAt) }}</strong></div>
        <div><span>Temps de détection</span><strong>{{ report.minutesToDetect ?? '—' }}<small v-if="report.minutesToDetect !== null"> min</small></strong></div>
        <div><span>Temps de rétablissement</span><strong>{{ report.minutesToRecover ?? '—' }}<small v-if="report.minutesToRecover !== null"> min</small></strong></div>
        <div><span>Rétabli le</span><strong>{{ when(report.recoveredAt) }}</strong></div>
      </div>

      <section class="pm-section"><h2>1. Impact</h2><p>{{ report.impact }}</p></section>
      <section class="pm-section"><h2>2. Détection</h2><p>{{ report.detection }}</p></section>
      <section class="pm-section">
        <h2>3. Cause</h2>
        <p><strong>{{ report.cause }}</strong><template v-if="report.confidence"> · confiance {{ CONF[report.confidence] ?? report.confidence }}</template></p>
        <p v-if="report.causeDetail">{{ report.causeDetail }}</p>
        <pre v-if="report.decisiveLine" class="pm-line">{{ report.decisiveLine }}</pre>
      </section>
      <section class="pm-section"><h2>4. Rétablissement</h2><p>{{ report.recovery }}</p></section>

      <section class="pm-section">
        <h2>5. Chronologie</h2>
        <ol class="pm-timeline">
          <li v-for="(e, i) in report.timeline" :key="i" :class="`is-${e.tone ?? 'neutral'}`">
            <time>{{ hour(e.at) }}</time><span class="pm-dot" /><span>{{ e.text }}</span>
          </li>
        </ol>
      </section>

      <section class="pm-section">
        <h2>6. Actions correctives</h2>
        <ul><li v-for="(a, i) in report.actions" :key="i">{{ a }}</li></ul>
      </section>
      <section class="pm-section">
        <h2>7. Ce que nous retenons</h2>
        <ul><li v-for="(l, i) in report.lessons" :key="i">{{ l }}</li></ul>
      </section>

      <footer class="pm-foot">Rapport généré automatiquement par SmartDeploy le {{ when(report.generatedAt) }}<template v-if="report.requestedBy"> · demandé par {{ report.requestedBy }}</template></footer>
    </article>
  </main>
</template>

<style scoped>
.pm { min-height: 100vh; padding: 24px 16px 48px; background: #eef2f7; }
.pm-bar { display: flex; justify-content: space-between; align-items: center; max-width: 860px; margin: 0 auto 14px; }
.pm-bar a { color: #1d4ed8; font-weight: 700; text-decoration: none; }
.pm-bar button { padding: 9px 16px; border: 0; border-radius: 10px; background: #1e3a8a; color: #fff; font: inherit; font-weight: 700; cursor: pointer; }
.pm-error { max-width: 860px; margin: 0 auto; padding: 12px 16px; border-radius: 12px; background: #fef2f2; color: #991b1b; }
.pm-doc { display: flex; flex-direction: column; gap: 18px; max-width: 860px; margin: 0 auto; padding: 40px 48px; border-radius: 6px; background: #fff; box-shadow: 0 10px 30px rgba(15, 23, 42, .08); color: #0f172a; }
.pm-head { padding-bottom: 14px; border-bottom: 3px solid #1e3a8a; }
.pm-brand { margin: 0; color: #4f46e5; font-size: 12px; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }
.pm-head h1 { margin: 4px 0 10px; font-size: 28px; }
.pm-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.pm-tags span { padding: 3px 10px; border-radius: 999px; background: #f1f5f9; color: #334155; font-size: 12.5px; font-weight: 700; }
.pm-tags .pm-sev.is-critical { background: #fee2e2; color: #991b1b; }
.pm-tags .pm-sev.is-warning { background: #fef3c7; color: #92400e; }
.pm-tags .pm-sev.is-low { background: #dcfce7; color: #166534; }
.pm-summary { padding: 14px 16px; border-left: 4px solid #4f46e5; border-radius: 8px; background: #f8fafc; font-size: 15px; line-height: 1.55; }
.pm-metrics { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.pm-metrics div { display: flex; flex-direction: column; gap: 2px; padding: 12px; border: 1px solid #e2e8f0; border-radius: 10px; }
.pm-metrics span { color: #64748b; font-size: 11.5px; font-weight: 700; text-transform: uppercase; letter-spacing: .03em; }
.pm-metrics strong { font-size: 17px; }
.pm-metrics small { color: #64748b; font-size: 12px; }
.pm-section h2 { margin: 0 0 6px; color: #1e3a8a; font-size: 16px; }
.pm-section p { margin: 0 0 6px; font-size: 14.5px; line-height: 1.55; }
.pm-section ul { margin: 0; padding-left: 20px; font-size: 14.5px; line-height: 1.6; }
.pm-line { margin: 6px 0 0; padding: 10px 12px; border-radius: 8px; background: #0f172a; color: #e2e8f0; font-size: 12.5px; white-space: pre-wrap; word-break: break-word; }
.pm-timeline { display: flex; flex-direction: column; margin: 0; padding: 0; list-style: none; }
.pm-timeline li { display: grid; grid-template-columns: 74px 14px 1fr; align-items: baseline; gap: 10px; padding: 5px 0; font-size: 14px; }
.pm-timeline time { color: #64748b; font-size: 12.5px; font-variant-numeric: tabular-nums; }
.pm-dot { width: 10px; height: 10px; border-radius: 999px; background: #94a3b8; }
.is-critical .pm-dot { background: #dc2626; } .is-ok .pm-dot { background: #16a34a; } .is-analysis .pm-dot { background: #6366f1; }
.pm-foot { padding-top: 12px; border-top: 1px solid #e2e8f0; color: #94a3b8; font-size: 12px; }
@media print {
  .pm { padding: 0; background: #fff; }
  .pm-bar { display: none; }
  .pm-doc { box-shadow: none; padding: 0; max-width: none; }
}
</style>
