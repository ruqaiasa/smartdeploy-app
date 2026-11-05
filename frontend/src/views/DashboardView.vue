<script setup lang="ts">
import DoraPanel from '@/components/dashboard/DoraPanel.vue'
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

    <!-- Header -->
    <div class="dashboard-header flex-between">
      <div>
        <h1>Tableau de bord</h1>
        <p class="subtitle">
          Vue globale et temps réel de votre plateforme SmartDeploy
        </p>
      </div>

      <RouterLink
        to="/deployments"
        class="button button-primary"
      >
        Voir les déploiements →
      </RouterLink>
    </div>

    <!-- Indicateurs DORA -->
    <DoraPanel />

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

        <!-- Deployments List (Cards) -->
        <div
          v-else
          class="deployments-list"
        >
          <article
            v-for="deployment in recentDeployments"
            :key="deployment.id"
            class="deployment-card"
          >
            <!-- Left: Application Info -->
            <div class="deployment-main">
              <RouterLink
                :to="`/applications/${deployment.applicationId}`"
                class="app-name"
              >
                {{ deployment.applicationName }}
              </RouterLink>

              <div class="deployment-details">
                <span class="version-badge">
                  v{{ deployment.version }}
                </span>

                <span class="environment-badge">
                  {{ deployment.environmentType }}
                </span>

                <span class="deployment-date">
                  {{ formatDate(deployment.createdAt) }}
                </span>
              </div>
            </div>

            <!-- Right: Environment + Status -->
            <div class="deployment-side">
              <div class="environment-info">
                <div class="env-label">
                  {{ deployment.environmentName }}
                </div>
              </div>

              <span
                class="status"
                :class="deployment.status.toLowerCase()"
              >
                {{ statusLabel(deployment.status) }}
              </span>
            </div>
          </article>
        </div>

      </section>

    </template>

  </section>
</template>

<style scoped>
.dashboard {
  display: flex;
  flex-direction: column;
  gap: var(--space-2xl);
}

/* HEADER */

.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-xl);
  flex-wrap: wrap;
}

.dashboard-header h1 {
  margin-bottom: var(--space-sm);
}

.subtitle {
  color: var(--color-gray-500);
  font-size: var(--font-size-lg);
  margin-top: var(--space-xs);
}

/* METRICS SECTION */

.statistics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--space-md);
}

.statistic-card {
  padding: var(--space-lg);
  background: white;
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--border-radius-lg);
  box-shadow: var(--shadow-sm);
  transition: all var(--transition-normal);
}

.statistic-card:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--color-primary);
}

.statistic-card p {
  margin: 0;
  color: var(--color-gray-500);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.statistic-card strong {
  display: block;
  margin-top: var(--space-md);
  color: var(--color-slate-dark);
  font-size: 32px;
  font-weight: var(--font-weight-bold);
}

.statistic-card small {
  display: block;
  margin-top: var(--space-sm);
  color: var(--color-gray-500);
  font-size: var(--font-size-sm);
}

/* TABLE SECTION */

.table-card {
  background: white;
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--border-radius-lg);
  box-shadow: var(--shadow-sm);
  overflow: hidden;
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: var(--space-lg);
  border-bottom: var(--border-width) solid var(--border-color);
  flex-wrap: wrap;
}

.table-header h3 {
  margin-bottom: var(--space-xs);
}

.table-header p {
  margin: 0;
  color: var(--color-gray-500);
  font-size: var(--font-size-sm);
}

.table-header a {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  white-space: nowrap;
}

/* DEPLOYMENTS LIST */

.deployments-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.deployment-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-lg);
  padding: var(--space-lg);
  background: white;
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--border-radius-lg);
  transition: all var(--transition-normal);
  cursor: pointer;
}

.deployment-card:hover {
  box-shadow: var(--shadow-md);
  border-color: var(--color-primary);
}

.deployment-main {
  flex: 1;
  min-width: 0;
}

.app-name {
  display: block;
  color: var(--color-slate-dark);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
  margin-bottom: var(--space-sm);
  text-decoration: none;
  transition: color var(--transition-fast);
}

.app-name:hover {
  color: var(--color-primary);
}

.deployment-details {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.version-badge,
.environment-badge,
.deployment-date {
  font-size: var(--font-size-sm);
  color: var(--color-gray-600);
}

.version-badge {
  font-weight: var(--font-weight-semibold);
  color: var(--color-primary);
  background-color: var(--color-primary-light);
  padding: 2px 8px;
  border-radius: var(--border-radius-sm);
}

.environment-badge {
  background-color: var(--color-gray-100);
  color: var(--color-gray-700);
  padding: 2px 8px;
  border-radius: var(--border-radius-sm);
  text-transform: uppercase;
  letter-spacing: 0.3px;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
}

.deployment-date {
  margin-left: auto;
  color: var(--color-gray-500);
}

.deployment-side {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
  white-space: nowrap;
}

.environment-info {
  text-align: right;
}

.env-label {
  color: var(--color-gray-800);
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-base);
}


.status {
  display: inline-flex;
  padding: 4px 12px;
  border-radius: var(--border-radius-full);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-bold);
}

.status.success {
  background-color: var(--color-success-light);
  color: var(--color-success-dark);
}

.status.failed {
  background-color: var(--color-error-light);
  color: var(--color-error-dark);
}

.status.running {
  background-color: var(--color-primary-light);
  color: var(--color-primary-hover);
}

.status.pending {
  background-color: var(--color-warning-light);
  color: var(--color-warning-dark);
}

.status.cancelled {
  background-color: var(--color-gray-100);
  color: var(--color-gray-600);
}

.status.rolled_back {
  background-color: #ede9fe;
  color: #6d28d9;
}

/* STATE MESSAGES */

.state-card,
.empty-state {
  padding: var(--space-2xl);
  background: white;
  border: var(--border-width) solid var(--border-color);
  border-radius: var(--border-radius-lg);
  color: var(--color-gray-500);
  text-align: center;
}

.state-card.error {
  color: var(--color-error-dark);
  background-color: var(--color-error-light);
  border-color: var(--color-error);
}

.retry-button {
  margin-top: var(--space-md);
  padding: var(--space-sm) var(--space-md);
  border: none;
  border-radius: var(--border-radius-md);
  background: var(--color-primary);
  color: white;
  font-weight: var(--font-weight-semibold);
  cursor: pointer;
  transition: background var(--transition-fast);
}

.retry-button:hover {
  background: var(--color-primary-hover);
}

/* RESPONSIVE */

/* RESPONSIVE */

@media (max-width: 768px) {
  .dashboard-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .statistics-grid {
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  }

  .table-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .deployment-card {
    flex-direction: column;
    align-items: flex-start;
  }

  .deployment-side {
    width: 100%;
    justify-content: space-between;
  }

  .deployment-date {
    margin-left: 0;
  }
}

@media (max-width: 480px) {
  .dashboard {
    gap: var(--space-lg);
  }

  .statistics-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-header h1 {
    font-size: var(--font-size-2xl);
  }

  .deployment-card {
    padding: var(--space-md);
  }

  .app-name {
    font-size: var(--font-size-base);
  }

  .deployment-details {
    gap: var(--space-sm);
  }

  .deployment-side {
    flex-direction: column;
    align-items: flex-start;
  }

  .environment-info {
    text-align: left;
  }
}
</style>