<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { adminAudit, adminOverview, notifyError } from '@/api/client';
import type { AuditEventResponse, OverviewResponse } from '@/api/generated/api';
import { useAppStore } from '@/stores/app';
import { formatDateTime } from '@/utils/format';

const appStore = useAppStore();
const overview = ref<OverviewResponse | null>(null);
const recent = ref<AuditEventResponse[]>([]);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    overview.value = (await adminOverview.getAdminOverview()).data;
  } catch (error) {
    notifyError(error);
  }
  if (appStore.can('iam.admin.audit.read')) {
    try {
      recent.value = (await adminAudit.listAuditEvents({ limit: 8 })).data.items ?? [];
    } catch {
      recent.value = [];
    }
  }
  loading.value = false;
}

onMounted(load);

const cards = [
  { label: '用户总数', value: () => overview.value?.totalUsers ?? '-', key: 'totalUsers' },
  { label: '启用用户', value: () => overview.value?.enabledUsers ?? '-', key: 'enabledUsers' },
  { label: '活跃 Session', value: () => overview.value?.activeSessions ?? '-', key: 'activeSessions' },
  { label: '活跃 Profile', value: () => overview.value?.activeProfiles ?? '-', key: 'activeProfiles' },
];
</script>

<template>
  <div>
    <h2>运营概览</h2>
    <el-row :gutter="16" v-loading="loading">
      <el-col v-for="card in cards" :key="card.key" :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">{{ card.label }}</div>
          <div class="stat-value">{{ card.value() }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" v-if="appStore.can('iam.admin.audit.read')" style="margin-top: 16px">
      <template #header>最近 Audit Events</template>
      <el-table :data="recent" size="small">
        <el-table-column label="时间" prop="occurredAt" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
        </el-table-column>
        <el-table-column label="事件" prop="action" min-width="140" />
        <el-table-column label="操作者" prop="actor" min-width="140" />
        <el-table-column label="资源" min-width="160">
          <template #default="{ row }">{{ row.resourceType }} / {{ row.resourceId }}</template>
        </el-table-column>
        <el-table-column label="结果" prop="result" width="110" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.stat-card {
  text-align: center;
}
.stat-label {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.stat-value {
  font-size: 28px;
  font-weight: 600;
  margin-top: 8px;
}
</style>
