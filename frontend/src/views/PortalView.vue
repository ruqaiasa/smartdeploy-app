<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { getWorkspace } from '@/api/portal.api'
import { getDeployments } from '@/api/deployments.api'
import { getClaims } from '@/api/claims.api'
import { useAuthStore } from '@/stores/auth'
import { openClaimsWidget } from '@/components/developer/claimsWidget'
import type { Workspace } from '@/types/portal'
import type { DeploymentResponse } from '@/types/deployment'

/* Accueil du développeur : ses chiffres, ce qu’il a à faire, son activité récente. Le détail est dans les rubriques. */
const auth = useAuthStore()
const workspace = ref<Workspace | null>(null)
const recent = ref<DeploymentResponse[]>([])
const todo = ref<{ link: string | null; text: string; claimId?: number }[]>([])

onMounted(async () => {
  const [ws, deps, claims] = await Promise.all([
    getWorkspace().catch(() => null), getDeployments({ mine: true, size: 6 }).catch(() => null), getClaims().catch(() => []),
  ])
  workspace.value = ws
  recent.value = deps?.content ?? []
  todo.value = [
    ...claims.filter(c => c.status === 'WAITING_REQUESTER').map(c => ({ link: null, text: `Répondre : ${c.title}`, claimId: c.id })),
    ...claims.filter(c => c.status === 'RESOLVED').map(c => ({ link: null, text: `Confirmer : ${c.title}`, claimId: c.id })),
    ...recent.value.filter(d => d.status === 'FAILED').slice(0, 3).map(d => ({ link: `/deployments/${d.id}`, text: `Échec : ${d.applicationName} ${d.version}` })),
  ]
})

const firstName = computed(() => auth.currentUser?.firstName ?? '')
const STATUS: Record<string, { label: string; tone: string }> = {
  AWAITING_APPROVAL: { label: 'En attente', tone: 'approval' }, PENDING: { label: 'En file', tone: 'muted' }, RUNNING: { label: 'En cours', tone: 'running' },
  SUCCESS: { label: 'Réussi', tone: 'ok' }, FAILED: { label: 'Échoué', tone: 'critical' }, REJECTED: { label: 'Refusé', tone: 'critical' },
  CANCELLED: { label: 'Annulé', tone: 'muted' }, ROLLED_BACK: { label: 'Remplacé', tone: 'muted' },
}
const status = (d: DeploymentResponse) => d.status === 'SUCCESS' && d.current ? { label: 'En service', tone: 'ok' } : STATUS[d.status] ?? { label: d.status, tone: 'muted' }
function relative(iso: string) {
  const h = (Date.now() - new Date(iso).getTime()) / 3_600_000
  return h < 1 ? 'il y a moins d’1 h' : h < 24 ? `il y a ${Math.round(h)} h` : `il y a ${Math.round(h / 24)} j`
}
</script>

<template>
  <section class="hm">
    <header class="hm-hero">
      <div>
        <h1>Bonjour {{ firstName }}</h1>
        <p>Votre activité sur SmartDeploy.</p>
      </div>
      <div class="hm-actions">
        <RouterLink to="/applications" class="hm-btn">+ Application</RouterLink>
        <RouterLink to="/deployments" class="hm-btn hm-primary">Demander un déploiement</RouterLink>
      </div>
    </header>

    <div class="hm-stats">
      <RouterLink to="/applications" class="hm-stat"><strong>{{ workspace?.applications ?? 0 }}</strong><span>Applications</span></RouterLink>
      <RouterLink to="/environments" class="hm-stat"><strong>{{ workspace?.inService ?? 0 }}</strong><span>En service</span></RouterLink>
      <RouterLink to="/deployments" class="hm-stat" :class="{ 'is-pending': workspace?.pendingRequests }"><strong>{{ workspace?.pendingRequests ?? 0 }}</strong><span>En attente</span></RouterLink>
    </div>

    <div class="hm-grid">
      <section class="hm-card">
        <h2>À faire</h2>
        <p v-if="!todo.length" class="hm-muted">Rien pour le moment.</p>
        <ul v-else class="hm-todo">
          <li v-for="(t, i) in todo" :key="i">
            <RouterLink v-if="t.link" :to="t.link">{{ t.text }}</RouterLink>
            <button v-else type="button" @click="openClaimsWidget()">{{ t.text }}</button>
          </li>
        </ul>
      </section>

      <section class="hm-card hm-wide">
        <header class="hm-card-head"><h2>Activité récente</h2><RouterLink to="/deployments">Tout voir</RouterLink></header>
        <p v-if="!recent.length" class="hm-muted">Aucun déploiement.</p>
        <ul v-else class="hm-recent">
          <li v-for="d in recent" :key="d.id">
            <RouterLink :to="`/deployments/${d.id}`">
              <span class="hm-app">{{ d.applicationName }} <code>{{ d.version }}</code></span>
              <span class="hm-env">{{ d.environmentName }}</span>
              <span class="hm-pill" :class="`is-${status(d).tone}`">{{ status(d).label }}</span>
              <span class="hm-when">{{ relative(d.createdAt) }}</span>
            </RouterLink>
          </li>
        </ul>
      </section>
    </div>
  </section>
</template>

<style scoped>
.hm { display: flex; flex-direction: column; gap: 18px; }
.hm-hero { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 14px; padding: 22px 24px; border-radius: 18px; background: linear-gradient(135deg, #7c3aed, #4f46e5); color: #fff; }
.hm-hero h1 { margin: 0; font-size: 26px; }
.hm-hero p { margin: 4px 0 0; opacity: .85; }
.hm-actions { display: flex; gap: 8px; }
.hm-btn { padding: 9px 14px; border-radius: 10px; background: rgba(255, 255, 255, .16); color: #fff; font-size: 14px; font-weight: 700; text-decoration: none; }
.hm-primary { background: #fff; color: #5b21b6; }
.hm-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; }
.hm-stat { display: flex; flex-direction: column; gap: 2px; padding: 16px 18px; border: 1px solid #ede9fe; border-radius: 14px; background: #fff; color: #111827; text-decoration: none; }
.hm-stat strong { font-size: 28px; }
.hm-stat span { color: #6b7280; font-size: 13.5px; font-weight: 600; }
.hm-stat.is-pending { border-color: #c4b5fd; background: #f5f3ff; }
.hm-grid { display: grid; grid-template-columns: minmax(260px, 1fr) minmax(0, 2fr); gap: 14px; align-items: start; }
@media (max-width: 900px) { .hm-grid, .hm-stats { grid-template-columns: 1fr; } }
.hm-card { display: flex; flex-direction: column; gap: 10px; padding: 16px 18px; border: 1px solid #ede9fe; border-radius: 14px; background: #fff; }
.hm-card h2 { margin: 0; font-size: 16px; }
.hm-card-head { display: flex; align-items: baseline; justify-content: space-between; }
.hm-card-head a { color: #6d28d9; font-size: 13.5px; font-weight: 700; text-decoration: none; }
.hm-muted { margin: 0; color: #6b7280; font-size: 14px; }
.hm-todo { display: flex; flex-direction: column; gap: 6px; margin: 0; padding: 0; list-style: none; }
.hm-todo a, .hm-todo button { display: block; box-sizing: border-box; width: 100%; padding: 9px 12px; border: 0; border-radius: 10px; background: #f5f3ff; color: #4c1d95; font: inherit; font-size: 14px; font-weight: 600; text-align: left; text-decoration: none; cursor: pointer; }
.hm-recent { display: flex; flex-direction: column; margin: 0; padding: 0; list-style: none; }
.hm-recent a { display: grid; grid-template-columns: minmax(0, 2fr) minmax(0, 1.2fr) auto 90px; align-items: center; gap: 10px; padding: 10px 4px; border-top: 1px solid #f3f0ff; color: inherit; text-decoration: none; font-size: 14px; }
.hm-recent li:first-child a { border-top: 0; }
.hm-recent a:hover { background: #faf9ff; }
.hm-app { font-weight: 600; }
.hm-app code { padding: 0 6px; border-radius: 5px; background: #f3f4f6; font-size: 12px; }
.hm-env { color: #6b7280; }
.hm-when { color: #9ca3af; font-size: 12.5px; text-align: right; }
.hm-pill { padding: 2px 9px; border-radius: 999px; font-size: 12px; font-weight: 700; white-space: nowrap; }
.hm-pill.is-approval { background: #ede9fe; color: #5b21b6; }
.hm-pill.is-running { background: #dbeafe; color: #1e40af; }
.hm-pill.is-ok { background: #dcfce7; color: #166534; }
.hm-pill.is-critical { background: #fee2e2; color: #991b1b; }
.hm-pill.is-muted { background: #f3f4f6; color: #4b5563; }
</style>
