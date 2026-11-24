<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { getMeetingContacts, proposeMeeting, type MeetingContact } from '@/api/meetings.api'

/* Proposer une visioconférence : un sujet, un interlocuteur, jusqu’à trois créneaux. */
const props = withDefaults(defineProps<{ open: boolean; deploymentId?: number | null; claimId?: number | null; defaultTopic?: string }>(),
  { deploymentId: null, claimId: null, defaultTopic: '' })
const emit = defineEmits<{ close: []; proposed: [] }>()

const contacts = ref<MeetingContact[]>([])
const form = reactive({ topic: '', agenda: '', inviteeId: null as number | null, slots: ['', '', ''], duration: 30 })
const busy = ref(false)
const error = ref('')
const ROLE: Record<string, string> = { ADMIN: 'Administrateur', DEVOPS: 'DevOps', DEVELOPER: 'Développeur' }

function nextSlot(daysAhead: number, hour: number) {
  const d = new Date()
  d.setDate(d.getDate() + daysAhead)
  d.setHours(hour, 0, 0, 0)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:00`
}
watch(() => props.open, async open => {
  if (!open) return
  Object.assign(form, { topic: props.defaultTopic, agenda: '', inviteeId: null, slots: [nextSlot(1, 10), nextSlot(1, 14), nextSlot(2, 10)], duration: 30 })
  error.value = ''
  contacts.value = await getMeetingContacts().catch(() => [])
  if (contacts.value.length === 1) form.inviteeId = contacts.value[0]!.id
}, { immediate: true })

async function submit() {
  if (!form.inviteeId || !form.topic.trim()) return
  busy.value = true
  error.value = ''
  try {
    await proposeMeeting({ topic: form.topic.trim(), agenda: form.agenda.trim(), inviteeId: form.inviteeId, deploymentId: props.deploymentId,
      claimId: props.claimId, slots: form.slots.filter(Boolean).map(s => (s.length === 16 ? `${s}:00` : s)), durationMinutes: form.duration })
    emit('proposed')
  } catch (exception) {
    error.value = (exception as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'La proposition n’a pas pu être envoyée.'
  } finally { busy.value = false }
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="pmd-backdrop" @click.self="emit('close')">
      <form class="pmd" role="dialog" aria-modal="true" aria-labelledby="pmd-title" @submit.prevent="submit">
        <header><h2 id="pmd-title">Proposer une visioconférence</h2><button type="button" class="pmd-x" aria-label="Fermer" @click="emit('close')">×</button></header>
        <label>Sujet<input v-model="form.topic" required maxlength="200" placeholder="Point sur la mise en production" /></label>
        <label>Avec
          <select v-model.number="form.inviteeId" required>
            <option :value="null" disabled>Choisir</option>
            <option v-for="c in contacts" :key="c.id" :value="c.id">{{ c.name }} · {{ ROLE[c.role ?? ''] ?? c.role }}</option>
          </select>
        </label>
        <fieldset>
          <legend>Créneaux proposés (jusqu’à trois)</legend>
          <input v-for="(_, i) in form.slots" :key="i" v-model="form.slots[i]" type="datetime-local" :required="i === 0" />
        </fieldset>
        <label>Durée
          <select v-model.number="form.duration"><option :value="15">15 min</option><option :value="30">30 min</option><option :value="45">45 min</option><option :value="60">1 h</option></select>
        </label>
        <label>Ordre du jour (facultatif)<textarea v-model="form.agenda" rows="3" maxlength="1000" /></label>
        <p v-if="deploymentId" class="pmd-hint">Lié au déploiement #{{ deploymentId }}.</p>
        <p v-if="error" class="pmd-error">{{ error }}</p>
        <footer><button type="button" class="pmd-btn" @click="emit('close')">Annuler</button><button type="submit" class="pmd-btn pmd-primary" :disabled="busy || !form.inviteeId">Envoyer la proposition</button></footer>
      </form>
    </div>
  </Teleport>
</template>

<style scoped>
.pmd-backdrop { position: fixed; inset: 0; z-index: 70; display: grid; place-items: center; padding: 16px; background: rgba(15, 23, 42, .45); }
.pmd { display: flex; flex-direction: column; gap: 10px; width: min(520px, 100%); max-height: 92vh; overflow-y: auto; padding: 18px 20px; border-radius: 16px; background: #fff; box-shadow: 0 24px 60px rgba(15, 23, 42, .25); }
.pmd header { display: flex; align-items: center; justify-content: space-between; }
.pmd h2 { margin: 0; font-size: 18px; }
.pmd-x { border: 0; background: none; color: #64748b; font-size: 22px; cursor: pointer; }
.pmd label, .pmd fieldset { display: flex; flex-direction: column; gap: 5px; font-size: 13px; font-weight: 700; color: #334155; }
.pmd fieldset { border: 0; margin: 0; padding: 0; }
.pmd legend { margin-bottom: 5px; }
.pmd input, .pmd select, .pmd textarea { padding: 9px 11px; border: 1px solid #e2e8f0; border-radius: 10px; font: inherit; font-size: 14px; font-weight: 400; background: #fff; }
.pmd-hint { margin: 0; color: #64748b; font-size: 12.5px; }
.pmd-error { margin: 0; color: #b91c1c; font-size: 13px; }
.pmd footer { display: flex; justify-content: flex-end; gap: 8px; }
.pmd-btn { padding: 9px 14px; border: 1px solid #e2e8f0; border-radius: 10px; background: #fff; font: inherit; font-weight: 700; cursor: pointer; }
.pmd-primary { border-color: #1e3a8a; background: #1e3a8a; color: #fff; }
.pmd-btn:disabled { opacity: .5; cursor: not-allowed; }
</style>
