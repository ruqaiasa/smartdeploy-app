import httpClient from './httpClient'

import type {
  AuthResponse,
  LoginRequest,
  RegisterRequest,
  RegisterResponse,
} from '@/types/auth'

export async function login(
  request: LoginRequest,
): Promise<AuthResponse> {
  const response = await httpClient.post<AuthResponse>(
    '/api/auth/login',
    request,
  )

  return response.data
}

export async function register(
  request: RegisterRequest,
): Promise<RegisterResponse> {
  const response =
    await httpClient.post<RegisterResponse>(
      '/api/users/register',
      request,
    )

  return response.data
}
/* Mot de passe oublié : le jeton voyage toujours dans le corps des requêtes. */
export async function requestPasswordReset(email: string): Promise<string> {
  const response = await httpClient.post<{ message: string }>('/api/auth/forgot-password', { email })
  return response.data.message
}

export async function validateResetToken(token: string): Promise<boolean> {
  const response = await httpClient.post<{ valid: boolean }>('/api/auth/reset-password/validate', { token })
  return response.data.valid === true
}

export async function resetPassword(token: string, newPassword: string): Promise<string> {
  const response = await httpClient.post<{ message: string }>('/api/auth/reset-password', { token, newPassword })
  return response.data.message
}
