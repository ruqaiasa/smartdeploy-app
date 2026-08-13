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