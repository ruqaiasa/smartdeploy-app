<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { BackupInfo } from '@/types/backup'
import type { DeploymentResponse } from '@/types/deployment'

/*
 * Confirmation de la restauration complète « état d'avant ce déploiement ».
 * Explique la différence avec le retour arrière : ici, le code ET les données
 * reviennent à l'instant du point de restauration ; les données écrites depuis sont perdues.
 */
const props = defineProps<{ open: boolean; deployment: DeploymentResponse | null; restorePoint: BackupInfo | null; loading: boolean }>()
const emit = defineEmits<{ close: []; confirm: [] }>()

const acknowledged = ref(false)
watch(() => props.open, () => { acknowledged.value = false })

const takenAt = computed(() => props.restorePoint?.completedAt
  ? new Date(props.restorePoint.completedAt).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' })
  : '—')
const protectedVersion = computed(() => props.restorePoint?.protectedVersion ?? 'la version précédente')
</script>

<template>
  <div v-if="open && deployment && restorePoint" class="rb-backdrop" @click.self="emit('close')">
    <div class="rb" role="dialog" aria-modal="true" aria-labelledby="rb-title">
      <h2 id="rb-title">Restaurer l’état d’avant le déploiement #{{ deployment.id }}</h2>
      <p class="rb-lead">
        L’environnement <strong>{{ deployment.environmentName }}</strong> va revenir exactement à son état du
        <strong>{{ takenAt }}</strong> : la version <strong>{{ protectedVersion }}</strong> et ses données.
      </p>

      <div class="rb-compare">
        <div class="rb-col">
          <h3>Revenir à une version</h3>
          <p>Bouton « Revenir ici » : <strong>le code</strong> revient, <strong>les données sont conservées</strong>. Environ 30 s.</p>
          <p class="rb-when">Quand la nouvelle version plante.</p>
        </div>
        <div class="rb-col is-selected">
          <h3>Restaurer l’état d’avant</h3>
          <p><strong>Le code et les données</strong> reviennent au {{ takenAt }}. Environ 1 à 2 min.</p>
          <p class="rb-when">Quand la nouvelle version a abîmé les données.</p>
        </div>
      </div>

      <p class="rb-warning">
        Les données écrites depuis le {{ takenAt }} seront <strong>perdues</strong>. L’application est indisponible pendant la restauration.
      </p>
      <label class="rb-ack">
        <input v-model="acknowledged" type="checkbox" />
        Je comprends que les données écrites depuis le point de restauration seront perdues.
      </label>

      <div class="rb-buttons">
        <button type="button" class="rb-btn" :disabled="loading" @click="emit('close')">Annuler</button>
        <button type="button" class="rb-btn rb-btn-danger" :disabled="!acknowledged || loading" @click="emit('confirm')">
          {{ loading ? 'Lancement…' : 'Restaurer l’état d’avant' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.rb-backdrop { position: fixed; inset: 0; z-index: 60; display: flex; align-items: center; justify-content: center; padding: 16px; background: rgba(15, 23, 42, .45); }
.rb { width: min(640px, 100%); max-height: 90vh; overflow: auto; padding: 22px 24px; border-radius: 14px; background: #fff; box-shadow: 0 20px 40px rgba(15, 23, 42, .25); }
.rb h2 { margin: 0 0 8px; font-size: 18px; }
.rb-lead { margin: 0 0 14px; font-size: 14px; line-height: 1.6; color: #334155; }
.rb-compare { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-bottom: 14px; }
.rb-col { padding: 12px; border: 1px solid #e5e7eb; border-radius: 10px; font-size: 13.5px; line-height: 1.5; color: #475569; }
.rb-col.is-selected { border-color: #0e9f8e; background: #f0fdfa; color: #134e4a; }
.rb-col h3 { margin: 0 0 6px; font-size: 14px; color: #0f172a; }
.rb-col p { margin: 0; }
.rb-when { margin-top: 6px !important; font-style: italic; }
.rb-warning { margin: 0 0 10px; padding: 10px 12px; border-radius: 8px; background: #fef2f2; color: #991b1b; font-size: 13.5px; line-height: 1.5; }
.rb-ack { display: flex; gap: 8px; align-items: flex-start; font-size: 13.5px; color: #334155; cursor: pointer; }
.rb-ack input { margin-top: 3px; }
.rb-buttons { display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px; }
.rb-btn { padding: 8px 14px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; font: inherit; font-size: 13.5px; font-weight: 600; cursor: pointer; }
.rb-btn:disabled { opacity: .55; cursor: not-allowed; }
.rb-btn-danger { border-color: #dc2626; background: #dc2626; color: #fff; }
@media (max-width: 560px) { .rb-compare { grid-template-columns: 1fr; } }
</style>
