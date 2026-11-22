<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { createClaim } from '@/api/claims.api'
import { markHelpful, suggestKnowledge } from '@/api/knowledge.api'
import { getApplications } from '@/api/applications.api'
import { getEnvironments } from '@/api/environments.api'
import { CLAIM_TYPES } from '@/components/claims/claimLabels'
import type { ApplicationResponse } from '@/types/application'
import type { EnvironmentResponse } from '@/types/environment'
import type { ClaimPriority, ClaimType, KnowledgeArticle } from '@/types/portal'

/*
 * Ouvrir une réclamation : le contexte (application, environnement, déploiement, analyse) est joint
 * automatiquement par le serveur. Pendant la saisie du titre, la base de connaissances propose les
 * solutions déjà trouvées : beaucoup de réclamations se règlent ainsi sans intervention.
 */
const props = defineProps<{ open: boolean; applicationId?: number | null; deploymentId?: number | null }>()
const emit = defineEmits<{ close: []; created: [id: number] }>()

const form = reactive({ type: 'INCIDENT' as ClaimType, priority: 'HIGH' as ClaimPriority, title: '', description: '',
  applicationId: null as number | null, environmentId: null as number | null })
const apps = ref<ApplicationResponse[]>([])
const envs = ref<EnvironmentResponse[]>([])
const suggestions = ref<KnowledgeArticle[]>([])
const solved = ref(false)
const busy = ref(false)
const error = ref('')
let timer: ReturnType<typeof setTimeout> | null = null

watch(() => props.open, async open => {
  if (!open) return
  Object.assign(form, { type: 'INCIDENT', priority: 'HIGH', title: '', description: '', applicationId: props.applicationId ?? null, environmentId: null })
  suggestions.value = []
  solved.value = false
  error.value = ''
  try { apps.value = (await getApplications({ size: 100 })).content } catch { apps.value = [] }
  if (!form.applicationId && apps.value.length === 1) form.applicationId = apps.value[0]!.id
}, { immediate: true })
watch(() => form.applicationId, async id => {
  form.environmentId = null
  envs.value = []
  if (!id) return
  try { envs.value = (await getEnvironments({ size: 100 })).content.filter(e => e.applicationId === id) } catch { envs.value = [] }
})
watch(() => form.type, type => {
  form.priority = type === 'INCIDENT' || type === 'CONTESTED_FAILURE' ? 'HIGH' : 'NORMAL'
})
watch(() => form.title, title => {
  if (timer) clearTimeout(timer)
  if (title.trim().length < 6) { suggestions.value = []; return }
  timer = setTimeout(async () => {
    try { suggestions.value = await suggestKnowledge(`${title} ${form.description}`) } catch { suggestions.value = [] }
  }, 400)
})
const typeHint = computed(() => CLAIM_TYPES.find(t => t.value === form.type)?.hint ?? '')

async function useArticle(article: KnowledgeArticle) {
  await markHelpful(article.id).catch(() => undefined)
  solved.value = true
}
async function submit() {
  if (!form.applicationId || !form.title.trim()) return
  busy.value = true
  error.value = ''
  try {
    const claim = await createClaim({ type: form.type, priority: form.priority, title: form.title.trim(), description: form.description.trim(),
      applicationId: form.applicationId, environmentId: form.environmentId, deploymentId: props.deploymentId ?? null })
    emit('created', claim.id)
  } catch (exception) {
    error.value = (exception as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'La réclamation n’a pas pu être envoyée.'
  } finally { busy.value = false }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="nc-backdrop" @click.self="emit('close')">
      <div class="nc" role="dialog" aria-modal="true" aria-labelledby="nc-title">
        <header class="nc-head">
          <h2 id="nc-title">Nouvelle réclamation</h2>
          <button type="button" class="nc-close" aria-label="Fermer" @click="emit('close')">×</button>
        </header>

        <p v-if="solved" class="nc-solved">Problème résolu : rien n’a été envoyé.</p>

        <form v-else class="nc-form" @submit.prevent="submit">
          <div class="nc-row">
            <label>Type
              <select v-model="form.type"><option v-for="t in CLAIM_TYPES" :key="t.value" :value="t.value">{{ t.label }}</option></select>
            </label>
            <label>Priorité
              <select v-model="form.priority">
                <option value="CRITICAL">Critique</option>
                <option value="HIGH">Haute</option>
                <option value="NORMAL">Normale</option>
              </select>
            </label>
          </div>
          <p class="nc-hint">{{ typeHint }}</p>
          <div class="nc-row">
            <label>Application
              <select v-model.number="form.applicationId" required>
                <option :value="null" disabled>Choisir</option>
                <option v-for="a in apps" :key="a.id" :value="a.id">{{ a.name }}</option>
              </select>
            </label>
            <label>Environnement (facultatif)
              <select v-model.number="form.environmentId">
                <option :value="null">—</option>
                <option v-for="e in envs" :key="e.id" :value="e.id">{{ e.name }}</option>
              </select>
            </label>
          </div>
          <label>Titre
            <input v-model="form.title" required maxlength="200" placeholder="Erreurs 500 en test" />
          </label>

          <div v-if="suggestions.length" class="nc-kb">
            <strong>Solutions connues</strong>
            <article v-for="a in suggestions" :key="a.id">
              <p class="nc-kb-title">{{ a.title }} <span v-if="a.score">· {{ a.score }} %</span></p>
              <p>{{ a.solution }}</p>
              <button type="button" class="nc-link" @click="useArticle(a)">Ça résout mon problème</button>
            </article>
          </div>

          <label>Description
            <textarea v-model="form.description" rows="4" maxlength="4000" placeholder="Ce qui s’est passé" />
          </label>
          <p class="nc-hint">Contexte joint automatiquement.</p>
          <p v-if="error" class="nc-error" role="alert">{{ error }}</p>
          <footer>
            <button type="button" class="nc-btn" @click="emit('close')">Annuler</button>
            <button type="submit" class="nc-btn nc-primary" :disabled="busy || !form.applicationId || !form.title.trim()">Envoyer</button>
          </footer>
        </form>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.nc-backdrop { position: fixed; inset: 0; z-index: 60; display: grid; place-items: center; padding: 16px; background: rgba(15, 23, 42, .45); }
.nc { display: flex; flex-direction: column; gap: 10px; width: min(640px, 100%); max-height: 92vh; overflow-y: auto; padding: 18px 20px; border-radius: 14px; background: #fff; box-shadow: 0 20px 50px rgba(15, 23, 42, .25); }
.nc-head { display: flex; justify-content: space-between; align-items: center; }
.nc-head h2 { margin: 0; font-size: 18px; }
.nc-close { border: 0; background: none; color: #6b7280; font-size: 22px; cursor: pointer; }
.nc-form { display: flex; flex-direction: column; gap: 10px; }
.nc-form label { display: flex; flex: 1; flex-direction: column; gap: 4px; font-size: 13px; font-weight: 600; }
.nc-form input, .nc-form select, .nc-form textarea { padding: 9px 11px; border: 1px solid #e5e7eb; border-radius: 8px; font: inherit; font-size: 14px; font-weight: 400; background: #fff; }
.nc-row { display: flex; flex-wrap: wrap; gap: 10px; }
.nc-hint { margin: -4px 0 0; color: #6b7280; font-size: 12.5px; }
.nc-kb { display: flex; flex-direction: column; gap: 8px; padding: 10px 12px; border: 1px solid #bbf7d0; border-radius: 10px; background: #f0fdf4; font-size: 13.5px; }
.nc-kb article { padding-top: 6px; border-top: 1px dashed #bbf7d0; }
.nc-kb p { margin: 2px 0; }
.nc-kb-title { font-weight: 600; }
.nc-link { padding: 0; border: 0; background: none; color: #166534; font: inherit; font-weight: 700; cursor: pointer; text-decoration: underline; }
.nc-solved { margin: 0; padding: 14px; border: 1px solid #bbf7d0; border-radius: 10px; background: #f0fdf4; color: #166534; }
.nc-form footer { display: flex; justify-content: flex-end; gap: 8px; }
.nc-btn { padding: 8px 14px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; }
.nc-primary { border-color: #1e3a8a; background: #1e3a8a; color: #fff; }
.nc-btn:disabled { opacity: .5; cursor: not-allowed; }
.nc-error { margin: 0; color: #b91c1c; font-size: 13px; }
</style>
