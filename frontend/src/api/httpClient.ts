import axios from 'axios'

console.log(
  'VITE_API_BASE_URL =',
  import.meta.env.VITE_API_BASE_URL,
)

const AUTH_STORAGE_KEY = 'smartdeploy-auth'

const httpClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 10000,

  headers: {
    'Content-Type': 'application/json',
  },
})

httpClient.interceptors.request.use((config) => {
  const storedAuth = localStorage.getItem(
    AUTH_STORAGE_KEY,
  )

  if (!storedAuth) {
    return config
  }

  const parsedAuth = JSON.parse(storedAuth) as {
    accessToken?: string
  }

  if (parsedAuth.accessToken) {
    config.headers.Authorization =
      `Bearer ${parsedAuth.accessToken}`
  }

  return config
})

export default httpClient