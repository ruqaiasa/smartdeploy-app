import axios from 'axios'
import { defineStore } from 'pinia'

import {
  createVersion,
  deleteVersion,
  getVersions,
  restoreVersion,
  updateVersion,
  updateVersionBuildStatus,
  updateVersionMetadata,
} from '@/api/versions.api'

import type {
  BuildStatus,
  CreateVersionRequest,
  UpdateBuildMetadataRequest,
  UpdateVersionRequest,
  VersionResponse,
  VersionSearchParams,
} from '@/types/version'

function extractErrorMessage(
  error: unknown,
  fallback: string,
): string {
  if (axios.isAxiosError(error)) {
    const message =
      error.response?.data?.message

    if (typeof message === 'string') {
      return message
    }
  }

  return fallback
}

export const useVersionsStore = defineStore(
  'versions',
  {
    state: () => ({
      versions: [] as VersionResponse[],

      loading: false,
      actionLoading: false,

      errorMessage: null as string | null,

      page: 0,
      size: 10,

      totalElements: 0,
      totalPages: 0,

      search: '',
      buildStatus: null as BuildStatus | null,
      applicationId: null as number | null,
    }),

    actions: {
      async loadVersions(
        params: VersionSearchParams = {},
      ) {
        this.loading = true
        this.errorMessage = null

        try {
          const response =
            await getVersions({
              page: this.page,
              size: this.size,

              search:
                this.search || undefined,

              buildStatus:
                this.buildStatus || undefined,

              applicationId:
                this.applicationId || undefined,

              ...params,
            })

          this.versions =
            response.content

          this.page =
            response.page

          this.size =
            response.size

          this.totalElements =
            response.totalElements

          this.totalPages =
            response.totalPages
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de charger les versions.',
            )
        } finally {
          this.loading = false
        }
      },

      async create(
        request: CreateVersionRequest,
      ) {
        this.actionLoading = true
        this.errorMessage = null

        try {
          await createVersion(request)

          this.page = 0

          await this.loadVersions()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de créer la version.',
            )

          throw error
        } finally {
          this.actionLoading = false
        }
      },

      async update(
        id: number,
        request: UpdateVersionRequest,
      ) {
        this.actionLoading = true
        this.errorMessage = null

        try {
          await updateVersion(
            id,
            request,
          )

          await this.loadVersions()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de modifier la version.',
            )

          throw error
        } finally {
          this.actionLoading = false
        }
      },

      async remove(
        id: number,
      ) {
        this.actionLoading = true
        this.errorMessage = null

        try {
          await deleteVersion(id)

          if (
            this.versions.length === 1
            && this.page > 0
          ) {
            this.page -= 1
          }

          await this.loadVersions()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de supprimer la version.',
            )

          throw error
        } finally {
          this.actionLoading = false
        }
      },

      async restore(
        id: number,
      ) {
        this.actionLoading = true
        this.errorMessage = null

        try {
          await restoreVersion(id)

          await this.loadVersions()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de restaurer la version.',
            )

          throw error
        } finally {
          this.actionLoading = false
        }
      },

      async updateStatus(
        id: number,
        status: BuildStatus,
      ) {
        this.actionLoading = true
        this.errorMessage = null

        try {
          await updateVersionBuildStatus(
            id,
            status,
          )

          await this.loadVersions()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de modifier le statut du build.',
            )

          throw error
        } finally {
          this.actionLoading = false
        }
      },

      async updateMetadata(
        id: number,
        request: UpdateBuildMetadataRequest,
      ) {
        this.actionLoading = true
        this.errorMessage = null

        try {
          await updateVersionMetadata(
            id,
            request,
          )

          await this.loadVersions()
        } catch (error) {
          this.errorMessage =
            extractErrorMessage(
              error,
              'Impossible de modifier les métadonnées du build.',
            )

          throw error
        } finally {
          this.actionLoading = false
        }
      },

      async refresh() {
        await this.loadVersions()
      },
    },
  },
)