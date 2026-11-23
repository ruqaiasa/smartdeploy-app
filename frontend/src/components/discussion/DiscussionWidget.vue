<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { getRecentDiscussions, type UnreadThread } from '@/api/portal.api'
import { discussions } from '@/components/discussion/discussions'
import DeploymentDiscussion from '@/components/deployments/DeploymentDiscussion.vue'

/*
 * Messagerie de l’équipe DevOps, en bas à droite comme un assistant : les conversations de tous les
 * déploiements, non lues d’abord ; la conversation s’ouvre dans la bulle, sans quitter la page.
 */
const open = ref(false)
const tab = ref<'unread' | 'all'>('unread')
const recent = ref<UnreadThread[]>([])
const current = ref<UnreadThread | null>(null)
const loading = ref(false)

async function loadRecent() {
  loading.value = true
  try { recent.value = await getRecentDiscussions() } catch { recent.value = [] } finally { loading.value = false }
}
watch(open, value => { if (value) { void loadRecent(); if (!discussions.total) tab.value = 'all' } })
watch(() => discussions.total, () => { if (open.value && !current.value) void loadRecent() })

const shown = computed(() => {
  if (tab.value === 'unread') return discussions.threads
  return recent.value.map(t => ({ ...t, unread: discussions.byDeployment[t.deploymentId] ?? 0 }))
})
function openThread(t: UnreadThread) { current.value = t }
function back() { current.value = null; void loadRecent() }
function age(iso: string) {
  const m = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  return m < 1 ? 'à l’instant' : m < 60 ? `${m} min` : m < 1440 ? `${Math.round(m / 60)} h` : `${Math.round(m / 1440)} j`
}
function initials(name: string) {
  return name.split(/[\s.@]+/).filter(Boolean).slice(0, 2).map(p => p[0]?.toUpperCase()).join('')
}
</script>

<template>
  <div class="mw">
    <Transition name="mw-pop">
      <section v-if="open" class="mw-panel" role="dialog" aria-label="Messagerie">
        <header class="mw-head">
          <button v-if="current" type="button" class="mw-icon" aria-label="Retour" @click="back">‹</button>
          <div>
            <strong>{{ current ? `${current.applicationName} ${current.version}` : 'Messagerie' }}</strong>
            <span>{{ current ? `#${current.deploymentId} · ${current.environmentName}` : 'Discussions des déploiements' }}</span>
          </div>
          <RouterLink v-if="current" :to="`/deployments/${current.deploymentId}`" class="mw-icon mw-link" title="Ouvrir la fiche" @click="open = false">↗</RouterLink>
          <button type="button" class="mw-icon" aria-label="Fermer" @click="open = false">×</button>
        </header>

        <template v-if="!current">
          <nav class="mw-tabs">
            <button type="button" :class="{ 'is-active': tab === 'unread' }" @click="tab = 'unread'">Non lus <span v-if="discussions.total">{{ discussions.total }}</span></button>
            <button type="button" :class="{ 'is-active': tab === 'all' }" @click="tab = 'all'">Toutes</button>
          </nav>
          <div class="mw-list">
            <p v-if="loading && !shown.length" class="mw-empty">Chargement…</p>
            <p v-else-if="!shown.length" class="mw-empty">{{ tab === 'unread' ? 'Aucun message non lu.' : 'Aucune conversation.' }}</p>
            <button v-for="t in shown" :key="t.deploymentId" type="button" class="mw-item" :class="{ 'is-unread': t.unread }" @click="openThread(t)">
              <span class="mw-avatar">{{ initials(t.lastAuthor) }}</span>
              <span class="mw-item-body">
                <span class="mw-item-top"><strong>{{ t.applicationName }} {{ t.version }}</strong><time>{{ age(t.lastAt) }}</time></span>
                <span class="mw-item-env">#{{ t.deploymentId }} · {{ t.environmentName }}</span>
                <span class="mw-item-msg">{{ t.lastAuthor }} : {{ t.lastMessage }}</span>
              </span>
              <span v-if="t.unread" class="mw-count">{{ t.unread }}</span>
            </button>
          </div>
        </template>
        <div v-else class="mw-thread"><DeploymentDiscussion :deployment-id="current.deploymentId" /></div>
      </section>
    </Transition>

    <button type="button" class="mw-launcher" :aria-label="open ? 'Fermer la messagerie' : 'Ouvrir la messagerie'" @click="open = !open">
      <svg v-if="!open" viewBox="0 0 24 24" width="27" height="27" aria-hidden="true">
        <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H10l-4.5 4v-4A2.5 2.5 0 0 1 4 13.5z" fill="#fff" />
        <circle cx="8.5" cy="9.5" r="1.3" fill="#4f46e5" /><circle cx="12" cy="9.5" r="1.3" fill="#4f46e5" /><circle cx="15.5" cy="9.5" r="1.3" fill="#4f46e5" />
      </svg>
      <span v-else class="mw-x">×</span>
      <span v-if="discussions.total && !open" class="mw-badge">{{ discussions.total > 99 ? '99+' : discussions.total }}</span>
    </button>
  </div>
</template>

<style scoped>
.mw { position: fixed; right: 22px; bottom: 22px; z-index: 80; display: flex; flex-direction: column; align-items: flex-end; gap: 12px; }
.mw-launcher { position: relative; display: grid; place-items: center; width: 60px; height: 60px; border: 0; border-radius: 999px; background: linear-gradient(135deg, #1e3a8a, #4f46e5); color: #fff; box-shadow: 0 12px 28px rgba(30, 58, 138, .35); cursor: pointer; transition: transform .15s; }
.mw-launcher:hover { transform: scale(1.05); }
.mw-x { font-size: 30px; line-height: 1; }
.mw-badge { position: absolute; top: -3px; right: -3px; display: grid; place-items: center; min-width: 22px; height: 22px; padding: 0 5px; border-radius: 999px; background: #dc2626; color: #fff; font-size: 11.5px; font-weight: 800; box-shadow: 0 0 0 2px #fff; }
.mw-panel { display: flex; flex-direction: column; width: min(420px, calc(100vw - 32px)); height: min(600px, calc(100vh - 120px)); overflow: hidden; border-radius: 18px; background: #fff; box-shadow: 0 24px 60px rgba(15, 23, 42, .28); }
.mw-head { display: flex; align-items: center; gap: 8px; padding: 14px 14px 14px 16px; background: linear-gradient(135deg, #1e3a8a, #4f46e5); color: #fff; }
.mw-head div { display: flex; flex: 1; flex-direction: column; min-width: 0; }
.mw-head strong { overflow: hidden; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.mw-head span { font-size: 12px; opacity: .85; }
.mw-icon { display: grid; place-items: center; width: 30px; height: 30px; border: 0; border-radius: 8px; background: rgba(255, 255, 255, .18); color: #fff; font-size: 19px; line-height: 1; text-decoration: none; cursor: pointer; }
.mw-link { font-size: 15px; }
.mw-tabs { display: flex; gap: 6px; padding: 10px 12px; border-bottom: 1px solid #eef0f3; }
.mw-tabs button { display: inline-flex; align-items: center; gap: 6px; padding: 6px 12px; border: 1px solid #e5e7eb; border-radius: 999px; background: #fff; font: inherit; font-size: 13px; font-weight: 700; cursor: pointer; }
.mw-tabs .is-active { border-color: #4f46e5; background: #eef2ff; color: #3730a3; }
.mw-tabs span { padding: 0 6px; border-radius: 999px; background: #dc2626; color: #fff; font-size: 11px; }
.mw-list { flex: 1; overflow-y: auto; }
.mw-empty { margin: 24px 0; color: #6b7280; font-size: 14px; text-align: center; }
.mw-item { display: flex; align-items: center; gap: 10px; width: 100%; padding: 11px 14px; border: 0; border-bottom: 1px solid #f3f4f6; background: #fff; font: inherit; text-align: left; cursor: pointer; }
.mw-item:hover { background: #f8faff; }
.mw-item.is-unread { background: #f5f7ff; }
.mw-avatar { display: grid; flex: 0 0 auto; place-items: center; width: 38px; height: 38px; border-radius: 999px; background: #ede9fe; color: #5b21b6; font-size: 12.5px; font-weight: 800; }
.mw-item-body { display: flex; flex: 1; flex-direction: column; gap: 1px; min-width: 0; }
.mw-item-top { display: flex; justify-content: space-between; gap: 8px; font-size: 14px; }
.mw-item-top time { color: #9ca3af; font-size: 11.5px; white-space: nowrap; }
.mw-item-env { color: #6b7280; font-size: 12px; }
.mw-item-msg { overflow: hidden; color: #374151; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.is-unread .mw-item-msg { color: #111827; font-weight: 600; }
.mw-count { flex: 0 0 auto; min-width: 22px; padding: 1px 7px; border-radius: 999px; background: #4f46e5; color: #fff; font-size: 12px; font-weight: 800; text-align: center; }
.mw-thread { flex: 1; overflow-y: auto; padding: 10px; background: #f8faff; }
.mw-thread :deep(.dd) { border: 0; padding: 4px; background: transparent; }
.mw-thread :deep(.dd-list) { max-height: none; }
.mw-pop-enter-active, .mw-pop-leave-active { transition: opacity .15s, transform .15s; }
.mw-pop-enter-from, .mw-pop-leave-to { opacity: 0; transform: translateY(10px) scale(.98); }
</style>
