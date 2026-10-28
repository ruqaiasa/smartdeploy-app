<script setup lang="ts">
import {
  computed,
  onMounted,
} from 'vue'

import {
  useAlertsStore,
} from '@/stores/alerts'

import type {
  AlertSeverity,
  AlertStatus,
  AlertType,
} from '@/types/alert'

const alertsStore = useAlertsStore()

onMounted(async () => {
  await alertsStore.loadAlerts()
})

const statistics = computed(() => [
  {
    label: 'Alertes ouvertes',
    value: alertsStore.openAlerts.length,
    description: 'Incidents actuellement actifs',
    className: 'open',
  },
  {
    label: 'Critiques',
    value: alertsStore.criticalAlerts.length,
    description: 'Intervention prioritaire',
    className: 'critical',
  },
  {
    label: 'Warnings',
    value: alertsStore.warningAlerts.length,
    description: 'Situations à surveiller',
    className: 'warning',
  },
  {
    label: 'Résolues',
    value: alertsStore.resolvedAlerts.length,
    description: 'Incidents rétablis',
    className: 'resolved',
  },
])

async function changeStatusFilter(
  status: AlertStatus | null,
) {
  await alertsStore.setStatusFilter(status)
}

function formatDate(
  value: string | null,
) {
  if (!value) {
    return '—'
  }

  return new Date(value).toLocaleString(
    'fr-FR',
    {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    },
  )
}

function formatType(
  type: AlertType,
) {
  const labels: Record<AlertType, string> = {
    POD_UNAVAILABLE:
      'Application indisponible',

    POD_AVAILABILITY_DEGRADED:
      'Disponibilité dégradée',

    HIGH_RESTART_COUNT:
      'Redémarrages élevés',

    DEPLOYMENT_DEGRADED:
      'Déploiement dégradé',
  }

  return labels[type]
}

function formatSeverity(
  severity: AlertSeverity,
) {
  if (severity === 'CRITICAL') {
    return 'Critique'
  }

  return 'Warning'
}

function formatStatus(
  status: AlertStatus,
) {
  if (status === 'OPEN') {
    return 'Ouverte'
  }

  return 'Résolue'
}

function formatMetric(
  metricName: string | null,
) {
  if (!metricName) {
    return '—'
  }

  const labels: Record<string, string> = {
    runningPods: 'Pods disponibles',
    restartCount: 'Redémarrages',
  }

  return labels[metricName] ?? metricName
}

function formatMetricValues(
  currentValue: number | null,
  thresholdValue: number | null,
) {
  if (
    currentValue === null
    || currentValue === undefined
  ) {
    return '—'
  }

  if (
    thresholdValue === null
    || thresholdValue === undefined
  ) {
    return String(currentValue)
  }

  return `${currentValue} / ${thresholdValue}`
}
</script>

<template>
  <section class="alerts-page">
    <header class="page-header">
      <div>
        <h2>Alertes</h2>

        <p>
          Surveillez les incidents détectés
          automatiquement sur vos déploiements.
        </p>
      </div>

      <button
        type="button"
        class="refresh-button"
        :disabled="alertsStore.loading"
        @click="alertsStore.refresh"
      >
        {{
          alertsStore.loading
            ? 'Actualisation...'
            : 'Actualiser'
        }}
      </button>
    </header>

    <section class="statistics-grid">
      <article
        v-for="statistic in statistics"
        :key="statistic.label"
        class="statistic-card"
        :class="statistic.className"
      >
        <p>{{ statistic.label }}</p>

        <strong>
          {{ statistic.value }}
        </strong>

        <small>
          {{ statistic.description }}
        </small>
      </article>
    </section>

    <section class="toolbar">
      <div class="filter-group">
        <button
          type="button"
          class="filter-button"
          :class="{
            active:
              alertsStore.statusFilter === null,
          }"
          :disabled="alertsStore.loading"
          @click="changeStatusFilter(null)"
        >
          Toutes
        </button>

        <button
          type="button"
          class="filter-button"
          :class="{
            active:
              alertsStore.statusFilter === 'OPEN',
          }"
          :disabled="alertsStore.loading"
          @click="changeStatusFilter('OPEN')"
        >
          Ouvertes
        </button>

        <button
          type="button"
          class="filter-button"
          :class="{
            active:
              alertsStore.statusFilter === 'RESOLVED',
          }"
          :disabled="alertsStore.loading"
          @click="changeStatusFilter('RESOLVED')"
        >
          Résolues
        </button>
      </div>

      <span class="result-count">
        {{ alertsStore.alerts.length }}
        alerte(s)
      </span>
    </section>

    <p
      v-if="alertsStore.errorMessage"
      class="error-message"
    >
      {{ alertsStore.errorMessage }}
    </p>

    <section class="table-card">
      <div
        v-if="alertsStore.loading"
        class="state-message"
      >
        Chargement des alertes...
      </div>

      <div
        v-else-if="
          alertsStore.alerts.length === 0
        "
        class="state-message"
      >
        Aucune alerte trouvée.
      </div>

      <div
        v-else
        class="table-wrapper"
      >
        <table>
          <thead>
            <tr>
              <th>Application</th>
              <th>Environnement</th>
              <th>Alerte</th>
              <th>Sévérité</th>
              <th>Statut</th>
              <th>Métrique</th>
              <th>Valeur / Seuil</th>
              <th>Déploiement</th>
              <th>Dernière détection</th>
              <th>Résolution</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="alert in alertsStore.alerts"
              :key="alert.id"
            >
              <td>
                <div class="entity-info">
                  <strong>
                    {{ alert.applicationName }}
                  </strong>

                  <small>
                    Application #{{ alert.applicationId }}
                  </small>
                </div>
              </td>

              <td>
                <div class="entity-info">
                  <strong>
                    {{ alert.environmentName }}
                  </strong>

                  <small>
                    Environnement #{{ alert.environmentId }}
                  </small>
                </div>
              </td>

              <td>
                <div class="alert-info">
                  <strong>
                    {{ alert.title }}
                  </strong>

                  <span>
                    {{ formatType(alert.type) }}
                  </span>

                  <small>
                    {{ alert.message }}
                  </small>
                </div>
              </td>

              <td>
                <span
                  class="badge severity-badge"
                  :class="
                    alert.severity.toLowerCase()
                  "
                >
                  {{
                    formatSeverity(
                      alert.severity,
                    )
                  }}
                </span>
              </td>

              <td>
                <span
                  class="badge status-badge"
                  :class="
                    alert.status.toLowerCase()
                  "
                >
                  {{
                    formatStatus(
                      alert.status,
                    )
                  }}
                </span>
              </td>

              <td>
                {{
                  formatMetric(
                    alert.metricName,
                  )
                }}
              </td>

              <td>
                <strong class="metric-value">
                  {{
                    formatMetricValues(
                      alert.currentValue,
                      alert.thresholdValue,
                    )
                  }}
                </strong>
              </td>

              <td>
                {{
                  alert.deploymentId
                    ? `#${alert.deploymentId}`
                    : '—'
                }}
              </td>

              <td>
                {{
                  formatDate(
                    alert.lastDetectedAt,
                  )
                }}
              </td>

              <td>
                {{
                  formatDate(
                    alert.resolvedAt,
                  )
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </section>
</template>

<style scoped>
.alerts-page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.page-header h2 {
  margin: 0;
  color: #0f172a;
  font-size: 26px;
}

.page-header p {
  margin: 7px 0 0;
  color: #64748b;
}

.refresh-button {
  min-height: 42px;
  padding: 0 18px;
  border: 0;
  border-radius: 10px;
  background: #2563eb;
  color: #ffffff;
  font-weight: 700;
  cursor: pointer;
}

.refresh-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.statistics-grid {
  display: grid;
  grid-template-columns:
    repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.statistic-card {
  display: flex;
  min-height: 135px;
  flex-direction: column;
  padding: 20px;
  border: 1px solid #e2e8f0;
  border-radius: 15px;
  background: #ffffff;
  box-shadow:
    0 8px 24px rgb(15 23 42 / 4%);
}

.statistic-card p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.statistic-card strong {
  margin-top: 12px;
  color: #0f172a;
  font-size: 30px;
}

.statistic-card small {
  margin-top: auto;
  color: #64748b;
}

.statistic-card.critical {
  border-top: 3px solid #dc2626;
}

.statistic-card.warning {
  border-top: 3px solid #f59e0b;
}

.statistic-card.open {
  border-top: 3px solid #2563eb;
}

.statistic-card.resolved {
  border-top: 3px solid #16a34a;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.filter-group {
  display: flex;
  gap: 8px;
}

.filter-button {
  min-height: 38px;
  padding: 0 15px;
  border: 1px solid #cbd5e1;
  border-radius: 9px;
  background: #ffffff;
  color: #475569;
  font-weight: 700;
  cursor: pointer;
}

.filter-button.active {
  border-color: #2563eb;
  background: #eff6ff;
  color: #1d4ed8;
}

.result-count {
  color: #64748b;
  font-size: 12px;
}

.table-card {
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 15px;
  background: #ffffff;
  box-shadow:
    0 8px 24px rgb(15 23 42 / 4%);
}

.table-wrapper {
  overflow-x: auto;
}

table {
  width: 100%;
  border-collapse: collapse;
}

th,
td {
  padding: 15px 16px;
  border-bottom: 1px solid #f1f5f9;
  text-align: left;
  vertical-align: middle;
}

th {
  color: #64748b;
  font-size: 10px;
  white-space: nowrap;
  text-transform: uppercase;
}

td {
  color: #334155;
  font-size: 12px;
}

.entity-info,
.alert-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.entity-info small,
.alert-info small,
.alert-info span {
  color: #64748b;
  font-size: 10px;
}

.alert-info {
  min-width: 220px;
}

.alert-info small {
  max-width: 300px;
  line-height: 1.45;
}

.badge {
  display: inline-flex;
  padding: 5px 9px;
  border-radius: 999px;
  font-size: 9px;
  font-weight: 800;
  white-space: nowrap;
}

.severity-badge.critical {
  background: #fef2f2;
  color: #b91c1c;
}

.severity-badge.warning {
  background: #fff7ed;
  color: #c2410c;
}

.status-badge.open {
  background: #eff6ff;
  color: #1d4ed8;
}

.status-badge.resolved {
  background: #dcfce7;
  color: #15803d;
}

.metric-value {
  white-space: nowrap;
}

.error-message {
  margin: 0;
  padding: 11px 13px;
  border: 1px solid #fecaca;
  border-radius: 9px;
  background: #fef2f2;
  color: #b91c1c;
}

.state-message {
  padding: 55px;
  color: #64748b;
  text-align: center;
}

@media (max-width: 1050px) {
  .statistics-grid {
    grid-template-columns:
      repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 650px) {
  .page-header,
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .statistics-grid {
    grid-template-columns: 1fr;
  }

  .filter-group {
    flex-wrap: wrap;
  }
}
</style>