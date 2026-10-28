<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed } from 'vue'

import { useAlertsStore } from '@/stores/alerts'
import { useAuthStore } from '@/stores/auth'

const alertsStore = useAlertsStore()
const authStore = useAuthStore()

const {
  globalOpenAlertCount,
  globalCriticalAlertCount,
} = storeToRefs(alertsStore)

const isAdmin = computed(() =>
  authStore.hasRole('ADMIN'),
)

defineProps<{
  collapsed: boolean
}>()

defineEmits<{
  toggle: []
}>()
</script>

<template>
  <aside
    class="sidebar"
    :class="{ collapsed }"
  >
    <!-- HEADER -->

    <div class="sidebar-header">
      <div class="logo">
        <span class="logo-icon">
          S
        </span>

        <div
          v-if="!collapsed"
          class="logo-text"
        >
          <strong>SmartDeploy</strong>
          <small>DevOps Platform</small>
        </div>
      </div>

      <button
        class="collapse-button"
        type="button"
        :aria-label="
          collapsed
            ? 'Développer le menu'
            : 'Réduire le menu'
        "
        @click="$emit('toggle')"
      >
        {{ collapsed ? '›' : '‹' }}
      </button>
    </div>

    <!-- NAVIGATION -->

    <nav class="navigation">

      <!-- VUE D'ENSEMBLE -->

      <div class="nav-section">
        <span
          v-if="!collapsed"
          class="nav-section-title"
        >
          Vue d'ensemble
        </span>

        <RouterLink
          to="/dashboard"
          class="nav-link"
          title="Dashboard"
        >
          <span class="nav-icon">
            <svg
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <rect
                x="3"
                y="3"
                width="7"
                height="7"
                rx="1.5"
              />

              <rect
                x="14"
                y="3"
                width="7"
                height="7"
                rx="1.5"
              />

              <rect
                x="3"
                y="14"
                width="7"
                height="7"
                rx="1.5"
              />

              <rect
                x="14"
                y="14"
                width="7"
                height="7"
                rx="1.5"
              />
            </svg>
          </span>

          <span
            v-if="!collapsed"
            class="nav-label"
          >
            Dashboard
          </span>
        </RouterLink>
      </div>

      <!-- GESTION -->

      <div class="nav-section">
        <span
          v-if="!collapsed"
          class="nav-section-title"
        >
          Gestion
        </span>

        <!-- APPLICATIONS -->

        <RouterLink
          to="/applications"
          class="nav-link"
          title="Applications"
        >
          <span class="nav-icon">
            <svg
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <rect
                x="3"
                y="4"
                width="18"
                height="16"
                rx="2"
              />

              <path d="M3 9h18" />
              <path d="M8 14h3" />
              <path d="M8 17h6" />
            </svg>
          </span>

          <span
            v-if="!collapsed"
            class="nav-label"
          >
            Applications
          </span>
        </RouterLink>

        <!-- ENVIRONNEMENTS -->

        <RouterLink
          to="/environments"
          class="nav-link"
          title="Environnements"
        >
          <span class="nav-icon">
            <svg
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <rect
                x="4"
                y="4"
                width="16"
                height="6"
                rx="2"
              />

              <rect
                x="4"
                y="14"
                width="16"
                height="6"
                rx="2"
              />

              <path d="M8 7h.01" />
              <path d="M8 17h.01" />
              <path d="M12 7h5" />
              <path d="M12 17h5" />
            </svg>
          </span>

          <span
            v-if="!collapsed"
            class="nav-label"
          >
            Environnements
          </span>
        </RouterLink>

        <!-- VERSIONS -->

        <RouterLink
          to="/versions"
          class="nav-link"
          title="Versions"
        >
          <span class="nav-icon">
            <svg
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path d="M6 3v12" />

              <circle
                cx="6"
                cy="18"
                r="3"
              />

              <circle
                cx="18"
                cy="6"
                r="3"
              />

              <path
                d="
                  M9 18h2
                  c4 0 7-3 7-7V9
                "
              />
            </svg>
          </span>

          <span
            v-if="!collapsed"
            class="nav-label"
          >
            Versions
          </span>
        </RouterLink>

        <!-- DEPLOIEMENTS -->

        <RouterLink
          to="/deployments"
          class="nav-link"
          title="Déploiements"
        >
          <span class="nav-icon">
            <svg
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path d="M12 20V8" />
              <path d="m7 13 5-5 5 5" />
              <path d="M5 4h14" />
            </svg>
          </span>

          <span
            v-if="!collapsed"
            class="nav-label"
          >
            Déploiements
          </span>
        </RouterLink>
      </div>

      <!-- SUPERVISION -->

      <div class="nav-section supervision-section">
        <span
          v-if="!collapsed"
          class="nav-section-title"
        >
          Supervision
        </span>

        <RouterLink
          to="/alerts"
          class="nav-link alert-link"
          title="Alertes"
        >
          <span class="nav-icon alert-icon">
            <svg
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                d="
                  M18 8
                  a6 6 0 0 0-12 0
                  c0 7-3 7-3 9
                  h18
                  c0-2-3-2-3-9
                "
              />

              <path d="M10 21h4" />
            </svg>
          </span>

          <span
            v-if="!collapsed"
            class="nav-label"
          >
            Alertes
          </span>

          <span
            v-if="
              !collapsed
              && globalOpenAlertCount > 0
            "
            class="alert-count"
            :class="{
              critical:
                globalCriticalAlertCount > 0,
            }"
          >
            {{
              globalOpenAlertCount > 99
                ? '99+'
                : globalOpenAlertCount
            }}
          </span>

          <span
            v-if="
              collapsed
              && globalOpenAlertCount > 0
            "
            class="collapsed-alert-indicator"
            :class="{
              critical:
                globalCriticalAlertCount > 0,
            }"
          ></span>
        </RouterLink>
      </div>

      <!-- ADMINISTRATION -->

      <div
        v-if="isAdmin"
        class="nav-section administration-section"
      >
        <span
          v-if="!collapsed"
          class="nav-section-title"
        >
          Administration
        </span>

        <RouterLink
          to="/users"
          class="nav-link"
          title="Utilisateurs"
        >
          <span class="nav-icon">
            <svg
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                d="
                  M16 21v-2
                  a4 4 0 0 0-4-4H6
                  a4 4 0 0 0-4 4v2
                "
              />

              <circle
                cx="9"
                cy="7"
                r="4"
              />

              <path
                d="
                  M22 21v-2
                  a4 4 0 0 0-3-3.87
                "
              />

              <path
                d="
                  M16 3.13
                  a4 4 0 0 1 0 7.75
                "
              />
            </svg>
          </span>

          <span
            v-if="!collapsed"
            class="nav-label"
          >
            Utilisateurs
          </span>

          <span
            v-if="!collapsed"
            class="admin-badge"
          >
            ADMIN
          </span>
        </RouterLink>
      </div>
    </nav>

    <!-- FOOTER -->

    <div class="sidebar-footer">
      <div class="system-status">
        <span class="status-dot"></span>

        <div v-if="!collapsed">
          <strong>
            Système opérationnel
          </strong>

          <small>
            Services disponibles
          </small>
        </div>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 20;

  display: flex;
  width: 260px;
  height: 100vh;
  flex-direction: column;

  overflow: hidden;

  border-right:
    1px solid
    rgba(148, 163, 184, 0.08);

  background:
    linear-gradient(
      180deg,
      #111827 0%,
      #0f172a 100%
    );

  color: #ffffff;

  box-shadow:
    6px 0 24px
    rgba(15, 23, 42, 0.08);

  transition:
    width 0.25s ease;
}

.sidebar.collapsed {
  width: 82px;
}

.sidebar-header {
  display: flex;
  min-height: 76px;
  align-items: center;
  justify-content: space-between;

  gap: 12px;

  box-sizing: border-box;
  padding: 0 18px;

  border-bottom:
    1px solid
    rgba(148, 163, 184, 0.12);
}

.logo {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.logo-icon {
  display: grid;
  width: 42px;
  height: 42px;
  flex-shrink: 0;
  place-items: center;

  border:
    1px solid
    rgba(96, 165, 250, 0.2);

  border-radius: 11px;

  background:
    linear-gradient(
      135deg,
      #2563eb,
      #3b82f6
    );

  color: #ffffff;

  font-size: 18px;
  font-weight: 800;

  box-shadow:
    0 8px 20px
    rgba(37, 99, 235, 0.22);
}

.logo-text {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.logo-text strong {
  color: #f8fafc;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.2px;
  white-space: nowrap;
}

.logo-text small {
  margin-top: 3px;
  color: #64748b;
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.2px;
  white-space: nowrap;
}

.collapse-button {
  display: flex;
  width: 30px;
  height: 30px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;

  padding: 0;

  border:
    1px solid
    rgba(148, 163, 184, 0.12);

  border-radius: 8px;

  background:
    rgba(51, 65, 85, 0.45);

  color: #94a3b8;

  cursor: pointer;

  font-family: inherit;
  font-size: 21px;
  font-weight: 300;

  transition:
    background 0.2s ease,
    color 0.2s ease,
    border-color 0.2s ease;
}

.collapse-button:hover {
  border-color:
    rgba(96, 165, 250, 0.25);

  background:
    rgba(51, 65, 85, 0.8);

  color: #ffffff;
}

.navigation {
  display: flex;
  flex: 1;
  flex-direction: column;

  gap: 21px;

  padding: 20px 14px;

  overflow-x: hidden;
  overflow-y: auto;

  scrollbar-color:
    #334155
    transparent;

  scrollbar-width: thin;
}

.nav-section {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.nav-section-title {
  display: block;

  margin:
    0
    12px
    7px;

  color: #64748b;

  font-size: 9px;
  font-weight: 700;
  letter-spacing: 1.25px;

  text-transform: uppercase;
  white-space: nowrap;
}

.supervision-section,
.administration-section {
  padding-top: 2px;
}

.nav-link {
  position: relative;

  display: flex;
  min-height: 44px;
  align-items: center;

  gap: 13px;

  box-sizing: border-box;
  padding: 0 13px;

  border:
    1px solid
    transparent;

  border-radius: 9px;

  color: #94a3b8;

  text-decoration: none;
  white-space: nowrap;

  transition:
    background 0.18s ease,
    color 0.18s ease,
    border-color 0.18s ease;
}

.nav-link:hover {
  border-color:
    rgba(148, 163, 184, 0.08);

  background:
    rgba(51, 65, 85, 0.42);

  color: #e2e8f0;
}

.nav-link.router-link-active {
  border-color:
    rgba(96, 165, 250, 0.18);

  background:
    linear-gradient(
      90deg,
      rgba(37, 99, 235, 0.22),
      rgba(37, 99, 235, 0.1)
    );

  color: #ffffff;
}

.nav-link.router-link-active::before {
  position: absolute;

  top: 9px;
  bottom: 9px;
  left: -14px;

  width: 3px;

  border-radius:
    0
    4px
    4px
    0;

  background: #3b82f6;

  box-shadow:
    0 0 12px
    rgba(59, 130, 246, 0.45);

  content: "";
}

.nav-icon {
  display: flex;
  width: 21px;
  height: 21px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;

  color: currentColor;
}

.nav-icon svg {
  width: 19px;
  height: 19px;

  fill: none;

  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.7;
}

.nav-label {
  min-width: 0;
  overflow: hidden;

  font-size: 12.5px;
  font-weight: 500;

  text-overflow: ellipsis;
}

.alert-link {
  margin-top: 1px;
}

.alert-count {
  min-width: 21px;
  margin-left: auto;
  padding: 3px 6px;

  border-radius: 999px;

  background:
    rgba(245, 158, 11, 0.16);

  color: #fbbf24;

  font-size: 10px;
  font-weight: 800;
  text-align: center;
}

.alert-count.critical {
  background:
    rgba(239, 68, 68, 0.18);

  color: #f87171;
}

.collapsed-alert-indicator {
  position: absolute;

  top: 8px;
  right: 8px;

  width: 8px;
  height: 8px;

  border: 2px solid #0f172a;
  border-radius: 50%;

  background: #f59e0b;
}

.collapsed-alert-indicator.critical {
  background: #ef4444;
}

.admin-badge {
  margin-left: auto;
  padding: 3px 6px;

  border:
    1px solid
    rgba(96, 165, 250, 0.18);

  border-radius: 5px;

  background:
    rgba(37, 99, 235, 0.12);

  color: #60a5fa;

  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.5px;
}

.sidebar-footer {
  padding: 15px 14px;

  border-top:
    1px solid
    rgba(148, 163, 184, 0.1);
}

.system-status {
  display: flex;
  min-height: 46px;
  align-items: center;

  gap: 11px;

  box-sizing: border-box;
  padding: 0 12px;

  border:
    1px solid
    rgba(148, 163, 184, 0.08);

  border-radius: 9px;

  background:
    rgba(30, 41, 59, 0.45);
}

.status-dot {
  width: 8px;
  height: 8px;
  flex-shrink: 0;

  border-radius: 50%;

  background: #22c55e;

  box-shadow:
    0 0 0 4px
    rgba(34, 197, 94, 0.1);
}

.system-status div {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.system-status strong {
  color: #cbd5e1;
  font-size: 10.5px;
  font-weight: 600;
  white-space: nowrap;
}

.system-status small {
  margin-top: 2px;

  color: #64748b;

  font-size: 9px;
  white-space: nowrap;
}

.sidebar.collapsed .sidebar-header {
  justify-content: center;
  padding: 0 10px;
}

.sidebar.collapsed .logo {
  display: none;
}

.sidebar.collapsed .collapse-button {
  width: 38px;
  height: 38px;
}

.sidebar.collapsed .navigation {
  padding:
    20px
    12px;
}

.sidebar.collapsed .nav-section {
  align-items: center;
}

.sidebar.collapsed .nav-link {
  width: 48px;
  justify-content: center;
  padding: 0;
}

.sidebar.collapsed
.nav-link.router-link-active::before {
  left: -12px;
}

.sidebar.collapsed .sidebar-footer {
  padding: 15px 12px;
}

.sidebar.collapsed .system-status {
  justify-content: center;
  padding: 0;
}
</style>