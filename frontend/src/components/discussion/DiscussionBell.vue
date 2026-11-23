<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { discussions } from '@/components/discussion/discussions'

/* Icône de messagerie : le nombre de messages non lus, et la liste des conversations concernées. */
const router = useRouter()
const open = ref(false)
const root = ref<HTMLElement | null>(null)

function go(deploymentId: number) {
  open.value = false
  void router.push(`/deployments/${deploymentId}#discussion`)
}
function outside(event: MouseEvent) {
  if (root.value && !root.value.contains(event.target as Node)) open.value = false
}
document.addEventListener('click', outside)
onBeforeUnmount(() => document.removeEventListener('click', outside))
function age(iso: string) {
  const m = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  return m < 1 ? 'à l’instant' : m < 60 ? `${m} min` : m < 1440 ? `${Math.round(m / 60)} h` : `${Math.round(m / 1440)} j`
}
</script>

<template>
  <div ref="root" class="db">
    <button type="button" class="db-btn" :aria-label="`Messages : ${discussions.total} non lu(s)`" @click="open = !open">
      <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true">
        <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H10l-4.5 4v-4A2.5 2.5 0 0 1 4 13.5z" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
        <path d="M8 8.5h8M8 11.5h5" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
      </svg>
      <span v-if="discussions.total" class="db-badge">{{ discussions.total > 99 ? '99+' : discussions.total }}</span>
    </button>
    <div v-if="open" class="db-panel" role="menu">
      <header>Messages</header>
      <p v-if="!discussions.threads.length" class="db-empty">Aucun message non lu.</p>
      <button v-for="t in discussions.threads" :key="t.deploymentId" type="button" class="db-item" @click="go(t.deploymentId)">
        <span class="db-item-top">
          <strong>{{ t.applicationName }} {{ t.version }}</strong>
          <span class="db-count">{{ t.unread }}</span>
        </span>
        <span class="db-item-env">#{{ t.deploymentId }} · {{ t.environmentName }}</span>
        <span class="db-item-msg"><b>{{ t.lastAuthor }}</b> : {{ t.lastMessage }}</span>
        <span class="db-item-age">{{ age(t.lastAt) }}</span>
      </button>
    </div>
  </div>
</template>

<style scoped>
.db { position: relative; }
.db-btn { position: relative; display: grid; place-items: center; width: 40px; height: 40px; border: 1px solid #e5e7eb; border-radius: 12px; background: #fff; color: #4b5563; cursor: pointer; }
.db-btn:hover { color: #6d28d9; border-color: #c4b5fd; }
.db-badge { position: absolute; top: -6px; right: -6px; display: grid; place-items: center; min-width: 20px; height: 20px; padding: 0 5px; border-radius: 999px; background: #dc2626; color: #fff; font-size: 11px; font-weight: 800; box-shadow: 0 0 0 2px #fff; }
.db-panel { position: absolute; right: 0; top: 48px; z-index: 70; width: 340px; max-height: 420px; overflow-y: auto; border: 1px solid #e5e7eb; border-radius: 14px; background: #fff; box-shadow: 0 18px 40px rgba(15, 23, 42, .18); }
.db-panel header { padding: 12px 14px; border-bottom: 1px solid #f1f2f4; font-weight: 800; }
.db-empty { margin: 0; padding: 18px 14px; color: #6b7280; font-size: 13.5px; text-align: center; }
.db-item { display: flex; flex-direction: column; gap: 2px; width: 100%; padding: 10px 14px; border: 0; border-bottom: 1px solid #f5f3ff; background: #fff; font: inherit; text-align: left; cursor: pointer; }
.db-item:hover { background: #faf9ff; }
.db-item-top { display: flex; justify-content: space-between; align-items: center; gap: 8px; font-size: 14px; }
.db-count { min-width: 20px; padding: 0 6px; border-radius: 999px; background: #7c3aed; color: #fff; font-size: 11.5px; font-weight: 800; text-align: center; }
.db-item-env { color: #6b7280; font-size: 12px; }
.db-item-msg { overflow: hidden; color: #374151; font-size: 13px; text-overflow: ellipsis; white-space: nowrap; }
.db-item-age { color: #9ca3af; font-size: 11.5px; }
</style>
