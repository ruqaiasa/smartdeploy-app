import axios from 'axios'
import { defineStore } from 'pinia'

import {
  activateEnvironment,
  createEnvironment,
  deactivateEnvironment,
  deleteEnvironment,
  getEnvironments,
  restoreEnvironment,
  updateEnvironment,
} from '@/api/environments.api'

import type {
  CreateEnvironmentRequest,
  EnvironmentResponse,
  EnvironmentSearchParams,
  UpdateEnvironmentRequest,
} from '@/types/environment'

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

export const useEnvironmentsStore = defineStore(
  'environments',
  {
    state: () => ({
      environments: [] as EnvironmentResponse[],

      loading: false,
      actionLoading: false,
      errorMessage: null as string | null,

      page: 0,
      size: 10,
      totalPages: 0,
      totalElements: 0,
    }),

    actions: {
      async loadEnvironments(
        params: EnvironmentSearchParams = {},
      ) {
        this.loading = true
        this.errorMessage = null

        try {
          const response =
            await getEnvironments({
              page: this.page,
              size: this.size,
              ...params,
            })

          this.environments = response.content
          this.page = response.page
          this.size = response.size
          this.totalPages = response.totalPages
          this.totalElements =
            response.totalElements
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de charger les environnements.',
            )
        } finally {
          this.loading = false
        }
      },

      async create(
        request: CreateEnvironmentRequest,
      ) {
        this.actionLoading = true
        this.errorMessage = null

        try {
          await createEnvironment(request)

          this.page = 0
          await this.loadEnvironments()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de créer l’environnement.',
            )

          throw error
        } finally {
          this.actionLoading = false
        }
      },

      async update(
        id: number,
        request: UpdateEnvironmentRequest,
      ) {
        this.actionLoading = true
        this.errorMessage = null

        try {
          await updateEnvironment(id, request)
          await this.loadEnvironments()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de modifier l’environnement.',
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
          await deleteEnvironment(id)

          if (
            this.environments.length === 1 &&
            this.page > 0
          ) {
            this.page -= 1
          }

          await this.loadEnvironments()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de supprimer l’environnement.',
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
          await restoreEnvironment(id)
          await this.loadEnvironments()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de restaurer l’environnement.',
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
          await activateEnvironment(id)
          await this.loadEnvironments()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible d’activer l’environnement.',
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
          await deactivateEnvironment(id)
          await this.loadEnvironments()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de désactiver l’environnement.',
            )

          throw error
        } finally {
          this.actionLoading = false
        }
      },

      async refresh() {
        await this.loadEnvironments()
      },
    },
  },
)