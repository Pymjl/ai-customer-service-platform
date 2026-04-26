<template>
  <el-container class="admin-layout" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
    <el-aside :width="sidebarCollapsed ? '0px' : '248px'" class="sidebar">
      <button class="logo" type="button" @click="goChat">
        <div class="logo-mark">AI</div>
        <div>
          <strong>智能客服平台</strong>
          <span>Customer Service</span>
        </div>
      </button>
      <el-menu :default-active="activeMenu" router background-color="transparent" text-color="#cbd5e1" active-text-color="#fff">
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <button class="sidebar-toggle" type="button" :title="sidebarCollapsed ? '展开侧边栏' : '隐藏侧边栏'" @click="toggleSidebar">
      <el-icon><component :is="sidebarCollapsed ? 'DArrowRight' : 'DArrowLeft'" /></el-icon>
    </button>
    <el-container class="main-panel">
      <el-header class="topbar">
        <div>
          <h2>{{ currentTitle }}</h2>
          <p><span class="status-dot" />智能客服与知识库管理已接入业务服务</p>
        </div>
        <div class="topbar-actions">
          <el-dropdown @command="handleCommand">
            <div class="profile">
              <el-avatar :size="38" :src="avatarSrc">{{ avatarText }}</el-avatar>
              <div>
                <strong>{{ authStore.user?.realName || authStore.user?.username || '用户' }}</strong>
                <span>{{ roleLabel }}</span>
              </div>
              <el-icon><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人信息</el-dropdown-item>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="content">
        <RouterView v-slot="{ Component, route: viewRoute }">
          <component :is="Component" :key="viewRoute.fullPath" />
        </RouterView>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import defaultAvatar from '@/assets/default-avatar.webp'

interface MenuItem {
  path: string
  title: string
  icon: string
  adminOnly?: boolean
  superAdminOnly?: boolean
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const sidebarCollapsed = ref(false)

const allMenuItems: MenuItem[] = [
  { path: '/chat', title: '智能客服', icon: 'Service' },
  { path: '/knowledge', title: '知识库', icon: 'Collection' },
  { path: '/users', title: '用户管理', icon: 'User', adminOnly: true },
  { path: '/roles', title: '角色管理', icon: 'Avatar', adminOnly: true },
  { path: '/resources', title: '资源管理', icon: 'Menu', adminOnly: true },
  { path: '/user-roles', title: '用户角色授权', icon: 'Connection', adminOnly: true, superAdminOnly: true },
  { path: '/role-resources', title: '角色资源授权', icon: 'Lock', adminOnly: true, superAdminOnly: true }
]

const adminRoleCodes = new Set(['ADMIN', 'ROLE_ADMIN', 'SUPER_ADMIN', 'ROLE_SUPER_ADMIN', '管理员', '超级管理员'])
const superAdminRoleCodes = new Set(['SUPER_ADMIN', 'ROLE_SUPER_ADMIN', '超级管理员'])

const isAdmin = computed(() => {
  return authStore.user?.roles?.some((role) => adminRoleCodes.has(role.toUpperCase()) || adminRoleCodes.has(role)) ?? false
})

const isSuperAdmin = computed(() => {
  return authStore.user?.roles?.some((role) => superAdminRoleCodes.has(role.toUpperCase()) || superAdminRoleCodes.has(role)) ?? false
})

const menuItems = computed(() => allMenuItems.filter((item) => {
  if (item.superAdminOnly) return isSuperAdmin.value
  return !item.adminOnly || isAdmin.value
}))
const activeMenu = computed(() => {
  if (route.path === '/profile') return ''
  return allMenuItems.find((item) => route.path === item.path || route.path.startsWith(`${item.path}/`))?.path || ''
})
const currentTitle = computed(() => {
  if (route.path === '/profile') return '个人信息'
  return allMenuItems.find((item) => route.path.startsWith(item.path))?.title || '智能客服'
})
const avatarText = computed(() => (authStore.user?.realName || authStore.user?.username || 'A').slice(0, 1).toUpperCase())
const avatarSrc = computed(() => authStore.user?.avatarPath || defaultAvatar)
const roleLabel = computed(() => authStore.user?.roles?.join(' / ') || '普通用户')

function goChat() {
  router.push('/chat')
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

async function handleCommand(command: string) {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command === 'logout') {
    await authStore.logout()
    router.replace('/login')
  }
}
</script>

<style scoped>
.admin-layout {
  min-height: 100vh;
  background: #f3f6fb;
}

.sidebar {
  position: relative;
  z-index: 20;
  flex-shrink: 0;
  padding: 22px 16px;
  overflow: hidden;
  background: linear-gradient(180deg, #0f172a 0%, #172554 100%);
  box-shadow: 10px 0 30px rgba(15, 23, 42, .08);
  transition: width .24s ease, padding .24s ease, box-shadow .24s ease;
}

.sidebar-collapsed .sidebar {
  padding-right: 0;
  padding-left: 0;
  box-shadow: none;
}

.sidebar-toggle {
  position: fixed;
  top: 92px;
  left: 232px;
  z-index: 30;
  display: grid;
  place-items: center;
  width: 34px;
  height: 42px;
  color: #1d4ed8;
  border: 1px solid #dbeafe;
  border-radius: 0 12px 12px 0;
  background: rgba(255, 255, 255, .94);
  box-shadow: 0 12px 28px rgba(15, 23, 42, .16);
  cursor: pointer;
  transition: left .24s ease, color .2s ease, background .2s ease;
}

.sidebar-toggle:hover {
  color: #fff;
  background: #2563eb;
}

.sidebar-collapsed .sidebar-toggle {
  left: 0;
}

.main-panel {
  min-width: 0;
}

.logo {
  width: 100%;
  display: flex;
  gap: 12px;
  align-items: center;
  margin: 0 0 28px;
  padding: 0;
  color: #fff;
  text-align: left;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.logo-mark {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border-radius: 14px;
  background: linear-gradient(135deg, #60a5fa, #2563eb);
  font-weight: 800;
}

.logo strong,
.logo span {
  display: block;
}

.logo span {
  margin-top: 3px;
  color: #94a3b8;
  font-size: 12px;
}

:deep(.el-menu) {
  border-right: none;
}

:deep(.el-menu-item) {
  height: 48px;
  margin: 6px 0;
  border-radius: 12px;
}

:deep(.el-menu-item.is-active) {
  background: rgba(37, 99, 235, .95);
}

.topbar {
  height: 76px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255,255,255,.86);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid #e5e7eb;
}

.topbar h2 {
  margin: 0;
  font-size: 22px;
}

.topbar p {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
}

.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 6px;
  border-radius: 50%;
  background: #22c55e;
}

.topbar-actions,
.profile {
  display: flex;
  align-items: center;
  gap: 14px;
}

.profile {
  padding: 8px 10px;
  border-radius: 16px;
  cursor: pointer;
  background: #f8fafc;
}

.profile strong,
.profile span {
  display: block;
}

.profile span {
  margin-top: 2px;
  color: #64748b;
  font-size: 12px;
}

.content {
  min-width: 0;
  padding: 24px;
  overflow: auto;
}
</style>
