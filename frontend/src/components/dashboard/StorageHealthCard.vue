<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getStorageHealth } from '@/api/infrastructure.api'
import type { RaidSnapshot, StorageHealthResponse } from '@/types/infrastructure'

/*
 * Santé du stockage : l'état des RAID de la machine qui héberge le cluster.
 * Infrastructure partagée, distincte des alertes applicatives.
 */
const health = ref<StorageHealthResponse | null>(null)
const error = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

const LABELS = { HEALTHY: 'Sain', DEGRADED: 'Dégradé', RECOVERING: 'Reconstruction', UNKNOWN: 'Inconnu' } as const
const TONES = { HEALTHY: 'ok', DEGRADED: 'ko', RECOVERING: 'pending', UNKNOWN: 'muted' } as const

const raids = computed<RaidSnapshot[]>(() => health.value?.raids ?? [])

async function load() {
  try {
    health.value = await getStorageHealth()
    error.value = false
  } catch {
    error.value = true
  }
}
function time(value: string) {
  return new Date(value).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

onMounted(() => {
  void load()
  timer = setInterval(() => { if (document.visibilityState === 'visible') void load() }, 30000)
})
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <section class="sh" aria-labelledby="sh-title">
    <header class="sh-head">
      <h2 id="sh-title">Stockage (RAID)</h2>
      <span class="sh-sub">Disques de la machine du cluster, vérifiés toutes les 30 s</span>
    </header>

    <p v-if="error" class="sh-muted">État du stockage indisponible pour le moment.</p>
    <p v-else-if="health && !health.prometheusReachable" class="sh-muted">Prometheus injoignable : dernier état connu affiché.</p>
    <p v-if="!error && health && !raids.length" class="sh-muted">Aucun RAID détecté pour le moment.</p>

    <div v-for="raid in raids" :key="raid.device" class="sh-raid" :class="`is-${TONES[raid.status]}`">
      <div class="sh-line">
        <code>{{ raid.device }}</code>
        <span class="sh-badge" :class="`is-${TONES[raid.status]}`">{{ LABELS[raid.status] }}</span>
        <span v-if="raid.activeDisks !== null && raid.requiredDisks !== null" class="sh-disks">
          {{ raid.activeDisks }} / {{ raid.requiredDisks }} disques actifs
        </span>
      </div>
      <p class="sh-msg">{{ raid.message }}</p>
      <small class="sh-muted">Vérifié à {{ time(raid.checkedAt) }}</small>
    </div>
  </section>
</template>

<style scoped>
.sh { padding: 16px 18px; border: 1px solid var(--color-gray-200, #e5e7eb); border-radius: 12px; background: #fff; }
.sh-head { display: flex; flex-wrap: wrap; align-items: baseline; gap: 4px 12px; margin-bottom: 10px; }
.sh-head h2 { margin: 0; font-size: 16px; }
.sh-sub, .sh-muted { color: var(--color-gray-500, #6b7280); font-size: 12.5px; }
.sh-muted { margin: 0; }
.sh-raid { padding: 10px 12px; border-left: 4px solid #d1d5db; border-radius: 8px; background: var(--color-gray-50, #f9fafb); }
.sh-raid + .sh-raid { margin-top: 8px; }
.sh-raid.is-ok { border-left-color: #16a34a; }
.sh-raid.is-ko { border-left-color: #dc2626; background: #fef2f2; }
.sh-raid.is-pending { border-left-color: #d97706; background: #fffbeb; }
.sh-line { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.sh-badge { padding: 2px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; }
.sh-badge.is-ok { background: #dcfce7; color: #166534; }
.sh-badge.is-ko { background: #fee2e2; color: #991b1b; }
.sh-badge.is-pending { background: #fef3c7; color: #92400e; }
.sh-badge.is-muted { background: #f3f4f6; color: #4b5563; }
.sh-disks { font-size: 13px; color: var(--color-gray-700, #374151); }
.sh-msg { margin: 6px 0 2px; font-size: 13.5px; }
</style>
