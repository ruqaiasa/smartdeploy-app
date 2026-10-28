import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import {
  getAlerts,
  getOpenAlerts,
} from '@/api/alerts.api'

import type {
  Alert,
  AlertStatus,
} from '@/types/alert'

export const useAlertsStore =
  defineStore('alerts', () => {
    /*
     * =========================================================
     * PAGE ALERTS
     * =========================================================
     */

    const alerts = ref<Alert[]>([])

    const loading = ref(false)

    const errorMessage =
      ref<string | null>(null)

    const statusFilter =
      ref<AlertStatus | null>(null)

    /*
     * =========================================================
     * GLOBAL MONITORING
     * =========================================================
     *
     * Ces alertes sont indépendantes des filtres de la page
     * Alertes.
     */

    const globalOpenAlerts =
      ref<Alert[]>([])

    const globalLoading =
      ref(false)

    const globalErrorMessage =
      ref<string | null>(null)

    /*
     * =========================================================
     * COMPUTED - PAGE
     * =========================================================
     */

    const openAlerts = computed(() =>
      alerts.value.filter(
        (alert) => alert.status === 'OPEN',
      ),
    )

    const resolvedAlerts = computed(() =>
      alerts.value.filter(
        (alert) => alert.status === 'RESOLVED',
      ),
    )

    const criticalAlerts = computed(() =>
      alerts.value.filter(
        (alert) =>
          alert.status === 'OPEN'
          && alert.severity === 'CRITICAL',
      ),
    )

    const warningAlerts = computed(() =>
      alerts.value.filter(
        (alert) =>
          alert.status === 'OPEN'
          && alert.severity === 'WARNING',
      ),
    )

    /*
     * =========================================================
     * COMPUTED - GLOBAL
     * =========================================================
     */

    const globalOpenAlertCount =
      computed(
        () => globalOpenAlerts.value.length,
      )

    const globalCriticalAlertCount =
      computed(
        () =>
          globalOpenAlerts.value.filter(
            (alert) =>
              alert.severity === 'CRITICAL',
          ).length,
      )

    const globalWarningAlertCount =
      computed(
        () =>
          globalOpenAlerts.value.filter(
            (alert) =>
              alert.severity === 'WARNING',
          ).length,
      )

    /*
     * =========================================================
     * PAGE ALERTS
     * =========================================================
     */

    async function loadAlerts() {
      loading.value = true
      errorMessage.value = null

      try {
        if (statusFilter.value === 'OPEN') {
          alerts.value =
            await getOpenAlerts()

          return
        }

        const result =
          await getAlerts()

        if (statusFilter.value === 'RESOLVED') {
          alerts.value =
            result.filter(
              (alert) =>
                alert.status === 'RESOLVED',
            )

          return
        }

        alerts.value = result
      } catch (error) {
        console.error(
          'Erreur lors du chargement des alertes',
          error,
        )

        errorMessage.value =
          'Impossible de charger les alertes.'
      } finally {
        loading.value = false
      }
    }

    async function setStatusFilter(
      status: AlertStatus | null,
    ) {
      statusFilter.value = status

      await loadAlerts()
    }

    async function refresh() {
      await loadAlerts()
    }

    /*
     * =========================================================
     * GLOBAL ALERT MONITORING
     * =========================================================
     */

    async function loadGlobalOpenAlerts() {
      if (globalLoading.value) {
        return globalOpenAlerts.value
      }

      globalLoading.value = true
      globalErrorMessage.value = null

      try {
        const result =
          await getOpenAlerts()

        globalOpenAlerts.value = result

        return result
      } catch (error) {
        console.error(
          'Erreur lors de la surveillance globale des alertes',
          error,
        )

        globalErrorMessage.value =
          'Impossible de vérifier les alertes ouvertes.'

        return globalOpenAlerts.value
      } finally {
        globalLoading.value = false
      }
    }

    return {
      alerts,
      loading,
      errorMessage,
      statusFilter,

      openAlerts,
      resolvedAlerts,
      criticalAlerts,
      warningAlerts,

      globalOpenAlerts,
      globalLoading,
      globalErrorMessage,

      globalOpenAlertCount,
      globalCriticalAlertCount,
      globalWarningAlertCount,

      loadAlerts,
      setStatusFilter,
      refresh,

      loadGlobalOpenAlerts,
    }
  })