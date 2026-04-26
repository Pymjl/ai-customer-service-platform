import request from '@/utils/request'
import type { PageResult, ResourceItem, ResourceTreeNode, RoleItem, UserItem } from '@/types/system'

export interface QueryParams {
  keyword?: string
  current?: number
  size?: number
}

function toPage<T>(result: T[] | PageResult<T>): PageResult<T> {
  return Array.isArray(result) ? { records: result, total: result.length } : result
}

export function fetchUsers(params: QueryParams) {
  return request.get<UserItem[] | PageResult<UserItem>, UserItem[] | PageResult<UserItem>>('/users', { params }).then(toPage)
}

export function fetchUser(userId: UserItem['userId']) {
  return request.get<UserItem, UserItem>(`/users/${userId}`)
}

export function createUser(data: Partial<UserItem> & { password?: string }) {
  return request.post<void, void>('/users', data)
}

export function updateUser(userId: UserItem['userId'], data: Partial<UserItem>) {
  return request.put<void, void>(`/users/${userId}`, data)
}

export function updateUserAvatar(userId: UserItem['userId'], avatar: File) {
  const formData = new FormData()
  formData.append('avatar', avatar)
  return request.put<string, string>(`/users/${userId}/avatar`, formData)
}

export function deleteUser(userId: UserItem['userId']) {
  return request.delete<void, void>(`/users/${userId}`)
}

export function fetchRoles(params?: QueryParams) {
  return request.get<RoleItem[] | PageResult<RoleItem>, RoleItem[] | PageResult<RoleItem>>('/roles', { params }).then(toPage)
}

export function createRole(data: Partial<RoleItem>) {
  return request.post<RoleItem, RoleItem>('/roles', data)
}

export function updateRole(id: RoleItem['id'], data: Partial<RoleItem>) {
  return request.put<RoleItem, RoleItem>(`/roles/${id}`, data)
}

export function deleteRole(id: RoleItem['id']) {
  return request.delete<void, void>(`/roles/${id}`)
}

export function fetchResources() {
  return request.get<ResourceTreeNode[], ResourceTreeNode[]>('/resources/tree')
}

export function createResource(data: Partial<ResourceItem>) {
  return request.post<ResourceItem, ResourceItem>('/resources', data)
}

export function updateResource(id: ResourceItem['id'], data: Partial<ResourceItem>) {
  return request.put<ResourceItem, ResourceItem>(`/resources/${id}`, data)
}

export function deleteResource(id: ResourceItem['id']) {
  return request.delete<void, void>(`/resources/${id}`)
}

export function syncResources() {
  return request.post<{ count: number }, { count: number }>('/resources/sync')
}

export function fetchUserRoleIds(userId: UserItem['userId']) {
  return request.get<Array<RoleItem['id']>, Array<RoleItem['id']>>(`/users/${userId}/roles`)
}

export function saveUserRoles(userId: UserItem['userId'], roleIds: Array<RoleItem['id']>) {
  return request.put<void, void>(`/users/${userId}/roles`, { roleIds })
}

export function fetchRoleResourceIds(roleId: RoleItem['id']) {
  return request.get<Array<ResourceItem['id']>, Array<ResourceItem['id']>>(`/resources/roles/${roleId}`)
}

export function saveRoleResources(roleId: RoleItem['id'], resourceIds: Array<ResourceItem['id']>) {
  return request.put<void, void>(`/resources/roles/${roleId}`, { resourceIds })
}
