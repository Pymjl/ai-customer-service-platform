import { defineStore } from 'pinia'
import { fetchCurrentUser, login, logout } from '@/api/auth'
import type { BackendLoginResult, CurrentUser, LoginPayload, LoginResult } from '@/types/system'

interface AuthState {
  token: string
  refreshToken: string
  expiresIn: number | null
  user: CurrentUser | null
}

const TOKEN_KEY = 'ai-customer-service-token'
const REFRESH_TOKEN_KEY = 'ai-customer-service-refresh-token'
const EXPIRES_IN_KEY = 'ai-customer-service-expires-in'

function readExpiresIn() {
  const value = Number(localStorage.getItem(EXPIRES_IN_KEY))
  return Number.isFinite(value) && value > 0 ? value : null
}

function isBackendLoginResult(result: BackendLoginResult | void): result is BackendLoginResult {
  return Boolean(result && typeof result === 'object' && 'accessToken' in result && result.accessToken)
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    refreshToken: localStorage.getItem(REFRESH_TOKEN_KEY) || '',
    expiresIn: readExpiresIn(),
    user: null
  }),
  getters: {
    isLoggedIn: (state) => Boolean(state.token)
  },
  actions: {
    async login(payload: LoginPayload) {
      const result = await login(payload)
      this.setSession(result)
      if (!this.user) {
        await this.loadCurrentUser()
      }
    },
    async applyRegisterResult(result: BackendLoginResult | void) {
      if (!isBackendLoginResult(result)) return false
      this.setSession({
        token: result.accessToken,
        refreshToken: result.refreshToken,
        expiresIn: result.expiresIn,
        user: result.profile
      })
      if (!this.user) {
        await this.loadCurrentUser()
      }
      return true
    },
    setSession(result: LoginResult) {
      this.token = result.token
      this.refreshToken = result.refreshToken || ''
      this.expiresIn = result.expiresIn ?? null
      this.user = result.user ?? null
      localStorage.setItem(TOKEN_KEY, result.token)
      if (this.refreshToken) {
        localStorage.setItem(REFRESH_TOKEN_KEY, this.refreshToken)
      } else {
        localStorage.removeItem(REFRESH_TOKEN_KEY)
      }
      if (this.expiresIn) {
        localStorage.setItem(EXPIRES_IN_KEY, String(this.expiresIn))
      } else {
        localStorage.removeItem(EXPIRES_IN_KEY)
      }
    },
    updateTokens(token: string, refreshToken?: string, expiresIn?: number) {
      this.token = token
      localStorage.setItem(TOKEN_KEY, token)
      if (refreshToken !== undefined) {
        this.refreshToken = refreshToken
        if (refreshToken) {
          localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
        } else {
          localStorage.removeItem(REFRESH_TOKEN_KEY)
        }
      }
      if (expiresIn !== undefined) {
        this.expiresIn = expiresIn
        localStorage.setItem(EXPIRES_IN_KEY, String(expiresIn))
      }
    },
    async loadCurrentUser() {
      if (!this.token) return
      this.user = await fetchCurrentUser()
    },
    async logout() {
      const currentRefreshToken = this.refreshToken
      try {
        await logout(currentRefreshToken)
      } finally {
        this.clearSession()
      }
    },
    clearSession() {
      this.token = ''
      this.refreshToken = ''
      this.expiresIn = null
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(REFRESH_TOKEN_KEY)
      localStorage.removeItem(EXPIRES_IN_KEY)
    }
  }
})
