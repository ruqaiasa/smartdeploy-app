<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { getPlatformBackupStatus, getProtection, getStorageLocation, runPlatformBackup } from '@/api/backups.api'
import type { EnvironmentProtection, PlatformBackupStatus, StorageLocationStatus } from '@/types/backup'
import { useAuthStore } from '@/stores/auth'

/*
 * Protection des données : pour chaque environnement en service, l'âge de sa dernière
 * sauvegarde réussie (Velero, hors site). Vert : moins de 26 h ; orange : plus ancien ou
 * aucune encore ; rouge : plus de 50 h ou dernière tentative en échec.
 */
const items = ref<EnvironmentProtection[] | null>(null)
const error = ref(false)
let timer: ReturnType<typeof setInterval> | null = null

const storage = ref<StorageLocationStatus | null>(null)
const platform = ref<PlatformBackupStatus | null>(null)
const platformRunning = ref(false)
const platformError = ref('')
const isAdmin = computed(() => useAuthStore().hasRole('ADMIN'))

async function load() {
  try {
    items.value = await getProtection()
    error.value = false
  } catch {
    error.value = true
  }
  try { storage.value = await getStorageLocation() } catch { storage.value = null }
  try { platform.value = await getPlatformBackupStatus() } catch { platform.value = null }
}

/* Stockage Azure : disponible, indisponible, ou état inconnu (illisible) */
const storageTone = computed(() => !storage.value?.readable ? 'muted' : storage.value.phase === 'Available' ? 'ok' : 'critical')
const storageText = computed(() => !storage.value?.readable
  ? 'État du stockage des sauvegardes non vérifié'
  : storage.value.phase === 'Available' ? 'Stockage des sauvegardes disponible' : `Stockage des sauvegardes indisponible (${storage.value.phase})`)

/* Base de SmartDeploy : vert sous 26 h, orange au-delà, rouge si la dernière tentative a échoué */
function ageHours(iso: string | null | undefined) {
  return iso ? (Date.now() - new Date(iso).getTime()) / 3_600_000 : Infinity
}
const platformTone = computed(() => {
  const p = platform.value
  if (!p) return 'muted'
  if (p.lastRun && !p.lastRun.success) return 'critical'
  return ageHours(p.lastSuccess?.finishedAt) <= 26 ? 'ok' : 'warning'
})
const platformText = computed(() => {
  const p = platform.value
  if (!p) return 'État inconnu'
  if (p.lastRun && !p.lastRun.success) return p.lastRun.message ?? 'Dernière sauvegarde en échec'
  if (!p.lastSuccess) return 'Aucune sauvegarde pour l’instant : la première aura lieu cette nuit'
  const h = ageHours(p.lastSuccess.finishedAt)
  const age = h < 1 ? 'il y a moins d’une heure' : `il y a ${Math.round(h)} h`
  return `Dernière sauvegarde ${age}, ${p.lastSuccess.uploaded ? 'sur le poste et sur Azure' : 'sur le poste uniquement'}`
})

async function backupPlatformNow() {
  platformRunning.value = true
  platformError.value = ''
  try {
    await runPlatformBackup()
    platform.value = await getPlatformBackupStatus()
  } catch (exception: unknown) {
    const response = (exception as { response?: { data?: { message?: string } } }).response
    platformError.value = response?.data?.message ?? 'La sauvegarde n’a pas pu être lancée.'
  } finally {
    platformRunning.value = false
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

    <ul class="dpc-list dpc-platform">
      <li :class="`is-${storageTone}`">
        <span class="dpc-dot" aria-hidden="true" />
        <span class="dpc-env">Stockage Azure</span>
        <span class="dpc-msg">{{ storageText }}</span>
      </li>
      <li :class="`is-${platformTone}`">
        <span class="dpc-dot" aria-hidden="true" />
        <span class="dpc-env">Base de SmartDeploy</span>
        <span class="dpc-msg">{{ platformText }}</span>
        <button v-if="isAdmin" type="button" class="dpc-btn" :disabled="platformRunning" @click="backupPlatformNow">
          {{ platformRunning ? 'Sauvegarde…' : 'Sauvegarder maintenant' }}
        </button>
        <span v-if="platformError" class="dpc-msg dpc-error">{{ platformError }}</span>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.dpc-platform { margin-top: 8px; padding-top: 8px; border-top: 1px dashed var(--color-gray-200, #e5e7eb); }
.is-muted .dpc-dot { background: #9ca3af; }
.dpc-btn { grid-column: 2; justify-self: start; margin-top: 4px; padding: 4px 10px; border: 1px solid #0e9f8e; border-radius: 6px; background: #fff; color: #0f766e; font: inherit; font-size: 12.5px; font-weight: 600; cursor: pointer; }
.dpc-btn:disabled { opacity: .6; cursor: wait; }
.dpc-error { color: #b91c1c; }
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
