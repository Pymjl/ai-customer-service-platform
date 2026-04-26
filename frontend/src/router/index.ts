import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const adminRoleCodes = new Set(['ADMIN', 'ROLE_ADMIN', 'SUPER_ADMIN', 'ROLE_SUPER_ADMIN', '管理员', '超级管理员'])
const superAdminRoleCodes = new Set(['SUPER_ADMIN', 'ROLE_SUPER_ADMIN', '超级管理员'])

function hasAdminRole(roles: string[] = []) {
  return roles.some((role) => adminRoleCodes.has(role.toUpperCase()) || adminRoleCodes.has(role))
}

function hasSuperAdminRole(roles: string[] = []) {
  return roles.some((role) => superAdminRoleCodes.has(role.toUpperCase()) || superAdminRoleCodes.has(role))
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { public: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    redirect: '/chat',
    children: [
      {
        path: 'chat',
        name: 'Chat',
        component: () => import('@/views/ChatView.vue'),
        meta: { title: '智能客服', icon: 'Service' }
      },
      {
        path: 'users',
        name: 'UserManagement',
        component: () => import('@/views/system/UserManagement.vue'),
        meta: { title: '用户管理', icon: 'User', requiresAdmin: true }
      },
      {
        path: 'profile',
        name: 'UserProfile',
        component: () => import('@/views/system/UserProfile.vue'),
        meta: { title: '个人信息', icon: 'UserFilled' }
      },
      {
        path: 'roles',
        name: 'RoleManagement',
        component: () => import('@/views/system/RoleManagement.vue'),
        meta: { title: '角色管理', icon: 'Avatar', requiresAdmin: true }
      },
      {
        path: 'resources',
        name: 'ResourceTree',
        component: () => import('@/views/system/ResourceTree.vue'),
        meta: { title: '资源管理', icon: 'Menu', requiresAdmin: true }
      },
      {
        path: 'user-roles',
        name: 'UserRoleGrant',
        component: () => import('@/views/system/UserRoleGrant.vue'),
        meta: { title: '用户角色授权', icon: 'Connection', requiresAdmin: true, requiresSuperAdmin: true }
      },
      {
        path: 'role-resources',
        name: 'RoleResourceGrant',
        component: () => import('@/views/system/RoleResourceGrant.vue'),
        meta: { title: '角色资源授权', icon: 'Lock', requiresAdmin: true, requiresSuperAdmin: true }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (to.meta.public) {
    return authStore.isLoggedIn && to.path === '/login' ? '/' : true
  }
  if (!authStore.isLoggedIn) {
    return `/login?redirect=${encodeURIComponent(to.fullPath)}`
  }
  if (!authStore.user) {
    try {
      await authStore.loadCurrentUser()
    } catch {
      authStore.clearSession()
      return '/login'
    }
  }
  if (to.meta.requiresAdmin && !hasAdminRole(authStore.user?.roles)) {
    return '/chat'
  }
  if (to.meta.requiresSuperAdmin && !hasSuperAdminRole(authStore.user?.roles)) {
    return '/chat'
  }
  return true
})

export default router
export { routes }
