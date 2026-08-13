<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'

import AppSidebar from '@/components/AppSidebar.vue'
import AppTopbar from '@/components/AppTopbar.vue'

const route = useRoute()

const sidebarCollapsed = ref(false)

const pageTitle = computed(() => {
  return String(route.meta.title ?? 'SmartDeploy')
})

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
}
</script>

<template>
  <div class="app-layout">
    <AppSidebar
      :collapsed="sidebarCollapsed"
      @toggle="toggleSidebar"
    />

    <section
      class="main-section"
      :class="{ 'sidebar-collapsed': sidebarCollapsed }"
    >
      <AppTopbar :page-title="pageTitle" />

      <main class="page-content">
        <RouterView />
      </main>
    </section>
  </div>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
  background: #f8fafc;
}

.main-section {
  min-height: 100vh;
  margin-left: 260px;

  transition: margin-left 0.25s ease;
}

.main-section.sidebar-collapsed {
  margin-left: 82px;
}

.page-content {
  padding: 30px 32px;
}

@media (max-width: 700px) {
  .main-section,
  .main-section.sidebar-collapsed {
    margin-left: 82px;
  }

  .page-content {
    padding: 22px 18px;
  }
}
</style>