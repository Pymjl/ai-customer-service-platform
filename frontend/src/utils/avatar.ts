import defaultAvatar from '@/assets/default-avatar.webp'

const absoluteUrlPattern = /^(?:[a-z][a-z\d+\-.]*:)?\/\//i
const inlineUrlPattern = /^(?:data|blob):/i
const defaultFileBaseUrl = 'http://localhost:9000'

function joinUrl(base: string, path: string) {
  return `${base.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`
}

function apiOrigin() {
  const apiBase = import.meta.env.VITE_API_BASE_URL || ''
  if (!absoluteUrlPattern.test(apiBase)) return ''
  try {
    return new URL(apiBase).origin
  } catch {
    return ''
  }
}

export function resolveAvatarUrl(path?: string | null) {
  const value = path?.trim()
  if (!value) return defaultAvatar
  if (absoluteUrlPattern.test(value) || inlineUrlPattern.test(value)) return value

  const fileBaseUrl = import.meta.env.VITE_FILE_PUBLIC_BASE_URL || import.meta.env.VITE_MINIO_PUBLIC_URL || defaultFileBaseUrl
  if (/^aicsp-[\w-]+\//.test(value)) return joinUrl(fileBaseUrl, value)
  if (value.startsWith('avatars/')) return joinUrl(fileBaseUrl, `aicsp-user/${value}`)
  if (value.startsWith('/aicsp-')) return joinUrl(fileBaseUrl, value)

  if (value.startsWith('/api/')) return value
  if (value.startsWith('/')) return apiOrigin() ? joinUrl(apiOrigin(), value) : value

  return value
}

export { defaultAvatar }
