<script setup lang="ts">
defineProps<{
  collapsed: boolean
}>()

defineEmits<{
  toggle: []
}>()
</script>

<template>
  <aside class="sidebar" :class="{ collapsed }">
    <div class="sidebar-header">
      <div class="logo">
        <span class="logo-icon">S</span>

        <div v-if="!collapsed" class="logo-text">
          <strong>SmartDeploy</strong>
          <small>DevOps Platform</small>
        </div>
      </div>

      <button
        class="collapse-button"
        type="button"
        aria-label="Réduire le menu"
        @click="$emit('toggle')"
      >
        {{ collapsed ? '→' : '←' }}
      </button>
    </div>

    <nav class="navigation">
      <RouterLink to="/dashboard" class="nav-link">
        <span class="nav-icon">⌂</span>
        <span v-if="!collapsed">Dashboard</span>
      </RouterLink>

      <RouterLink to="/applications" class="nav-link">
        <span class="nav-icon">▣</span>
        <span v-if="!collapsed">Applications</span>
      </RouterLink>

      <RouterLink to="/environments" class="nav-link">
        <span class="nav-icon">◈</span>
        <span v-if="!collapsed">Environnements</span>
      </RouterLink>

      <RouterLink to="/versions" class="nav-link">
        <span class="nav-icon">◆</span>
        <span v-if="!collapsed">Versions</span>
      </RouterLink>

      <RouterLink to="/deployments" class="nav-link">
        <span class="nav-icon">↗</span>
        <span v-if="!collapsed">Déploiements</span>
      </RouterLink>
    </nav>

    <div class="sidebar-footer">
      <div class="system-status">
        <span class="status-dot"></span>

        <div v-if="!collapsed">
          <strong>Système opérationnel</strong>
          <small>Services disponibles</small>
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
  flex-direction: column;

  background: #111827;
  color: #ffffff;

  transition: width 0.25s ease;
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

  padding: 0 18px;
  border-bottom: 1px solid #253047;
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

  border-radius: 12px;
  background: #2563eb;

  font-size: 22px;
  font-weight: 800;
}

.logo-text {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.logo-text strong {
  font-size: 16px;
  white-space: nowrap;
}

.logo-text small {
  margin-top: 3px;
  color: #94a3b8;
  font-size: 11px;
  white-space: nowrap;
}

.collapse-button {
  width: 31px;
  height: 31px;

  border: 0;
  border-radius: 8px;
  background: #253047;
  color: #cbd5e1;

  cursor: pointer;
}

.navigation {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 8px;

  padding: 22px 14px;
}

.nav-link {
  display: flex;
  min-height: 46px;
  align-items: center;
  gap: 14px;

  padding: 0 15px;
  border-radius: 10px;

  color: #cbd5e1;
  text-decoration: none;
  white-space: nowrap;

  transition:
    background 0.2s ease,
    color 0.2s ease;
}

.nav-link:hover {
  background: #1f2937;
  color: #ffffff;
}

.nav-link.router-link-active {
  background: #2563eb;
  color: #ffffff;
}

.nav-icon {
  width: 22px;
  flex-shrink: 0;

  font-size: 19px;
  text-align: center;
}

.sidebar-footer {
  padding: 16px 14px 22px;
}

.system-status {
  display: flex;
  min-height: 58px;
  align-items: center;
  gap: 12px;

  padding: 12px;
  border: 1px solid #253047;
  border-radius: 12px;
  background: #182234;
}

.status-dot {
  width: 10px;
  height: 10px;
  flex-shrink: 0;

  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 5px rgb(34 197 94 / 12%);
}

.system-status div {
  display: flex;
  flex-direction: column;
}

.system-status strong {
  font-size: 12px;
  white-space: nowrap;
}

.system-status small {
  margin-top: 4px;
  color: #94a3b8;
  font-size: 10px;
  white-space: nowrap;
}
</style>