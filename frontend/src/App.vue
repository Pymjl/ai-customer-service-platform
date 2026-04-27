<template>
  <n-config-provider :theme-overrides="themeOverrides">
    <RouterView />
  </n-config-provider>
</template>

<script setup lang="ts">
import { themeOverrides } from '@/styles/naive-theme'
</script>
