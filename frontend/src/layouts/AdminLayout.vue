<template>
  <div class="admin-layout" :class="{ 'sidebar-collapsed': sidebarCollapsed }">
    <aside class="sidebar" aria-label="主导航">
      <button class="logo" type="button" @click="goChat">
        <span class="logo-mark">AI</span>
        <span class="logo-copy">
          <strong>智能客服平台</strong>
          <small>Customer Service</small>
        </span>
      </button>

      <nav class="nav-list">
        <RouterLink
          v-for="item in menuItems"
          :key="item.path"
          class="nav-item"
          :class="{ active: activeMenu === item.path }"
          :to="item.path"
        >
          <n-icon :component="item.icon" />
          <span>{{ item.title }}</span>
        </RouterLink>
      </nav>

      <button class="sidebar-toggle" type="button" :aria-label="sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'" @click="toggleSidebar">
        <n-icon :component="sidebarCollapsed ? ChevronRight24Regular : ChevronLeft24Regular" />
      </button>
    </aside>

    <section class="main-panel">
      <header class="topbar">
        <button ref="drawerTrigger" class="mobile-menu" type="button" aria-label="打开导航菜单" @click="drawerVisible = true">
          <n-icon :component="PanelLeft24Regular" />
        </button>
        <div class="topbar-title">
          <h1>{{ currentTitle }}</h1>
          <p><span class="status-dot" />智能客服与知识库管理已接入业务服务</p>
        </div>
        <n-popover v-model:show="profilePopoverVisible" trigger="click" placement="bottom-end" :show-arrow="false" class="profile-popover">
          <template #trigger>
            <button class="profile" type="button">
              <n-avatar round :size="38" :src="avatarSrc" :fallback-src="defaultAvatar">{{ avatarText }}</n-avatar>
              <span>
                <strong>{{ authStore.user?.realName || authStore.user?.username || '用户' }}</strong>
                <small>{{ roleLabel }}</small>
              </span>
            </button>
          </template>
          <div class="profile-card-popover">
            <div class="profile-card-head">
              <n-avatar round :size="46" :src="avatarSrc" :fallback-src="defaultAvatar">{{ avatarText }}</n-avatar>
              <span>
                <strong>{{ authStore.user?.realName || authStore.user?.username || '用户' }}</strong>
                <small>{{ authStore.user?.username || '-' }}</small>
              </span>
            </div>
            <div class="role-tags">
              <span v-for="role in authStore.user?.roles || ['普通用户']" :key="role" class="status-pill primary">{{ role }}</span>
            </div>
            <button class="profile-menu-item" type="button" @click="openProfileDrawer">
              <n-icon :component="Person24Regular" aria-hidden="true" />
              <span>个人信息</span>
            </button>
            <div class="menu-separator" />
            <n-popconfirm positive-text="确认退出" negative-text="取消" :positive-button-props="{ type: 'error' }" @positive-click="handleLogout">
              <template #trigger>
                <button class="profile-menu-item danger" type="button">
                  <n-icon :component="SignOut24Regular" aria-hidden="true" />
                  <span>退出登录</span>
                </button>
              </template>
              确定要退出登录吗？
            </n-popconfirm>
            </div>
        </n-popover>
      </header>

      <main class="content">
        <RouterView v-slot="{ Component, route: viewRoute }">
          <component :is="Component" :key="viewRoute.fullPath" />
        </RouterView>
      </main>
    </section>

    <n-drawer v-model:show="drawerVisible" placement="left" :width="320" @after-leave="drawerTrigger?.focus()">
      <n-drawer-content title="导航菜单" closable>
        <nav class="drawer-nav">
          <RouterLink
            v-for="item in menuItems"
            :key="item.path"
            class="nav-item"
            :class="{ active: activeMenu === item.path }"
            :to="item.path"
            @click="drawerVisible = false"
          >
            <n-icon :component="item.icon" />
            <span>{{ item.title }}</span>
          </RouterLink>
        </nav>
      </n-drawer-content>
    </n-drawer>

    <n-drawer v-model:show="profileDrawerVisible" placement="right" :width="480">
      <n-drawer-content title="个人信息" closable body-content-style="padding: 0;">
        <UserProfile embedded @close="profileDrawerVisible = false" />
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import type { Component } from 'vue'
import {
  BookOpen24Regular,
  Bot24Regular,
  ChevronLeft24Regular,
  ChevronRight24Regular,
  Database24Regular,
  Key24Regular,
  PanelLeft24Regular,
  People24Regular,
  Person24Regular,
  Shield24Regular,
  SignOut24Regular
} from '@vicons/fluent'
import { useAuthStore } from '@/stores/auth'
import { defaultAvatar, resolveAvatarUrl } from '@/utils/avatar'
import UserProfile from '@/views/system/UserProfile.vue'

interface MenuItem {
  path: string
  title: string
  icon: Component
  adminOnly?: boolean
  superAdminOnly?: boolean
}

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const sidebarCollapsed = ref(false)
const drawerVisible = ref(false)
const profilePopoverVisible = ref(false)
const profileDrawerVisible = ref(false)
const drawerTrigger = ref<HTMLButtonElement | null>(null)

const allMenuItems: MenuItem[] = [
  { path: '/chat', title: '智能客服', icon: Bot24Regular },
  { path: '/knowledge', title: '知识库', icon: Database24Regular },
  { path: '/users', title: '用户管理', icon: People24Regular, adminOnly: true },
  { path: '/roles', title: '角色管理', icon: Shield24Regular, adminOnly: true },
  { path: '/resources', title: '资源管理', icon: BookOpen24Regular, adminOnly: true },
  { path: '/user-roles', title: '用户角色授权', icon: Person24Regular, adminOnly: true, superAdminOnly: true },
  { path: '/role-resources', title: '角色资源授权', icon: Key24Regular, adminOnly: true, superAdminOnly: true }
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
const avatarSrc = computed(() => resolveAvatarUrl(authStore.user?.avatarPath))
const roleLabel = computed(() => authStore.user?.roles?.join(' / ') || '普通用户')

function goChat() {
  router.push('/chat')
}

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}

function openProfileDrawer() {
  profilePopoverVisible.value = false
  profileDrawerVisible.value = true
}

async function handleLogout() {
  profilePopoverVisible.value = false
  await authStore.logout()
  router.replace('/login')
}
</script>

<style scoped>
.admin-layout {
  --sidebar-width: 260px;
  --sidebar-collapsed-width: 56px;
  min-height: 100vh;
  display: grid;
  grid-template-columns: var(--sidebar-width) minmax(0, 1fr);
  background:
    radial-gradient(circle at 0 0, var(--mesh-color-1), transparent 32%),
    radial-gradient(circle at 100% 100%, var(--mesh-color-3), transparent 30%),
    var(--bg-app);
  transition: grid-template-columns var(--duration-base) var(--ease-out-quint);
}

.admin-layout.sidebar-collapsed {
  grid-template-columns: var(--sidebar-collapsed-width) minmax(0, 1fr);
}

.sidebar {
  position: sticky;
  top: 0;
  z-index: 20;
  height: 100vh;
  padding: 22px 16px;
  overflow: visible;
  color: var(--text-primary);
  background: rgba(255, 255, 255, 0.76);
  backdrop-filter: blur(var(--glass-blur-floating));
  border-right: 1px solid var(--glass-border);
  box-shadow: var(--shadow-md), var(--glass-highlight);
  transition:
    padding var(--duration-spring) var(--ease-spring),
    box-shadow var(--duration-base) var(--ease-out-quint),
    border-color var(--duration-base) var(--ease-out-quint);
}

.sidebar-collapsed .sidebar {
  padding: 18px 7px;
}

.logo {
  width: 100%;
  display: flex;
  gap: 12px;
  align-items: center;
  margin: 0 0 24px;
  padding: 0;
  text-align: left;
  border: 0;
  background: transparent;
  cursor: pointer;
  transition: justify-content var(--duration-base) var(--ease-out-quint);
}

.sidebar-collapsed .logo {
  justify-content: center;
}

.logo-mark {
  display: grid;
  place-items: center;
  width: 46px;
  height: 46px;
  flex: 0 0 auto;
  color: var(--text-on-accent);
  border-radius: 16px;
  background: linear-gradient(135deg, var(--accent-cyan), var(--accent-primary));
  font-weight: 850;
  box-shadow: 0 12px 24px var(--accent-primary-muted);
  transition:
    width var(--duration-spring) var(--ease-spring),
    height var(--duration-spring) var(--ease-spring),
    border-radius var(--duration-spring) var(--ease-spring);
}

.sidebar-collapsed .logo-mark {
  width: 40px;
  height: 40px;
  border-radius: 14px;
}

.logo-copy strong,
.logo-copy small {
  display: block;
}

.logo-copy,
.nav-item span {
  transition:
    opacity var(--duration-base) var(--ease-out-quint),
    transform var(--duration-base) var(--ease-out-quint),
    max-width var(--duration-base) var(--ease-out-quint);
}

.sidebar-collapsed .logo-copy,
.sidebar-collapsed .nav-item span {
  max-width: 0;
  overflow: hidden;
  opacity: 0;
  transform: translateX(-8px);
  pointer-events: none;
}

.logo-copy small {
  margin-top: 3px;
  color: var(--text-muted);
  font-size: 12px;
}

.nav-list,
.drawer-nav {
  display: grid;
  gap: 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 46px;
  padding: 0 12px;
  color: var(--text-secondary);
  border: 1px solid transparent;
  border-radius: 14px;
  transition:
    transform var(--duration-spring) var(--ease-spring),
    border-color var(--duration-base) var(--ease-out-quint),
    background-color var(--duration-base) var(--ease-out-quint),
    color var(--duration-base) var(--ease-out-quint);
}

.sidebar-collapsed .nav-item {
  justify-content: center;
  gap: 0;
  min-height: 42px;
  padding: 0;
}

.nav-item:hover,
.nav-item.active {
  transform: translateX(2px);
  color: var(--accent-primary);
  border-color: var(--glass-border);
  background: var(--glass-surface-hover);
}

.sidebar-toggle {
  position: fixed;
  top: 50vh;
  left: calc(var(--sidebar-width) - 16px);
  z-index: 30;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  color: var(--text-secondary);
  border: 1.5px solid var(--border-subtle);
  border-radius: 50%;
  background: var(--bg-surface);
  box-shadow: var(--shadow-md), var(--glass-highlight);
  cursor: pointer;
  opacity: 0.62;
  transform: translateY(-50%);
  transition:
    left var(--duration-spring) var(--ease-spring),
    top var(--duration-spring) var(--ease-spring),
    opacity var(--duration-base) var(--ease-out-quint),
    transform var(--duration-spring) var(--ease-spring),
    color var(--duration-base) var(--ease-out-quint),
    border-color var(--duration-base) var(--ease-out-quint),
    background-color var(--duration-base) var(--ease-out-quint),
    box-shadow var(--duration-base) var(--ease-out-quint);
}

.sidebar-toggle::before {
  content: "";
  position: absolute;
  inset: -8px;
  border-radius: 50%;
}

.sidebar-toggle :deep(.n-icon) {
  font-size: 14px;
}

.sidebar-collapsed .sidebar-toggle {
  top: calc(100vh - 42px);
  left: 12px;
}

.sidebar:hover .sidebar-toggle,
.sidebar-toggle:focus-visible {
  opacity: 1;
  transform: translateY(-50%) scale(1.08);
}

.sidebar-collapsed .sidebar-toggle:hover,
.sidebar-collapsed .sidebar-toggle:focus-visible {
  transform: translateY(-50%) scale(1.08);
}

.sidebar-toggle:hover,
.sidebar-toggle:focus-visible {
  color: var(--text-on-accent);
  border-color: var(--accent-primary);
  background: var(--accent-primary);
  box-shadow: var(--shadow-md), 0 8px 20px var(--accent-primary-muted);
}

.main-panel {
  min-width: 0;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 15;
  min-height: 76px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 24px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--border-subtle);
}

.topbar-title h1,
.topbar-title p {
  margin: 0;
}

.topbar-title h1 {
  font-size: 22px;
  line-height: var(--leading-tight);
}

.topbar-title p {
  margin-top: 6px;
  color: var(--text-muted);
  font-size: 13px;
}

.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 6px;
  border-radius: 50%;
  background: var(--accent-success);
}

.profile {
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 280px;
  padding: 8px 10px;
  color: var(--text-primary);
  border: 1px solid var(--border-subtle);
  border-radius: 16px;
  background: var(--bg-surface);
  cursor: pointer;
}

.profile-popover {
  padding: 0;
  border-radius: 18px;
  background: transparent;
}

.profile-card-popover {
  width: 220px;
  display: grid;
  gap: 12px;
  padding: 14px;
  color: var(--text-primary);
  border: 1px solid var(--glass-border);
  border-radius: 18px;
  background: var(--glass-surface);
  backdrop-filter: blur(var(--glass-blur-floating));
  -webkit-backdrop-filter: blur(var(--glass-blur-floating));
  box-shadow: var(--shadow-lg), var(--glass-highlight);
}

.profile-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.profile-card-head span,
.profile-card-head strong,
.profile-card-head small {
  min-width: 0;
  display: block;
}

.profile-card-head strong,
.profile-card-head small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-card-head small {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 12px;
}

.role-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.profile-menu-item {
  width: 100%;
  min-height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  color: var(--text-secondary);
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.profile-menu-item:hover {
  color: var(--accent-primary);
  border-color: var(--border-subtle);
  background: var(--glass-surface-hover);
}

.profile-menu-item.danger {
  color: var(--accent-danger);
}

.profile-menu-item.danger:hover {
  border-color: color-mix(in srgb, var(--accent-danger) 24%, transparent);
  background: color-mix(in srgb, var(--accent-danger) 10%, var(--bg-surface));
}

.menu-separator {
  height: 1px;
  background: var(--border-subtle);
}

.profile strong,
.profile small {
  display: block;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile small {
  margin-top: 2px;
  color: var(--text-muted);
  font-size: 12px;
}

.content {
  min-width: 0;
  padding: 24px;
  overflow: auto;
}

.mobile-menu {
  display: none;
}

@media (max-width: 1023px) {
  .admin-layout,
  .admin-layout.sidebar-collapsed {
    grid-template-columns: 1fr;
  }

  .sidebar,
  .sidebar-toggle {
    display: none;
  }

  .mobile-menu {
    display: grid;
    place-items: center;
    width: 40px;
    height: 40px;
    border: 1px solid var(--glass-border);
    border-radius: 12px;
    background: var(--bg-surface);
  }

  .topbar {
    justify-content: flex-start;
  }

  .profile {
    margin-left: auto;
  }
}

@media (max-width: 767px) {
  .topbar {
    padding: 10px 12px;
  }

  .topbar-title p,
  .profile span {
    display: none;
  }

  .content {
    padding: 0;
  }
}
</style>
