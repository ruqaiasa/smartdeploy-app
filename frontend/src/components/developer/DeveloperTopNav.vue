<script setup lang="ts">
import { computed } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/* Interface du développeur : une barre horizontale, claire, distincte de la console DevOps. */
const auth = useAuthStore()
const router = useRouter()
const initials = computed(() => {
  const u = auth.currentUser
  return u ? `${u.firstName?.[0] ?? ''}${u.lastName?.[0] ?? ''}`.toUpperCase() : ''
})
const LINKS = [
  { to: '/portal', label: 'Accueil' },
  { to: '/applications', label: 'Applications' },
  { to: '/environments', label: 'Environnements' },
  { to: '/versions', label: 'Versions' },
  { to: '/deployments', label: 'Déploiements' },
  { to: '/knowledge', label: 'Aide' },
]
async function logout() {
  await auth.logout()
  await router.push('/login')
}
</script>

<template>
  <header class="dn">
    <div class="dn-inner">
      <RouterLink to="/portal" class="dn-brand">
        <span class="dn-logo">S</span>
        <span class="dn-name">SmartDeploy</span>
        <span class="dn-badge">Développeur</span>
      </RouterLink>
      <nav class="dn-links" aria-label="Navigation">
        <RouterLink v-for="l in LINKS" :key="l.to" :to="l.to" class="dn-link" active-class="is-active">{{ l.label }}</RouterLink>
      </nav>
      <div class="dn-user">
        <span class="dn-avatar" :title="auth.fullName">{{ initials }}</span>
        <button type="button" class="dn-logout" @click="logout">Déconnexion</button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.dn { position: sticky; top: 0; z-index: 40; border-bottom: 1px solid #ede9fe; background: rgba(255, 255, 255, .92); backdrop-filter: blur(8px); }
.dn-inner { display: flex; align-items: center; gap: 20px; max-width: 1240px; margin: 0 auto; padding: 10px 24px; }
.dn-brand { display: flex; align-items: center; gap: 8px; color: #1f1147; text-decoration: none; }
.dn-logo { display: grid; place-items: center; width: 32px; height: 32px; border-radius: 9px; background: linear-gradient(135deg, #7c3aed, #4f46e5); color: #fff; font-weight: 800; }
.dn-name { font-size: 16px; font-weight: 800; }
.dn-badge { padding: 2px 8px; border-radius: 999px; background: #ede9fe; color: #6d28d9; font-size: 11.5px; font-weight: 700; }
.dn-links { display: flex; flex: 1; flex-wrap: wrap; gap: 2px; }
.dn-link { padding: 7px 12px; border-radius: 8px; color: #4b5563; font-size: 14px; font-weight: 600; text-decoration: none; }
.dn-link:hover { background: #f5f3ff; color: #5b21b6; }
.dn-link.is-active { background: #ede9fe; color: #5b21b6; }
.dn-user { display: flex; align-items: center; gap: 10px; }
.dn-avatar { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 999px; background: #1f1147; color: #fff; font-size: 13px; font-weight: 700; }
.dn-logout { padding: 7px 12px; border: 1px solid #e5e7eb; border-radius: 8px; background: #fff; color: #374151; font: inherit; font-size: 13px; font-weight: 600; cursor: pointer; }
@media (max-width: 900px) { .dn-inner { flex-wrap: wrap; } .dn-links { order: 3; width: 100%; } }
</style>
