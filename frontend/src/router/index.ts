import {
  createRouter,
  createWebHistory,
} from 'vue-router'

import AppLayout from '@/layouts/AppLayout.vue'

import LoginView from '@/views/LoginView.vue'
import RegisterView from '@/views/RegisterView.vue'
import DashboardView from '@/views/DashboardView.vue'
import ApplicationsView from '@/views/ApplicationsView.vue'
import EnvironmentsView from '@/views/EnvironmentsView.vue'
import VersionsView from '@/views/VersionsView.vue'
import DeploymentsView from '@/views/DeploymentsView.vue'

import { useAuthStore } from '@/stores/auth'

import type {
  UserRole,
} from '@/types/auth'

const router = createRouter({
  history: createWebHistory(),

  routes: [
    {
      path: '/',
      redirect: '/dashboard',
    },

    {
      path: '/login',
      name: 'login',
      component: LoginView,

      meta: {
        title: 'Connexion',
        requiresGuest: true,
      },
    },

    {
      path: '/register',
      name: 'register',
      component: RegisterView,

      meta: {
        title: 'Créer un compte',
        requiresGuest: true,
      },
    },

    {
      path: '/',
      component: AppLayout,

      meta: {
        requiresAuth: true,
      },

      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: DashboardView,

          meta: {
            title: 'Dashboard',
          },
        },

        {
          path: 'applications',
          name: 'applications',
          component: ApplicationsView,

          meta: {
            title: 'Applications',
          },
        },

        {
          path: 'applications/:id',
          name: 'application-details',

          component: () =>
            import(
              '@/views/ApplicationDetailsView.vue'
            ),

          meta: {
            title: 'Détail application',
          },
        },

        {
          path: 'applications/:id/environments',
          name: 'application-environments',

          component: () =>
            import(
              '@/views/ApplicationEnvironmentsView.vue'
            ),

          meta: {
            title:
              'Environnements application',
          },
        },

        {
          path: 'applications/:id/versions',
          name: 'application-versions',

          component: () =>
            import(
              '@/views/ApplicationVersionsView.vue'
            ),

          meta: {
            title: 'Versions application',
          },
        },

        {
          path: 'applications/:id/deployments',
          name: 'application-deployments',

          component: () =>
            import(
              '@/views/ApplicationDeploymentsView.vue'
            ),

          meta: {
            title:
              'Déploiements application',
          },
        },

        {
          path: 'environments',
          name: 'environments',
          component: EnvironmentsView,

          meta: {
            title: 'Environnements',
          },
        },

        {
          path: 'versions',
          name: 'versions',
          component: VersionsView,

          meta: {
            title: 'Versions',
          },
        },

        {
          path: 'deployments',
          name: 'deployments',
          component: DeploymentsView,

          meta: {
            title: 'Déploiements',
          },
        },

        {
          path: 'alerts',
          name: 'alerts',

          component: () =>
            import(
              '@/views/AlertsView.vue'
            ),

          meta: {
            title: 'Alertes',
          },
        },

        {
          path: 'users',
          name: 'users',

          component: () =>
            import(
              '@/views/UsersView.vue'
            ),

          meta: {
            title: 'Utilisateurs',
            roles: [
              'ADMIN',
            ] satisfies UserRole[],
          },
        },
      ],
    },

    {
      path: '/:pathMatch(.*)*',
      redirect: '/dashboard',
    },
  ],
})

router.beforeEach((to) => {
  const authStore =
    useAuthStore()

  const requiresAuth =
    to.matched.some(
      (record) =>
        record.meta.requiresAuth,
    )

  const requiresGuest =
    to.matched.some(
      (record) =>
        record.meta.requiresGuest,
    )

  if (
    requiresAuth
    && !authStore.isAuthenticated
  ) {
    return {
      path: '/login',

      query: {
        redirect: to.fullPath,
      },
    }
  }

  if (
    requiresGuest
    && authStore.isAuthenticated
  ) {
    return '/dashboard'
  }

  const allowedRoles =
    to.meta.roles as
      | UserRole[]
      | undefined

  if (
    allowedRoles
    && !authStore.hasRole(
      ...allowedRoles,
    )
  ) {
    return '/dashboard'
  }

  const pageTitle =
    typeof to.meta.title === 'string'
      ? to.meta.title
      : 'SmartDeploy'

  document.title =
    `${pageTitle} • SmartDeploy`

  return true
})

export default router