<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { storeToRefs } from 'pinia';
import { useAuthStore } from '@/stores/auth';
import { useAppStore } from '@/stores/app';

const route = useRoute();
const router = useRouter();
const authStore = useAuthStore();
const appStore = useAppStore();
const { menus } = storeToRefs(appStore);
const { principal } = storeToRefs(authStore);

const activeMenu = computed(() => {
  if (route.path.startsWith('/users/')) return '/users';
  if (route.path.startsWith('/templates/')) return '/templates';
  if (route.path.startsWith('/profiles/')) return '/profiles';
  return route.path;
});

const displayName = computed(() => principal.value?.identityId ?? '');

async function handleCommand(command: string) {
  if (command === 'account') {
    await router.push('/account');
  } else if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定要退出登录吗？', '退出', {
        confirmButtonText: '退出',
        cancelButtonText: '取消',
        type: 'warning',
      });
    } catch {
      return;
    }
    await authStore.logout();
    await router.replace('/login');
  }
}

onMounted(async () => {
  if (menus.value.length === 0) {
    try {
      const value = await authStore.loadCapabilities();
      appStore.setPermissions(Array.from(value.permissions ?? []));
    } catch {
      appStore.setPermissions([]);
    }
  }
});
</script>

<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="admin-aside">
      <div class="brand">IAM Admin</div>
      <el-menu :default-active="activeMenu" router class="admin-menu">
        <template v-if="menus.length > 0">
          <el-menu-item v-for="item in menus" :key="item.key" :index="item.path">
            <span>{{ item.label }}</span>
          </el-menu-item>
        </template>
        <el-menu-item v-if="menus.length === 0" disabled>
          <span>未授予管理权限</span>
        </el-menu-item>
      </el-menu>
      <div class="self-entry">
        <el-link type="primary" @click="router.push('/account')">个人安全中心</el-link>
      </div>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <div class="header-title">IAM 管理控制台</div>
        <el-dropdown trigger="click" @command="handleCommand">
          <span class="user-chip">
            {{ displayName }}
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="account">个人安全中心</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="admin-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.admin-layout {
  height: 100vh;
}
.admin-aside {
  border-right: 1px solid var(--el-border-color-light);
  background: #fff;
  display: flex;
  flex-direction: column;
}
.brand {
  font-weight: 600;
  font-size: 18px;
  padding: 18px 20px;
  color: var(--el-color-primary);
}
.admin-menu {
  border-right: none;
  flex: 1;
}
.self-entry {
  padding: 12px 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.admin-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-light);
  background: #fff;
}
.header-title {
  font-weight: 500;
}
.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: var(--el-text-color-primary);
}
.admin-main {
  background: #f5f6f8;
}
</style>
