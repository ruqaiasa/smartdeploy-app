<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { createEnvironment } from '@/api/environments.api'
import type { EnvironmentType } from '@/types/environment'

/*
 * RG-O2 : un développeur crée les environnements de développement et de test de ses applications.
 * La préproduction et la production restent créées par l’équipe DevOps.
 */
const props = defineProps<{ open: boolean; applicationId: number | null; applicationName: string; existingTypes: string[] }>()
const emit = defineEmits<{ close: []; created: [] }>()

const CHOICES: { type: EnvironmentType; label: string; suffix: string }[] = [
  { type: 'DEVELOPMENT', label: 'Développement', suffix: 'dev' },
  { type: 'TEST', label: 'Test', suffix: 'test' },
]
const available = computed(() => CHOICES.filter(c => !props.existingTypes.includes(c.type)))
const form = reactive({ type: 'DEVELOPMENT' as EnvironmentType, name: '', description: '' })
const busy = ref(false)
const error = ref('')

watch(() => props.open, open => {
  if (!open) return
  const first = available.value[0]
  form.type = first?.type ?? 'DEVELOPMENT'
  form.name = first ? `${props.applicationName}-${first.suffix}` : ''
  form.description = ''
  error.value = ''
})
watch(() => form.type, type => {
  const choice = CHOICES.find(c => c.type === type)
  if (choice) form.name = `${props.applicationName}-${choice.suffix}`
})

async function create() {
  if (!props.applicationId) return
  busy.value = true; error.value = ''
  try {
    await createEnvironment({ applicationId: props.applicationId, type: form.type, name: form.name.trim(), description: form.description.trim(), namespace: '', clusterUrl: '' })
    emit('created'); emit('close')
  } catch (exception) {
    error.value = (exception as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'L’environnement n’a pas pu être créé.'
  } finally { busy.value = false }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="ne-backdrop" @click.self="emit('close')">
      <form class="ne" role="dialog" aria-modal="true" @submit.prevent="create">
        <h2>Ajouter un environnement à {{ applicationName }}</h2>
        <template v-if="available.length">
          <label for="ne-type">Type</label>
          <select id="ne-type" v-model="form.type">
            <option v-for="c in available" :key="c.type" :value="c.type">{{ c.label }}</option>
          </select>
          <label for="ne-name">Nom</label>
          <input id="ne-name" v-model="form.name" required maxlength="100" />
          <label for="ne-desc">Description (facultative)</label>
          <input id="ne-desc" v-model="form.description" maxlength="500" />
          <p class="ne-muted">L’espace de noms Kubernetes est créé automatiquement. La préproduction et la production sont créées par l’équipe DevOps.</p>
        </template>
        <p v-else class="ne-muted">Votre application a déjà ses environnements de développement et de test. Pour la production, adressez-vous à l’équipe DevOps.</p>
        <p v-if="error" class="ne-error" role="alert">{{ error }}</p>
        <footer>
          <button type="button" class="ne-btn" @click="emit('close')">{{ available.length ? 'Annuler' : 'Fermer' }}</button>
          <button v-if="available.length" type="submit" class="ne-btn ne-primary" :disabled="busy || !form.name.trim()">Créer l’environnement</button>
        </footer>
      </form>
    </div>
  </Teleport>
</template>

<style scoped>
.ne-backdrop { position: fixed; inset: 0; z-index: 60; display: grid; place-items: center; padding: 16px; background: rgba(15, 23, 42, .45); }
.ne { display: flex; flex-direction: column; gap: 6px; width: min(460px, 100%); padding: 18px 20px; border-radius: 14px; background: #fff; box-shadow: 0 20px 50px rgba(15, 23, 42, .25); }
.ne h2 { margin: 0 0 6px; font-size: 17px; }
.ne label { font-size: 13px; font-weight: 600; }
.ne input, .ne select { padding: 9px 11px; border: 1px solid #e5e7eb; border-radius: 8px; font: inherit; font-size: 14px; background: #fff; }
.ne-muted { margin: 4px 0 0; color: #6b7280; font-size: 13px; }
.ne footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
.ne-btn { padding: 8px 14px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; }
.ne-primary { border-color: #1e3a8a; background: #1e3a8a; color: #fff; }
.ne-btn:disabled { opacity: .5; cursor: not-allowed; }
.ne-error { margin: 0; color: #b91c1c; font-size: 13px; }
</style>
