<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ConfidenceScoreCard from '@/components/deployments/ConfidenceScoreCard.vue'
import DeploymentPrecheckNotices from '@/components/deployments/DeploymentPrecheckNotices.vue'
import type { DeploymentResponse } from '@/types/deployment'

/*
 * Décision d’approbation d’un déploiement en production (principe des quatre yeux).
 * L’approbateur voit le score de confiance et les avertissements avant de décider ;
 * un refus doit être motivé.
 */
const props = defineProps<{ open: boolean; deployment: DeploymentResponse | null; loading: boolean; error: string }>()
const emit = defineEmits<{ close: []; approve: [comment: string]; reject: [comment: string] }>()

const comment = ref('')
watch(() => props.open, () => { comment.value = '' })
const canReject = computed(() => comment.value.trim().length > 0)
const requestedAt = computed(() => props.deployment?.createdAt
  ? new Date(props.deployment.createdAt).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' })
  : '—')
</script>

<template>
  <div v-if="open && deployment" class="ad-backdrop" @click.self="emit('close')">
    <div class="ad" role="dialog" aria-modal="true" aria-labelledby="ad-title">
      <p class="ad-kicker">Approbation avant la production</p>
      <h2 id="ad-title">Déploiement #{{ deployment.id }} : {{ deployment.applicationName }} {{ deployment.version }}</h2>
      <p class="ad-lead">
        Demandé par <strong>{{ deployment.requestedByEmail }}</strong> le {{ requestedAt }},
        vers <strong>{{ deployment.environmentName }}</strong>. Rien n’a été lancé : le pipeline ne partira qu’après votre accord.
      </p>

      <ConfidenceScoreCard :version-id="deployment.versionId" :environment-id="deployment.environmentId" />
      <DeploymentPrecheckNotices :version-id="deployment.versionId" :environment-id="deployment.environmentId" readonly />

      <label class="ad-label" for="ad-comment">Commentaire <span>(obligatoire pour un refus)</span></label>
      <textarea id="ad-comment" v-model="comment" rows="3" maxlength="1000" placeholder="Par exemple : validée en test le 16/11, sans réserve du gardien." :disabled="loading" />

      <p v-if="error" class="ad-error" role="alert">{{ error }}</p>

      <div class="ad-buttons">
        <button type="button" class="ad-btn" :disabled="loading" @click="emit('close')">Fermer</button>
        <button type="button" class="ad-btn ad-btn-danger" :disabled="loading || !canReject"
          :title="canReject ? 'Refuser cette demande' : 'Indiquez la raison du refus'" @click="emit('reject', comment.trim())">Refuser</button>
        <button type="button" class="ad-btn ad-btn-primary" :disabled="loading" @click="emit('approve', comment.trim())">
          {{ loading ? 'Enregistrement…' : 'Approuver et lancer' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ad-backdrop { position: fixed; inset: 0; z-index: 60; display: flex; align-items: center; justify-content: center; padding: 16px; background: rgba(15, 23, 42, .45); }
.ad { width: min(680px, 100%); max-height: 92vh; overflow: auto; display: flex; flex-direction: column; gap: 12px; padding: 22px 24px; border-radius: 14px; background: #fff; box-shadow: 0 20px 40px rgba(15, 23, 42, .25); }
.ad-kicker { margin: 0; font-size: 12.5px; font-weight: 700; letter-spacing: .04em; text-transform: uppercase; color: #0e9f8e; }
.ad h2 { margin: 0; font-size: 18px; }
.ad-lead { margin: 0; font-size: 14px; line-height: 1.6; color: #334155; }
.ad-label { font-size: 13.5px; font-weight: 600; color: #334155; }
.ad-label span { font-weight: 400; color: #64748b; }
.ad textarea { padding: 10px 12px; border: 1px solid #cbd5e1; border-radius: 10px; font: inherit; font-size: 14px; resize: vertical; }
.ad-error { margin: 0; padding: 9px 11px; border-radius: 8px; background: #fef2f2; color: #991b1b; font-size: 13.5px; }
.ad-buttons { display: flex; justify-content: flex-end; flex-wrap: wrap; gap: 8px; }
.ad-btn { padding: 8px 14px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; font: inherit; font-size: 13.5px; font-weight: 600; cursor: pointer; }
.ad-btn:disabled { opacity: .55; cursor: not-allowed; }
.ad-btn-danger { border-color: #dc2626; color: #b91c1c; }
.ad-btn-primary { border-color: #0e9f8e; background: #0e9f8e; color: #fff; }
</style>
