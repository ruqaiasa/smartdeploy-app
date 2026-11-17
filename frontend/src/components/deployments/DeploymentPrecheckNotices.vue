<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { getDeploymentPrecheck } from '@/api/deployments.api'
import type { DeploymentPrecheck } from '@/types/deployment'

/*
 * Avertissements avant le lancement d’un déploiement (règles RG-V4 à RG-V8) :
 * redéploiement de la version en service, version plus ancienne, dégradation passée,
 * échec répété sans changement de code, image réutilisée sans reconstruction.
 */
const props = defineProps<{ versionId: number | null; environmentId: number | null }>()
const confirmRedeploy = defineModel<boolean>('confirmRedeploy', { default: false })
const forceRebuild = defineModel<boolean>('forceRebuild', { default: false })
/** Vrai tant qu’une confirmation explicite manque (le lancement reste bloqué). */
const blocked = defineModel<boolean>('blocked', { default: false })

const precheck = ref<DeploymentPrecheck | null>(null)
const failed = ref(false)

const shortCommit = (commit: string | null | undefined) => (commit ? commit.slice(0, 7) : null)
const date = (value: string | null | undefined) =>
  value ? new Date(value).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : '—'

async function load() {
  precheck.value = null
  failed.value = false
  confirmRedeploy.value = false
  forceRebuild.value = false
  if (!props.versionId || !props.environmentId) return
  try {
    precheck.value = await getDeploymentPrecheck(props.versionId, props.environmentId)
  } catch {
    failed.value = true
  }
}
watch(() => [props.versionId, props.environmentId], load, { immediate: true })
watch([precheck, confirmRedeploy], () => {
  blocked.value = precheck.value?.sameVersionInService === true && !confirmRedeploy.value
}, { immediate: true })

const failureTone = computed(() => {
  const failure = precheck.value?.lastFailure
  if (!failure) return 'info'
  if (failure.codeCause && failure.unchangedSinceFailure === true) return 'danger'
  return failure.codeCause && failure.unchangedSinceFailure === null ? 'warning' : 'info'
})
</script>

<template>
  <div v-if="precheck" class="pn" aria-live="polite">
    <!-- RG-V5 : redéploiement de la version en service -->
    <div v-if="precheck.sameVersionInService" class="pn-box is-warning" role="alert">
      <strong>Cette version est déjà en service sur cet environnement</strong>
      <span>
        Déploiement #{{ precheck.currentDeploymentId }}, depuis le {{ date(precheck.currentSince) }}. Un redéploiement
        remplace les pods par le même code : utile pour appliquer une nouvelle configuration (ports, répliques),
        mais il n’apporte aucun changement à l’application.
      </span>
      <label class="pn-check">
        <input v-model="confirmRedeploy" type="checkbox" />
        Je veux quand même redéployer cette version.
      </label>
    </div>

    <!-- RG-V6 : version plus ancienne que celle en service -->
    <div v-if="precheck.olderThanCurrent" class="pn-box is-warning">
      <strong>Version plus ancienne que celle en service</strong>
      <span>
        La version {{ precheck.currentVersion }} est en service.
        <template v-if="precheck.rollbackTargetId">
          Pour revenir en arrière, préférez « Revenir ici » sur le déploiement #{{ precheck.rollbackTargetId }} :
          l’image exacte, en une trentaine de secondes, sans reconstruction.
        </template>
        <template v-else>Vérifiez qu’il s’agit bien d’un retour en arrière voulu.</template>
      </span>
    </div>

    <!-- RG-V7 : version déjà jugée dégradée -->
    <div v-if="precheck.degraded" class="pn-box is-danger">
      <strong>Version déjà jugée dégradée par le gardien</strong>
      <span>
        Le {{ date(precheck.degraded.at) }} sur {{ precheck.degraded.environmentName }}
        (déploiement #{{ precheck.degraded.deploymentId }}) : {{ precheck.degraded.cause }}.
      </span>
    </div>

    <!-- RG-V8 : dernier échec de la version -->
    <div v-if="precheck.lastFailure" class="pn-box" :class="`is-${failureTone}`">
      <strong>
        <template v-if="precheck.lastFailure.codeCause && precheck.lastFailure.unchangedSinceFailure === true">
          Ce déploiement échouera de la même façon
        </template>
        <template v-else>Cette version a déjà échoué</template>
      </strong>
      <span>
        Déploiement #{{ precheck.lastFailure.deploymentId }}, le {{ date(precheck.lastFailure.at) }} :
        {{ precheck.lastFailure.title }}<template v-if="precheck.lastFailure.detail"> ({{ precheck.lastFailure.detail }})</template>.
      </span>
      <span v-if="!precheck.lastFailure.codeCause">
        La cause était extérieure au code : une nouvelle tentative peut réussir.
      </span>
      <span v-else-if="precheck.lastFailure.unchangedSinceFailure === true">
        Le code n’a pas changé depuis cet échec. Corrigez-le, puis créez une nouvelle version.
      </span>
      <span v-else-if="precheck.lastFailure.unchangedSinceFailure === false">
        Le code a changé depuis : une nouvelle tentative est justifiée.
      </span>
      <span v-else>
        Impossible de vérifier si le code a changé depuis : {{ precheck.lastFailure.unverifiableReason ?? 'information indisponible' }}.
      </span>
    </div>

    <!-- RG-V4 : image réutilisée -->
    <div v-if="precheck.reuse" class="pn-box is-info">
      <strong>Image déjà construite : elle sera réutilisée</strong>
      <span>
        Construite lors du déploiement #{{ precheck.reuse.deploymentId }} ({{ precheck.reuse.environmentName
        }}<template v-if="precheck.reuse.commit">, commit {{ shortCommit(precheck.reuse.commit) }}</template>).
        Le déploiement réutilise exactement la même image, déjà testée et analysée par Trivy, sans reconstruction.
      </span>
      <label class="pn-check">
        <input v-model="forceRebuild" type="checkbox" />
        Reconstruire l’image quand même.
      </label>
    </div>
  </div>
  <p v-else-if="failed" class="pn-muted">La vérification préalable est indisponible : le déploiement reste possible.</p>
</template>

<style scoped>
.pn { display: flex; flex-direction: column; gap: 10px; }
.pn-box { display: flex; flex-direction: column; gap: 4px; padding: 11px 13px; border: 1px solid #e5e7eb; border-left-width: 4px; border-radius: 8px; font-size: 13.5px; line-height: 1.5; }
.pn-box strong { font-size: 14px; }
.pn-box.is-info { border-color: #bfdbfe; border-left-color: #2563eb; background: #eff6ff; color: #1e3a8a; }
.pn-box.is-warning { border-color: #fde68a; border-left-color: #d97706; background: #fffbeb; color: #78350f; }
.pn-box.is-danger { border-color: #fecaca; border-left-color: #dc2626; background: #fef2f2; color: #7f1d1d; }
.pn-check { display: flex; gap: 8px; align-items: center; margin-top: 4px; font-weight: 600; cursor: pointer; }
.pn-muted { margin: 0; color: #6b7280; font-size: 13px; }
</style>
