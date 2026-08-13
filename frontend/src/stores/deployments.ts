import axios from 'axios'
import { defineStore } from 'pinia'

import {
  createDeployment,
  deleteDeployment,
  getDeployments,
  rollbackDeployment,
  updateDeploymentMetadata,
  updateDeploymentStatus,
} from '@/api/deployments.api'

import type {
  CreateDeploymentRequest,
  DeploymentResponse,
  DeploymentSearchParams,
  DeploymentStatus,
  UpdateDeploymentMetadataRequest,
  UpdateDeploymentStatusRequest,
} from '@/types/deployment'

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

export const useDeploymentsStore =
  defineStore(
    'deployments',
    {
      state: () => ({
        deployments:
          [] as DeploymentResponse[],

        loading: false,
        actionLoading: false,

        errorMessage:
          null as string | null,

        page: 0,
        size: 10,

        totalElements: 0,
        totalPages: 0,

        search: '',

        status:
          null as DeploymentStatus | null,

        applicationId:
          null as number | null,

        versionId:
          null as number | null,

        environmentId:
          null as number | null,
      }),

      actions: {
        async loadDeployments(
          params: DeploymentSearchParams = {},
        ) {
          this.loading = true
          this.errorMessage = null

          try {
            const response =
              await getDeployments({
                page: this.page,
                size: this.size,

                search:
                  this.search || undefined,

                status:
                  this.status || undefined,

                applicationId:
                  this.applicationId
                  || undefined,

                versionId:
                  this.versionId
                  || undefined,

                environmentId:
                  this.environmentId
                  || undefined,

                ...params,
              })

            this.deployments =
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
                'Impossible de charger les déploiements.',
              )
          } finally {
            this.loading = false
          }
        },

        async create(
          request: CreateDeploymentRequest,
        ) {
          this.actionLoading = true
          this.errorMessage = null

          try {
            await createDeployment(request)

            this.page = 0

            await this.loadDeployments()
          } catch (error) {
            this.errorMessage =
              extractErrorMessage(
                error,
                'Impossible de créer le déploiement.',
              )

            throw error
          } finally {
            this.actionLoading = false
          }
        },

        async updateStatus(
          id: number,
          request:
            UpdateDeploymentStatusRequest,
        ) {
          this.actionLoading = true
          this.errorMessage = null

          try {
            await updateDeploymentStatus(
              id,
              request,
            )

            await this.loadDeployments()
          } catch (error) {
            this.errorMessage =
              extractErrorMessage(
                error,
                'Impossible de modifier le statut.',
              )

            throw error
          } finally {
            this.actionLoading = false
          }
        },

        async updateMetadata(
          id: number,
          request:
            UpdateDeploymentMetadataRequest,
        ) {
          this.actionLoading = true
          this.errorMessage = null

          try {
            await updateDeploymentMetadata(
              id,
              request,
            )

            await this.loadDeployments()
          } catch (error) {
            this.errorMessage =
              extractErrorMessage(
                error,
                'Impossible de modifier les métadonnées.',
              )

            throw error
          } finally {
            this.actionLoading = false
          }
        },

        async rollback(
          id: number,
        ) {
          this.actionLoading = true
          this.errorMessage = null

          try {
            await rollbackDeployment(id)

            await this.loadDeployments()
          } catch (error) {
            this.errorMessage =
              extractErrorMessage(
                error,
                'Impossible d’effectuer le rollback.',
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
            await deleteDeployment(id)

            if (
              this.deployments.length === 1
              && this.page > 0
            ) {
              this.page -= 1
            }

            await this.loadDeployments()
          } catch (error) {
            this.errorMessage =
              extractErrorMessage(
                error,
                'Impossible de supprimer le déploiement.',
              )

            throw error
          } finally {
            this.actionLoading = false
          }
        },

        async refresh() {
          await this.loadDeployments()
        },
      },
    },
  )