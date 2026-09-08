<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { admin, adminProfiles, adminTemplates, notifyError } from '@/api/client';
import type { AuthorizationProfileResponse, PermissionTemplateVersionResponse, UserResponse } from '@/api/generated/api';
import { formatId } from '@/utils/format';

const router = useRouter();
const loading = ref(false);
const items = ref<AuthorizationProfileResponse[]>([]);
const afterProfileId = ref(0);
const hasMore = ref(false);
const createVisible = ref(false);
const users = ref<UserResponse[]>([]);
const publishedVersions = ref<PermissionTemplateVersionResponse[]>([]);
const createForm = reactive({ userId: undefined as number | undefined, profileName: '', templateVersionId: undefined as number | undefined,
  clientTypes: ['WEB'] as string[], enabled: true, scopes: [] as { scopeType: string; scopeRefId: string; accessMode: 'READ' | 'WRITE' }[] });

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

async function openCreator() {
  createForm.userId = undefined; createForm.profileName = ''; createForm.templateVersionId = undefined;
  createForm.clientTypes = ['WEB']; createForm.enabled = true; createForm.scopes = [];
  try {
    users.value = (await admin.listUsers({ limit: 100 })).data.items ?? [];
    const templates = (await adminTemplates.listPermissionTemplates({ limit: 100 })).data.items ?? [];
    const versions = await Promise.all(templates.map((template) => adminTemplates.listPermissionTemplateVersions({ templateId: template.templateId })));
    publishedVersions.value = versions.flatMap((response) => response.data).filter((version) => version.status === 'PUBLISHED');
    createVisible.value = true;
  } catch (error) { notifyError(error); }
}

function addScope() { createForm.scopes.push({ scopeType: '', scopeRefId: '', accessMode: 'READ' }); }

async function createProfile() {
  if (!createForm.userId || !createForm.profileName.trim() || !createForm.templateVersionId || !createForm.clientTypes.length) return;
  try {
    await adminProfiles.createAuthorizationProfile({ authorizationProfileCreateRequest: {
      userId: createForm.userId, profileName: createForm.profileName.trim(), templateVersionId: createForm.templateVersionId,
      clientTypes: new Set(createForm.clientTypes), enabled: createForm.enabled, revoked: false, validFrom: null, validUntil: null,
      scopes: createForm.scopes.filter((scope) => scope.scopeType.trim() && scope.scopeRefId.trim()),
    } });
    ElMessage.success('Profile 已创建'); createVisible.value = false; await load();
  } catch (error) { notifyError(error); }
}

onMounted(load);
</script>

<template>
  <div>
    <h2>Profile</h2>
    <div style="margin-bottom: 12px"><el-button type="primary" @click="openCreator">新建 Profile</el-button></div>
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
    <el-dialog v-model="createVisible" title="新建 Profile" width="640px">
      <el-form label-width="130px">
        <el-form-item label="User" required><el-select v-model="createForm.userId" filterable style="width: 100%"><el-option v-for="user in users" :key="user.userId" :label="`${user.username} (${user.userId})`" :value="user.userId" /></el-select></el-form-item>
        <el-form-item label="Profile Name" required><el-input v-model="createForm.profileName" /></el-form-item>
        <el-form-item label="Published Version" required><el-select v-model="createForm.templateVersionId" style="width: 100%"><el-option v-for="version in publishedVersions" :key="version.versionId" :label="`Template ${version.templateId} / v${version.versionNumber}`" :value="version.versionId" /></el-select></el-form-item>
        <el-form-item label="Client Types"><el-select v-model="createForm.clientTypes" multiple allow-create filterable style="width: 100%"><el-option label="WEB" value="WEB" /><el-option label="MOBILE" value="MOBILE" /><el-option label="BACKEND" value="BACKEND" /></el-select></el-form-item>
        <el-form-item label="Enabled"><el-switch v-model="createForm.enabled" /></el-form-item>
        <el-form-item label="Scopes"><el-button @click="addScope">添加 Scope</el-button><div v-for="(scope, index) in createForm.scopes" :key="index" style="display:flex; gap:6px; margin-top:6px; width:100%"><el-input v-model="scope.scopeType" placeholder="Type" /><el-input v-model="scope.scopeRefId" placeholder="Reference ID" /><el-select v-model="scope.accessMode" style="width:110px"><el-option label="READ" value="READ" /><el-option label="WRITE" value="WRITE" /></el-select><el-button link type="danger" @click="createForm.scopes.splice(index, 1)">删除</el-button></div></el-form-item>
      </el-form>
      <template #footer><el-button @click="createVisible = false">取消</el-button><el-button type="primary" :disabled="!createForm.userId || !createForm.profileName.trim() || !createForm.templateVersionId" @click="createProfile">创建</el-button></template>
    </el-dialog>
  </div>
</template>
