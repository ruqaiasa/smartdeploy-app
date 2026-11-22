<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { createVersion } from '@/api/versions.api'

const props = defineProps<{ open: boolean; applicationId: number | null; applicationName: string }>()
const emit = defineEmits<{ close: []; created: [] }>()

const form = reactive({ version: '', branch: 'main', commit: '' })
const busy = ref(false)
const error = ref('')
watch(() => props.open, open => { if (open) { form.version = ''; form.branch = 'main'; form.commit = ''; error.value = '' } })

async function create() {
  if (!props.applicationId) return
  busy.value = true; error.value = ''
  try {
    await createVersion({ applicationId: props.applicationId, version: form.version.trim(), gitBranch: form.branch.trim(), gitCommit: form.commit.trim(), dockerImage: '', artifactPath: '', buildLogsUrl: '' })
    emit('created'); emit('close')
  } catch (exception) {
    error.value = (exception as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'La version n’a pas pu être créée.'
  } finally { busy.value = false }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="nv-backdrop" @click.self="emit('close')">
      <form class="nv" role="dialog" aria-modal="true" @submit.prevent="create">
        <h2>Nouvelle version · {{ applicationName }}</h2>
        <label for="nv-version">Numéro</label>
        <input id="nv-version" v-model="form.version" required placeholder="1.2.0" />
        <label for="nv-branch">Branche Git</label>
        <input id="nv-branch" v-model="form.branch" required />
        <label for="nv-commit">Commit (facultatif)</label>
        <input id="nv-commit" v-model="form.commit" placeholder="dernier commit par défaut" />
        <p v-if="error" class="nv-error" role="alert">{{ error }}</p>
        <footer>
          <button type="button" class="nv-btn" @click="emit('close')">Annuler</button>
          <button type="submit" class="nv-btn nv-primary" :disabled="busy || !form.version.trim()">Créer</button>
        </footer>
      </form>
    </div>
  </Teleport>
</template>

<style scoped>
.nv-backdrop { position: fixed; inset: 0; z-index: 60; display: grid; place-items: center; padding: 16px; background: rgba(15, 23, 42, .45); }
.nv { display: flex; flex-direction: column; gap: 6px; width: min(440px, 100%); padding: 18px 20px; border-radius: 14px; background: #fff; box-shadow: 0 20px 50px rgba(15, 23, 42, .25); }
.nv h2 { margin: 0 0 6px; font-size: 17px; }
.nv label { font-size: 13px; font-weight: 600; }
.nv input { padding: 9px 11px; border: 1px solid #e5e7eb; border-radius: 8px; font: inherit; font-size: 14px; }
.nv footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }
.nv-btn { padding: 8px 14px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; }
.nv-primary { border-color: #1e3a8a; background: #1e3a8a; color: #fff; }
.nv-btn:disabled { opacity: .5; cursor: not-allowed; }
.nv-error { margin: 0; color: #b91c1c; font-size: 13px; }
</style>
