<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { createBackup, deleteBackup, getBackupEnvironments, getBackups, getProtection, getRestorePhase, restoreBackup } from '@/api/backups.api'
import { useAuthStore } from '@/stores/auth'
import type { BackupEnvironment, BackupInfo, EnvironmentProtection } from '@/types/backup'

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

/* État de protection de l'environnement choisi (âge de la dernière sauvegarde réussie). */
const protections = ref<EnvironmentProtection[]>([])
const protection = computed(() => protections.value.find(p => p.environmentId === environmentId.value) ?? null)
async function loadProtection() {
  try { protections.value = await getProtection() } catch { /* complément : sans blocage */ }
}
const nextNightly = computed(() => {
  const next = new Date()
  next.setHours(2, 0, 0, 0)
  if (next.getTime() <= Date.now()) next.setDate(next.getDate() + 1)
  const today = new Date().toDateString() === next.toDateString()
  return `${today ? 'aujourd’hui' : 'cette nuit'} à 2 h`
})
const environment = computed(() => environments.value.find(e => e.environmentId === environmentId.value) ?? null)
const deployedCount = computed(() => environments.value.filter(e => e.deployed).length)

/* En cours : Velero ne sait pas interrompre une sauvegarde lancée. */
const RUNNING = ['New', 'InProgress', 'WaitingForPluginOperations', 'WaitingForPluginOperationsPartiallyFailed', 'Finalizing', 'FinalizingPartiallyFailed']
function tone(phase: string) {
  if (phase === 'Completed') return 'ok'
  if (RUNNING.includes(phase)) return 'pending'
  if (phase === 'PartiallyFailed') return 'warn'
  return 'ko'
}
function phaseLabel(phase: string) {
  return ({ Completed: 'Réussie', InProgress: 'En cours', New: 'En attente', PartiallyFailed: 'Partielle', Failed: 'Échec', FailedValidation: 'Refusée' } as Record<string, string>)[phase] ?? (RUNNING.includes(phase) ? 'En cours' : phase)
}
function phaseHint(phase: string) {
  return ({
    PartiallyFailed: 'Les ressources sont sauvegardées, mais pas toutes les données des volumes : non proposée à la restauration.',
    Failed: 'La sauvegarde n’a pas abouti.',
    FailedValidation: 'Velero a refusé la sauvegarde avant de la commencer (configuration invalide).',
  } as Record<string, string>)[phase] ?? ''
}

/* Une seule sauvegarde restaurable : elle est protégée contre la suppression. */
const restorableCount = computed(() => backups.value.filter(b => b.phase === 'Completed').length)
function deleteBlockedReason(backup: BackupInfo) {
  if (RUNNING.includes(backup.phase)) return 'Une sauvegarde en cours ne peut pas être interrompue : elle pourra être supprimée une fois terminée.'
  if (backup.phase === 'Completed' && restorableCount.value <= 1) return 'C’est la seule sauvegarde restaurable de cet environnement.'
  return ''
}
async function remove(backup: BackupInfo) {
  if (!environmentId.value) return
  if (!window.confirm(`Supprimer la sauvegarde du ${date(backup.startedAt ?? backup.expiresAt)} ?\nSes données seront aussi effacées du stockage Azure. Cette action est définitive.`)) return
  busy.value = true
  error.value = ''
  try {
    await deleteBackup(environmentId.value, backup.name)
    info.value = 'Suppression demandée : la sauvegarde disparaîtra de la liste d’ici quelques secondes.'
    setTimeout(() => { void loadBackups() }, 4000)
  } catch (exception: unknown) {
    const response = (exception as { response?: { data?: { message?: string } } }).response
    error.value = response?.data?.message ?? 'La sauvegarde n’a pas pu être supprimée.'
  } finally {
    busy.value = false
  }
}

function typeLabel(backup: BackupInfo) {
  if (backup.origin === 'SCHEDULED') return 'Automatique'
  if (backup.origin === 'MANUAL') return 'Manuelle'
  return 'Avant déploiement'
}
function typeDetail(backup: BackupInfo) {
  if (backup.origin === 'PRE_DEPLOYMENT') return `#${backup.deploymentId ?? '?'}${backup.protectedVersion ? ` · version ${backup.protectedVersion}` : ''}`
  return ''
}
function relative(value: string | null) {
  if (!value) return ''
  const h = (Date.now() - new Date(value).getTime()) / 3_600_000
  if (h < 1) return 'il y a moins d’une heure'
  if (h < 24) return `il y a ${Math.round(h)} h`
  return `il y a ${Math.round(h / 24)} j`
}
function technical(backup: BackupInfo) {
  return [`Nom : ${backup.name}`, `Durée : ${duration(backup)}`, backup.itemsBackedUp ? `${backup.itemsBackedUp} ressources` : '', `Expire le ${date(backup.expiresAt)}`]
    .filter(Boolean).join('\n')
}
/* Ce que contient une sauvegarde : tout le cluster (la nuit), ou cet environnement seulement. */
function wholeCluster(backup: BackupInfo) {
  return !backup.includedNamespaces || backup.includedNamespaces.length === 0
}
function scopeTitle(backup: BackupInfo) {
  return wholeCluster(backup)
    ? 'Sauvegarde de tout le cluster : toutes les applications et tous les environnements, dont celui-ci.'
    : `Espaces de noms inclus : ${backup.includedNamespaces!.join(', ')}`
}
const lastSuccess = computed(() => backups.value.find(b => b.phase === 'Completed') ?? null)
const statusTitle = computed(() => {
  const level = protection.value?.level
  return level === 'OK' ? 'Environnement protégé' : level === 'CRITICAL' ? 'Protection insuffisante' : 'À surveiller'
})
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
    void loadProtection()
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
    const typed = window.prompt(`Restaurer l’environnement : ${environment.value.namespace} va revenir à l’état de « ${backup.name} », application ET données.\nLes données écrites depuis seront perdues, et l’application sera indisponible pendant 1 à 2 minutes.\n\nPour confirmer, saisissez le nom de l’espace de noms :`)
    if (typed !== environment.value.namespace) return
  } else if (!window.confirm(`Vérifier « ${backup.name} » dans une copie : l’application et ses données seront restaurées dans un espace de noms séparé, sans toucher à l’application en service. Pensez à supprimer la copie après le contrôle.`)) {
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
        <p class="bk-sub">Chaque environnement est sauvegardé (application et données) hors de la machine, dans Azure.</p>
      </div>
      <select v-model.number="environmentId" aria-label="Environnement" class="bk-select">
        <optgroup v-if="deployedCount" label="Déployés">
          <option v-for="env in environments.filter(e => e.deployed)" :key="env.environmentId" :value="env.environmentId">{{ env.applicationName }} · {{ env.environmentName }}</option>
        </optgroup>
        <optgroup v-if="deployedCount < environments.length" label="Non déployés">
          <option v-for="env in environments.filter(e => !e.deployed)" :key="env.environmentId" :value="env.environmentId">{{ env.applicationName }} · {{ env.environmentName }}</option>
        </optgroup>
      </select>
    </header>

    <p v-if="environment && !environment.deployed" class="bk-note">
      Cet environnement n’est pas encore déployé : il n’y a rien à sauvegarder. Il sera protégé automatiquement dès son premier déploiement.
    </p>

    <!-- État de protection -->
    <section v-if="environment && environment.deployed" class="bk-status" :class="`is-${(protection?.level ?? 'WARNING').toLowerCase()}`">
      <div class="bk-status-main">
        <strong>{{ protection ? statusTitle : 'Calcul de l’état…' }}</strong>
        <span v-if="protection">{{ protection.message.replace(/\s*\([^)]*\)\s*$/, '') }}</span>
        <span class="bk-status-facts">
          <span>Dernière réussie : <b>{{ lastSuccess ? `${date(lastSuccess.startedAt)} (${relative(lastSuccess.startedAt)})` : 'aucune' }}</b></span>
          <span>Prochaine : <b>{{ nextNightly }}</b>, ou au démarrage de la machine</span>
        </span>
      </div>
      <button v-if="canManage" type="button" class="bk-btn bk-btn-primary" :disabled="busy" @click="backupNow">Sauvegarder maintenant</button>
    </section>

    <p v-if="info" class="bk-info" role="status">{{ info }}</p>
    <p v-if="error" class="bk-error" role="alert">{{ error }}</p>

    <div v-if="restoreTracking" class="bk-restore" role="status">
      <strong>Restauration en cours vers <code>{{ restoreTracking.target }}</code> : {{ phaseLabel(restoreTracking.phase) }}</strong>
      <span v-if="restoreTracking.phase === 'Completed' && restoreTracking.target !== environment?.namespace">
        La copie est prête. Une fois le contrôle fait, supprimez l’espace de noms <code>{{ restoreTracking.target }}</code>.
      </span>
    </div>

    <!-- Historique -->
    <section v-if="environment?.deployed" class="bk-card">
      <header class="bk-card-head">
        <h2>Historique · {{ environment.applicationName }} · {{ environment.environmentName }}</h2>
        <span class="bk-muted">Conservées 7 jours ; points de restauration avant déploiement : 3 jours</span>
      </header>
      <p class="bk-explain">
        Les sauvegardes <strong>automatiques</strong> de la nuit couvrent <strong>tout le cluster</strong> (toutes les applications à la fois) :
        elles apparaissent dans chaque environnement. Les sauvegardes <strong>manuelles</strong> et <strong>avant déploiement</strong>
        ne couvrent que <strong>cet environnement</strong> (<code>{{ environment.namespace }}</code>).
      </p>
      <p v-if="loading && !backups.length" class="bk-muted">Chargement des sauvegardes…</p>
      <p v-else-if="!backups.length" class="bk-muted">Aucune sauvegarde pour le moment.</p>
      <div v-else class="bk-table-wrap">
        <table class="bk-table">
          <thead>
            <tr><th>Date</th><th>Type</th><th>Contenu</th><th>Résultat</th><th class="bk-right"><span class="sr-only">Actions</span></th></tr>
          </thead>
          <tbody>
            <tr v-for="backup in backups" :key="backup.name">
              <td :title="technical(backup)">
                <span class="bk-date">{{ date(backup.startedAt) === '—' ? 'Non démarrée' : date(backup.startedAt) }}</span>
                <small v-if="backup.startedAt">{{ relative(backup.startedAt) }}</small>
              </td>
              <td>{{ typeLabel(backup) }}<small v-if="typeDetail(backup)">{{ typeDetail(backup) }}</small></td>
              <td>
                <span class="bk-scope" :class="wholeCluster(backup) ? 'is-cluster' : 'is-env'" :title="scopeTitle(backup)">
                  {{ wholeCluster(backup) ? 'Tout le cluster' : 'Cet environnement' }}
                </span>
              </td>
              <td><span class="bk-phase" :class="`is-${tone(backup.phase)}`" :title="phaseHint(backup.phase) || undefined">{{ phaseLabel(backup.phase) }}</span></td>
              <td class="bk-right">
                <div class="bk-row-actions">
                  <template v-if="backup.phase === 'Completed'">
                    <button v-if="canManage" type="button" class="bk-btn bk-btn-sm" :disabled="busy" title="Restaurer dans une copie séparée, pour vérifier son contenu sans risque" @click="restore(backup, 'side')">Vérifier</button>
                    <button v-if="canReplace" type="button" class="bk-btn bk-btn-sm bk-btn-warn" :disabled="busy" title="Remettre l’environnement (application et données) dans l’état de cette sauvegarde" @click="restore(backup, 'replace')">Restaurer</button>
                  </template>
                  <button v-if="canManage" type="button" class="bk-btn bk-btn-sm bk-btn-ghost" :disabled="busy || !!deleteBlockedReason(backup)" :title="deleteBlockedReason(backup) || 'Supprimer cette sauvegarde et ses données'" @click="remove(backup)">Supprimer</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<style scoped>
.bk { display: flex; flex-direction: column; gap: 16px; }
.bk-head { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 12px; }
.bk-head h1 { margin: 0; font-size: 24px; }
.bk-sub { margin: 4px 0 0; color: #6b7280; font-size: 14px; }
.bk-select { min-width: 260px; padding: 8px 10px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; font: inherit; font-size: 14px; }
.bk-note, .bk-info { margin: 0; padding: 10px 14px; border: 1px solid #bfdbfe; border-radius: 10px; background: #eff6ff; color: #1e3a8a; font-size: 14px; }
.bk-error { margin: 0; padding: 10px 14px; border: 1px solid #fecaca; border-radius: 10px; background: #fef2f2; color: #991b1b; font-size: 14px; }
.bk-status { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 14px; padding: 16px 18px; border: 1px solid #e5e7eb; border-left-width: 5px; border-radius: 12px; background: #fff; }
.bk-status.is-ok { border-left-color: #16a34a; }
.bk-status.is-warning { border-left-color: #d97706; }
.bk-status.is-critical { border-left-color: #dc2626; }
.bk-status-main { display: flex; flex-direction: column; gap: 4px; font-size: 14px; color: #374151; }
.bk-status-main strong { color: #111827; font-size: 16px; }
.bk-status.is-ok strong { color: #166534; }
.bk-status.is-critical strong { color: #991b1b; }
.bk-status-facts { display: flex; flex-wrap: wrap; gap: 4px 18px; margin-top: 2px; color: #6b7280; font-size: 13px; }
.bk-status-facts b { color: #111827; font-weight: 600; }
.bk-restore { display: flex; flex-direction: column; gap: 4px; padding: 10px 14px; border: 1px solid #ddd6fe; border-radius: 10px; background: #f5f3ff; color: #4c1d95; font-size: 14px; }
.bk-card { padding: 16px 18px; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; }
.bk-card-head { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: 8px; margin-bottom: 8px; }
.bk-card-head h2 { margin: 0; font-size: 16px; }
.bk-muted { color: #6b7280; font-size: 13px; }
.bk-table-wrap { overflow-x: auto; }
.bk-table { width: 100%; border-collapse: collapse; font-size: 14px; }
.bk-table th { padding: 8px 10px; border-bottom: 1px solid #e5e7eb; color: #6b7280; font-size: 12px; font-weight: 600; letter-spacing: .03em; text-align: left; text-transform: uppercase; }
.bk-table td { padding: 11px 10px; border-bottom: 1px solid #f1f2f4; vertical-align: middle; }
.bk-table tbody tr:last-child td { border-bottom: 0; }
.bk-table tbody tr:hover td { background: #fafbff; }
.bk-table small { display: block; color: #6b7280; font-size: 12px; }
.bk-date { font-weight: 600; }
.bk-table .bk-right { text-align: right; }
.bk-explain { margin: 0 0 10px; padding: 8px 12px; border-radius: 8px; background: #f8fafc; color: #475569; font-size: 13px; line-height: 1.5; }
.bk-explain code { font-size: 12px; }
.bk-scope { display: inline-block; padding: 2px 9px; border-radius: 6px; font-size: 12px; font-weight: 600; cursor: help; }
.bk-scope.is-cluster { background: #f1f5f9; color: #334155; }
.bk-scope.is-env { background: #e0e7ff; color: #3730a3; }
.bk-phase { display: inline-block; padding: 2px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; cursor: default; }
.bk-phase.is-ok { background: #dcfce7; color: #166534; }
.bk-phase.is-pending { background: #dbeafe; color: #1e40af; }
.bk-phase.is-warn { background: #ffedd5; color: #9a3412; }
.bk-phase.is-ko { background: #fee2e2; color: #991b1b; }
.bk-row-actions { display: inline-flex; flex-wrap: wrap; justify-content: flex-end; gap: 6px; }
.bk-btn { padding: 8px 14px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #111827; font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; }
.bk-btn:hover:not(:disabled) { background: #f9fafb; }
.bk-btn:disabled { opacity: .45; cursor: not-allowed; }
.bk-btn-primary { border-color: #1e3a8a; background: #1e3a8a; color: #fff; }
.bk-btn-primary:hover:not(:disabled) { background: #1e40af; }
.bk-btn-sm { padding: 4px 10px; font-size: 12.5px; }
.bk-btn-warn { border-color: #fed7aa; color: #9a3412; }
.bk-btn-ghost { border-color: transparent; color: #b91c1c; }
.bk-btn-ghost:hover:not(:disabled) { background: #fef2f2; }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
</style>
