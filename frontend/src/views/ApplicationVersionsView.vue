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
  getVersionsByApplication,
} from '@/api/versions.api'

import type {
  ApplicationResponse,
} from '@/types/application'

import type {
  VersionResponse,
} from '@/types/version'

const route = useRoute()
const router = useRouter()

const application =
  ref<ApplicationResponse | null>(null)

const versions =
  ref<VersionResponse[]>([])

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
      versionsResponse,
    ] = await Promise.all([
      getApplicationById(
        applicationId.value,
      ),

      getVersionsByApplication(
        applicationId.value,
      ),
    ])

    application.value =
      applicationResponse

    versions.value =
      versionsResponse
  } catch {
    errorMessage.value =
      'Impossible de charger les versions de l’application.'
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
  value: string,
) {
  return new Date(value)
    .toLocaleDateString(
      'fr-FR',
      {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
      },
    )
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
          Versions
        </h2>

        <p v-if="application">
          Application :
          <strong>
            {{ application.name }}
          </strong>
        </p>
      </div>

      <span class="count-badge">
        {{ versions.length }}
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
      v-else-if="versions.length === 0"
      class="state-card"
    >
      Aucune version liée à cette application.
    </div>

    <div
      v-else
      class="versions-list"
    >
      <article
        v-for="version in versions"
        :key="version.id"
        class="version-card"
      >
        <header class="card-header">
          <div>
            <span class="version-label">
              Version
            </span>

            <h3>
              {{ version.version }}
            </h3>
          </div>

          <span
            class="status-badge"
            :class="
              version.buildStatus.toLowerCase()
            "
          >
            {{ version.buildStatus }}
          </span>
        </header>

        <div class="details-grid">
          <div class="detail-box">
            <span>
              Branche Git
            </span>

            <strong>
              {{ version.gitBranch }}
            </strong>
          </div>

          <div class="detail-box">
            <span>
              Commit
            </span>

            <code>
              {{
                version.gitCommit
                || 'Non défini'
              }}
            </code>
          </div>

          <div class="detail-box">
            <span>
              Image Docker
            </span>

            <code>
              {{
                version.dockerImage
                || 'Non définie'
              }}
            </code>
          </div>

          <div class="detail-box">
            <span>
              Build #
            </span>

            <strong>
              {{
                version.buildNumber
                ?? '—'
              }}
            </strong>
          </div>

          <div class="detail-box">
            <span>
              Artefact
            </span>

            <strong>
              {{
                version.artifactPath
                || 'Non défini'
              }}
            </strong>
          </div>

          <div class="detail-box">
            <span>
              Créée le
            </span>

            <strong>
              {{
                formatDate(
                  version.createdAt,
                )
              }}
            </strong>
          </div>
        </div>

        <a
          v-if="version.buildLogsUrl"
          :href="version.buildLogsUrl"
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

.versions-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.version-card {
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

.version-label {
  color: #2563eb;

  font-size: 10px;
  font-weight: 800;

  text-transform: uppercase;
}

.card-header h3 {
  margin: 8px 0 0;

  color: #0f172a;
  font-size: 21px;
}

.status-badge {
  display: inline-flex;

  padding: 5px 9px;

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
  gap: 8px;

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

code {
  width: fit-content;
  max-width: 100%;

  overflow: hidden;

  padding: 5px 8px;

  border-radius: 7px;

  background: #e2e8f0;
  color: #334155;

  text-overflow: ellipsis;
  white-space: nowrap;
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