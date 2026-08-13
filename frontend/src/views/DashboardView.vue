<script setup lang="ts">
const statistics = [
  {
    label: 'Applications',
    value: 8,
    description: '7 applications actives',
  },
  {
    label: 'Environnements',
    value: 15,
    description: 'DEV, TEST et PROD',
  },
  {
    label: 'Déploiements',
    value: 126,
    description: '18 durant les 7 derniers jours',
  },
  {
    label: 'Taux de réussite',
    value: '94%',
    description: '6 déploiements échoués',
  },
]

const recentDeployments = [
  {
    id: 126,
    application: 'SmartDeploy Backend',
    version: '1.4.0',
    environment: 'PRODUCTION',
    status: 'SUCCESS',
  },
  {
    id: 125,
    application: 'Payment API',
    version: '2.1.0',
    environment: 'TEST',
    status: 'RUNNING',
  },
  {
    id: 124,
    application: 'SmartDeploy Frontend',
    version: '1.3.2',
    environment: 'STAGING',
    status: 'FAILED',
  },
]
</script>

<template>
  <section class="dashboard">
    <div class="welcome-section">
      <div>
        <h2>Bonjour Ruqaia 👋</h2>
        <p>
          Voici une vue globale de votre plateforme de déploiement.
        </p>
      </div>

      <button type="button" class="primary-button">
        + Nouveau déploiement
      </button>
    </div>

    <div class="statistics-grid">
      <article
        v-for="statistic in statistics"
        :key="statistic.label"
        class="statistic-card"
      >
        <p>{{ statistic.label }}</p>
        <strong>{{ statistic.value }}</strong>
        <small>{{ statistic.description }}</small>
      </article>
    </div>

    <section class="table-card">
      <div class="table-header">
        <div>
          <h3>Déploiements récents</h3>
          <p>Suivi des dernières opérations</p>
        </div>

        <RouterLink to="/deployments">
          Voir tout
        </RouterLink>
      </div>

      <div class="table-wrapper">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Application</th>
              <th>Version</th>
              <th>Environnement</th>
              <th>Statut</th>
            </tr>
          </thead>

          <tbody>
            <tr
              v-for="deployment in recentDeployments"
              :key="deployment.id"
            >
              <td>#{{ deployment.id }}</td>
              <td>{{ deployment.application }}</td>
              <td>{{ deployment.version }}</td>
              <td>{{ deployment.environment }}</td>

              <td>
                <span
                  class="status"
                  :class="deployment.status.toLowerCase()"
                >
                  {{ deployment.status }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
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
}

.primary-button {
  min-height: 42px;
  padding: 0 18px;

  border: 0;
  border-radius: 10px;
  background: #2563eb;
  color: #ffffff;

  font-weight: 700;
}

.statistics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.statistic-card {
  display: flex;
  min-height: 145px;
  flex-direction: column;

  padding: 22px;
  border: 1px solid #e2e8f0;
  border-radius: 15px;
  background: #ffffff;

  box-shadow: 0 8px 24px rgb(15 23 42 / 4%);
}

.statistic-card p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
  font-weight: 700;
}

.statistic-card strong {
  margin-top: 13px;

  color: #0f172a;
  font-size: 30px;
}

.statistic-card small {
  margin-top: auto;
  color: #64748b;
}

.table-card {
  overflow: hidden;

  border: 1px solid #e2e8f0;
  border-radius: 15px;
  background: #ffffff;

  box-shadow: 0 8px 24px rgb(15 23 42 / 4%);
}

.table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;

  padding: 22px 24px;
  border-bottom: 1px solid #e2e8f0;
}

.table-header h3 {
  margin: 0;
  color: #0f172a;
}

.table-header p {
  margin: 5px 0 0;
  color: #64748b;
  font-size: 13px;
}

.table-header a {
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
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
  padding: 16px 24px;
  border-bottom: 1px solid #f1f5f9;
  text-align: left;
}

th {
  color: #64748b;
  font-size: 11px;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

td {
  color: #334155;
  font-size: 13px;
}

.status {
  display: inline-flex;
  padding: 5px 9px;

  border-radius: 999px;

  font-size: 10px;
  font-weight: 800;
}

.status.success {
  background: #dcfce7;
  color: #15803d;
}

.status.running {
  background: #dbeafe;
  color: #1d4ed8;
}

.status.failed {
  background: #fee2e2;
  color: #b91c1c;
}

@media (max-width: 1050px) {
  .statistics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 650px) {
  .welcome-section {
    align-items: flex-start;
    flex-direction: column;
  }

  .statistics-grid {
    grid-template-columns: 1fr;
  }
}
</style>