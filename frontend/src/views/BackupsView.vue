<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { createBackup, getBackupEnvironments, getBackups, getRestorePhase, restoreBackup } from '@/api/backups.api'
import { useAuthStore } from '@/stores/auth'
import type { BackupEnvironment, BackupInfo } from '@/types/backup'

/*
 * Sauvegardes Velero d'un environnement : planifiées, points de restauration
 * avant déploiement et sauvegardes manuelles, avec restauration de
 * vérification (à côté) ou en remplacement (administrateurs).
 */
const authStore = useAuthStore()
const canManage = computed(() => authStore.hasRole('DEVOPS', 'ADMIN'))
const canReplace = computed(() => authStore.hasRole('ADMIN'))

const environments = ref<BackupEnvironment[]>([])
const environmentId = ref<number | null>(null)
const backups = ref<BackupInfo[]>([])
const loading = ref(false)
const error = ref('')
const info = ref('')
const busy = ref(false)
const restoreTracking = ref<{ name: string; target: string; phase: string } | null>(null)
let refreshTimer: ReturnType<typeof setInterval> | null = null

const ORIGINS = { SCHEDULED: 'Planifiée', PRE_DEPLOYMENT: 'Avant déploiement', MANUAL: 'Manuelle' } as const
const environment = computed(() => environments.value.find(e => e.environmentId === environmentId.value) ?? null)
const deployedCount = computed(() => environments.value.filter(e => e.deployed).length)

function tone(phase: string) {
  if (phase === 'Completed') return 'ok'
  if (phase === 'PartiallyFailed' || phase === 'InProgress' || phase === 'New' || phase === 'WaitingForPluginOperations') return 'pending'
  return 'ko'
}
function phaseLabel(phase: string) {
  return ({ Completed: 'Terminée', InProgress: 'En cours', New: 'En attente', PartiallyFailed: 'Partielle', Failed: 'Échec', FailedValidation: 'Refusée' } as Record<string, string>)[phase] ?? phase
}
function date(value: string | null) {
  return value ? new Date(value).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—'
}
function duration(backup: BackupInfo) {
  if (!backup.startedAt || !backup.completedAt) return '—'
  const s = Math.round((new Date(backup.completedAt).getTime() - new Date(backup.startedAt).getTime()) / 1000)
  return s >= 60 ? `${Math.floor(s / 60)} min ${String(s % 60).padStart(2, '0')} s` : `${s} s`
}

async function loadBackups() {
  if (!environmentId.value) return
  loading.value = true
  error.value = ''
  try {
    backups.value = await getBackups(environmentId.value)
  } catch {
    error.value = 'Impossible de lire les sauvegardes : vérifiez que Velero et le cluster répondent.'
  } finally {
    loading.value = false
  }
}

async function backupNow() {
  if (!environmentId.value) return
  busy.value = true
  info.value = ''
  try {
    const name = await createBackup(environmentId.value)
    info.value = `Sauvegarde « ${name} » lancée. Elle apparaîtra comme terminée dans une à deux minutes.`
    await loadBackups()
  } catch {
    error.value = 'La sauvegarde n’a pas pu être lancée.'
  } finally {
    busy.value = false
  }
}

async function restore(backup: BackupInfo, mode: 'side' | 'replace') {
  if (!environmentId.value || !environment.value) return
  if (mode === 'replace') {
    const typed = window.prompt(`Restauration en remplacement : l’espace de noms ${environment.value.namespace} sera SUPPRIMÉ puis restauré depuis « ${backup.name} ».\n\nPour confirmer, saisissez le nom de l’espace de noms :`)
    if (typed !== environment.value.namespace) return
  } else if (!window.confirm(`Restaurer « ${backup.name} » dans un espace de noms de vérification, à côté de l’application en service ?`)) {
    return
  }
  busy.value = true
  error.value = ''
  try {
    const result = await restoreBackup(environmentId.value, backup.name, mode)
    restoreTracking.value = { name: result.restoreName, target: result.targetNamespace, phase: 'InProgress' }
  } catch {
    error.value = 'La restauration n’a pas pu être lancée.'
  } finally {
    busy.value = false
  }
}

async function tick() {
  if (restoreTracking.value && !['Completed', 'PartiallyFailed', 'Failed', 'FailedValidation'].includes(restoreTracking.value.phase)) {
    try {
      restoreTracking.value.phase = await getRestorePhase(restoreTracking.value.name)
    } catch { /* nouvel essai */ }
  }
  if (backups.value.some(b => tone(b.phase) === 'pending')) await loadBackups()
}

onMounted(async () => {
  try {
    environments.value = await getBackupEnvironments()
    environmentId.value = environments.value[0]?.environmentId ?? null
  } catch {
    error.value = 'Impossible de charger les environnements.'
  }
  refreshTimer = setInterval(() => { if (document.visibilityState === 'visible') void tick() }, 5000)
})
onBeforeUnmount(() => { if (refreshTimer) clearInterval(refreshTimer) })
watch(environmentId, () => { restoreTracking.value = null; info.value = ''; void loadBackups() })
</script>

<template>
  <div class="bk">
    <header class="bk-head">
      <div>
        <h1>Sauvegardes</h1>
        <p class="bk-sub">Sauvegardes Velero hors site (Azure Blob Storage) : ressources Kubernetes et données des volumes.</p>
      </div>
      <div class="bk-actions">
        <select v-model.number="environmentId" aria-label="Environnement">
          <optgroup v-if="deployedCount" label="Déployés dans le cluster">
            <option v-for="env in environments.filter(e => e.deployed)" :key="env.environmentId" :value="env.environmentId">{{ env.applicationName }} · {{ env.environmentName }}</option>
          </optgroup>
          <optgroup v-if="deployedCount < environments.length" label="Non déployés">
            <option v-for="env in environments.filter(e => !e.deployed)" :key="env.environmentId" :value="env.environmentId">{{ env.applicationName }} · {{ env.environmentName }} (non déployé)</option>
          </optgroup>
        </select>
        <button v-if="canManage" type="button" class="bk-btn bk-btn-primary" :disabled="busy || !environment?.deployed" :title="environment && !environment.deployed ? 'Environnement non déployé : rien à sauvegarder' : undefined" @click="backupNow">Sauvegarder maintenant</button>
      </div>
    </header>

    <p v-if="environment && !environment.deployed" class="bk-info" role="status">
      Cet environnement n’est pas encore déployé dans le cluster (l’espace de noms <code>{{ environment.namespace }}</code> n’existe pas) : il n’y a rien à sauvegarder. Il sera protégé automatiquement dès son premier déploiement.
    </p>
    <p v-if="environment" class="bk-ns">Espace de noms : <code>{{ environment.namespace }}</code> · sauvegarde planifiée chaque nuit, conservée 7 jours ; point de restauration avant chaque déploiement, conservé 3 jours.</p>
    <p v-if="info" class="bk-info" role="status">{{ info }}</p>
    <p v-if="error" class="bk-error" role="alert">{{ error }}</p>

    <div v-if="restoreTracking" class="bk-restore" role="status">
      <strong>Restauration « {{ restoreTracking.name }} »</strong>
      <span>Vers <code>{{ restoreTracking.target }}</code> · {{ phaseLabel(restoreTracking.phase) }}</span>
      <span v-if="restoreTracking.phase === 'Completed' && restoreTracking.target !== environment?.namespace" class="bk-hint">
        Vérification possible : kubectl get pods -n {{ restoreTracking.target }} — puis supprimez cet espace de noms une fois le contrôle fait.
      </span>
    </div>

    <p v-if="loading && !backups.length" class="bk-muted">Chargement des sauvegardes…</p>
    <p v-else-if="!backups.length" class="bk-muted">Aucune sauvegarde ne couvre encore cet environnement.</p>
    <div v-else class="bk-table-wrap">
      <table class="bk-table">
        <thead>
          <tr><th scope="col">Sauvegarde</th><th scope="col">Origine</th><th scope="col">Statut</th><th scope="col">Début</th><th scope="col">Durée</th><th scope="col">Expire</th><th scope="col"><span class="sr-only">Actions</span></th></tr>
        </thead>
        <tbody>
          <tr v-for="backup in backups" :key="backup.name">
            <td><code>{{ backup.name }}</code><small v-if="backup.itemsBackedUp">{{ backup.itemsBackedUp }} ressources</small></td>
            <td>{{ ORIGINS[backup.origin] }}<template v-if="backup.deploymentId"> #{{ backup.deploymentId }}</template></td>
            <td><span class="bk-phase" :class="`is-${tone(backup.phase)}`">{{ phaseLabel(backup.phase) }}</span></td>
            <td>{{ date(backup.startedAt) }}</td>
            <td>{{ duration(backup) }}</td>
            <td>{{ date(backup.expiresAt) }}</td>
            <td>
              <div v-if="backup.phase === 'Completed'" class="bk-row-actions">
                <button v-if="canManage" type="button" class="bk-btn" :disabled="busy" @click="restore(backup, 'side')">Restaurer à côté</button>
                <button v-if="canReplace" type="button" class="bk-btn bk-btn-danger" :disabled="busy" @click="restore(backup, 'replace')">Remplacer</button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<style scoped>
.bk { display: flex; flex-direction: column; gap: 14px; }
.bk-head { display: flex; flex-wrap: wrap; align-items: flex-start; justify-content: space-between; gap: 12px; }
.bk-head h1 { margin: 0; font-size: 22px; }
.bk-sub { margin: 4px 0 0; color: var(--color-gray-500, #6b7280); font-size: 14px; }
.bk-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; }
.bk-actions select { min-width: 240px; padding: 8px 10px; border: 1px solid var(--color-gray-200, #e5e7eb); border-radius: 8px; background: #fff; font: inherit; }
.bk-ns, .bk-muted { margin: 0; color: var(--color-gray-500, #6b7280); font-size: 13px; }
.bk-info { margin: 0; padding: 10px 12px; border-radius: 8px; background: #eff6ff; color: #1e40af; font-size: 13.5px; }
.bk-error { margin: 0; padding: 10px 12px; border-radius: 8px; background: #fef2f2; color: #991b1b; font-size: 13.5px; }
.bk-restore { display: flex; flex-direction: column; gap: 3px; padding: 12px 14px; border: 1px solid #ddd6fe; border-radius: 10px; background: #f5f3ff; color: #3b0764; font-size: 13.5px; }
.bk-hint { font-size: 12.5px; opacity: .85; }
.bk-table-wrap { overflow-x: auto; border: 1px solid var(--color-gray-200, #e5e7eb); border-radius: 10px; background: #fff; }
.bk-table { width: 100%; border-collapse: collapse; font-size: 13.5px; }
.bk-table th { padding: 10px 12px; background: var(--color-gray-50, #f9fafb); color: var(--color-gray-500, #6b7280); font-size: 12px; font-weight: 600; text-align: left; white-space: nowrap; }
.bk-table td { padding: 10px 12px; border-top: 1px solid var(--color-gray-200, #e5e7eb); vertical-align: middle; }
.bk-table td small { display: block; color: var(--color-gray-500, #6b7280); font-size: 12px; }
.bk-table code { font-size: 12.5px; }
.bk-phase { padding: 2px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; white-space: nowrap; }
.bk-phase.is-ok { background: #dcfce7; color: #166534; }
.bk-phase.is-pending { background: #fef3c7; color: #92400e; }
.bk-phase.is-ko { background: #fee2e2; color: #991b1b; }
.bk-row-actions { display: flex; justify-content: flex-end; gap: 6px; white-space: nowrap; }
.bk-btn { padding: 6px 12px; border: 1px solid var(--color-gray-200, #e5e7eb); border-radius: 8px; background: #fff; font: inherit; font-size: 13px; font-weight: 600; cursor: pointer; }
.bk-btn:hover:not(:disabled) { background: var(--color-gray-50, #f9fafb); }
.bk-btn:disabled { opacity: .6; cursor: not-allowed; }
.bk-btn-primary { border-color: #2563eb; background: #2563eb; color: #fff; }
.bk-btn-primary:hover:not(:disabled) { background: #1d4ed8; }
.bk-btn-danger { border-color: #fecaca; color: #b91c1c; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
</style>
