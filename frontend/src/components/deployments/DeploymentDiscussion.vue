<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { addComment, getComments } from '@/api/portal.api'
import type { DeploymentComment } from '@/types/portal'

/* Discussion d’une demande : le demandeur et l’équipe DevOps échangent ; l’autre partie est prévenue par e-mail. */
const props = defineProps<{ deploymentId: number }>()

const comments = ref<DeploymentComment[]>([])
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
    comments.value = await getComments(props.deploymentId)
    await nextTick()
    list.value?.scrollTo({ top: list.value.scrollHeight })
  } catch {
    error.value = 'La discussion n’a pas pu être chargée.'
  } finally {
    loading.value = false
  }
}

async function send() {
  const text = message.value.trim()
  if (!text) return
  sending.value = true
  error.value = ''
  try {
    comments.value.push(await addComment(props.deploymentId, text))
    message.value = ''
    await nextTick()
    list.value?.scrollTo({ top: list.value.scrollHeight, behavior: 'smooth' })
  } catch (exception: unknown) {
    const response = (exception as { response?: { data?: { message?: string } } }).response
    error.value = response?.data?.message ?? 'Le message n’a pas pu être envoyé.'
  } finally {
    sending.value = false
  }
}

function when(iso: string) {
  return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' })
}

onMounted(load)
watch(() => props.deploymentId, load)
</script>

<template>
  <section class="dd" aria-label="Discussion">
    <header class="dd-head">
      <strong>Discussion</strong>
      <span>L’autre partie est prévenue par e-mail à chaque message.</span>
    </header>
    <div ref="list" class="dd-list">
      <p v-if="loading && !comments.length" class="dd-empty">Chargement…</p>
      <p v-else-if="!comments.length" class="dd-empty">Aucun message pour l’instant. Posez une question ou ajoutez une précision.</p>
      <article v-for="c in comments" :key="c.id" class="dd-msg" :class="{ 'is-mine': c.mine }">
        <div class="dd-meta">
          <strong>{{ c.mine ? 'Vous' : c.authorName }}</strong>
          <span v-if="c.authorRole && !c.mine" class="dd-role">{{ ROLES[c.authorRole] ?? c.authorRole }}</span>
          <time>{{ when(c.createdAt) }}</time>
        </div>
        <p>{{ c.message }}</p>
      </article>
    </div>
    <form class="dd-form" @submit.prevent="send">
      <textarea v-model="message" rows="2" maxlength="1000" placeholder="Écrire un message…" :disabled="sending"
        @keydown.enter.ctrl.prevent="send" />
      <button type="submit" class="dd-btn" :disabled="sending || !message.trim()">{{ sending ? 'Envoi…' : 'Envoyer' }}</button>
    </form>
    <p v-if="error" class="dd-error" role="alert">{{ error }}</p>
  </section>
</template>

<style scoped>
.dd { display: flex; flex-direction: column; gap: 8px; padding: 12px 14px; border: 1px solid #e5e7eb; border-radius: 10px; background: #fff; }
.dd-head { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: 6px; }
.dd-head span { color: #6b7280; font-size: 12px; }
.dd-list { display: flex; flex-direction: column; gap: 8px; max-height: 240px; overflow-y: auto; padding: 4px 2px; }
.dd-empty { margin: 0; color: #6b7280; font-size: 13px; }
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
