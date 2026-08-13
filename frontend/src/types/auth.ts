export type UserRole =
  | 'ADMIN'
  | 'DEVOPS'
  | 'DEVELOPER'
  | 'VIEWER'

export interface LoginRequest {
  email: string
  password: string
}

export interface AuthResponse {
  id: number
  firstName: string
  lastName: string
  email: string
  role: UserRole
  accessToken: string
  refreshToken: string
  tokenType: string
  expiresIn: number
}

export interface RegisterRequest {
  firstName: string
  lastName: string
  email: string
  password: string
}

export interface RegisterResponse {
  id: number
  firstName: string
  lastName: string
  email: string
  role: UserRole
  enabled: boolean
}