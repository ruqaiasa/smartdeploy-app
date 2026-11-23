<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getEnvironments } from '@/api/environments.api'
import EnvironmentCharts from '@/components/charts/EnvironmentCharts.vue'
import type { EnvironmentResponse } from '@/types/environment'

/* Supervision : les courbes de chaque environnement, avec les déploiements et les alertes. */
const route = useRoute()
const router = useRouter()
const envs = ref<EnvironmentResponse[]>([])
const selected = ref<number | null>(null)

onMounted(async () => {
  try { envs.value = (await getEnvironments({ size: 200 })).content } catch { envs.value = [] }
  const fromQuery = Number(route.query.env)
  selected.value = fromQuery && envs.value.some(e => e.id === fromQuery) ? fromQuery : envs.value[0]?.id ?? null
})
watch(selected, id => { if (id) void router.replace({ query: { env: String(id) } }) })
const grouped = computed(() => {
  const g: Record<string, EnvironmentResponse[]> = {}
  for (const e of envs.value) (g[e.applicationName ?? '—'] ??= []).push(e)
  return g
})
</script>

<template>
  <section class="sv">
    <header class="sv-head">
      <div>
        <h1>Supervision</h1>
        <p>Processeur, mémoire, pods et redémarrages dans le temps ; les déploiements et les alertes sont marqués sur les courbes.</p>
      </div>
      <select v-model.number="selected" class="sv-select" aria-label="Environnement">
        <optgroup v-for="(list, app) in grouped" :key="app" :label="String(app)">
          <option v-for="e in list" :key="e.id" :value="e.id">{{ e.name }} ({{ e.type }})</option>
        </optgroup>
      </select>
    </header>
    <p v-if="!envs.length" class="sv-empty">Aucun environnement.</p>
    <EnvironmentCharts v-if="selected" :key="selected" :environment-id="selected" />
  </section>
</template>

<style scoped>
.sv { display: flex; flex-direction: column; gap: 14px; }
.sv-head { display: flex; flex-wrap: wrap; align-items: flex-end; justify-content: space-between; gap: 12px; }
.sv-head h1 { margin: 0; font-size: 24px; }
.sv-head p { margin: 4px 0 0; color: #6b7280; font-size: 14px; }
.sv-select { min-width: 280px; padding: 9px 12px; border: 1px solid #e5e7eb; border-radius: 10px; font: inherit; font-size: 14px; background: #fff; }
.sv-empty { color: #6b7280; }
</style>
