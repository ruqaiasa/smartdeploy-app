<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { checkCompatibility } from '@/api/portal.api'
import { createApplication } from '@/api/applications.api'
import CompatibilityResult from '@/components/portal/CompatibilityResult.vue'
import type { CompatibilityReport } from '@/types/portal'

/* Enregistrer une application en deux temps : vérifier le dépôt, puis le décrire. */
const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: []; created: [] }>()

const step = ref<1 | 2>(1)
const repo = reactive({ url: '', branch: 'main' })
const form = reactive({ name: '', description: '' })
const report = ref<CompatibilityReport | null>(null)
const busy = ref(false)
const error = ref('')

watch(() => props.open, open => {
  if (!open) return
  step.value = 1; repo.url = ''; repo.branch = 'main'; form.name = ''; form.description = ''; report.value = null; error.value = ''
})

function message(exception: unknown, fallback: string) {
  return (exception as { response?: { data?: { message?: string } } }).response?.data?.message ?? fallback
}
async function check() {
  busy.value = true; error.value = ''; report.value = null
  try {
    report.value = await checkCompatibility(repo.url.trim(), repo.branch.trim() || 'main')
    // Nom proposé : le nom du dépôt
    if (!form.name) form.name = report.value.repository.split('/').pop() ?? ''
  } catch (exception) { error.value = message(exception, 'Le dépôt n’a pas pu être vérifié.') } finally { busy.value = false }
}
const canContinue = computed(() => report.value !== null && report.value.verdict !== 'NOT_READY')
async function create() {
  busy.value = true; error.value = ''
  try {
    await createApplication({ name: form.name.trim(), description: form.description.trim(), repositoryUrl: repo.url.trim(), technology: 'SPRING_BOOT' })
    emit('created'); emit('close')
  } catch (exception) { error.value = message(exception, 'L’application n’a pas pu être enregistrée.') } finally { busy.value = false }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="nd-backdrop" @click.self="emit('close')">
      <div class="nd" role="dialog" aria-modal="true" aria-labelledby="nd-title">
        <header class="nd-head">
          <div>
            <p class="nd-kicker">Étape {{ step }} sur 2</p>
            <h2 id="nd-title">{{ step === 1 ? 'Vérifier votre dépôt' : 'Décrire votre application' }}</h2>
          </div>
          <button type="button" class="nd-close" aria-label="Fermer" @click="emit('close')">×</button>
        </header>

        <template v-if="step === 1">
          <p class="nd-muted">SmartDeploy vérifie que votre dépôt GitHub public contient ce que son pipeline attend.</p>
          <form class="nd-row" @submit.prevent="check">
            <input v-model="repo.url" type="url" required placeholder="https://github.com/equipe/projet" aria-label="Adresse du dépôt" />
            <input v-model="repo.branch" type="text" class="nd-branch" placeholder="main" aria-label="Branche" />
            <button type="submit" class="nd-btn" :disabled="busy">{{ busy ? 'Analyse…' : 'Vérifier' }}</button>
          </form>
          <CompatibilityResult v-if="report" :report="report" compact />
          <p v-if="report?.verdict === 'NOT_READY'" class="nd-muted">Corrigez les points bloquants dans votre dépôt, puis vérifiez à nouveau.</p>
        </template>

        <template v-else>
          <label class="nd-label" for="nd-name">Nom de l’application</label>
          <input id="nd-name" v-model="form.name" type="text" maxlength="150" required />
          <label class="nd-label" for="nd-desc">Description (facultative)</label>
          <textarea id="nd-desc" v-model="form.description" rows="3" maxlength="1000" />
          <p class="nd-muted">Dépôt : <code>{{ repo.url }}</code>. Vous en serez le propriétaire : vous seul (et l’équipe DevOps) la verrez.</p>
        </template>

        <p v-if="error" class="nd-error" role="alert">{{ error }}</p>

        <footer class="nd-foot">
          <button v-if="step === 2" type="button" class="nd-btn nd-btn-ghost" @click="step = 1">Retour</button>
          <button v-if="step === 1" type="button" class="nd-btn nd-btn-primary" :disabled="!canContinue" @click="step = 2">Continuer</button>
          <button v-else type="button" class="nd-btn nd-btn-primary" :disabled="busy || !form.name.trim()" @click="create">Enregistrer l’application</button>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.nd-backdrop { position: fixed; inset: 0; z-index: 60; display: grid; place-items: center; padding: 16px; background: rgba(15, 23, 42, .45); }
.nd { display: flex; flex-direction: column; gap: 12px; width: min(680px, 100%); max-height: 90vh; overflow-y: auto; padding: 18px 20px; border-radius: 14px; background: #fff; box-shadow: 0 20px 50px rgba(15, 23, 42, .25); }
.nd-head { display: flex; align-items: flex-start; justify-content: space-between; }
.nd-head h2 { margin: 0; font-size: 18px; }
.nd-kicker { margin: 0 0 2px; color: #4338ca; font-size: 12px; font-weight: 700; letter-spacing: .04em; text-transform: uppercase; }
.nd-close { border: 0; background: none; color: #6b7280; font-size: 22px; cursor: pointer; }
.nd-muted { margin: 0; color: #6b7280; font-size: 13.5px; }
.nd-row { display: flex; flex-wrap: wrap; gap: 8px; }
.nd-row input, .nd input, .nd textarea { padding: 9px 11px; border: 1px solid #e5e7eb; border-radius: 8px; font: inherit; font-size: 14px; }
.nd-row input { flex: 1; min-width: 220px; }
.nd-row .nd-branch { flex: 0 0 110px; min-width: 90px; }
.nd-label { margin-bottom: -6px; font-size: 13px; font-weight: 600; }
.nd-btn { padding: 8px 14px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #111827; font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; }
.nd-btn:disabled { opacity: .5; cursor: not-allowed; }
.nd-btn-primary { border-color: #1e3a8a; background: #1e3a8a; color: #fff; }
.nd-btn-ghost { border-color: transparent; }
.nd-foot { display: flex; justify-content: flex-end; gap: 8px; }
.nd-error { margin: 0; padding: 9px 12px; border: 1px solid #fecaca; border-radius: 8px; background: #fef2f2; color: #991b1b; font-size: 13.5px; }
</style>
