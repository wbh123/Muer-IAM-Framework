<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { adminTemplates, notifyError } from '@/api/client';
import type { PermissionTemplateSummary } from '@/api/generated/api';
import { formatId } from '@/utils/format';

const router = useRouter();
const loading = ref(false);
const items = ref<PermissionTemplateSummary[]>([]);
const afterTemplateId = ref(0);
const hasMore = ref(false);

async function load(reset = true) {
  if (reset) afterTemplateId.value = 0;
  loading.value = true;
  try {
    const page = (
      await adminTemplates.listPermissionTemplates({
        afterTemplateId: afterTemplateId.value,
        limit: 50,
      })
    ).data;
    items.value = page.items ?? [];
    hasMore.value = page.nextAfterTemplateId !== undefined && page.nextAfterTemplateId !== null;
    afterTemplateId.value = page.nextAfterTemplateId ?? 0;
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
    <h2>Permission Template</h2>
    <el-card shadow="never">
      <el-table :data="items" v-loading="loading">
        <el-table-column label="Template ID" width="110">
          <template #default="{ row }">{{ formatId(row.templateId) }}</template>
        </el-table-column>
        <el-table-column label="Name" prop="name" min-width="180" />
        <el-table-column label="Key" prop="templateKey" min-width="180" />
        <el-table-column label="Description" prop="description" min-width="220">
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="Latest Version" width="130">
          <template #default="{ row }">
            <template v-if="row.latestVersionNumber">
              v{{ row.latestVersionNumber }}
              <el-tag size="small" :type="row.latestVersionStatus === 'PUBLISHED' ? 'success' : 'warning'">
                {{ row.latestVersionStatus }}
              </el-tag>
            </template>
            <template v-else>-</template>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="router.push(`/templates/${row.templateId}`)">
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
