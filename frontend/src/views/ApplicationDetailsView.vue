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
  getApplicationDetails,
} from '@/api/applications.api'

import type {
  ApplicationDetailsResponse,
} from '@/types/application'

const route = useRoute()
const router = useRouter()

const application =
  ref<ApplicationDetailsResponse | null>(null)

const loading = ref(false)

const errorMessage =
  ref<string | null>(null)

const applicationId = computed(() => {
  return Number(route.params.id)
})

onMounted(async () => {
  await loadApplication()
})

async function loadApplication() {
  loading.value = true
  errorMessage.value = null

  try {
    application.value =
      await getApplicationDetails(
        applicationId.value,
      )
  } catch {
    errorMessage.value =
      'Impossible de charger les détails de l’application.'
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push('/applications')
}

function openEnvironments() {
  router.push({
    name: 'application-environments',

    params: {
      id: applicationId.value,
    },
  })
}

function openVersions() {
  router.push({
    name: 'application-versions',

    params: {
      id: applicationId.value,
    },
  })
}

function openDeployments() {
  router.push({
    name: 'application-deployments',

    params: {
      id: applicationId.value,
    },
  })
}

function formatDate(
  value: string,
) {
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
</script>

<template>
  <section class="details-page">

    <!-- HEADER -->

    <header class="page-header">
      <div>
        <button
          type="button"
          class="back-button"
          @click="goBack"
        >
          ← Applications
        </button>

        <h2>
          Détail de l’application
        </h2>

        <p>
          Informations générales et ressources liées.
        </p>
      </div>

      <button
        type="button"
        class="refresh-button"
        :disabled="loading"
        @click="loadApplication"
      >
        Rafraîchir
      </button>
    </header>

    <!-- ERROR -->

    <p
      v-if="errorMessage"
      class="error-message"
    >
      {{ errorMessage }}
    </p>

    <!-- LOADING -->

    <div
      v-if="loading"
      class="state-card"
    >
      Chargement...
    </div>

    <!-- CONTENT -->

    <template v-else-if="application">

      <!-- APPLICATION -->

      <section class="application-card">
        <div class="application-heading">
          <div>
            <p class="eyebrow">
              Application
            </p>

            <h1>
              {{ application.name }}
            </h1>

            <p class="description">
              {{
                application.description
                  || 'Aucune description'
              }}
            </p>
          </div>

          <span
            class="status-badge"
            :class="{
              active: application.active,
              inactive: !application.active,
            }"
          >
            {{
              application.active
                ? 'Active'
                : 'Inactive'
            }}
          </span>
        </div>

        <div class="info-grid">

          <div class="info-box">
            <span class="info-label">
              Technologie
            </span>

            <strong>
              {{ application.technology }}
            </strong>
          </div>

          <div class="info-box">
            <span class="info-label">
              Repository
            </span>

            <a
              :href="application.repositoryUrl"
              target="_blank"
              rel="noopener noreferrer"
            >
              Ouvrir le dépôt
            </a>
          </div>

          <div class="info-box">
            <span class="info-label">
              Créée le
            </span>

            <strong>
              {{
                formatDate(
                  application.createdAt,
                )
              }}
            </strong>
          </div>

          <div class="info-box">
            <span class="info-label">
              Dernière modification
            </span>

            <strong>
              {{
                formatDate(
                  application.updatedAt,
                )
              }}
            </strong>
          </div>

        </div>
      </section>

      <!-- RESOURCES -->

      <section class="resources-section">

        <div class="section-heading">
          <p class="eyebrow">
            Ressources
          </p>

          <h3>
            Ressources liées
          </h3>

          <p>
            Accédez séparément aux environnements,
            versions et déploiements de cette
            application.
          </p>
        </div>

        <div class="resources-grid">

          <!-- ENVIRONMENTS -->

          <button
            type="button"
            class="resource-card"
            @click="openEnvironments"
          >
            <div>
              <span class="resource-label">
                Infrastructure
              </span>

              <h4>
                Environnements
              </h4>

              <p>
                Consultez les environnements
                Development, Test, Staging
                et Production.
              </p>
            </div>

            <div class="resource-footer">
              <strong>
                {{ application.environments.length }}
              </strong>

              <span>
                Voir les environnements →
              </span>
            </div>
          </button>

          <!-- VERSIONS -->

          <button
            type="button"
            class="resource-card"
            @click="openVersions"
          >
            <div>
              <span class="resource-label">
                CI / CD
              </span>

              <h4>
                Versions
              </h4>

              <p>
                Consultez les versions Git,
                images Docker, artefacts et
                résultats des builds.
              </p>
            </div>

            <div class="resource-footer">
              <strong>
                Versions
              </strong>

              <span>
                Voir les versions →
              </span>
            </div>
          </button>

          <!-- DEPLOYMENTS -->

          <button
            type="button"
            class="resource-card"
            @click="openDeployments"
          >
            <div>
              <span class="resource-label">
                Delivery
              </span>

              <h4>
                Déploiements
              </h4>

              <p>
                Consultez les déploiements,
                leurs statuts, les builds Jenkins
                et les rollbacks.
              </p>
            </div>

            <div class="resource-footer">
              <strong>
                Deployments
              </strong>

              <span>
                Voir les déploiements →
              </span>
            </div>
          </button>

        </div>
      </section>

    </template>
  </section>
</template>

<style scoped>
.details-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* HEADER */

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.page-header h2 {
  margin: 10px 0 0;

  color: #0f172a;
  font-size: 27px;
}

.page-header > div > p:last-child {
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

  cursor: pointer;
}

.refresh-button {
  min-height: 40px;
  padding: 0 15px;

  border: 1px solid #cbd5e1;
  border-radius: 9px;

  background: #ffffff;
  color: #334155;

  font-weight: 700;

  cursor: pointer;
}

.refresh-button:hover:not(:disabled) {
  background: #f8fafc;
}

.refresh-button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

/* APPLICATION */

.application-card,
.resources-section,
.state-card {
  border: 1px solid #e2e8f0;
  border-radius: 16px;

  background: #ffffff;

  box-shadow:
    0 8px 24px rgb(15 23 42 / 4%);
}

.application-card {
  padding: 27px;
}

.application-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;

  gap: 20px;

  padding-bottom: 24px;

  border-bottom: 1px solid #f1f5f9;
}

.application-heading h1 {
  margin: 0;

  color: #0f172a;
  font-size: 30px;
}

.description {
  max-width: 700px;

  margin: 10px 0 0;

  color: #64748b;
  line-height: 1.6;
}

.eyebrow {
  margin: 0 0 7px;

  color: #2563eb;

  font-size: 10px;
  font-weight: 800;

  letter-spacing: 0.1em;
  text-transform: uppercase;
}

/* APPLICATION INFO */

.info-grid {
  display: grid;

  grid-template-columns:
    repeat(2, minmax(0, 1fr));

  gap: 14px;

  margin-top: 24px;
}

.info-box {
  display: flex;

  min-height: 75px;

  flex-direction: column;
  justify-content: center;

  gap: 7px;

  padding: 14px 16px;

  border: 1px solid #e2e8f0;
  border-radius: 11px;

  background: #f8fafc;
}

.info-label {
  color: #94a3b8;

  font-size: 10px;
  font-weight: 800;

  letter-spacing: 0.05em;

  text-transform: uppercase;
}

.info-box strong {
  color: #334155;

  font-size: 13px;
}

/* LINKS */

a {
  color: #2563eb;

  font-size: 13px;
  font-weight: 700;

  text-decoration: none;
}

a:hover {
  text-decoration: underline;
}

/* STATUS */

.status-badge {
  display: inline-flex;

  padding: 6px 10px;

  border-radius: 999px;

  font-size: 10px;
  font-weight: 800;
}

.status-badge.active {
  background: #dcfce7;
  color: #15803d;
}

.status-badge.inactive {
  background: #f1f5f9;
  color: #64748b;
}

/* RESOURCES */

.resources-section {
  padding: 27px;
}

.section-heading h3 {
  margin: 0;

  color: #0f172a;
  font-size: 21px;
}

.section-heading > p:last-child {
  margin: 7px 0 0;

  color: #64748b;
  font-size: 13px;
}

/* THREE CARDS */

.resources-grid {
  display: grid;

  grid-template-columns:
    repeat(3, minmax(0, 1fr));

  gap: 18px;

  margin-top: 22px;
}

.resource-card {
  display: flex;

  min-height: 225px;

  flex-direction: column;
  justify-content: space-between;

  padding: 22px;

  border: 1px solid #dbe4f0;
  border-radius: 15px;

  background: #ffffff;

  text-align: left;

  cursor: pointer;

  box-shadow:
    0 5px 18px rgb(15 23 42 / 5%);

  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.resource-card:hover {
  transform: translateY(-3px);

  border-color: #93c5fd;

  box-shadow:
    0 12px 30px rgb(15 23 42 / 10%);
}

.resource-label {
  color: #2563eb;

  font-size: 10px;
  font-weight: 800;

  letter-spacing: 0.08em;

  text-transform: uppercase;
}

.resource-card h4 {
  margin: 10px 0 0;

  color: #0f172a;

  font-size: 20px;
}

.resource-card p {
  margin: 10px 0 0;

  color: #64748b;

  font-size: 13px;
  line-height: 1.55;
}

.resource-footer {
  display: flex;

  align-items: center;
  justify-content: space-between;

  gap: 15px;

  margin-top: 24px;

  padding-top: 16px;

  border-top: 1px solid #f1f5f9;
}

.resource-footer strong {
  color: #0f172a;

  font-size: 17px;
}

.resource-footer span {
  color: #2563eb;

  font-size: 12px;
  font-weight: 700;
}

/* ERROR / LOADING */

.state-card {
  padding: 50px 20px;

  color: #64748b;

  text-align: center;
}

.error-message {
  margin: 0;

  padding: 12px 14px;

  border: 1px solid #fecaca;
  border-radius: 10px;

  background: #fef2f2;
  color: #b91c1c;
}

/* RESPONSIVE */

@media (max-width: 1000px) {
  .resources-grid {
    grid-template-columns:
      repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 700px) {
  .page-header,
  .application-heading {
    align-items: stretch;
    flex-direction: column;
  }

  .info-grid,
  .resources-grid {
    grid-template-columns: 1fr;
  }

  .refresh-button {
    width: 100%;
  }
}
</style>