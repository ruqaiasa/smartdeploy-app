<script setup lang="ts">
import {
  computed,
  onMounted,
  onUnmounted,
  ref,
} from 'vue'

import {
  useRoute,
  useRouter,
} from 'vue-router'

import AppSidebar from '@/components/AppSidebar.vue'
import AppTopbar from '@/components/AppTopbar.vue'

import {
  useAlertsStore,
} from '@/stores/alerts'

import type {
  Alert,
} from '@/types/alert'

const route = useRoute()
const router = useRouter()

const alertsStore =
  useAlertsStore()

const sidebarCollapsed =
  ref(false)

/*
 * ============================================================
 * ALERT NOTIFICATION
 * ============================================================
 */

const POLLING_INTERVAL_MS = 20_000
const TOAST_DURATION_MS = 7_000

const notification =
  ref<Alert | null>(null)

const initializedAlerts =
  ref(false)

const knownAlertIds =
  new Set<number>()

let pollingTimer:
  ReturnType<typeof setInterval> | null =
  null

let toastTimer:
  ReturnType<typeof setTimeout> | null =
  null

/*
 * ============================================================
 * PAGE
 * ============================================================
 */

const pageTitle = computed(() => {
  return String(
    route.meta.title ?? 'SmartDeploy',
  )
})

function toggleSidebar() {
  sidebarCollapsed.value =
    !sidebarCollapsed.value
}

/*
 * ============================================================
 * NOTIFICATION
 * ============================================================
 */

function closeNotification() {
  notification.value = null

  if (toastTimer !== null) {
    clearTimeout(toastTimer)
    toastTimer = null
  }
}

function showNotification(
  alert: Alert,
) {
  closeNotification()

  notification.value = alert

  toastTimer = setTimeout(() => {
    notification.value = null
    toastTimer = null
  }, TOAST_DURATION_MS)
}

async function openAlertPage() {
  closeNotification()

  await router.push('/alerts')
}

/*
 * ============================================================
 * POLLING
 * ============================================================
 */

async function checkAlerts() {
  const currentAlerts =
    await alertsStore.loadGlobalOpenAlerts()

  /*
   * Premier chargement :
   *
   * on mémorise les alertes déjà existantes,
   * mais on ne déclenche PAS de notification.
   *
   * Cela évite d'afficher plusieurs anciennes alertes
   * dès la connexion de l'utilisateur.
   */

  if (!initializedAlerts.value) {
    currentAlerts.forEach(
      (alert) => {
        knownAlertIds.add(alert.id)
      },
    )

    initializedAlerts.value = true

    return
  }

  /*
   * Recherche uniquement des nouvelles alertes.
   */

  const newAlerts =
    currentAlerts.filter(
      (alert) =>
        !knownAlertIds.has(alert.id),
    )

  /*
   * Mise à jour des IDs connus.
   */

  currentAlerts.forEach(
    (alert) => {
      knownAlertIds.add(alert.id)
    },
  )

  if (newAlerts.length === 0) {
    return
  }

  /*
   * Priorité aux alertes CRITICAL.
   */

const alertToNotify =
  newAlerts.find(
    (alert) =>
      alert.severity === 'CRITICAL',
  )
  ?? newAlerts.at(0)

if (!alertToNotify) {
  return
}

showNotification(alertToNotify)
}

/*
 * ============================================================
 * LIFECYCLE
 * ============================================================
 */

onMounted(async () => {
  await checkAlerts()

  pollingTimer = setInterval(
    () => {
      void checkAlerts()
    },
    POLLING_INTERVAL_MS,
  )
})

onUnmounted(() => {
  if (pollingTimer !== null) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }

  if (toastTimer !== null) {
    clearTimeout(toastTimer)
    toastTimer = null
  }
})
</script>

<template>
  <div class="app-layout">
    <AppSidebar
      :collapsed="sidebarCollapsed"
      @toggle="toggleSidebar"
    />

    <section
      class="main-section"
      :class="{
        'sidebar-collapsed':
          sidebarCollapsed,
      }"
    >
      <AppTopbar
        :page-title="pageTitle"
      />

      <main class="page-content">
        <RouterView />
      </main>
    </section>

    <!-- =====================================================
         GLOBAL ALERT TOAST
         ===================================================== -->

    <Transition name="alert-toast">
      <article
        v-if="notification"
        class="alert-toast"
        :class="
          notification.severity.toLowerCase()
        "
      >
        <div class="toast-icon">
          <svg
            viewBox="0 0 24 24"
            aria-hidden="true"
          >
            <path
              d="
                M12 9v4
                M12 17h.01
                M10.3 4.3
                2.4 18
                a2 2 0 0 0
                1.7 3h15.8
                a2 2 0 0 0
                1.7-3
                L13.7 4.3
                a2 2 0 0 0-3.4 0Z
              "
            />
          </svg>
        </div>

        <div class="toast-content">
          <div class="toast-heading">
            <span class="toast-severity">
              {{
                notification.severity
                  === 'CRITICAL'
                  ? 'Alerte critique'
                  : 'Avertissement'
              }}
            </span>

            <button
              type="button"
              class="toast-close"
              aria-label="Fermer la notification"
              @click="closeNotification"
            >
              ×
            </button>
          </div>

          <strong class="toast-title">
            {{ notification.title }}
          </strong>

          <p class="toast-message">
            {{ notification.message }}
          </p>

          <div class="toast-context">
            <span>
              {{ notification.applicationName }}
            </span>

            <span class="context-separator">
              •
            </span>

            <span>
              {{ notification.environmentName }}
            </span>
          </div>

          <button
            type="button"
            class="toast-action"
            @click="openAlertPage"
          >
            Voir les alertes
            <span>→</span>
          </button>
        </div>
      </article>
    </Transition>
  </div>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;

  background: #f8fafc;
}

.main-section {
  min-height: 100vh;

  margin-left: 260px;

  transition:
    margin-left 0.25s ease;
}

.main-section.sidebar-collapsed {
  margin-left: 82px;
}

.page-content {
  padding: 30px 32px;
}

/* ============================================================
   ALERT TOAST
   ============================================================ */

.alert-toast {
  position: fixed;

  top: 92px;
  right: 24px;

  z-index: 1000;

  display: flex;

  width: min(
    390px,
    calc(100vw - 40px)
  );

  gap: 13px;

  padding: 16px;

  box-sizing: border-box;

  border:
    1px solid
    #fed7aa;

  border-radius: 13px;

  background: #ffffff;

  box-shadow:
    0 18px 45px
    rgba(15, 23, 42, 0.16);
}

.alert-toast.critical {
  border-color: #fecaca;
}

.toast-icon {
  display: flex;
  align-items: center;
  justify-content: center;

  width: 38px;
  height: 38px;

  flex-shrink: 0;

  border-radius: 10px;

  background: #fffbeb;

  color: #d97706;
}

.alert-toast.critical
.toast-icon {
  background: #fef2f2;

  color: #dc2626;
}

.toast-icon svg {
  width: 20px;
  height: 20px;

  fill: none;

  stroke: currentColor;

  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.toast-content {
  min-width: 0;
  flex: 1;
}

.toast-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;

  gap: 12px;
}

.toast-severity {
  color: #d97706;

  font-size: 10px;
  font-weight: 800;

  letter-spacing: 0.7px;

  text-transform: uppercase;
}

.alert-toast.critical
.toast-severity {
  color: #dc2626;
}

.toast-close {
  display: flex;
  align-items: center;
  justify-content: center;

  width: 25px;
  height: 25px;

  padding: 0;

  border: 0;
  border-radius: 6px;

  background: transparent;

  color: #94a3b8;

  cursor: pointer;

  font-size: 19px;

  transition:
    background 0.15s ease,
    color 0.15s ease;
}

.toast-close:hover {
  background: #f1f5f9;

  color: #334155;
}

.toast-title {
  display: block;

  margin-top: 3px;

  color: #0f172a;

  font-size: 14px;
  font-weight: 700;
}

.toast-message {
  margin:
    6px
    0
    0;

  color: #64748b;

  font-size: 12px;
  line-height: 1.5;
}

.toast-context {
  display: flex;
  align-items: center;

  gap: 6px;

  margin-top: 9px;

  color: #64748b;

  font-size: 10px;
  font-weight: 600;
}

.context-separator {
  color: #cbd5e1;
}

.toast-action {
  display: inline-flex;
  align-items: center;

  gap: 7px;

  margin-top: 11px;
  padding: 0;

  border: 0;

  background: transparent;

  color: #2563eb;

  cursor: pointer;

  font-family: inherit;
  font-size: 11px;
  font-weight: 700;
}

.toast-action:hover {
  color: #1d4ed8;
}

/* ============================================================
   TOAST TRANSITION
   ============================================================ */

.alert-toast-enter-active,
.alert-toast-leave-active {
  transition:
    opacity 0.22s ease,
    transform 0.22s ease;
}

.alert-toast-enter-from,
.alert-toast-leave-to {
  opacity: 0;

  transform:
    translateX(18px);
}

/* ============================================================
   RESPONSIVE
   ============================================================ */

@media (max-width: 700px) {
  .main-section,
  .main-section.sidebar-collapsed {
    margin-left: 82px;
  }

  .page-content {
    padding: 22px 18px;
  }

  .alert-toast {
    top: 82px;
    right: 14px;

    width:
      calc(100vw - 110px);
  }
}
</style>