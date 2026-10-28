import { defineStore } from 'pinia'
import { ref } from 'vue'

import {
  getDeploymentMetrics,
} from '@/api/monitoring.api'

import type {
  ApplicationMetrics,
} from '@/types/monitoring'

export const useMonitoringStore =
  defineStore('monitoring', () => {
    const metrics =
      ref<ApplicationMetrics | null>(null)

    const loading = ref(false)

    const errorMessage =
      ref<string | null>(null)

    const currentDeploymentId =
      ref<number | null>(null)

    async function loadDeploymentMetrics(
      deploymentId: number,
    ) {
      loading.value = true
      errorMessage.value = null
      currentDeploymentId.value =
        deploymentId

      try {
        metrics.value =
          await getDeploymentMetrics(
            deploymentId,
          )
      } catch (error) {
        console.error(
          'Erreur lors du chargement des métriques',
          error,
        )

        metrics.value = null

        errorMessage.value =
          'Impossible de charger les métriques du déploiement.'
      } finally {
        loading.value = false
      }
    }

    async function refresh() {
      if (
        currentDeploymentId.value === null
      ) {
        return
      }

      await loadDeploymentMetrics(
        currentDeploymentId.value,
      )
    }

    function clear() {
      metrics.value = null
      errorMessage.value = null
      currentDeploymentId.value = null
    }

    return {
      metrics,
      loading,
      errorMessage,
      currentDeploymentId,

      loadDeploymentMetrics,
      refresh,
      clear,
    }
  })