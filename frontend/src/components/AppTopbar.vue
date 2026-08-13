<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

defineProps<{
  pageTitle: string
}>()

const router = useRouter()
const authStore = useAuthStore()

const initials = computed(() => {
  const user = authStore.currentUser

  if (!user) {
    return '?'
  }

  return (
    user.firstName.charAt(0) +
    user.lastName.charAt(0)
  ).toUpperCase()
})

const displayedRole = computed(() => {
  const roleLabels = {
    ADMIN: 'Administratrice',
    DEVOPS: 'DevOps',
    DEVELOPER: 'Développeuse',
    VIEWER: 'Consultation',
  }

  return authStore.role
    ? roleLabels[authStore.role]
    : ''
})

async function logout() {
  authStore.logout()
  await router.push('/login')
}
</script>

<template>
  <header class="topbar">
    <div>
      <p class="eyebrow">Plateforme DevOps</p>
      <h1>{{ pageTitle }}</h1>
    </div>

    <div class="topbar-actions">
      <button
        class="notification-button"
        type="button"
        aria-label="Notifications"
      >
        <span>○</span>
        <span class="notification-indicator"></span>
      </button>

      <div class="user-profile">
        <div class="avatar">
          {{ initials }}
        </div>

        <div class="user-information">
          <strong>
            {{ authStore.fullName }}
          </strong>

          <small>
            {{ displayedRole }}
          </small>
        </div>
      </div>

      <button
        class="logout-button"
        type="button"
        @click="logout"
      >
        Déconnexion
      </button>
    </div>
  </header>
</template>

<style scoped>
.topbar {
  display: flex;
  min-height: 76px;
  align-items: center;
  justify-content: space-between;
  gap: 24px;

  padding: 0 32px;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
}

.eyebrow {
  margin: 0 0 4px;

  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h1 {
  margin: 0;

  color: #111827;
  font-size: 23px;
  line-height: 1.2;
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 18px;
}

.notification-button {
  position: relative;

  display: grid;
  width: 41px;
  height: 41px;
  place-items: center;

  border: 1px solid #e2e8f0;
  border-radius: 11px;
  background: #ffffff;
  color: #475569;
}

.notification-indicator {
  position: absolute;
  top: 7px;
  right: 7px;

  width: 7px;
  height: 7px;

  border: 2px solid #ffffff;
  border-radius: 50%;
  background: #ef4444;
}

.user-profile {
  display: flex;
  align-items: center;
  gap: 11px;
}

.avatar {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;

  border-radius: 12px;
  background: #dbeafe;
  color: #1d4ed8;

  font-size: 13px;
  font-weight: 800;
}

.user-information {
  display: flex;
  flex-direction: column;
}

.user-information strong {
  color: #1e293b;
  font-size: 13px;
}

.user-information small {
  margin-top: 3px;
  color: #64748b;
  font-size: 11px;
}

.logout-button {
  min-height: 39px;
  padding: 0 13px;

  border: 1px solid #fecaca;
  border-radius: 9px;
  background: #ffffff;
  color: #b91c1c;

  font-size: 12px;
  font-weight: 700;
}

.logout-button:hover {
  background: #fef2f2;
}

@media (max-width: 700px) {
  .topbar {
    padding: 0 18px;
  }

  .user-information {
    display: none;
  }
}
</style>