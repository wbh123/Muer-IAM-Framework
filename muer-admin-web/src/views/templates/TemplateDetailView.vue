<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { admin, adminPermissions, adminTemplates, notifyError } from '@/api/client';
import type { PermissionSummary, PermissionTemplateSummary, PermissionTemplateVersionResponse } from '@/api/generated/api';
import { useAppStore } from '@/stores/app';

const route = useRoute();
const appStore = useAppStore();
const templateId = computed(() => Number(route.params.id));

const template = ref<PermissionTemplateSummary | null>(null);
const versions = ref<PermissionTemplateVersionResponse[]>([]);
const loading = ref(false);

const editorVisible = ref(false);
const editingVersion = ref<PermissionTemplateVersionResponse | null>(null);
const workingPermissions = ref<string[]>([]);
const editor = reactive({ newPermission: '' });
const draftVisible = ref(false);
const draftPermissions = ref<string[]>([]);
const permissionOptions = ref<PermissionSummary[]>([]);

async function load() {
  loading.value = true;
  try {
    template.value = (await adminTemplates.getPermissionTemplate({ templateId: templateId.value })).data;
    versions.value = (await adminTemplates.listPermissionTemplateVersions({ templateId: templateId.value })).data;
    permissionOptions.value = (await adminPermissions.listPermissions({ limit: 100 })).data.items ?? [];
  } catch (error) {
    notifyError(error);
  } finally {
    loading.value = false;
  }
}

function openDraftCreator() {
  draftPermissions.value = [];
  draftVisible.value = true;
}

async function createDraft() {
  try {
    await adminTemplates.createPermissionTemplateVersion({
      templateId: templateId.value,
      permissionTemplateVersionCreateRequest: { permissions: new Set(draftPermissions.value) },
    });
    ElMessage.success('Draft 版本已创建');
    draftVisible.value = false;
    await load();
  } catch (error) { notifyError(error); }
}

async function publish(version: PermissionTemplateVersionResponse) {
  try {
    await ElMessageBox.confirm('发布后该版本将不可再编辑。', '发布版本', { type: 'warning' });
    await adminTemplates.publishPermissionTemplateVersion({ versionId: version.versionId });
    ElMessage.success('版本已发布');
    await load();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') notifyError(error);
  }
}

function openEditor(version: PermissionTemplateVersionResponse) {
  editingVersion.value = version;
  workingPermissions.value = Array.from(version.permissions ?? []);
  editor.newPermission = '';
  editorVisible.value = true;
}

function addPermission() {
  const code = editor.newPermission.trim();
  if (!code) return;
  if (workingPermissions.value.includes(code)) {
    ElMessage.warning('权限已存在');
    return;
  }
  workingPermissions.value = [...workingPermissions.value, code];
  editor.newPermission = '';
}

function removePermission(index: number) {
  workingPermissions.value = workingPermissions.value.filter((_, i) => i !== index);
}

async function saveDraft() {
  if (!editingVersion.value) return;
  try {
    await admin.savePermissionTemplateVersion({
      versionId: editingVersion.value.versionId,
      permissionTemplateVersionRequest: {
        templateId: editingVersion.value.templateId,
        versionNumber: editingVersion.value.versionNumber,
        status: 'DRAFT' as const,
        permissions: new Set(workingPermissions.value),
      },
    });
    ElMessage.success('已保存 Draft');
    editorVisible.value = false;
    await load();
  } catch (error) {
    notifyError(error);
  }
}

onMounted(load);
</script>

<template>
  <div v-loading="loading">
    <h2>Template 详情</h2>
    <el-card shadow="never">
      <el-descriptions v-if="template" :column="2" border>
        <el-descriptions-item label="Template ID">{{ template.templateId }}</el-descriptions-item>
        <el-descriptions-item label="Key">{{ template.templateKey }}</el-descriptions-item>
        <el-descriptions-item label="Name">{{ template.name }}</el-descriptions-item>
        <el-descriptions-item label="Enabled">{{ template.enabled ? '启用' : '禁用' }}</el-descriptions-item>
        <el-descriptions-item label="Description" :span="2">{{ template.description || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <span>版本与权限（版本不可变：PUBLISHED 版本只读）</span>
        <el-button v-if="appStore.can('iam.admin.template.write')" type="primary" size="small" style="float: right" @click="openDraftCreator">新建版本</el-button>
      </template>
      <el-table :data="versions" size="small">
        <el-table-column label="Version ID" width="110" prop="versionId" />
        <el-table-column label="Version" width="110" prop="versionNumber" />
        <el-table-column label="Status" width="110">
          <template #default="{ row }">
            <el-tag :type="row.status === 'PUBLISHED' ? 'success' : row.status === 'RETIRED' ? 'info' : 'warning'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Permissions" min-width="280">
          <template #default="{ row }">
            <el-tag v-for="code in row.permissions ?? []" :key="code" size="small" style="margin-right: 4px">
              {{ code }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'DRAFT' && appStore.can('iam.admin.template.write')"
              size="small"
              link
              type="primary"
              @click="openEditor(row)"
            >
              编辑 Draft
            </el-button>
            <el-button v-if="row.status === 'DRAFT' && appStore.can('iam.admin.template.write')" size="small" link type="success" @click="publish(row)">发布</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="draftVisible" title="新建 Draft 版本" width="560px">
      <el-form label-width="110px">
        <el-form-item label="Permissions">
          <el-select v-model="draftPermissions" multiple filterable style="width: 100%" placeholder="选择已注册权限">
            <el-option v-for="permission in permissionOptions" :key="permission.permissionCode" :label="permission.permissionCode" :value="permission.permissionCode" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="draftVisible = false">取消</el-button>
        <el-button type="primary" @click="createDraft">创建 Draft</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editorVisible" title="编辑 Draft 权限" width="560px">
      <div v-if="editingVersion">
        <el-input v-model="editor.newPermission" placeholder="输入 permission code 后回车添加" @keyup.enter="addPermission">
          <template #append>
            <el-button @click="addPermission">添加</el-button>
          </template>
        </el-input>
        <div style="margin-top: 12px">
          <el-tag
            v-for="(code, index) in workingPermissions"
            :key="`${code}-${index}`"
            closable
            style="margin: 2px"
            @close="removePermission(index)"
          >
            {{ code }}
          </el-tag>
        </div>
      </div>
      <template #footer>
        <el-button @click="editorVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDraft">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
