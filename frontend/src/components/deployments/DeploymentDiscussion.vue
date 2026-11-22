<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { addComment, getTimeline } from '@/api/portal.api'
import type { TimelineItem } from '@/types/portal'

/*
 * Fil d’un déploiement : les messages des personnes et les événements de la plateforme, dans l’ordre,
 * comme une pull request. L’autre partie est prévenue par courriel à chaque message.
 */
const props = defineProps<{ deploymentId: number }>()

const items = ref<TimelineItem[]>([])
const message = ref('')
const loading = ref(false)
const sending = ref(false)
const error = ref('')
const list = ref<HTMLElement | null>(null)
const ROLES: Record<string, string> = { ADMIN: 'Administrateur', DEVOPS: 'DevOps', DEVELOPER: 'Développeur', VIEWER: 'Lecteur' }

async function load() {
  loading.value = true
  error.value = ''
  try {
    items.value = await getTimeline(props.deploymentId)
    await nextTick()
    list.value?.scrollTo({ top: list.value.scrollHeight })
  } catch { error.value = 'Le fil n’a pas pu être chargé.' }
  finally { loading.value = false }
}
async function send() {
  const text = message.value.trim()
  if (!text) return
  sending.value = true
  error.value = ''
  try { await addComment(props.deploymentId, text); message.value = ''; await load() }
  catch (exception: unknown) {
    error.value = (exception as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Le message n’a pas pu être envoyé.'
  } finally { sending.value = false }
}
function when(iso: string | null) {
  return iso ? new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) : ''
}
onMounted(load)
watch(() => props.deploymentId, load)
</script>

<template>
  <section class="dd" aria-label="Fil de la demande">
    <header class="dd-head">
      <strong>Fil</strong>
      <span>Notifications par courriel.</span>
    </header>
    <div ref="list" class="dd-list">
      <p v-if="loading && !items.length" class="dd-empty">Chargement…</p>
      <template v-for="(it, i) in items" :key="i">
        <p v-if="it.kind === 'EVENT'" class="dd-event" :class="`is-${it.tone ?? 'neutral'}`">
          <span class="dd-event-dot" /> {{ it.text }} <time>{{ when(it.at) }}</time>
        </p>
        <article v-else class="dd-msg" :class="{ 'is-mine': it.mine }">
          <div class="dd-meta">
            <strong>{{ it.mine ? 'Vous' : it.author }}</strong>
            <span v-if="it.authorRole && !it.mine" class="dd-role">{{ ROLES[it.authorRole] ?? it.authorRole }}</span>
            <time>{{ when(it.at) }}</time>
          </div>
          <p>{{ it.text }}</p>
        </article>
      </template>
    </div>
    <form class="dd-form" @submit.prevent="send">
      <textarea v-model="message" rows="2" maxlength="1000" placeholder="Écrire un message…" :disabled="sending" @keydown.enter.ctrl.prevent="send" />
      <button type="submit" class="dd-btn" :disabled="sending || !message.trim()">{{ sending ? 'Envoi…' : 'Envoyer' }}</button>
    </form>
    <p v-if="error" class="dd-error" role="alert">{{ error }}</p>
  </section>
</template>

<style scoped>
.dd { display: flex; flex-direction: column; gap: 8px; padding: 12px 14px; border: 1px solid #e5e7eb; border-radius: 10px; background: #fff; }
.dd-head { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: 6px; }
.dd-head span { color: #6b7280; font-size: 12px; }
.dd-list { display: flex; flex-direction: column; gap: 8px; max-height: 340px; overflow-y: auto; padding: 4px 2px; }
.dd-empty { margin: 0; color: #6b7280; font-size: 13px; }
.dd-event { display: flex; align-items: center; gap: 8px; margin: 0; color: #4b5563; font-size: 12.5px; }
.dd-event time { margin-left: auto; color: #9ca3af; }
.dd-event-dot { flex: 0 0 auto; width: 8px; height: 8px; border-radius: 999px; background: #9ca3af; }
.dd-event.is-ok .dd-event-dot { background: #16a34a; }
.dd-event.is-critical .dd-event-dot { background: #dc2626; }
.dd-event.is-analysis .dd-event-dot { background: #7c3aed; }
.dd-msg { align-self: flex-start; max-width: 85%; padding: 8px 12px; border-radius: 12px 12px 12px 4px; background: #f3f4f6; }
.dd-msg.is-mine { align-self: flex-end; border-radius: 12px 12px 4px 12px; background: #e0e7ff; }
.dd-msg p { margin: 2px 0 0; font-size: 14px; white-space: pre-wrap; word-break: break-word; }
.dd-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; font-size: 12px; color: #4b5563; }
.dd-role { padding: 0 6px; border-radius: 999px; background: #fff; color: #6d28d9; font-weight: 600; }
.dd-meta time { color: #9ca3af; }
.dd-form { display: flex; gap: 8px; align-items: flex-end; }
.dd-form textarea { flex: 1; padding: 8px 10px; border: 1px solid #e5e7eb; border-radius: 8px; font: inherit; font-size: 14px; resize: vertical; }
.dd-btn { padding: 8px 14px; border: 1px solid #1e3a8a; border-radius: 8px; background: #1e3a8a; color: #fff; font: inherit; font-size: 14px; font-weight: 600; cursor: pointer; }
.dd-btn:disabled { opacity: .5; cursor: not-allowed; }
.dd-error { margin: 0; color: #b91c1c; font-size: 13px; }
</style>
