<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { getProtection } from '@/api/backups.api'
import type { EnvironmentProtection } from '@/types/backup'

/*
 * Protection des données : pour chaque environnement en service, l'âge de sa dernière
 * sauvegarde réussie (Velero, hors site). Vert : moins de 26 h ; orange : plus ancien ou
 * aucune encore ; rouge : plus de 50 h ou dernière tentative en échec.
 */
const items = ref<EnvironmentProtection[] | null>(null)
const error = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

async function load() {
  try {
    items.value = await getProtection()
    error.value = false
  } catch {
    error.value = true
  }
}
onMounted(() => {
  void load()
  timer = setInterval(() => { if (document.visibilityState === 'visible') void load() }, 60000)
})
onBeforeUnmount(() => { if (timer) clearInterval(timer) })
</script>

<template>
  <section class="dpc" aria-labelledby="dpc-title">
    <header class="dpc-head">
      <h2 id="dpc-title">Protection des données</h2>
      <RouterLink to="/backups" class="dpc-link">Sauvegardes</RouterLink>
    </header>
    <p class="dpc-sub">Sauvegardes Velero hors site, par environnement en service</p>

    <p v-if="error" class="dpc-muted">État des sauvegardes indisponible pour le moment.</p>
    <p v-else-if="items === null" class="dpc-muted">Chargement…</p>
    <p v-else-if="!items.length" class="dpc-muted">Aucun environnement en service pour l’instant.</p>
    <ul v-else class="dpc-list">
      <li v-for="item in items" :key="item.environmentId" :class="`is-${item.level.toLowerCase()}`">
        <span class="dpc-dot" aria-hidden="true" />
        <span class="dpc-env">{{ item.applicationName }} · {{ item.environmentName }}</span>
        <span class="dpc-msg">{{ item.message }}</span>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.dpc { padding: 16px 18px; border: 1px solid var(--color-gray-200, #e5e7eb); border-radius: 12px; background: #fff; }
.dpc-head { display: flex; align-items: baseline; justify-content: space-between; gap: 8px; }
.dpc-head h2 { margin: 0; font-size: 16px; }
.dpc-link { font-size: 13px; font-weight: 600; color: #0f766e; text-decoration: none; }
.dpc-sub, .dpc-muted { margin: 4px 0 10px; color: var(--color-gray-500, #6b7280); font-size: 12.5px; }
.dpc-list { display: flex; flex-direction: column; gap: 6px; margin: 0; padding: 0; list-style: none; }
.dpc-list li { display: grid; grid-template-columns: 10px minmax(0, 1fr); column-gap: 10px; align-items: center; padding: 8px 10px; border-radius: 8px; background: var(--color-gray-50, #f9fafb); }
.dpc-dot { width: 10px; height: 10px; border-radius: 50%; background: #9ca3af; }
.is-ok .dpc-dot { background: #16a34a; }
.is-warning .dpc-dot { background: #d97706; }
.is-critical .dpc-dot { background: #dc2626; }
.is-critical { background: #fef2f2 !important; }
.dpc-env { font-size: 13.5px; font-weight: 600; color: #111827; }
.dpc-msg { grid-column: 2; font-size: 12.5px; color: #4b5563; }
</style>
