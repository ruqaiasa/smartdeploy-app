<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { confirmClaim, createClaim, getClaimThread, getClaims, postClaimMessage, reopenClaim } from '@/api/claims.api'
import { getApplications } from '@/api/applications.api'
import { markHelpful, suggestKnowledge } from '@/api/knowledge.api'
import { claimsWidget } from '@/components/developer/claimsWidget'
import { CLAIM_TYPES, STATUS_LABEL, STATUS_TONE, age } from '@/components/claims/claimLabels'
import type { ApplicationResponse } from '@/types/application'
import type { Claim, ClaimMessage, ClaimType, KnowledgeArticle } from '@/types/portal'

/* Réclamations du développeur, dans une bulle en bas à droite, comme une messagerie d’assistance. */
type Screen = 'list' | 'new' | 'thread'
const screen = ref<Screen>('list')
const claims = ref<Claim[]>([])
const current = ref<Claim | null>(null)
const thread = ref<ClaimMessage[]>([])
const message = ref('')
const busy = ref(false)
const error = ref('')
const apps = ref<ApplicationResponse[]>([])
const suggestions = ref<KnowledgeArticle[]>([])
const solved = ref(false)
const form = reactive({ type: 'INCIDENT' as ClaimType, title: '', description: '', applicationId: null as number | null })
const body = ref<HTMLElement | null>(null)
let timer: ReturnType<typeof setTimeout> | null = null

const waiting = computed(() => claims.value.filter(c => c.waitingFor === 'REQUESTER').length)

async function loadClaims() { try { claims.value = await getClaims() } catch { claims.value = [] } }
onMounted(() => { void loadClaims(); setInterval(loadClaims, 60000) })

watch(() => claimsWidget.open, async open => {
  if (!open) return
  await loadClaims()
  if (claimsWidget.prefill) await startNew()
  else screen.value = 'list'
})

async function startNew() {
  screen.value = 'new'
  solved.value = false
  error.value = ''
  suggestions.value = []
  Object.assign(form, { type: 'INCIDENT', title: '', description: '', applicationId: claimsWidget.prefill?.applicationId ?? null })
  try { apps.value = (await getApplications({ size: 100 })).content } catch { apps.value = [] }
  if (!form.applicationId && apps.value.length === 1) form.applicationId = apps.value[0]!.id
}
watch(() => form.title, title => {
  if (timer) clearTimeout(timer)
  if (title.trim().length < 6) { suggestions.value = []; return }
  timer = setTimeout(async () => { try { suggestions.value = await suggestKnowledge(title) } catch { suggestions.value = [] } }, 400)
})
async function useArticle(a: KnowledgeArticle) { await markHelpful(a.id).catch(() => undefined); solved.value = true }

async function send() {
  if (!form.applicationId || !form.title.trim()) return
  busy.value = true
  error.value = ''
  try {
    const claim = await createClaim({ type: form.type, priority: form.type === 'INCIDENT' ? 'HIGH' : 'NORMAL', title: form.title.trim(),
      description: form.description.trim(), applicationId: form.applicationId, environmentId: null, deploymentId: claimsWidget.prefill?.deploymentId ?? null })
    claimsWidget.prefill = null
    await loadClaims()
    await openThread(claim)
  } catch (exception) { error.value = (exception as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'Envoi impossible.' }
  finally { busy.value = false }
}
async function openThread(c: Claim) {
  current.value = c
  screen.value = 'thread'
  thread.value = await getClaimThread(c.id).catch(() => [])
  await nextTick()
  body.value?.scrollTo({ top: body.value.scrollHeight })
}
async function reply() {
  if (!current.value || !message.value.trim()) return
  busy.value = true
  try { await postClaimMessage(current.value.id, message.value.trim(), false); message.value = ''; await refreshThread() }
  finally { busy.value = false }
}
async function refreshThread() {
  if (!current.value) return
  await loadClaims()
  current.value = claims.value.find(c => c.id === current.value!.id) ?? current.value
  thread.value = await getClaimThread(current.value.id).catch(() => [])
  await nextTick()
  body.value?.scrollTo({ top: body.value.scrollHeight })
}
async function confirm() { if (current.value) { await confirmClaim(current.value.id).catch(() => undefined); await refreshThread() } }
async function reopen() { if (current.value) { await reopenClaim(current.value.id, '').catch(() => undefined); await refreshThread() } }
function back() { screen.value = 'list'; current.value = null; claimsWidget.prefill = null }
function when(iso: string) { return new Date(iso).toLocaleString('fr-FR', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' }) }
</script>

<template>
  <div class="cw">
    <Transition name="cw-pop">
      <section v-if="claimsWidget.open" class="cw-panel" role="dialog" aria-label="Réclamations">
        <header class="cw-head">
          <button v-if="screen !== 'list'" type="button" class="cw-icon" aria-label="Retour" @click="back">‹</button>
          <div>
            <strong>{{ screen === 'new' ? 'Nouvelle réclamation' : screen === 'thread' ? current?.title : 'Assistance' }}</strong>
            <span v-if="screen === 'thread' && current">{{ STATUS_LABEL[current.status] }}</span>
            <span v-else>Équipe DevOps</span>
          </div>
          <button type="button" class="cw-icon" aria-label="Fermer" @click="claimsWidget.open = false">×</button>
        </header>

        <!-- Liste -->
        <div v-if="screen === 'list'" class="cw-body">
          <button type="button" class="cw-new" @click="startNew">+ Nouvelle réclamation</button>
          <p v-if="!claims.length" class="cw-empty">Aucune réclamation.</p>
          <button v-for="c in claims" :key="c.id" type="button" class="cw-item" @click="openThread(c)">
            <span class="cw-item-title">{{ c.title }}</span>
            <span class="cw-item-meta">
              <span class="cw-pill" :class="`is-${STATUS_TONE[c.status]}`">{{ STATUS_LABEL[c.status] }}</span>
              {{ age(c.updatedAt ?? c.createdAt) }}
            </span>
          </button>
        </div>

        <!-- Nouvelle -->
        <div v-else-if="screen === 'new'" class="cw-body">
          <p v-if="solved" class="cw-solved">Problème résolu, rien n’a été envoyé.</p>
          <form v-else class="cw-form" @submit.prevent="send">
            <div class="cw-types">
              <button v-for="t in CLAIM_TYPES" :key="t.value" type="button" :class="{ 'is-active': form.type === t.value }" @click="form.type = t.value">{{ t.label }}</button>
            </div>
            <select v-model.number="form.applicationId" required aria-label="Application">
              <option :value="null" disabled>Application</option>
              <option v-for="a in apps" :key="a.id" :value="a.id">{{ a.name }}</option>
            </select>
            <input v-model="form.title" required maxlength="200" placeholder="Titre" />
            <div v-if="suggestions.length" class="cw-kb">
              <strong>Solutions connues</strong>
              <article v-for="a in suggestions" :key="a.id">
                <p class="cw-kb-title">{{ a.title }}</p>
                <p>{{ a.solution }}</p>
                <button type="button" @click="useArticle(a)">Ça résout mon problème</button>
              </article>
            </div>
            <textarea v-model="form.description" rows="4" maxlength="4000" placeholder="Ce qui s’est passé" />
            <p v-if="claimsWidget.prefill?.deploymentId" class="cw-hint">Déploiement #{{ claimsWidget.prefill.deploymentId }} joint</p>
            <p v-if="error" class="cw-error">{{ error }}</p>
            <button type="submit" class="cw-send" :disabled="busy || !form.applicationId || !form.title.trim()">Envoyer</button>
          </form>
        </div>

        <!-- Conversation -->
        <template v-else-if="current">
          <div ref="body" class="cw-body cw-thread">
            <p v-if="current.description" class="cw-desc">{{ current.description }}</p>
            <template v-for="m in thread" :key="m.id">
              <p v-if="m.systemEvent" class="cw-event">{{ m.message }}</p>
              <div v-else class="cw-msg" :class="{ 'is-mine': m.mine }">
                <p>{{ m.message }}</p>
                <time>{{ m.mine ? 'Vous' : m.authorName }} · {{ when(m.createdAt) }}</time>
              </div>
            </template>
            <div v-if="current.status === 'RESOLVED'" class="cw-resolved">
              <p><strong>Résolue :</strong> {{ current.resolution }}</p>
              <div><button type="button" class="cw-send" @click="confirm">Confirmer</button><button type="button" class="cw-ghost" @click="reopen">Rouvrir</button></div>
            </div>
          </div>
          <form v-if="current.status !== 'CLOSED'" class="cw-reply" @submit.prevent="reply">
            <input v-model="message" placeholder="Votre message" aria-label="Message" />
            <button type="submit" :disabled="busy || !message.trim()" aria-label="Envoyer">➤</button>
          </form>
        </template>
      </section>
    </Transition>

    <button type="button" class="cw-launcher" :aria-label="claimsWidget.open ? 'Fermer' : 'Assistance'" @click="claimsWidget.open = !claimsWidget.open">
      <svg v-if="!claimsWidget.open" viewBox="0 0 24 24" width="26" height="26" aria-hidden="true"><path d="M4 5h16v11H9l-5 4z" fill="none" stroke="currentColor" stroke-width="2" stroke-linejoin="round" /></svg>
      <span v-else class="cw-x">×</span>
      <span v-if="waiting && !claimsWidget.open" class="cw-badge">{{ waiting }}</span>
    </button>
  </div>
</template>

<style scoped>
.cw { position: fixed; right: 22px; bottom: 22px; z-index: 80; display: flex; flex-direction: column; align-items: flex-end; gap: 12px; }
.cw-launcher { position: relative; display: grid; place-items: center; width: 58px; height: 58px; border: 0; border-radius: 999px; background: linear-gradient(135deg, #1e3a8a, #4f46e5); color: #fff; box-shadow: 0 10px 25px rgba(79, 70, 229, .35); cursor: pointer; }
.cw-x { font-size: 28px; line-height: 1; }
.cw-badge { position: absolute; top: -2px; right: -2px; display: grid; place-items: center; min-width: 20px; height: 20px; padding: 0 5px; border-radius: 999px; background: #dc2626; color: #fff; font-size: 11.5px; font-weight: 800; }
.cw-panel { display: flex; flex-direction: column; width: min(380px, calc(100vw - 32px)); height: min(560px, calc(100vh - 120px)); overflow: hidden; border-radius: 18px; background: #fff; box-shadow: 0 24px 60px rgba(15, 23, 42, .28); }
.cw-head { display: flex; align-items: center; gap: 10px; padding: 14px 14px 14px 16px; background: linear-gradient(135deg, #1e3a8a, #4f46e5); color: #fff; }
.cw-head div { display: flex; flex: 1; flex-direction: column; min-width: 0; }
.cw-head strong { overflow: hidden; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.cw-head span { font-size: 12px; opacity: .85; }
.cw-icon { border: 0; background: rgba(255, 255, 255, .18); color: #fff; width: 30px; height: 30px; border-radius: 8px; font-size: 20px; line-height: 1; cursor: pointer; }
.cw-body { display: flex; flex: 1; flex-direction: column; gap: 8px; overflow-y: auto; padding: 14px; background: #f8faff; }
.cw-new { padding: 11px; border: 1px dashed #c7d2fe; border-radius: 12px; background: #fff; color: #1d4ed8; font: inherit; font-weight: 700; cursor: pointer; }
.cw-empty { margin: 8px 0; color: #6b7280; font-size: 13.5px; text-align: center; }
.cw-item { display: flex; flex-direction: column; gap: 5px; padding: 11px 12px; border: 1px solid #e0e7ff; border-radius: 12px; background: #fff; text-align: left; font: inherit; cursor: pointer; }
.cw-item:hover { border-color: #c7d2fe; }
.cw-item-title { color: #111827; font-size: 14px; font-weight: 600; }
.cw-item-meta { display: flex; align-items: center; gap: 8px; color: #6b7280; font-size: 12px; }
.cw-pill { padding: 1px 8px; border-radius: 999px; font-size: 11px; font-weight: 700; }
.cw-pill.is-critical { background: #fee2e2; color: #991b1b; }
.cw-pill.is-running { background: #dbeafe; color: #1e40af; }
.cw-pill.is-approval { background: #e0e7ff; color: #1e40af; }
.cw-pill.is-ok { background: #dcfce7; color: #166534; }
.cw-pill.is-muted { background: #f3f4f6; color: #4b5563; }
.cw-form { display: flex; flex-direction: column; gap: 8px; }
.cw-types { display: flex; flex-wrap: wrap; gap: 6px; }
.cw-types button { padding: 5px 10px; border: 1px solid #e5e7eb; border-radius: 999px; background: #fff; font: inherit; font-size: 12.5px; font-weight: 600; cursor: pointer; }
.cw-types .is-active { border-color: #2563eb; background: #e0e7ff; color: #1e40af; }
.cw-form input, .cw-form select, .cw-form textarea { padding: 9px 11px; border: 1px solid #e5e7eb; border-radius: 10px; font: inherit; font-size: 14px; background: #fff; }
.cw-kb { display: flex; flex-direction: column; gap: 6px; padding: 10px; border-radius: 10px; background: #f0fdf4; font-size: 13px; }
.cw-kb p { margin: 0; }
.cw-kb-title { font-weight: 700; }
.cw-kb button { align-self: flex-start; padding: 0; border: 0; background: none; color: #166534; font: inherit; font-weight: 700; text-decoration: underline; cursor: pointer; }
.cw-hint { margin: 0; color: #6b7280; font-size: 12px; }
.cw-error { margin: 0; color: #b91c1c; font-size: 13px; }
.cw-send { padding: 10px 14px; border: 0; border-radius: 10px; background: #1d4ed8; color: #fff; font: inherit; font-weight: 700; cursor: pointer; }
.cw-send:disabled { opacity: .5; cursor: not-allowed; }
.cw-ghost { margin-left: 8px; padding: 10px 14px; border: 1px solid #e5e7eb; border-radius: 10px; background: #fff; font: inherit; font-weight: 600; cursor: pointer; }
.cw-solved { margin: 0; padding: 14px; border-radius: 12px; background: #f0fdf4; color: #166534; }
.cw-thread { gap: 10px; }
.cw-desc { margin: 0; padding: 10px 12px; border-radius: 12px; background: #fff; border: 1px solid #e0e7ff; font-size: 13.5px; }
.cw-event { margin: 0; color: #6b7280; font-size: 11.5px; text-align: center; }
.cw-msg { align-self: flex-start; max-width: 82%; padding: 9px 12px; border-radius: 14px 14px 14px 4px; background: #fff; border: 1px solid #e0e7ff; }
.cw-msg.is-mine { align-self: flex-end; border: 0; border-radius: 14px 14px 4px 14px; background: #1d4ed8; color: #fff; }
.cw-msg p { margin: 0; font-size: 14px; white-space: pre-wrap; word-break: break-word; }
.cw-msg time { display: block; margin-top: 3px; font-size: 11px; opacity: .7; }
.cw-resolved { padding: 10px 12px; border-radius: 12px; background: #f0fdf4; font-size: 13.5px; }
.cw-resolved p { margin: 0 0 8px; }
.cw-reply { display: flex; gap: 8px; padding: 10px; border-top: 1px solid #e0e7ff; background: #fff; }
.cw-reply input { flex: 1; padding: 10px 12px; border: 1px solid #e5e7eb; border-radius: 999px; font: inherit; font-size: 14px; }
.cw-reply button { width: 40px; border: 0; border-radius: 999px; background: #1d4ed8; color: #fff; font-size: 15px; cursor: pointer; }
.cw-reply button:disabled { opacity: .5; }
.cw-pop-enter-active, .cw-pop-leave-active { transition: opacity .15s, transform .15s; }
.cw-pop-enter-from, .cw-pop-leave-to { opacity: 0; transform: translateY(10px) scale(.98); }
</style>
