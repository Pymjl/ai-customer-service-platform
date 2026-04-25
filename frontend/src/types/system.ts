export interface ApiResult<T> {
  code: number
  message: string
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
}

export interface LoginPayload {
  username: string
  password: string
  captchaToken: string
}

export interface RegisterPayload {
  username: string
  password: string
  captchaToken: string
  tenantId?: string
  gender: number
  realName: string
  age: number
  email?: string
  phone?: string
  address?: string
}

export interface CaptchaChallenge {
  challengeId: string
  backgroundImage: string
  sliderImage: string
  width: number
  height: number
  sliderWidth: number
  sliderY: number
  expiresIn: number
}

export interface CaptchaVerifyPayload {
  challengeId: string
  x: number
}

export interface CaptchaVerifyResult {
  captchaToken: string
}

export interface BackendLoginResult {
  accessToken: string
  refreshToken?: string
  tokenType?: string
  expiresIn?: number
  profile?: CurrentUser
}

export interface LoginResult {
  token: string
  refreshToken?: string
  expiresIn?: number
  user?: CurrentUser
}

export interface CurrentUser {
  userId: string
  tenantId: string
  username: string
  avatarPath?: string
  gender?: number
  realName?: string
  age?: number
  email?: string
  phone?: string
  address?: string
  roles: string[]
}

export interface UserItem {
  id?: number | string
  userId: string
  username: string
  nickname?: string
  tenantId: string
  avatarPath?: string
  gender?: number
  realName?: string
  age?: number
  email?: string
  phone?: string
  address?: string
  status: number
  roles?: string[]
  createdAt?: string
}

export interface RoleItem {
  id: number | string
  roleCode?: string
  roleName?: string
  code?: string
  name?: string
  description?: string
  enabled?: boolean
  createdAt?: string
}

export type ResourceType = 'MENU' | 'BUTTON' | 'API'

export interface ResourceItem {
  id: number | string
  parentId?: number | string | null
  resourceCode?: string
  serviceName?: string
  controllerPath?: string
  controllerName?: string
  httpMethod?: string
  path?: string
  methodName?: string
  description?: string
  enabled?: boolean
  name?: string
  code?: string
  type: ResourceType
  method?: string
  icon?: string
  sort?: number
  children?: ResourceItem[]
}
