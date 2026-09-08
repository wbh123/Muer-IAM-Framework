<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { adminPermissions, notifyError } from '@/api/client';
import type { PermissionSummary } from '@/api/generated/api';
import { formatId } from '@/utils/format';

const loading = ref(false);
const items = ref<PermissionSummary[]>([]);
const afterId = ref(0);
const hasMore = ref(false);

const filters = reactive({ keyword: '', domain: '' });

async function load(reset = true) {
  if (reset) afterId.value = 0;
  loading.value = true;
  try {
    const page = (
      await adminPermissions.listPermissions({
        keyword: filters.keyword || undefined,
        domain: filters.domain || undefined,
        afterId: afterId.value,
        limit: 50,
      })
    ).data;
    items.value = page.items ?? [];
    hasMore.value = page.nextAfterPermissionId !== undefined && page.nextAfterPermissionId !== null;
    afterId.value = page.nextAfterPermissionId ?? 0;
  } catch (error) {
    notifyError(error);
  } finally {
    loading.value = false;
  }
}

async function next() {
  if (hasMore.value) await load(false);
}

onMounted(load);
</script>

<template>
  <div>
    <h2>Permission Explorer</h2>
    <el-card shadow="never">
      <el-alert
        type="info"
        show-icon
        :closable="false"
        title="展示 Permission Registry 中已登记的权限及其在 Template 中的使用情况。"
        style="margin-bottom: 12px"
      />
      <el-form inline>
        <el-form-item label="关键词">
          <el-input v-model="filters.keyword" clearable style="width: 200px" @keyup.enter="load()" />
        </el-form-item>
        <el-form-item label="Domain">
          <el-input v-model="filters.domain" clearable placeholder="如 iam" style="width: 140px" @keyup.enter="load()" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load()">搜索</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="items" v-loading="loading">
        <el-table-column label="Code" prop="permissionCode" min-width="240" />
        <el-table-column label="Display Name" prop="displayName" min-width="180" />
        <el-table-column label="Description" prop="description" min-width="200">
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="In Use" width="100">
          <template #default="{ row }">{{ formatId(row.inUseCount) }}</template>
        </el-table-column>
        <el-table-column label="Enabled" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 12px; text-align: right">
        <el-button :disabled="!hasMore" @click="next">加载更多</el-button>
      </div>
    </el-card>
  </div>
</template>
