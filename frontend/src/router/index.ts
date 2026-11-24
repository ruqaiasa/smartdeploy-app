import {
  createRouter,
  createWebHistory,
} from 'vue-router'

import AppLayout from '@/layouts/AppLayout.vue'

import LoginView from '@/views/LoginView.vue'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import ResetPasswordView from '@/views/ResetPasswordView.vue'
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
      path: '/forgot-password',
      name: 'forgot-password',
      component: ForgotPasswordView,
      meta: {
        title: 'Mot de passe oublié',
        requiresGuest: true,
      },
    },

    {
      // Accessible connecté ou non : le lien arrive par e-mail.
      path: '/reset-password',
      name: 'reset-password',
      component: ResetPasswordView,
      meta: {
        title: 'Nouveau mot de passe',
      },
    },

    {
      // Post-mortem : hors de la mise en page, pour s'imprimer proprement en PDF.
      path: '/rapport/:id(\\d+)',
      name: 'postmortem',
      component: () => import('@/views/PostMortemView.vue'),
      meta: { title: 'Rapport d’incident', requiresAuth: true },
    },

    {
      // Accessible connecté ou non : le lien arrive par courriel (RG-U3).
      path: '/verify-email',
      name: 'verify-email',
      component: () => import('@/views/VerifyEmailView.vue'),
      meta: {
        title: 'Confirmation de l’adresse',
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
            // Chiffres de toute la plateforme : le développeur a « Mon espace ».
            roles: [
              'VIEWER',
              'DEVOPS',
              'ADMIN',
            ] satisfies UserRole[],
          },
        },

        {
          path: 'applications',
          name: 'applications',
          component: ApplicationsView,

          meta: {
            roles: ['VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN'] satisfies UserRole[],
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
            roles: ['VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN'] satisfies UserRole[],
            title: 'Environnements',
          },
        },

        {
          path: 'versions',
          name: 'versions',
          component: VersionsView,

          meta: {
            roles: ['VIEWER', 'DEVELOPER', 'DEVOPS', 'ADMIN'] satisfies UserRole[],
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
            roles: ['VIEWER', 'DEVOPS', 'ADMIN'] satisfies UserRole[],
            title: 'Alertes',
          },
        },

        {
          path: 'audit',
          name: 'audit',

          component: () =>
            import(
              '@/views/AuditView.vue'
            ),

          meta: {
            title: 'Journal d’activité',
            roles: [
              'ADMIN',
            ] satisfies UserRole[],
          },
        },

        {
          path: 'deployments/:id(\\d+)',
          name: 'deployment-detail',
          component: () => import('@/views/DeploymentDetailView.vue'),
          meta: { title: 'Fiche de déploiement' },
        },

        {
          path: 'applications/:id(\\d+)/excellence',
          name: 'application-scorecard',
          component: () => import('@/views/ScorecardView.vue'),
          meta: { title: 'Fiche d’excellence' },
        },

        {
          path: 'supervision',
          name: 'supervision',
          component: () => import('@/views/SupervisionView.vue'),
          meta: { title: 'Supervision', roles: ['VIEWER', 'DEVOPS', 'ADMIN'] satisfies UserRole[] },
        },

        {
          path: 'meetings',
          name: 'meetings',
          component: () => import('@/views/MeetingsView.vue'),
          meta: { title: 'Rendez-vous', roles: ['DEVELOPER', 'DEVOPS', 'ADMIN'] satisfies UserRole[] },
        },

        {
          path: 'meetings/:id(\\d+)',
          name: 'meeting-room',
          component: () => import('@/views/MeetingRoomView.vue'),
          meta: { title: 'Visioconférence', roles: ['DEVELOPER', 'DEVOPS', 'ADMIN'] satisfies UserRole[] },
        },

        {
          path: 'claims',
          name: 'claims',
          component: () => import('@/views/ClaimsView.vue'),
          meta: { title: 'Réclamations', roles: ['DEVELOPER', 'DEVOPS', 'ADMIN'] satisfies UserRole[] },
        },

        {
          path: 'claims/:id(\\d+)',
          name: 'claim-detail',
          component: () => import('@/views/ClaimDetailView.vue'),
          meta: { title: 'Réclamation', roles: ['DEVELOPER', 'DEVOPS', 'ADMIN'] satisfies UserRole[] },
        },

        {
          path: 'knowledge',
          name: 'knowledge',
          component: () => import('@/views/KnowledgeView.vue'),
          meta: { title: 'Aide' },
        },

        {
          path: 'console',
          name: 'console',
          component: () => import('@/views/WorkQueueView.vue'),
          meta: {
            title: 'File de travail',
            roles: [
              'DEVOPS',
              'ADMIN',
            ] satisfies UserRole[],
          },
        },

        {
          path: 'portal',
          name: 'portal',

          component: () =>
            import(
              '@/views/PortalView.vue'
            ),

          meta: {
            roles: ['DEVELOPER'] satisfies UserRole[],
            title: 'Mon espace',
          },
        },

        {
          path: 'backups',
          name: 'backups',

          component: () =>
            import(
              '@/views/BackupsView.vue'
            ),

          meta: {
            roles: ['VIEWER', 'DEVOPS', 'ADMIN'] satisfies UserRole[],
            title: 'Sauvegardes',
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

  // Page d’accueil selon le rôle : « Mon espace » pour un développeur.
  const home =
    authStore.hasRole('DEVELOPER') && !authStore.hasRole('VIEWER', 'DEVOPS', 'ADMIN')
      ? '/portal'
      : '/dashboard'

  if (
    requiresGuest
    && authStore.isAuthenticated
  ) {
    return home
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
    return to.path === home ? '/portal' : home
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