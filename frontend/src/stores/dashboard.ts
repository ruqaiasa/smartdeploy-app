import { defineStore } from 'pinia'
import { ref } from 'vue'

import { getDashboard } from '@/api/dashboard.api'
import type { Dashboard } from '@/types/dashboard'

export const useDashboardStore =
  defineStore('dashboard', () => {

    const dashboard =
      ref<Dashboard | null>(null)

    const loading = ref(false)

    const errorMessage =
      ref<string | null>(null)

    async function loadDashboard() {
      loading.value = true
      errorMessage.value = null

      try {
        dashboard.value =
          await getDashboard()
      } catch (error) {
        console.error(
          'Erreur lors du chargement du dashboard',
          error,
        )

        dashboard.value = null

        errorMessage.value =
          'Impossible de charger les données du tableau de bord.'
      } finally {
        loading.value = false
      }
    }

    async function refresh() {
      await loadDashboard()
    }

    return {
      dashboard,
      loading,
      errorMessage,
      loadDashboard,
      refresh,
    }
  })