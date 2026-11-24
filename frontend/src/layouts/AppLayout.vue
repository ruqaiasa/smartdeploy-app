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
import DeploymentToasts from '@/components/DeploymentToasts.vue'
import DeveloperTopNav from '@/components/developer/DeveloperTopNav.vue'
import ClaimsWidget from '@/components/developer/ClaimsWidget.vue'
import DiscussionWidget from '@/components/discussion/DiscussionWidget.vue'
import { useAuthStore } from '@/stores/auth'
import { pendingAccounts, startPendingAccountsWatch, stopPendingAccountsWatch } from '@/components/admin/pendingAccounts'
import { discussions, startDiscussionsWatch, stopDiscussionsWatch } from '@/components/discussion/discussions'

import {
  useAlertsStore,
} from '@/stores/alerts'

import type {
  Alert,
} from '@/types/alert'

const route = useRoute()

/* Deux interfaces : le développeur a son espace (barre horizontale, bulle d’assistance),
 * l’équipe DevOps et l’administrateur gardent la console (menu latéral). */
const authForLayout = useAuthStore()
const isDeveloper = computed(() => authForLayout.hasRole('DEVELOPER') && !authForLayout.hasRole('VIEWER', 'DEVOPS', 'ADMIN'))
const isAdmin = computed(() => authForLayout.hasRole('ADMIN'))
onMounted(() => {
  if (isAdmin.value) startPendingAccountsWatch()
  if (authForLayout.hasRole('DEVELOPER', 'DEVOPS', 'ADMIN')) startDiscussionsWatch()
})
onUnmounted(() => { stopPendingAccountsWatch(); stopDiscussionsWatch() })
function openDiscussion() {
  const t = discussions.toast
  discussions.toast = null
  if (t) void router.push(`/deployments/${t.deploymentId}#discussion`)
}
function openUsers() { pendingAccounts.toast = null; void router.push('/users') }
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
  <div v-if="isDeveloper" class="dev-layout">
    <DeveloperTopNav />
    <main class="dev-content">
      <RouterView />
    </main>
    <DeploymentToasts />
    <ClaimsWidget />
    <Transition name="alert-toast">
      <article v-if="discussions.toast" class="message-toast" role="status">
        <span class="message-toast-icon" aria-hidden="true"><svg viewBox="0 0 24 24" width="22" height="22"><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H10l-4.5 4v-4A2.5 2.5 0 0 1 4 13.5z" fill="none" stroke="#4f46e5" stroke-width="1.9" stroke-linejoin="round" /></svg></span>
        <div>
          <strong>Nouveau message · {{ discussions.toast.lastAuthor }}</strong>
          <p>#{{ discussions.toast.deploymentId }} {{ discussions.toast.applicationName }} {{ discussions.toast.version }} : {{ discussions.toast.lastMessage }}</p>
        </div>
        <div class="account-toast-actions">
          <button type="button" class="account-toast-open" @click="openDiscussion">Voir</button>
          <button type="button" class="account-toast-close" aria-label="Fermer" @click="discussions.toast = null">×</button>
        </div>
      </article>
    </Transition>
  </div>

  <div v-else class="app-layout">
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

      <!-- Notifications de fin de déploiement, sur toutes les pages -->
      <DeploymentToasts />
    </section>

    <!-- Messagerie de l'équipe, en bas à droite -->
    <DiscussionWidget />

    <!-- Nouveau message dans une discussion -->
    <Transition name="alert-toast">
      <article v-if="discussions.toast" class="message-toast" role="status">
        <span class="message-toast-icon" aria-hidden="true"><svg viewBox="0 0 24 24" width="22" height="22"><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3h11A2.5 2.5 0 0 1 20 5.5v8a2.5 2.5 0 0 1-2.5 2.5H10l-4.5 4v-4A2.5 2.5 0 0 1 4 13.5z" fill="none" stroke="#4f46e5" stroke-width="1.9" stroke-linejoin="round" /></svg></span>
        <div>
          <strong>Nouveau message · {{ discussions.toast.lastAuthor }}</strong>
          <p>#{{ discussions.toast.deploymentId }} {{ discussions.toast.applicationName }} {{ discussions.toast.version }} : {{ discussions.toast.lastMessage }}</p>
        </div>
        <div class="account-toast-actions">
          <button type="button" class="account-toast-open" @click="openDiscussion">Voir</button>
          <button type="button" class="account-toast-close" aria-label="Fermer" @click="discussions.toast = null">×</button>
        </div>
      </article>
    </Transition>

    <!-- Nouveau compte à valider (administrateur) -->
    <Transition name="alert-toast">
      <article v-if="pendingAccounts.toast" class="account-toast" role="status">
        <div>
          <strong>Nouveau compte à valider</strong>
          <p>{{ pendingAccounts.toast.name }} · {{ pendingAccounts.toast.email }}</p>
        </div>
        <div class="account-toast-actions">
          <button type="button" class="account-toast-open" @click="openUsers">Voir</button>
          <button type="button" class="account-toast-close" aria-label="Fermer" @click="pendingAccounts.toast = null">×</button>
        </div>
      </article>
    </Transition>

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
.message-toast { position: fixed; left: 50%; top: 18px; z-index: 95; display: flex; align-items: center; gap: 12px; max-width: min(560px, calc(100vw - 32px)); padding: 12px 14px; border: 1px solid #c7d2fe; border-left: 5px solid #4f46e5; border-radius: 14px; background: #fff; box-shadow: 0 18px 40px rgba(15, 23, 42, .18); transform: translateX(-50%); }
.message-toast strong { color: #312e81; }
.message-toast p { margin: 2px 0 0; overflow: hidden; color: #4b5563; font-size: 13.5px; text-overflow: ellipsis; white-space: nowrap; }
.message-toast-icon { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 10px; background: #eef2ff; }
.account-toast { position: fixed; right: 96px; bottom: 26px; z-index: 90; display: flex; align-items: center; gap: 14px; padding: 14px 16px; border: 1px solid #c7d2fe; border-left: 5px solid #2563eb; border-radius: 14px; background: #fff; box-shadow: 0 18px 40px rgba(15, 23, 42, .18); }
.account-toast strong { color: #1e3a8a; }
.account-toast p { margin: 2px 0 0; color: #4b5563; font-size: 13.5px; }
.account-toast-actions { display: flex; align-items: center; gap: 6px; }
.account-toast-open { padding: 7px 12px; border: 0; border-radius: 8px; background: #2563eb; color: #fff; font: inherit; font-weight: 700; cursor: pointer; }
.account-toast-close { border: 0; background: none; color: #6b7280; font-size: 20px; cursor: pointer; }
.dev-layout { min-height: 100vh; background: linear-gradient(180deg, #f8faff 0%, #f6f7fb 240px); }
.dev-content { max-width: 1240px; margin: 0 auto; padding: 24px 24px 96px; }
/* Les pages partagées prennent les couleurs de l’espace développeur. */
.dev-layout :deep(.primary-button),
.dev-layout :deep(.dp-btn-primary) { border-color: transparent; background: linear-gradient(135deg, #1e3a8a, #4f46e5); color: #fff; }
.dev-layout :deep(.primary-button:hover),
.dev-layout :deep(.dp-btn-primary:hover) { filter: brightness(1.06); }
.dev-layout :deep(.panel),
.dev-layout :deep(.table-card),
.dev-layout :deep(.dp-card) { border-color: #e0e7ff; border-radius: 14px; }
.dev-layout :deep(.page-title),
.dev-layout :deep(h1) { color: #0f172a; }
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