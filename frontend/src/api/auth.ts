import request from '@/utils/request'
import type {
  BackendLoginResult,
  CaptchaChallenge,
  CaptchaVerifyPayload,
  CaptchaVerifyResult,
  CurrentUser,
  LoginPayload,
  LoginResult,
  RegisterPayload
} from '@/types/system'

function toLoginResult(result: BackendLoginResult): LoginResult {
  return {
    token: result.accessToken,
    refreshToken: result.refreshToken,
    expiresIn: result.expiresIn,
    user: result.profile
  }
}

export function fetchCaptcha() {
  return request.get<CaptchaChallenge, CaptchaChallenge>('/auth/captcha')
}

export function verifyCaptcha(data: CaptchaVerifyPayload) {
  return request.post<CaptchaVerifyResult, CaptchaVerifyResult>('/auth/captcha/verify', data)
}

export function login(data: LoginPayload) {
  return request.post<BackendLoginResult, BackendLoginResult>('/auth/login', data).then(toLoginResult)
}

export function register(data: RegisterPayload) {
  return request.post<BackendLoginResult | void, BackendLoginResult | void>('/auth/register', data)
}

export function registerWithAvatar(data: RegisterPayload, avatar: File) {
  const formData = new FormData()
  Object.entries(data).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      formData.append(key, String(value))
    }
  })
  formData.append('avatar', avatar)
  return request.post<BackendLoginResult | void, BackendLoginResult | void>('/auth/register-with-avatar', formData)
}

export function fetchCurrentUser() {
  return request.get<CurrentUser, CurrentUser>('/auth/me')
}

export function refreshToken(refreshToken: string) {
  return request.post<BackendLoginResult, BackendLoginResult>('/auth/refresh', { refreshToken }).then(toLoginResult)
}

export function logout(refreshToken?: string) {
  return request.post<void, void>('/auth/logout', refreshToken ? { refreshToken } : undefined, { _suppressError: true } as any)
}
