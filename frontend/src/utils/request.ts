import axios, { type AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

interface RetryRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean
  _skipAuthRefresh?: boolean
  _suppressError?: boolean
}

interface BackendResult<T> {
  code?: number
  message?: string
  data?: T
}

interface RefreshResult {
  accessToken: string
  refreshToken?: string
  expiresIn?: number
}

const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000
})

const refreshService = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000
})

let refreshPromise: Promise<string> | null = null

function unwrapBackendResult<T>(response: AxiosResponse<BackendResult<T> | T>) {
  const result = response.data as BackendResult<T>
  if (result && typeof result.code === 'number' && result.code !== 0 && result.code !== 200) {
    return Promise.reject(new Error(result.message || '请求处理失败'))
  }
  return (result?.data ?? response.data) as T
}

function isAuthEntryRequest(url?: string) {
  if (!url) return false
  return [
    '/auth/captcha',
    '/auth/captcha/verify',
    '/auth/login',
    '/auth/register',
    '/auth/register-with-avatar',
    '/auth/refresh'
  ].some((path) => url.includes(path))
}

async function refreshAccessToken(refreshToken: string) {
  const authStore = useAuthStore()
  const response = await refreshService.post<BackendResult<RefreshResult> | RefreshResult>('/auth/refresh', { refreshToken })
  const result = await unwrapBackendResult<RefreshResult>(response)
  authStore.updateTokens(result.accessToken, result.refreshToken, result.expiresIn)
  return result.accessToken
}

service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`
  }
  return config
})

service.interceptors.response.use(
  (response) => {
    const result = response.data
    if (result && typeof result.code === 'number' && result.code !== 0 && result.code !== 200) {
      ElMessage.error(result.message || '请求处理失败')
      return Promise.reject(result)
    }
    return result?.data ?? result
  },
  async (error: AxiosError<{ message?: string }>) => {
    const config = error.config as RetryRequestConfig | undefined
    const authStore = useAuthStore()

    if (
      error.response?.status === 401 &&
      config &&
      !config._retry &&
      !config._skipAuthRefresh &&
      !isAuthEntryRequest(config.url) &&
      authStore.refreshToken
    ) {
      config._retry = true
      try {
        refreshPromise ||= refreshAccessToken(authStore.refreshToken).finally(() => {
          refreshPromise = null
        })
        const accessToken = await refreshPromise
        config.headers.Authorization = `Bearer ${accessToken}`
        return service(config)
      } catch (refreshError) {
        authStore.clearSession()
        router.replace('/login')
        ElMessage.warning('登录状态已过期，请重新登录')
        return Promise.reject(refreshError)
      }
    }

    if (config?._suppressError) {
      return Promise.reject(error)
    }

    if (error.response?.status === 401) {
      authStore.clearSession()
      router.replace('/login')
      ElMessage.warning('登录状态已过期，请重新登录')
    } else {
      ElMessage.error(error.response?.data?.message || error.message || '网络请求失败')
    }
    return Promise.reject(error)
  }
)

export default service
