<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { adminProfiles, notifyError } from '@/api/client';
import type { AuthorizationProfileResponse } from '@/api/generated/api';
import { formatId } from '@/utils/format';

const router = useRouter();
const loading = ref(false);
const items = ref<AuthorizationProfileResponse[]>([]);
const afterProfileId = ref(0);
const hasMore = ref(false);

const filters = reactive({
  userId: undefined as number | undefined,
  enabled: undefined as boolean | undefined,
  revoked: undefined as boolean | undefined,
  clientType: '',
});

async function load(reset = true) {
  if (reset) afterProfileId.value = 0;
  loading.value = true;
  try {
    const page = (
      await adminProfiles.listAuthorizationProfiles({
        afterProfileId: afterProfileId.value,
        limit: 30,
        userId: filters.userId || undefined,
        enabled: filters.enabled,
        revoked: filters.revoked,
        clientType: filters.clientType || undefined,
      })
    ).data;
    items.value = page.items ?? [];
    hasMore.value = page.nextAfterProfileId !== undefined && page.nextAfterProfileId !== null;
    afterProfileId.value = page.nextAfterProfileId ?? 0;
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
    <h2>Profile</h2>
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="用户 ID">
          <el-input-number v-model="filters.userId" :controls="false" placeholder="用户 ID" style="width: 140px" />
        </el-form-item>
        <el-form-item label="Enabled">
          <el-select v-model="filters.enabled" clearable placeholder="全部" style="width: 110px">
            <el-option label="启用" :value="true" />
            <el-option label="禁用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="Revoked">
          <el-select v-model="filters.revoked" clearable placeholder="全部" style="width: 110px">
            <el-option label="已撤销" :value="true" />
            <el-option label="未撤销" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item label="Client Type">
          <el-input v-model="filters.clientType" style="width: 130px" @keyup.enter="load()" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load()">搜索</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="items" v-loading="loading">
        <el-table-column label="Profile ID" width="110">
          <template #default="{ row }">{{ formatId(row.profileId) }}</template>
        </el-table-column>
        <el-table-column label="Profile Name" prop="profileName" min-width="180" />
        <el-table-column label="User" width="100">
          <template #default="{ row }">{{ formatId(row.userId) }}</template>
        </el-table-column>
        <el-table-column label="Template Version" width="130">
          <template #default="{ row }">{{ formatId(row.templateVersionId) }}</template>
        </el-table-column>
        <el-table-column label="Client Types" min-width="130">
          <template #default="{ row }">{{ (row.clientTypes ?? []).join(', ') || '-' }}</template>
        </el-table-column>
        <el-table-column label="Enabled" width="90">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Revoked" width="90">
          <template #default="{ row }">
            <el-tag :type="row.revoked ? 'danger' : 'success'" size="small">{{ row.revoked ? '已撤销' : '正常' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="router.push(`/profiles/${row.profileId}`)">
              详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 12px; text-align: right">
        <el-button :disabled="!hasMore" @click="next">加载更多</el-button>
      </div>
    </el-card>
  </div>
</template>
