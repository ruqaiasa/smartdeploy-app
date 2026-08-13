import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { login as loginRequest } from '@/api/auth.api'

import type {
  AuthResponse,
  LoginRequest,
  UserRole,
} from '@/types/auth'

const AUTH_STORAGE_KEY = 'smartdeploy-auth'

export const useAuthStore = defineStore('auth', () => {
  const storedAuth = localStorage.getItem(AUTH_STORAGE_KEY)

  const initialAuth: AuthResponse | null = storedAuth
    ? JSON.parse(storedAuth)
    : null

  const auth = ref<AuthResponse | null>(initialAuth)
  const loading = ref(false)
  const errorMessage = ref<string | null>(null)

  const isAuthenticated = computed(() => {
    return auth.value !== null
  })

  const accessToken = computed(() => {
    return auth.value?.accessToken ?? null
  })

  const refreshToken = computed(() => {
    return auth.value?.refreshToken ?? null
  })

  const currentUser = computed(() => {
    if (!auth.value) {
      return null
    }

    return {
      id: auth.value.id,
      firstName: auth.value.firstName,
      lastName: auth.value.lastName,
      email: auth.value.email,
      role: auth.value.role,
    }
  })

  const fullName = computed(() => {
    if (!auth.value) {
      return ''
    }

    return `${auth.value.firstName} ${auth.value.lastName}`
  })

  const role = computed<UserRole | null>(() => {
    return auth.value?.role ?? null
  })

  async function login(credentials: LoginRequest) {
    loading.value = true
    errorMessage.value = null

    try {
      const response = await loginRequest(credentials)

      auth.value = response

      localStorage.setItem(
        AUTH_STORAGE_KEY,
        JSON.stringify(response),
      )
    } catch (error) {
      errorMessage.value =
        'Email ou mot de passe incorrect.'

      throw error
    } finally {
      loading.value = false
    }
  }

  function logout() {
    auth.value = null
    errorMessage.value = null

    localStorage.removeItem(AUTH_STORAGE_KEY)
  }

  function hasRole(...roles: UserRole[]) {
    if (!auth.value) {
      return false
    }

    return roles.includes(auth.value.role)
  }

  return {
    auth,
    loading,
    errorMessage,

    isAuthenticated,
    accessToken,
    refreshToken,
    currentUser,
    fullName,
    role,

    login,
    logout,
    hasRole,
  }
})