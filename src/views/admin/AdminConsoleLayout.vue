<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppLayout from '@/components/AppLayout.vue'
</script>

<template>
  <!-- /admin 路由级布局：导航切换时仅内容区更换，顶栏与侧栏不重挂载、不闪烁 -->
  <AppLayout admin>
    <RouterView />
  </AppLayout>
</template>
