import axios from 'axios'
import { defineStore } from 'pinia'

import {
  activateApplication,
  createApplication,
  deactivateApplication,
  deleteApplication,
  getApplications,
  restoreApplication,
  updateApplication,
} from '@/api/applications.api'

import type {
  ApplicationResponse,
  ApplicationSearchParams,
  CreateApplicationRequest,
  UpdateApplicationRequest,
} from '@/types/application'

function extractErrorMessage(
  error: unknown,
  fallbackMessage: string,
): string {
  if (axios.isAxiosError(error)) {
    const backendMessage =
      error.response?.data?.message

    if (typeof backendMessage === 'string') {
      return backendMessage
    }
  }

  return fallbackMessage
}

export const useApplicationsStore = defineStore(
  'applications',
  {
    state: () => ({
      applications: [] as ApplicationResponse[],
      loading: false,
      actionLoading: false,
      errorMessage: null as string | null,

      page: 0,
      size: 10,
      totalPages: 0,
      totalElements: 0,
    }),

    actions: {
      async loadApplications(
        params: ApplicationSearchParams = {},
      ) {
        this.loading = true
        this.errorMessage = null

        try {
          const response =
            await getApplications({
              page: this.page,
              size: this.size,
              ...params,
            })

          this.applications = response.content
          this.page = response.page
          this.size = response.size
          this.totalPages = response.totalPages
          this.totalElements =
            response.totalElements
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de charger les applications.',
            )
        } finally {
          this.loading = false
        }
      },

      async create(
        request: CreateApplicationRequest,
      ) {
        this.actionLoading = true
        this.errorMessage = null

        try {
          await createApplication(request)

          this.page = 0
          await this.loadApplications()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de créer l’application.',
            )

          throw error
        } finally {
          this.actionLoading = false
        }
      },

      async update(
        id: number,
        request: UpdateApplicationRequest,
      ) {
        this.actionLoading = true
        this.errorMessage = null

        try {
          await updateApplication(id, request)
          await this.loadApplications()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de modifier l’application.',
            )

          throw error
        } finally {
          this.actionLoading = false
        }
      },

      async remove(id: number) {
        this.actionLoading = true
        this.errorMessage = null

        try {
          await deleteApplication(id)

          if (
            this.applications.length === 1 &&
            this.page > 0
          ) {
            this.page -= 1
          }

          await this.loadApplications()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de supprimer l’application.',
            )

          throw error
        } finally {
          this.actionLoading = false
        }
      },

      async restore(id: number) {
        this.actionLoading = true
        this.errorMessage = null

        try {
          await restoreApplication(id)
          await this.loadApplications()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de restaurer l’application.',
            )

          throw error
        } finally {
          this.actionLoading = false
        }
      },

      async activate(id: number) {
        this.actionLoading = true
        this.errorMessage = null

        try {
          await activateApplication(id)
          await this.loadApplications()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible d’activer l’application.',
            )

          throw error
        } finally {
          this.actionLoading = false
        }
      },

      async deactivate(id: number) {
        this.actionLoading = true
        this.errorMessage = null

        try {
          await deactivateApplication(id)
          await this.loadApplications()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de désactiver l’application.',
            )

          throw error
        } finally {
          this.actionLoading = false
        }
      },

      async refresh() {
        await this.loadApplications()
      },
    },
  },
)