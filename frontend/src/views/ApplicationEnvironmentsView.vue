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
      'Impossible de charger les environnements.'
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
          Environnements
        </h2>

        <p v-if="application">
          Application :
          <strong>
            {{ application.name }}
          </strong>
        </p>
      </div>

      <span
        v-if="application"
        class="count-badge"
      >
        {{ application.environments.length }}
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

    <template v-else-if="application">
      <div
        v-if="
          application.environments.length === 0
        "
        class="state-card"
      >
        Aucun environnement lié à cette application.
      </div>

      <div
        v-else
        class="environment-list"
      >
        <article
          v-for="
            environment
            in application.environments
          "
          :key="environment.id"
          class="environment-card"
        >
          <header class="card-header">
            <div>
              <span
                class="type-badge"
                :class="
                  environment.type.toLowerCase()
                "
              >
                {{ environment.type }}
              </span>

              <h3>
                {{ environment.name }}
              </h3>
            </div>

            <span
              class="status-badge"
              :class="{
                active: environment.active,
                inactive: !environment.active,
              }"
            >
              {{
                environment.active
                  ? 'Actif'
                  : 'Inactif'
              }}
            </span>
          </header>

          <p class="description">
            {{
              environment.description
                || 'Aucune description'
            }}
          </p>

          <div class="details-grid">
            <div class="detail-box">
              <span>
                Namespace
              </span>

              <code>
                {{ environment.namespace }}
              </code>
            </div>

            <div class="detail-box">
              <span>
                Cluster
              </span>

              <a
                v-if="environment.clusterUrl"
                :href="environment.clusterUrl"
                target="_blank"
                rel="noopener noreferrer"
              >
                Ouvrir le cluster
              </a>

              <strong v-else>
                Non configuré
              </strong>
            </div>
          </div>
        </article>
      </div>
    </template>
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

.environment-list {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.environment-card {
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
}

.card-header h3 {
  margin: 9px 0 0;

  color: #0f172a;
}

.type-badge,
.status-badge {
  display: inline-flex;

  padding: 5px 9px;

  border-radius: 999px;

  font-size: 9px;
  font-weight: 800;
}

.type-badge.development {
  background: #eff6ff;
  color: #1d4ed8;
}

.type-badge.test {
  background: #fefce8;
  color: #a16207;
}

.type-badge.staging {
  background: #f5f3ff;
  color: #6d28d9;
}

.type-badge.production {
  background: #fef2f2;
  color: #b91c1c;
}

.status-badge.active {
  background: #dcfce7;
  color: #15803d;
}

.status-badge.inactive {
  background: #f1f5f9;
  color: #64748b;
}

.description {
  margin: 18px 0;

  color: #64748b;
  line-height: 1.55;
}

.details-grid {
  display: grid;

  grid-template-columns:
    repeat(2, minmax(0, 1fr));

  gap: 12px;
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

  padding: 5px 8px;

  border-radius: 7px;

  background: #e2e8f0;
  color: #334155;
}

a {
  color: #2563eb;

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