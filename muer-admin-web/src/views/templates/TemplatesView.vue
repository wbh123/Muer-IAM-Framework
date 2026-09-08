<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { adminTemplates, notifyError } from '@/api/client';
import type { PermissionTemplateSummary } from '@/api/generated/api';
import { formatId } from '@/utils/format';

const router = useRouter();
const loading = ref(false);
const items = ref<PermissionTemplateSummary[]>([]);
const afterTemplateId = ref(0);
const hasMore = ref(false);
const createVisible = ref(false);
const createForm = reactive({ templateKey: '', name: '', description: '' });

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

async function createTemplate() {
  if (!createForm.templateKey.trim() || !createForm.name.trim()) return;
  try {
    const created = (await adminTemplates.createPermissionTemplate({
      permissionTemplateCreateRequest: {
        templateKey: createForm.templateKey.trim(), name: createForm.name.trim(),
        description: createForm.description.trim() || null,
      },
    })).data;
    ElMessage.success('模板已创建');
    createVisible.value = false;
    await router.push(`/templates/${created.templateId}`);
  } catch (error) {
    notifyError(error);
  }
}

onMounted(load);
</script>

<template>
  <div>
    <h2>Permission Template</h2>
    <div style="margin-bottom: 12px">
      <el-button type="primary" @click="createVisible = true">新建模板</el-button>
    </div>
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
    <el-dialog v-model="createVisible" title="新建模板" width="520px">
      <el-form label-width="110px">
        <el-form-item label="Template Key" required><el-input v-model="createForm.templateKey" /></el-form-item>
        <el-form-item label="Name" required><el-input v-model="createForm.name" /></el-form-item>
        <el-form-item label="Description"><el-input v-model="createForm.description" type="textarea" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!createForm.templateKey.trim() || !createForm.name.trim()" @click="createTemplate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>
