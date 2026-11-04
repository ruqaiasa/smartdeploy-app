<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import type { DeploymentGuard } from '@/types/guard'

/* Badge du gardien sous le statut d'un déploiement ; un clic ouvre le détail. */
const props = defineProps<{ guard: DeploymentGuard }>()
defineEmits<{ open: [] }>()

const now = ref(Date.now())
let timer: ReturnType<typeof setInterval> | null = null
onMounted(() => { timer = setInterval(() => (now.value = Date.now()), 15_000) })
onBeforeUnmount(() => { if (timer) clearInterval(timer) })

const remaining = computed(() => {
  const minutes = Math.ceil((new Date(props.guard.endsAt).getTime() - now.value) / 60_000)
  return minutes > 1 ? `${minutes} min` : 'moins d’une minute'
})

const view = computed(() => {
  switch (props.guard.status) {
    case 'WATCHING': return { tone: 'watching', label: `Surveillance · ${remaining.value}` }
    case 'HEALTHY': return { tone: 'healthy', label: 'Validé par le gardien' }
    case 'HEALTHY_WITH_WARNINGS': return { tone: 'warning', label: 'Validé avec réserves' }
    case 'DEGRADED': return { tone: 'degraded', label: 'Dégradation détectée' }
    case 'INCONCLUSIVE': return { tone: 'neutral', label: 'Surveillance non concluante' }
    default: return null
  }
})
</script>

<template>
  <button v-if="view" type="button" class="gb" :class="`is-${view.tone}`" title="Voir la garde de déploiement" @click="$emit('open')">
    <svg viewBox="0 0 20 20" width="13" height="13" aria-hidden="true">
      <path d="M10 2.5 4 5v4.6c0 3.8 2.6 6.9 6 7.9 3.4-1 6-4.1 6-7.9V5l-6-2.5Z" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linejoin="round" />
    </svg>
    <span>{{ view.label }}</span>
    <span v-if="guard.status === 'WATCHING'" class="gb-pulse" aria-hidden="true" />
  </button>
</template>

<style scoped>
.gb {
  display: inline-flex; align-items: center; gap: 5px; margin-top: 5px; padding: 2px 8px;
  border: 1px solid transparent; border-radius: 999px; background: none;
  font: inherit; font-size: 11.5px; font-weight: 500; line-height: 18px; cursor: pointer;
}
.gb:focus-visible { outline: 2px solid var(--color-primary-hover, #2563eb); outline-offset: 2px; }
.gb.is-watching { border-color: #bfdbfe; background: #eff6ff; color: #1d4ed8; }
.gb.is-healthy { border-color: #a7f3d0; background: #ecfdf5; color: #047857; }
.gb.is-warning { border-color: #fde68a; background: #fffbeb; color: #b45309; }
.gb.is-degraded { border-color: #fecaca; background: #fef2f2; color: #b91c1c; }
.gb.is-neutral { border-color: var(--color-gray-200, #e5e7eb); background: var(--color-gray-50, #f9fafb); color: var(--color-gray-500, #6b7280); }
.gb-pulse { width: 6px; height: 6px; border-radius: 50%; background: currentColor; animation: gb-pulse 1.6s ease-in-out infinite; }
@keyframes gb-pulse { 50% { opacity: .25; } }
@media (prefers-reduced-motion: reduce) { .gb-pulse { animation: none; } }
</style>
