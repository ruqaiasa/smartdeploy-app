<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { getClaims } from '@/api/claims.api'
import { useAuthStore } from '@/stores/auth'
import NewClaimDialog from '@/components/claims/NewClaimDialog.vue'
import { PRIORITY_LABEL, STATUS_LABEL, STATUS_TONE, TYPE_LABEL, age } from '@/components/claims/claimLabels'
import type { Claim } from '@/types/portal'

/* Réclamations : les siennes pour un développeur, toutes pour l’équipe DevOps. */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const isTeam = computed(() => auth.hasRole('DEVOPS', 'ADMIN'))
const claims = ref<Claim[]>([])
const filter = ref<'active' | 'mine-to-act' | 'all'>('active')
const dialogOpen = ref(false)

async function load() { try { claims.value = await getClaims() } catch { claims.value = [] } }
onMounted(async () => {
  await load()
  if (route.query.new === '1') dialogOpen.value = true
})
const shown = computed(() => claims.value.filter(c => {
  if (filter.value === 'all') return true
  if (filter.value === 'mine-to-act') return isTeam.value ? c.waitingFor === 'TEAM' : c.waitingFor === 'REQUESTER'
  return c.status !== 'CLOSED'
}))
const queryApp = computed(() => route.query.applicationId ? Number(route.query.applicationId) : null)
const queryDeployment = computed(() => route.query.deploymentId ? Number(route.query.deploymentId) : null)
async function created(id: number) { dialogOpen.value = false; await router.push(`/claims/${id}`) }
</script>

<template>
  <section class="cl">
    <header class="cl-head">
      <div>
        <h1>Réclamations</h1>
        <p>{{ isTeam ? 'Toutes les réclamations.' : 'Vos réclamations.' }}</p>
      </div>
      <button type="button" class="cl-btn cl-primary" @click="dialogOpen = true">+ Réclamation</button>
    </header>

    <div class="cl-filters" role="tablist">
      <button type="button" :class="{ 'is-active': filter === 'active' }" @click="filter = 'active'">En cours</button>
      <button type="button" :class="{ 'is-active': filter === 'mine-to-act' }" @click="filter = 'mine-to-act'">
        {{ isTeam ? 'À traiter' : 'À faire' }}
      </button>
      <button type="button" :class="{ 'is-active': filter === 'all' }" @click="filter = 'all'">Toutes</button>
    </div>

    <p v-if="!shown.length" class="cl-empty">Aucune réclamation.</p>
    <ul v-else class="cl-list">
      <li v-for="c in shown" :key="c.id">
        <RouterLink :to="`/claims/${c.id}`" class="cl-item">
          <div class="cl-main">
            <p class="cl-title">{{ c.title }}</p>
            <p class="cl-muted">#{{ c.id }} · {{ TYPE_LABEL[c.type] }} · {{ c.applicationName }}<template v-if="c.environmentName"> · {{ c.environmentName }}</template> · {{ age(c.createdAt) }}
              <template v-if="isTeam"> · {{ c.requesterEmail }}</template></p>
          </div>
          <div class="cl-side">
            <span v-if="c.priority !== 'NORMAL'" class="cl-pill" :class="c.priority === 'CRITICAL' ? 'is-critical' : 'is-approval'">{{ PRIORITY_LABEL[c.priority] }}</span>
            <span class="cl-pill" :class="`is-${STATUS_TONE[c.status]}`">{{ STATUS_LABEL[c.status] }}</span>
          </div>
        </RouterLink>
      </li>
    </ul>

    <NewClaimDialog :open="dialogOpen" :application-id="queryApp" :deployment-id="queryDeployment" @close="dialogOpen = false" @created="created" />
  </section>
</template>

<style scoped>
.cl { display: flex; flex-direction: column; gap: 14px; }
.cl-head { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 12px; }
.cl-head h1 { margin: 0; font-size: 24px; }
.cl-head p { margin: 4px 0 0; color: #6b7280; font-size: 14px; }
.cl-filters { display: flex; gap: 6px; }
.cl-filters button { padding: 6px 12px; border: 1px solid #e5e7eb; border-radius: 999px; background: #fff; font: inherit; font-size: 13.5px; font-weight: 600; cursor: pointer; }
.cl-filters .is-active { border-color: #1e3a8a; background: #eef2ff; color: #1e3a8a; }
.cl-empty { margin: 0; padding: 22px; border: 1px dashed #e5e7eb; border-radius: 12px; color: #6b7280; text-align: center; }
.cl-list { display: flex; flex-direction: column; gap: 8px; margin: 0; padding: 0; list-style: none; }
.cl-item { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 16px; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; color: inherit; text-decoration: none; }
.cl-item:hover { border-color: #c7d2fe; }
.cl-main { min-width: 0; }
.cl-main p { margin: 0; }
.cl-title { font-weight: 600; font-size: 14.5px; }
.cl-side { display: flex; gap: 6px; }
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
