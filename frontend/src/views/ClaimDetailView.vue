<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { assignClaim, confirmClaim, getClaim, getClaimThread, postClaimMessage, reopenClaim, resolveClaim } from '@/api/claims.api'
import { articleFromClaim } from '@/api/knowledge.api'
import { useAuthStore } from '@/stores/auth'
import { CAUSES, PRIORITY_LABEL, STATUS_LABEL, STATUS_TONE, TYPE_LABEL, age } from '@/components/claims/claimLabels'
import type { Claim, ClaimCause, ClaimMessage } from '@/types/portal'

/* Une réclamation : son contexte, son fil, et les actions de chacun selon son rôle. */
const route = useRoute()
const auth = useAuthStore()
const id = computed(() => Number(route.params.id))
const claim = ref<Claim | null>(null)
const thread = ref<ClaimMessage[]>([])
const error = ref('')
const info = ref('')
const busy = ref(false)
const message = ref('')
const askRequester = ref(false)
const resolveOpen = ref(false)
const resolution = ref('')
const cause = ref<ClaimCause>('CODE')

const isTeam = computed(() => auth.hasRole('DEVOPS', 'ADMIN'))
const isRequester = computed(() => claim.value?.requesterEmail === auth.currentUser?.email)
const canAct = computed(() => claim.value !== null && claim.value.status !== 'CLOSED')

async function load() {
  try { [claim.value, thread.value] = await Promise.all([getClaim(id.value), getClaimThread(id.value)]); error.value = '' }
  catch (exception) {
    error.value = (exception as { response?: { status?: number } }).response?.status === 403
      ? 'Cette réclamation ne vous concerne pas.' : 'La réclamation n’a pas pu être chargée.'
  }
}
onMounted(load)
watch(id, load)

async function run(action: () => Promise<unknown>, done?: string) {
  busy.value = true
  error.value = ''
  info.value = ''
  try { await action(); if (done) info.value = done; await load() }
  catch (exception) { error.value = (exception as { response?: { data?: { message?: string } } }).response?.data?.message ?? 'L’action a échoué.' }
  finally { busy.value = false }
}
const send = () => run(async () => { await postClaimMessage(id.value, message.value.trim(), askRequester.value); message.value = ''; askRequester.value = false })
const take = () => run(() => assignClaim(id.value))
const resolve = () => run(async () => { await resolveClaim(id.value, resolution.value.trim(), cause.value); resolveOpen.value = false })
const confirm = () => run(() => confirmClaim(id.value))
const reopen = () => {
  const why = window.prompt('Raison ?') ?? ''
  return run(() => reopenClaim(id.value, why))
}
const publish = () => run(() => articleFromClaim(id.value), 'Publié dans l’Aide.')
function when(iso: string) { return new Date(iso).toLocaleString('fr-FR', { dateStyle: 'short', timeStyle: 'short' }) }
</script>

<template>
  <section class="cd">
    <RouterLink to="/claims" class="cd-back">← Réclamations</RouterLink>
    <p v-if="error" class="cd-error" role="alert">{{ error }}</p>
    <p v-if="info" class="cd-info" role="status">{{ info }}</p>

    <template v-if="claim">
      <header class="cd-head">
        <div>
          <p class="cd-kicker">Réclamation #{{ claim.id }} · {{ TYPE_LABEL[claim.type] }}</p>
          <h1>{{ claim.title }}</h1>
          <p class="cl-muted">{{ claim.applicationName }}<template v-if="claim.environmentName"> · {{ claim.environmentName }}</template>
            · ouverte par {{ claim.requesterEmail }} {{ age(claim.createdAt) }}
            <template v-if="claim.assigneeEmail"> · prise en charge par {{ claim.assigneeEmail }}</template></p>
        </div>
        <div class="cd-badges">
          <span class="cl-pill" :class="claim.priority === 'CRITICAL' ? 'is-critical' : claim.priority === 'HIGH' ? 'is-approval' : 'is-muted'">{{ PRIORITY_LABEL[claim.priority] }}</span>
          <span class="cl-pill" :class="`is-${STATUS_TONE[claim.status]}`">{{ STATUS_LABEL[claim.status] }}</span>
        </div>
      </header>

      <p v-if="claim.waitingFor" class="cd-waiting" :class="claim.waitingFor === 'REQUESTER' ? 'is-requester' : 'is-team'">
        {{ claim.waitingFor === 'TEAM' ? 'En attente de l’équipe' : claim.status === 'RESOLVED' ? 'À confirmer par le demandeur' : 'Précision demandée' }}
      </p>

      <div class="cd-grid">
        <div class="cd-col">
          <section v-if="claim.description" class="cd-card"><h2>Description</h2><p class="cd-pre">{{ claim.description }}</p></section>
          <section v-if="claim.resolution" class="cd-card cd-resolution"><h2>Solution</h2><p class="cd-pre">{{ claim.resolution }}</p></section>

          <section class="cd-card">
            <h2>Fil</h2>
            <div class="cd-thread">
              <template v-for="m in thread" :key="m.id">
                <p v-if="m.systemEvent" class="cd-event"><span class="cd-dot" /> {{ m.message }} <time>{{ when(m.createdAt) }}</time></p>
                <article v-else class="cd-msg" :class="{ 'is-mine': m.mine }">
                  <p class="cd-meta"><strong>{{ m.mine ? 'Vous' : m.authorName }}</strong> <time>{{ when(m.createdAt) }}</time></p>
                  <p class="cd-pre">{{ m.message }}</p>
                </article>
              </template>
            </div>
            <form v-if="canAct" class="cd-form" @submit.prevent="send">
              <textarea v-model="message" rows="2" maxlength="2000" placeholder="Écrire un message…" />
              <div class="cd-form-bar">
                <label v-if="isTeam"><input v-model="askRequester" type="checkbox" /> Demander une précision</label>
                <button type="submit" class="cl-btn cl-primary" :disabled="busy || !message.trim()">Envoyer</button>
              </div>
            </form>
          </section>
        </div>

        <aside class="cd-col">
          <section class="cd-card">
            <h2>Actions</h2>
            <button v-if="isTeam && canAct && claim.status !== 'RESOLVED'" type="button" class="cl-btn" :disabled="busy" @click="take">Prendre en charge</button>
            <button v-if="isTeam && canAct && claim.status !== 'RESOLVED'" type="button" class="cl-btn cl-primary" :disabled="busy" @click="resolveOpen = !resolveOpen">Résoudre</button>
            <form v-if="resolveOpen" class="cd-resolve" @submit.prevent="resolve">
              <label>Solution<textarea v-model="resolution" rows="4" required maxlength="4000" /></label>
              <label>Cause<select v-model="cause"><option v-for="c in CAUSES" :key="c.value" :value="c.value">{{ c.label }}</option></select></label>
              <button type="submit" class="cl-btn cl-primary" :disabled="busy || !resolution.trim()">Résoudre</button>
            </form>
            <template v-if="isRequester && claim.status === 'RESOLVED'">
              <button type="button" class="cl-btn cl-primary" :disabled="busy" @click="confirm">Confirmer</button>
              <button type="button" class="cl-btn" :disabled="busy" @click="reopen">Rouvrir</button>
            </template>
            <button v-if="isTeam && claim.resolution" type="button" class="cl-btn" :disabled="busy" @click="publish">Publier dans l’Aide</button>
            <RouterLink v-if="claim.deploymentId" :to="`/deployments/${claim.deploymentId}`" class="cl-btn">Déploiement #{{ claim.deploymentId }}</RouterLink>
            <p v-if="!canAct" class="cl-muted">Réclamation fermée.</p>
          </section>
          <section v-if="claim.context" class="cd-card"><h2>Contexte</h2><p class="cd-pre cd-context">{{ claim.context }}</p></section>
          <section class="cd-card cl-muted">
            <p>Ouverte {{ age(claim.createdAt) }}</p>
            <p v-if="claim.firstResponseAt">Première réponse {{ age(claim.firstResponseAt) }}</p>
            <p v-if="claim.resolvedAt">Résolue {{ age(claim.resolvedAt) }}</p>
          </section>
        </aside>
      </div>
    </template>
  </section>
</template>

<style scoped>
.cd { display: flex; flex-direction: column; gap: 12px; max-width: 1120px; }
.cd-back { color: #1e40af; font-size: 13.5px; font-weight: 600; text-decoration: none; }
.cd-error { margin: 0; padding: 10px 14px; border: 1px solid #fecaca; border-radius: 10px; background: #fef2f2; color: #991b1b; }
.cd-info { margin: 0; padding: 10px 14px; border: 1px solid #bbf7d0; border-radius: 10px; background: #f0fdf4; color: #166534; }
.cd-head { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 10px; }
.cd-kicker { margin: 0; color: #4338ca; font-size: 12px; font-weight: 700; letter-spacing: .05em; text-transform: uppercase; }
.cd-head h1 { margin: 2px 0; font-size: 22px; }
.cd-head p { margin: 0; }
.cd-badges { display: flex; gap: 6px; }
.cd-waiting { margin: 0; padding: 9px 14px; border-radius: 10px; font-size: 14px; font-weight: 600; }
.cd-waiting.is-team { background: #eff6ff; color: #1e40af; }
.cd-waiting.is-requester { background: #f5f3ff; color: #5b21b6; }
.cd-grid { display: grid; grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr); gap: 14px; align-items: start; }
@media (max-width: 900px) { .cd-grid { grid-template-columns: 1fr; } }
.cd-col { display: flex; flex-direction: column; gap: 12px; }
.cd-card { display: flex; flex-direction: column; gap: 8px; padding: 14px 16px; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; }
.cd-card h2 { margin: 0; font-size: 15px; }
.cd-card p { margin: 0; }
.cd-pre { white-space: pre-wrap; word-break: break-word; font-size: 14px; }
.cd-context { color: #374151; font-size: 13px; }
.cd-resolution { border-color: #bbf7d0; background: #f0fdf4; }
.cd-thread { display: flex; flex-direction: column; gap: 8px; }
.cd-event { display: flex; align-items: center; gap: 8px; color: #4b5563; font-size: 12.5px; }
.cd-event time, .cd-meta time { margin-left: auto; color: #9ca3af; font-size: 12px; }
.cd-dot { width: 8px; height: 8px; border-radius: 999px; background: #7c3aed; }
.cd-msg { align-self: flex-start; max-width: 88%; padding: 8px 12px; border-radius: 12px 12px 12px 4px; background: #f3f4f6; }
.cd-msg.is-mine { align-self: flex-end; border-radius: 12px 12px 4px 12px; background: #e0e7ff; }
.cd-meta { display: flex; gap: 8px; font-size: 12px; color: #4b5563; }
.cd-form { display: flex; flex-direction: column; gap: 6px; }
.cd-form textarea, .cd-resolve textarea, .cd-resolve select { padding: 8px 10px; border: 1px solid #e5e7eb; border-radius: 8px; font: inherit; font-size: 14px; }
.cd-form-bar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 8px; font-size: 13px; }
.cd-resolve { display: flex; flex-direction: column; gap: 8px; }
.cd-resolve label { display: flex; flex-direction: column; gap: 4px; font-size: 13px; font-weight: 600; }
.cl-btn { display: inline-flex; align-items: center; padding: 8px 14px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #111827; font: inherit; font-size: 14px; font-weight: 600; text-decoration: none; cursor: pointer; white-space: nowrap; }
.cl-primary { border-color: #1e3a8a; background: #1e3a8a; color: #fff; }
.cl-btn:disabled { opacity: .5; cursor: not-allowed; }
.cl-pill { display: inline-block; padding: 2px 9px; border-radius: 999px; font-size: 12px; font-weight: 700; white-space: nowrap; }
.cl-pill.is-critical { background: #fee2e2; color: #991b1b; }
.cl-pill.is-running { background: #dbeafe; color: #1e40af; }
.cl-pill.is-approval { background: #ede9fe; color: #5b21b6; }
.cl-pill.is-ok { background: #dcfce7; color: #166534; }
.cl-pill.is-muted { background: #f3f4f6; color: #4b5563; }
.cl-muted { color: #6b7280; font-size: 13px; }
</style>
