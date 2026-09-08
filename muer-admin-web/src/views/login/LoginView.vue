<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import type { FormInstance, FormRules } from 'element-plus';
import { useAuthStore } from '@/stores/auth';
import { useAppStore } from '@/stores/app';
import { errorText } from '@/api/client';

const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const appStore = useAppStore();

const formRef = ref<FormInstance>();
const loading = ref(false);
const form = reactive({ username: '', password: '', clientType: 'WEB' });

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  clientType: [{ required: true, message: '请选择 Client Type', trigger: 'change' }],
};

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  loading.value = true;
  try {
    await authStore.login(form.username, form.password, form.clientType);
    try {
      const caps = await authStore.loadCapabilities();
      appStore.setPermissions(Array.from(caps.permissions ?? []));
    } catch {
      appStore.setPermissions([]);
    }
    const redirect = (route.query.redirect as string) || '/dashboard';
    await router.replace(redirect);
  } catch (error) {
    ElMessage.error(errorText(error));
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <el-card class="login-card">
      <h1 class="login-title">Muer Admin Console</h1>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" autocomplete="current-password" show-password />
        </el-form-item>
        <el-form-item label="Client Type" prop="clientType">
          <el-select v-model="form.clientType" style="width: 100%">
            <el-option label="WEB" value="WEB" />
            <el-option label="MOBILE" value="MOBILE" />
            <el-option label="BACKEND" value="BACKEND" />
          </el-select>
        </el-form-item>
        <el-button type="primary" :loading="loading" style="width: 100%" @click="submit">登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f2f5;
}
.login-card {
  width: 380px;
}
.login-title {
  font-size: 20px;
  text-align: center;
  margin: 4px 0 24px;
}
</style>
