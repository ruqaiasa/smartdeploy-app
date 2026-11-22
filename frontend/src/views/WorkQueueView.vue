<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getAnalystStats, getWorkQueue } from '@/api/portal.api'
import type { AnalystStats, WorkQueue } from '@/types/portal'

/* Console DevOps : tout ce qui attend une action, trié par urgence, avec son âge. */
const queue = ref<WorkQueue | null>(null)
const stats = ref<AnalystStats | null>(null)
const error = ref('')
let timer: ReturnType<typeof setInterval> | null = null

async function load() {
  try { queue.value = await getWorkQueue(); error.value = ''; stats.value = await getAnalystStats().catch(() => null) }
  catch { error.value = 'La file de travail n’a pas pu être chargée.' }
}
onMounted(() => { void load(); timer = setInterval(load, 30000) })
onBeforeUnmount(() => { if (timer) clearInterval(timer) })

const KIND: Record<string, string> = { APPROVAL: 'Approbation', ALERT: 'Alerte', FAILURE: 'Échec', CLAIM: 'Réclamation', ACCOUNT: 'Compte' }
function tone(urgency: number) { return urgency >= 90 ? 'critical' : urgency >= 60 ? 'approval' : 'warning' }
function age(iso: string | null) {
  if (!iso) return ''
  const m = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  return m < 60 ? `depuis ${m} min` : m < 1440 ? `depuis ${Math.round(m / 60)} h` : `depuis ${Math.round(m / 1440)} j`
}
const empty = computed(() => queue.value !== null && queue.value.items.length === 0)
</script>

<template>
  <section class="wq">
    <header class="wq-head">
      <div>
        <h1>File de travail</h1>
        <p>Par ordre d’urgence.</p>
      </div>
      <button type="button" class="wq-btn" @click="load">Actualiser</button>
    </header>

    <div v-if="queue" class="wq-stats">
      <div :class="{ 'is-active': queue.approvals }"><strong>{{ queue.approvals }}</strong><span>approbation{{ queue.approvals > 1 ? 's' : '' }}</span></div>
      <div :class="{ 'is-active': queue.alerts }"><strong>{{ queue.alerts }}</strong><span>alerte{{ queue.alerts > 1 ? 's' : '' }} ouverte{{ queue.alerts > 1 ? 's' : '' }}</span></div>
      <div :class="{ 'is-active': queue.claims }"><strong>{{ queue.claims }}</strong><span>réclamation{{ queue.claims > 1 ? 's' : '' }}</span></div>
      <div :class="{ 'is-active': queue.failures }"><strong>{{ queue.failures }}</strong><span>échec{{ queue.failures > 1 ? 's' : '' }} en 24 h</span></div>
      <div title="Part des analyses jugées justes par l’équipe (une analyse partielle compte pour moitié)">
        <strong>{{ stats?.precision ?? '—' }}<template v-if="stats?.precision !== null && stats?.precision !== undefined"> %</template></strong>
        <span>précision analyste</span>
      </div>
    </div>

    <p v-if="error" class="wq-error" role="alert">{{ error }}</p>
    <p v-if="empty" class="wq-empty">Rien à traiter.</p>

    <ul v-if="queue?.items.length" class="wq-list">
      <li v-for="(item, i) in queue.items" :key="`${item.kind}-${item.deploymentId ?? i}-${i}`" :class="`is-${tone(item.urgency)}`">
        <div class="wq-main">
          <p class="wq-top"><span class="wq-kind">{{ KIND[item.kind] }}</span> <strong>{{ item.title }}</strong></p>
          <p class="wq-detail">{{ item.detail }}</p>
          <p class="wq-meta">
            <span v-if="item.environmentType === 'PRODUCTION'" class="wq-prod">Production</span>
            <span v-if="item.requester">{{ item.requester }}</span>
            <span>{{ age(item.since) }}</span>
          </p>
        </div>
        <RouterLink :to="item.link" class="wq-btn wq-btn-primary">{{ item.kind === 'APPROVAL' ? 'Examiner' : 'Ouvrir' }}</RouterLink>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.wq { display: flex; flex-direction: column; gap: 14px; }
.wq-head { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 12px; }
.wq-head h1 { margin: 0; font-size: 24px; }
.wq-head p { margin: 4px 0 0; color: #6b7280; font-size: 14px; }
.wq-btn { display: inline-flex; align-items: center; padding: 8px 14px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #111827; font: inherit; font-size: 14px; font-weight: 600; text-decoration: none; cursor: pointer; white-space: nowrap; }
.wq-btn-primary { border-color: #1e3a8a; background: #1e3a8a; color: #fff; }
.wq-stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 12px; }
.wq-stats div { display: flex; align-items: baseline; gap: 8px; padding: 14px 16px; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; }
.wq-stats strong { font-size: 24px; }
.wq-stats span { color: #6b7280; font-size: 13.5px; }
.wq-stats .is-active { border-color: #c7d2fe; background: #f8faff; }
.wq-error { margin: 0; padding: 10px 14px; border: 1px solid #fecaca; border-radius: 10px; background: #fef2f2; color: #991b1b; }
.wq-empty { margin: 0; padding: 24px; border: 1px dashed #bbf7d0; border-radius: 12px; background: #f0fdf4; color: #166534; text-align: center; }
.wq-list { display: flex; flex-direction: column; gap: 8px; margin: 0; padding: 0; list-style: none; }
.wq-list li { display: flex; align-items: center; justify-content: space-between; gap: 14px; padding: 12px 16px; border: 1px solid #e5e7eb; border-left-width: 5px; border-radius: 12px; background: #fff; }
.wq-list li.is-critical { border-left-color: #dc2626; }
.wq-list li.is-approval { border-left-color: #7c3aed; }
.wq-list li.is-warning { border-left-color: #d97706; }
.wq-main { display: flex; flex-direction: column; gap: 3px; min-width: 0; }
.wq-main p { margin: 0; }
.wq-top { font-size: 14.5px; }
.wq-kind { padding: 1px 7px; border-radius: 5px; background: #f3f4f6; color: #374151; font-size: 11.5px; font-weight: 700; }
.wq-detail { color: #374151; font-size: 13.5px; overflow-wrap: anywhere; }
.wq-meta { display: flex; flex-wrap: wrap; gap: 10px; color: #6b7280; font-size: 12.5px; }
.wq-prod { padding: 0 7px; border-radius: 999px; background: #fee2e2; color: #991b1b; font-weight: 700; }
</style>
