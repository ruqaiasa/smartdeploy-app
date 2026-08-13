<script setup lang="ts">
import {
  computed,
  onMounted,
  ref,
} from 'vue'

import {
  useRoute,
  useRouter,
} from 'vue-router'

import {
  getApplicationById,
} from '@/api/applications.api'

import {
  getDeployments,
} from '@/api/deployments.api'

import type {
  ApplicationResponse,
} from '@/types/application'

import type {
  DeploymentResponse,
} from '@/types/deployment'

const route = useRoute()
const router = useRouter()

const application =
  ref<ApplicationResponse | null>(null)

const deployments =
  ref<DeploymentResponse[]>([])

const loading = ref(false)

const errorMessage =
  ref<string | null>(null)

const applicationId = computed(() => {
  return Number(route.params.id)
})

onMounted(async () => {
  await loadData()
})

async function loadData() {
  loading.value = true
  errorMessage.value = null

  try {
    const [
      applicationResponse,
      deploymentPage,
    ] = await Promise.all([
      getApplicationById(
        applicationId.value,
      ),

      getDeployments({
        page: 0,
        size: 100,
        applicationId:
          applicationId.value,
      }),
    ])

    application.value =
      applicationResponse

    deployments.value =
      deploymentPage.content
  } catch {
    errorMessage.value =
      'Impossible de charger les déploiements de l’application.'
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push({
    name: 'application-details',

    params: {
      id: applicationId.value,
    },
  })
}

function formatDate(
  value: string | null,
) {
  if (!value) {
    return '—'
  }

  return new Date(value)
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

function formatDuration(
  seconds: number | null,
) {
  if (
    seconds === null
    || seconds === undefined
  ) {
    return '—'
  }

  if (seconds < 60) {
    return `${seconds}s`
  }

  const minutes =
    Math.floor(seconds / 60)

  const remainingSeconds =
    seconds % 60

  return `${minutes}m ${remainingSeconds}s`
}
</script>

<template>
  <section class="page">
    <header class="page-header">
      <div>
        <button
          type="button"
          class="back-button"
          @click="goBack"
        >
          ← Retour au détail
        </button>

        <h2>
          Déploiements
        </h2>

        <p v-if="application">
          Application :
          <strong>
            {{ application.name }}
          </strong>
        </p>
      </div>

      <span class="count-badge">
        {{ deployments.length }}
      </span>
    </header>

    <p
      v-if="errorMessage"
      class="error-message"
    >
      {{ errorMessage }}
    </p>

    <div
      v-if="loading"
      class="state-card"
    >
      Chargement...
    </div>

    <div
      v-else-if="deployments.length === 0"
      class="state-card"
    >
      Aucun déploiement lié à cette application.
    </div>

    <div
      v-else
      class="deployment-list"
    >
      <article
        v-for="deployment in deployments"
        :key="deployment.id"
        class="deployment-card"
      >
        <header class="card-header">
          <div>
            <span class="deployment-label">
              Déploiement #{{ deployment.id }}
            </span>

            <h3>
              Version
              {{ deployment.version }}
            </h3>
          </div>

          <span
            class="status-badge"
            :class="
              deployment.status
                .toLowerCase()
                .replace('_', '-')
            "
          >
            {{ deployment.status }}
          </span>
        </header>

        <div class="details-grid">
          <div class="detail-box">
            <span>
              Environnement
            </span>

            <strong>
              {{ deployment.environmentName }}
            </strong>

            <small>
              {{ deployment.environmentType }}
            </small>
          </div>

          <div class="detail-box">
            <span>
              Demandé par
            </span>

            <strong>
              {{ deployment.requestedByEmail }}
            </strong>
          </div>

          <div class="detail-box">
            <span>
              Build Jenkins
            </span>

            <strong>
              {{
                deployment.jenkinsBuildNumber
                ?? '—'
              }}
            </strong>
          </div>

          <div class="detail-box">
            <span>
              Durée
            </span>

            <strong>
              {{
                formatDuration(
                  deployment.durationSeconds,
                )
              }}
            </strong>
          </div>

          <div class="detail-box">
            <span>
              Début
            </span>

            <strong>
              {{
                formatDate(
                  deployment.startedAt,
                )
              }}
            </strong>
          </div>

          <div class="detail-box">
            <span>
              Fin
            </span>

            <strong>
              {{
                formatDate(
                  deployment.finishedAt,
                )
              }}
            </strong>
          </div>

          <div class="detail-box">
            <span>
              Créé le
            </span>

            <strong>
              {{
                formatDate(
                  deployment.createdAt,
                )
              }}
            </strong>
          </div>

          <div class="detail-box">
            <span>
              Rollback de
            </span>

            <strong>
              {{
                deployment.rollbackOfId
                  ? `#${deployment.rollbackOfId}`
                  : '—'
              }}
            </strong>
          </div>
        </div>

        <p
          v-if="deployment.errorMessage"
          class="deployment-error"
        >
          {{ deployment.errorMessage }}
        </p>

        <a
          v-if="deployment.deploymentLogsUrl"
          :href="deployment.deploymentLogsUrl"
          target="_blank"
          rel="noopener noreferrer"
          class="logs-link"
        >
          Ouvrir les logs Jenkins →
        </a>
      </article>
    </div>
  </section>
</template>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.page-header h2 {
  margin: 10px 0 0;

  color: #0f172a;
  font-size: 26px;
}

.page-header p {
  margin: 7px 0 0;
  color: #64748b;
}

.back-button {
  padding: 0;

  border: 0;
  background: transparent;
  color: #2563eb;

  font-size: 13px;
  font-weight: 700;
}

.count-badge {
  display: grid;

  min-width: 38px;
  height: 38px;

  place-items: center;

  border-radius: 999px;

  background: #eff6ff;
  color: #1d4ed8;

  font-weight: 800;
}

.deployment-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.deployment-card {
  padding: 22px;

  border: 1px solid #e2e8f0;
  border-radius: 15px;

  background: #ffffff;

  box-shadow:
    0 7px 22px rgb(15 23 42 / 5%);
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;

  gap: 18px;

  padding-bottom: 18px;

  border-bottom: 1px solid #f1f5f9;
}

.deployment-label {
  color: #2563eb;

  font-size: 10px;
  font-weight: 800;

  text-transform: uppercase;
}

.card-header h3 {
  margin: 8px 0 0;

  color: #0f172a;
  font-size: 20px;
}

.status-badge {
  display: inline-flex;

  padding: 6px 10px;

  border-radius: 999px;

  font-size: 9px;
  font-weight: 800;
}

.status-badge.pending {
  background: #f1f5f9;
  color: #475569;
}

.status-badge.running {
  background: #eff6ff;
  color: #1d4ed8;
}

.status-badge.success {
  background: #dcfce7;
  color: #15803d;
}

.status-badge.failed {
  background: #fef2f2;
  color: #b91c1c;
}

.status-badge.cancelled {
  background: #fff7ed;
  color: #c2410c;
}

.status-badge.rolled-back {
  background: #f5f3ff;
  color: #6d28d9;
}

.details-grid {
  display: grid;

  grid-template-columns:
    repeat(2, minmax(0, 1fr));

  gap: 12px;

  margin-top: 18px;
}

.detail-box {
  display: flex;

  flex-direction: column;
  gap: 7px;

  padding: 14px;

  border: 1px solid #e2e8f0;
  border-radius: 10px;

  background: #f8fafc;
}

.detail-box span {
  color: #94a3b8;

  font-size: 10px;
  font-weight: 800;

  text-transform: uppercase;
}

.detail-box strong {
  color: #334155;

  font-size: 12px;
}

.detail-box small {
  color: #64748b;
}

.deployment-error {
  margin: 18px 0 0;

  padding: 12px 14px;

  border: 1px solid #fecaca;
  border-radius: 9px;

  background: #fef2f2;
  color: #b91c1c;

  font-size: 12px;
}

.logs-link {
  display: inline-flex;

  margin-top: 18px;

  color: #2563eb;

  font-size: 12px;
  font-weight: 700;

  text-decoration: none;
}

.state-card {
  padding: 50px;

  border: 1px solid #e2e8f0;
  border-radius: 15px;

  background: #ffffff;
  color: #64748b;

  text-align: center;
}

.error-message {
  padding: 12px 14px;

  border: 1px solid #fecaca;
  border-radius: 10px;

  background: #fef2f2;
  color: #b91c1c;
}

@media (max-width: 700px) {
  .page-header {
    flex-direction: column;
  }

  .details-grid {
    grid-template-columns: 1fr;
  }
}
</style>