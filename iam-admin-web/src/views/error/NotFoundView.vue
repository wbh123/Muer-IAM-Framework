<script setup lang="ts">
import { useRouter } from 'vue-router';

const router = useRouter();
</script>

<template>
  <div class="error-page">
    <h1>404</h1>
    <p>页面不存在或数据已被移除。</p>
    <el-button type="primary" @click="router.push('/dashboard')">返回概览</el-button>
  </div>
</template>

<style scoped>
.error-page {
  text-align: center;
  padding-top: 120px;
}
.error-page h1 {
  font-size: 48px;
  color: var(--el-color-danger);
}
</style>
