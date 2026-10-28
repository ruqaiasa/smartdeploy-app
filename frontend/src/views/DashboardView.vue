<script setup lang="ts">
import {
  computed,
  onMounted,
} from 'vue'

import { RouterLink } from 'vue-router'

import { useDashboardStore } from '@/stores/dashboard'

const dashboardStore =
  useDashboardStore()

const statistics = computed(() => {
  const dashboard =
    dashboardStore.dashboard

  if (!dashboard) {
    return []
  }

  return [
    {
      label: 'Applications',
      value: dashboard.totalApplications,
      description:
        `${dashboard.activeApplications} application(s) active(s)`,
    },
    {
      label: 'Environnements',
      value: dashboard.totalEnvironments,
      description:
        `${dashboard.activeEnvironments} environnement(s) actif(s)`,
    },
    {
      label: 'Déploiements',
      value: dashboard.totalDeployments,
      description:
        `${dashboard.successfulDeployments} réussi(s) · ${dashboard.failedDeployments} échoué(s)`,
    },
    {
      label: 'Taux de réussite',
      value:
        `${dashboard.successRate.toFixed(2)} %`,
      description:
        'Déploiements terminés',
    },
    {
      label: 'Alertes ouvertes',
      value: dashboard.openAlerts,
      description:
        dashboard.openAlerts === 0
          ? 'Aucune alerte active'
          : 'Nécessite une attention',
    },
  ]
})

const recentDeployments = computed(
  () =>
    dashboardStore.dashboard
      ?.recentDeployments ?? [],
)

function formatDate(date: string) {
  return new Date(date)
    .toLocaleString(
      'fr-FR',
      {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
      },
    )
}

function statusLabel(status: string) {
  switch (status) {
    case 'SUCCESS':
      return 'Réussi'

    case 'FAILED':
      return 'Échoué'

    case 'RUNNING':
      return 'En cours'

    case 'PENDING':
      return 'En attente'

    case 'CANCELLED':
      return 'Annulé'

    case 'ROLLED_BACK':
      return 'Rollback'

    default:
      return status
  }
}

onMounted(async () => {
  await dashboardStore.loadDashboard()
})
</script>

<template>
  <section class="dashboard">

    <div class="welcome-section">
      <div>
        <h2>
          Tableau de bord
        </h2>

        <p>
          Vue globale et temps réel de votre
          plateforme SmartDeploy.
        </p>
      </div>

      <RouterLink
        to="/deployments"
        class="primary-button"
      >
        Voir les déploiements
      </RouterLink>
    </div>

    <div
      v-if="dashboardStore.loading"
      class="state-card"
    >
      Chargement du tableau de bord...
    </div>

    <div
      v-else-if="dashboardStore.errorMessage"
      class="state-card error"
    >
      <p>
        {{ dashboardStore.errorMessage }}
      </p>

      <button
        type="button"
        class="retry-button"
        @click="dashboardStore.refresh()"
      >
        Réessayer
      </button>
    </div>

    <template v-else-if="dashboardStore.dashboard">

      <div class="statistics-grid">
        <article
          v-for="statistic in statistics"
          :key="statistic.label"
          class="statistic-card"
        >
          <p>
            {{ statistic.label }}
          </p>

          <strong>
            {{ statistic.value }}
          </strong>

          <small>
            {{ statistic.description }}
          </small>
        </article>
      </div>

      <section class="table-card">

        <div class="table-header">
          <div>
            <h3>
              Déploiements récents
            </h3>

            <p>
              Les cinq dernières opérations
              enregistrées par SmartDeploy.
            </p>
          </div>

          <RouterLink to="/deployments">
            Voir tout
          </RouterLink>
        </div>

        <div
          v-if="recentDeployments.length === 0"
          class="empty-state"
        >
          Aucun déploiement disponible.
        </div>

        <div
          v-else
          class="table-wrapper"
        >
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Application</th>
                <th>Version</th>
                <th>Environnement</th>
                <th>Date</th>
                <th>Statut</th>
              </tr>
            </thead>

            <tbody>
              <tr
                v-for="deployment in recentDeployments"
                :key="deployment.id"
              >
                <td>
                  #{{ deployment.id }}
                </td>

                <td>
                  <RouterLink
                    :to="`/applications/${deployment.applicationId}`"
                    class="entity-link"
                  >
                    {{ deployment.applicationName }}
                  </RouterLink>
                </td>

                <td>
                  {{ deployment.version }}
                </td>

                <td>
                  <div class="environment-cell">
                    <strong>
                      {{ deployment.environmentName }}
                    </strong>

                    <small>
                      {{ deployment.environmentType }}
                    </small>
                  </div>
                </td>

                <td>
                  {{ formatDate(deployment.createdAt) }}
                </td>

                <td>
                  <span
                    class="status"
                    :class="deployment.status.toLowerCase()"
                  >
                    {{ statusLabel(deployment.status) }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

      </section>

    </template>

  </section>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.welcome-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.welcome-section h2 {
  margin: 0;

  color: #0f172a;
  font-size: 25px;
}

.welcome-section p {
  margin: 7px 0 0;

  color: #64748b;
  font-size: 14px;
}

.primary-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;

  padding: 11px 18px;

  border-radius: 9px;

  background: #2563eb;
  color: white;

  font-size: 13px;
  font-weight: 600;

  text-decoration: none;
}

.statistics-grid {
  display: grid;
  grid-template-columns:
    repeat(auto-fit, minmax(190px, 1fr));
  gap: 18px;
}

.statistic-card {
  padding: 20px;

  border: 1px solid #e2e8f0;
  border-radius: 12px;

  background: white;
}

.statistic-card p {
  margin: 0;

  color: #64748b;
  font-size: 13px;
}

.statistic-card strong {
  display: block;

  margin-top: 12px;

  color: #0f172a;
  font-size: 28px;
}

.statistic-card small {
  display: block;

  margin-top: 8px;

  color: #64748b;
  font-size: 12px;
}

.table-card {
  overflow: hidden;

  border: 1px solid #e2e8f0;
  border-radius: 12px;

  background: white;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  padding: 20px;

  border-bottom: 1px solid #e2e8f0;
}

.table-header h3 {
  margin: 0;

  color: #0f172a;
  font-size: 16px;
}

.table-header p {
  margin: 5px 0 0;

  color: #64748b;
  font-size: 12px;
}

.table-header a {
  color: #2563eb;

  font-size: 13px;
  font-weight: 600;

  text-decoration: none;
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
  padding: 14px 20px;

  border-bottom: 1px solid #f1f5f9;

  text-align: left;
}

th {
  color: #64748b;

  background: #f8fafc;

  font-size: 11px;
  font-weight: 600;

  text-transform: uppercase;
}

td {
  color: #334155;

  font-size: 13px;
}

tbody tr:last-child td {
  border-bottom: none;
}

.entity-link {
  color: #2563eb;

  font-weight: 600;

  text-decoration: none;
}

.entity-link:hover {
  text-decoration: underline;
}

.environment-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.environment-cell strong {
  color: #334155;

  font-size: 13px;
}

.environment-cell small {
  color: #94a3b8;

  font-size: 10px;
}

.status {
  display: inline-flex;

  padding: 5px 9px;

  border-radius: 999px;

  font-size: 11px;
  font-weight: 700;
}

.status.success {
  background: #dcfce7;
  color: #166534;
}

.status.failed {
  background: #fee2e2;
  color: #991b1b;
}

.status.running {
  background: #dbeafe;
  color: #1d4ed8;
}

.status.pending {
  background: #fef3c7;
  color: #92400e;
}

.status.cancelled {
  background: #f1f5f9;
  color: #475569;
}

.status.rolled_back {
  background: #ede9fe;
  color: #6d28d9;
}

.state-card,
.empty-state {
  padding: 28px;

  border: 1px solid #e2e8f0;
  border-radius: 12px;

  background: white;

  color: #64748b;

  text-align: center;
}

.state-card.error {
  color: #991b1b;
}

.retry-button {
  margin-top: 12px;
  padding: 8px 14px;

  border: none;
  border-radius: 7px;

  background: #2563eb;
  color: white;

  cursor: pointer;
}

@media (max-width: 700px) {
  .welcome-section {
    align-items: flex-start;
    flex-direction: column;
  }

  .statistics-grid {
    grid-template-columns: 1fr;
  }
}
</style>