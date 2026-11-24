<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import { downloadMeetingIcs, getMeeting, type Meeting } from '@/api/meetings.api'
import { useAuthStore } from '@/stores/auth'

/* Salle de visioconférence (Jitsi Meet intégré) : la salle porte un nom long et aléatoire, propre au rendez-vous. */
const route = useRoute()
const auth = useAuthStore()
const meeting = ref<Meeting | null>(null)
const error = ref('')
const joined = ref(false)

onMounted(async () => {
  try { meeting.value = await getMeeting(Number(route.params.id)) }
  catch { error.value = 'Ce rendez-vous est introuvable, ou ne vous concerne pas.' }
})
const src = computed(() => {
  if (!meeting.value) return ''
  const name = encodeURIComponent(auth.fullName || auth.currentUser?.email || 'SmartDeploy')
  return `${meeting.value.roomUrl}#userInfo.displayName="${name}"&config.prejoinPageEnabled=true&config.subject="${encodeURIComponent(meeting.value.topic)}"`
})
const when = computed(() => meeting.value?.startsAt
  ? new Date(meeting.value.startsAt).toLocaleString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' }) : '')
</script>

<template>
  <section class="mr">
    <RouterLink to="/meetings" class="mr-back">← Rendez-vous</RouterLink>
    <p v-if="error" class="mr-error">{{ error }}</p>
    <template v-if="meeting">
      <header class="mr-head">
        <div>
          <h1>{{ meeting.topic }}</h1>
          <p>{{ meeting.organizer.name }} et {{ meeting.invitee.name }}<template v-if="when"> · {{ when }} · {{ meeting.durationMinutes }} min</template>
            <template v-if="meeting.deploymentId"> · <RouterLink :to="`/deployments/${meeting.deploymentId}`">déploiement #{{ meeting.deploymentId }}</RouterLink></template></p>
        </div>
        <div class="mr-actions">
          <button v-if="meeting.startsAt" type="button" class="mr-btn" @click="downloadMeetingIcs(meeting.id)">Agenda (.ics)</button>
          <a :href="meeting.roomUrl" target="_blank" rel="noopener" class="mr-btn">Ouvrir dans un onglet</a>
        </div>
      </header>
      <p v-if="meeting.agenda" class="mr-agenda"><strong>Ordre du jour :</strong> {{ meeting.agenda }}</p>
      <div v-if="!joined" class="mr-lobby">
        <p>La visioconférence s’ouvre ici même. Votre navigateur vous demandera l’accès à la caméra et au micro.</p>
        <button type="button" class="mr-btn mr-primary" @click="joined = true">Rejoindre la visio</button>
      </div>
      <iframe v-else :src="src" class="mr-frame" title="Visioconférence" allow="camera; microphone; fullscreen; display-capture; autoplay; clipboard-write" />
    </template>
  </section>
</template>

<style scoped>
.mr { display: flex; flex-direction: column; gap: 12px; }
.mr-back { color: #1d4ed8; font-size: 13.5px; font-weight: 700; text-decoration: none; }
.mr-error { margin: 0; padding: 10px 14px; border-radius: 10px; background: #fef2f2; color: #991b1b; }
.mr-head { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 10px; }
.mr-head h1 { margin: 0; font-size: 22px; }
.mr-head p { margin: 4px 0 0; color: #64748b; font-size: 13.5px; }
.mr-actions { display: flex; gap: 8px; }
.mr-btn { display: inline-flex; align-items: center; padding: 8px 14px; border: 1px solid #e2e8f0; border-radius: 10px; background: #fff; color: #0f172a; font: inherit; font-size: 13.5px; font-weight: 700; text-decoration: none; cursor: pointer; }
.mr-primary { border-color: #16a34a; background: #16a34a; color: #fff; }
.mr-agenda { margin: 0; padding: 10px 14px; border-radius: 10px; background: #f8fafc; font-size: 14px; }
.mr-lobby { display: flex; flex-direction: column; align-items: center; gap: 12px; padding: 60px 20px; border: 1px dashed #c7d2fe; border-radius: 16px; background: #f8faff; text-align: center; }
.mr-lobby p { margin: 0; color: #475569; }
.mr-frame { width: 100%; height: calc(100vh - 230px); min-height: 480px; border: 0; border-radius: 16px; background: #0f172a; }
</style>
