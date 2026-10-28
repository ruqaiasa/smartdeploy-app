import httpClient from './httpClient'

import type {
  Alert,
} from '@/types/alert'

export interface AlertFilters {
  applicationId?: number
  environmentId?: number
}

const ALERTS_ENDPOINT = '/api/alerts'

function buildParams(
  filters?: AlertFilters,
) {
  if (
    filters?.applicationId !== undefined
    && filters?.environmentId !== undefined
  ) {
    return {
      applicationId:
        filters.applicationId,

      environmentId:
        filters.environmentId,
    }
  }

  return undefined
}

export async function getAlerts(
  filters?: AlertFilters,
): Promise<Alert[]> {
  const response =
    await httpClient.get<Alert[]>(
      ALERTS_ENDPOINT,
      {
        params: buildParams(filters),
      },
    )

  return response.data
}

export async function getOpenAlerts(
  filters?: AlertFilters,
): Promise<Alert[]> {
  const response =
    await httpClient.get<Alert[]>(
      `${ALERTS_ENDPOINT}/open`,
      {
        params: buildParams(filters),
      },
    )

  return response.data
}