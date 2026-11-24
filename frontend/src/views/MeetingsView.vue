<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { acceptMeeting, declineMeeting, downloadMeetingIcs, getMeetings, type Meeting } from '@/api/meetings.api'
import ProposeMeetingDialog from '@/components/meetings/ProposeMeetingDialog.vue'

/* Rendez-vous : les propositions à traiter, le calendrier de la semaine, les prochaines visioconférences. */
const meetings = ref<Meeting[]>([])
const proposeOpen = ref(false)
const weekOffset = ref(0)

async function load() { meetings.value = await getMeetings().catch(() => []) }
onMounted(load)

const toAnswer = computed(() => meetings.value.filter(m => m.status === 'PROPOSED' && !m.organizedByMe))
const waiting = computed(() => meetings.value.filter(m => m.status === 'PROPOSED' && m.organizedByMe))
const upcoming = computed(() => meetings.value.filter(m => m.status === 'CONFIRMED' && m.startsAt
  && new Date(m.startsAt).getTime() + m.durationMinutes * 60000 > Date.now())
  .sort((a, b) => new Date(a.startsAt!).getTime() - new Date(b.startsAt!).getTime()))

/* Calendrier de la semaine (lundi → dimanche), de 8 h à 19 h */
const HOURS = Array.from({ length: 12 }, (_, i) => 8 + i)
const weekStart = computed(() => {
  const d = new Date()
  const day = (d.getDay() + 6) % 7
  d.setHours(0, 0, 0, 0)
  d.setDate(d.getDate() - day + weekOffset.value * 7)
  return d
})
const weekDays = computed(() => Array.from({ length: 7 }, (_, i) => { const d = new Date(weekStart.value); d.setDate(d.getDate() + i); return d }))
const weekLabel = computed(() => {
  const end = new Date(weekStart.value); end.setDate(end.getDate() + 6)
  const f = (d: Date) => d.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long' })
  return `Semaine du ${f(weekStart.value)} au ${f(end)}`
})
function eventsOf(day: Date) {
  return meetings.value.filter(m => m.status === 'CONFIRMED' && m.startsAt && new Date(m.startsAt).toDateString() === day.toDateString())
}
function proposalsOf(day: Date) {
  return meetings.value.filter(m => m.status === 'PROPOSED').flatMap(m => m.slots.map(s => ({ m, s })))
    .filter(x => new Date(x.s).toDateString() === day.toDateString())
}
function blockStyle(iso: string, minutes: number) {
  const d = new Date(iso)
  const top = ((d.getHours() + d.getMinutes() / 60) - 8) * 44
  return { top: `${Math.max(0, top)}px`, height: `${Math.max(22, (minutes / 60) * 44)}px` }
}
const isToday = (d: Date) => d.toDateString() === new Date().toDateString()
const fmt = (iso: string) => new Date(iso).toLocaleString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit' })
const hm = (iso: string) => new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })
function canJoin(m: Meeting) {
  if (!m.startsAt) return false
  const start = new Date(m.startsAt).getTime()
  return Date.now() >= start - 10 * 60000 && Date.now() <= start + m.durationMinutes * 60000
}
function other(m: Meeting) { return m.organizedByMe ? m.invitee : m.organizer }
async function accept(m: Meeting, slot: number) { await acceptMeeting(m.id, slot).catch(() => undefined); await load() }
async function decline(m: Meeting) {
  if (!window.confirm(m.organizedByMe ? 'Annuler ce rendez-vous ?' : 'Refuser ce rendez-vous ?')) return
  await declineMeeting(m.id).catch(() => undefined); await load()
}
</script>

<template>
  <section class="mt">
    <header class="mt-head">
      <div><h1>Rendez-vous</h1><p>Visioconférences entre développeurs et équipe DevOps.</p></div>
      <button type="button" class="mt-btn mt-primary" @click="proposeOpen = true">+ Proposer une visio</button>
    </header>

    <section v-if="toAnswer.length" class="mt-card mt-answer">
      <h2>À répondre</h2>
      <article v-for="m in toAnswer" :key="m.id" class="mt-proposal">
        <div><strong>{{ m.topic }}</strong><span>proposé par {{ m.organizer.name }} · {{ m.durationMinutes }} min</span><p v-if="m.agenda">{{ m.agenda }}</p></div>
        <div class="mt-slots">
          <button v-for="(s, i) in m.slots" :key="s" type="button" class="mt-slot" @click="accept(m, i + 1)">{{ fmt(s) }}</button>
          <button type="button" class="mt-btn" @click="decline(m)">Refuser</button>
        </div>
      </article>
    </section>

    <div class="mt-grid">
      <section class="mt-card">
        <header class="mt-cal-head">
          <h2>{{ weekLabel }}</h2>
          <div><button type="button" class="mt-btn" aria-label="Semaine précédente" @click="weekOffset--">‹</button><button type="button" class="mt-btn" @click="weekOffset = 0">Aujourd’hui</button><button type="button" class="mt-btn" aria-label="Semaine suivante" @click="weekOffset++">›</button></div>
        </header>
        <div class="mt-cal">
          <div class="mt-hours"><span v-for="h in HOURS" :key="h">{{ h }} h</span></div>
          <div v-for="d in weekDays" :key="d.toISOString()" class="mt-day" :class="{ 'is-today': isToday(d) }">
            <p class="mt-day-name">{{ d.toLocaleDateString('fr-FR', { weekday: 'short', day: 'numeric' }) }}</p>
            <div class="mt-day-body">
              <span v-for="h in HOURS" :key="h" class="mt-line" />
              <RouterLink v-for="m in eventsOf(d)" :key="m.id" :to="`/meetings/${m.id}`" class="mt-event" :style="blockStyle(m.startsAt!, m.durationMinutes)">
                <strong>{{ hm(m.startsAt!) }}</strong> {{ m.topic }}
              </RouterLink>
              <span v-for="x in proposalsOf(d)" :key="`${x.m.id}-${x.s}`" class="mt-event mt-event-proposed" :style="blockStyle(x.s, x.m.durationMinutes)" :title="`Proposé : ${x.m.topic}`">
                <strong>{{ hm(x.s) }}</strong> {{ x.m.topic }}
              </span>
            </div>
          </div>
        </div>
        <p class="mt-legend"><i class="is-confirmed" /> Confirmé <i class="is-proposed" /> Créneau proposé</p>
      </section>

      <aside class="mt-side">
        <section class="mt-card">
          <h2>Prochaines visios</h2>
          <p v-if="!upcoming.length" class="mt-muted">Aucune visio prévue.</p>
          <article v-for="m in upcoming" :key="m.id" class="mt-next">
            <strong>{{ m.topic }}</strong>
            <span>{{ fmt(m.startsAt!) }} · avec {{ other(m).name }}</span>
            <div class="mt-next-actions">
              <RouterLink :to="`/meetings/${m.id}`" class="mt-btn" :class="{ 'mt-join': canJoin(m) }">{{ canJoin(m) ? 'Rejoindre' : 'Ouvrir' }}</RouterLink>
              <button type="button" class="mt-btn" @click="downloadMeetingIcs(m.id)">Agenda (.ics)</button>
              <button v-if="m.organizedByMe" type="button" class="mt-btn mt-danger" @click="decline(m)">Annuler</button>
            </div>
          </article>
        </section>
        <section v-if="waiting.length" class="mt-card">
          <h2>En attente de réponse</h2>
          <article v-for="m in waiting" :key="m.id" class="mt-next">
            <strong>{{ m.topic }}</strong><span>envoyé à {{ m.invitee.name }} · {{ m.slots.length }} créneau(x)</span>
            <div class="mt-next-actions"><button type="button" class="mt-btn mt-danger" @click="decline(m)">Annuler</button></div>
          </article>
        </section>
      </aside>
    </div>

    <ProposeMeetingDialog :open="proposeOpen" @close="proposeOpen = false" @proposed="proposeOpen = false; load()" />
  </section>
</template>

<style scoped>
.mt { display: flex; flex-direction: column; gap: 16px; }
.mt-head { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 12px; }
.mt-head h1 { margin: 0; font-size: 24px; }
.mt-head p { margin: 4px 0 0; color: #64748b; font-size: 14px; }
.mt-btn { display: inline-flex; align-items: center; padding: 7px 12px; border: 1px solid #e2e8f0; border-radius: 9px; background: #fff; color: #0f172a; font: inherit; font-size: 13px; font-weight: 700; text-decoration: none; cursor: pointer; }
.mt-primary { border-color: #1e3a8a; background: #1e3a8a; color: #fff; }
.mt-join { border-color: #16a34a; background: #16a34a; color: #fff; }
.mt-danger { color: #b91c1c; }
.mt-card { display: flex; flex-direction: column; gap: 10px; padding: 16px 18px; border: 1px solid #e2e8f0; border-radius: 16px; background: #fff; }
.mt-card h2 { margin: 0; font-size: 15.5px; }
.mt-answer { border-color: #c7d2fe; background: #f8faff; }
.mt-proposal { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 10px; padding-top: 10px; border-top: 1px solid #e0e7ff; }
.mt-proposal > div:first-child { display: flex; flex-direction: column; gap: 2px; }
.mt-proposal span { color: #64748b; font-size: 12.5px; }
.mt-proposal p { margin: 4px 0 0; font-size: 13.5px; }
.mt-slots { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; }
.mt-slot { padding: 7px 12px; border: 1px solid #a5b4fc; border-radius: 9px; background: #eef2ff; color: #3730a3; font: inherit; font-size: 13px; font-weight: 700; cursor: pointer; }
.mt-slot::first-letter { text-transform: uppercase; }
.mt-slot:hover { background: #e0e7ff; }
.mt-grid { display: grid; grid-template-columns: minmax(0, 2fr) minmax(260px, 1fr); gap: 16px; align-items: start; }
@media (max-width: 1000px) { .mt-grid { grid-template-columns: 1fr; } }
.mt-cal-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.mt-cal-head div { display: flex; gap: 4px; }
.mt-cal { display: grid; grid-template-columns: 40px repeat(7, 1fr); gap: 4px; }
.mt-hours { display: flex; flex-direction: column; padding-top: 26px; }
.mt-hours span { height: 44px; color: #94a3b8; font-size: 11px; }
.mt-day-name { margin: 0 0 6px; color: #475569; font-size: 12px; font-weight: 800; text-align: center; text-transform: capitalize; }
.is-today .mt-day-name { color: #1d4ed8; }
.mt-day-body { position: relative; border-radius: 10px; background: #f8fafc; }
.is-today .mt-day-body { background: #eff6ff; }
.mt-line { display: block; height: 44px; border-top: 1px solid #eef2f7; }
.mt-event { position: absolute; right: 3px; left: 3px; overflow: hidden; padding: 3px 6px; border-radius: 7px; background: #1e3a8a; color: #fff; font-size: 11.5px; line-height: 1.25; text-decoration: none; }
.mt-event-proposed { background: repeating-linear-gradient(45deg, #e0e7ff, #e0e7ff 6px, #eef2ff 6px, #eef2ff 12px); color: #3730a3; border: 1px dashed #818cf8; }
.mt-legend { display: flex; align-items: center; gap: 8px; margin: 0; color: #64748b; font-size: 12px; }
.mt-legend i { display: inline-block; width: 12px; height: 12px; border-radius: 3px; }
.mt-legend .is-confirmed { background: #1e3a8a; } .mt-legend .is-proposed { background: #e0e7ff; border: 1px dashed #818cf8; }
.mt-side { display: flex; flex-direction: column; gap: 16px; }
.mt-next { display: flex; flex-direction: column; gap: 3px; padding-top: 10px; border-top: 1px solid #f1f5f9; }
.mt-next span { color: #64748b; font-size: 12.5px; }
.mt-next-actions { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 4px; }
.mt-muted { margin: 0; color: #64748b; font-size: 13.5px; }
</style>
