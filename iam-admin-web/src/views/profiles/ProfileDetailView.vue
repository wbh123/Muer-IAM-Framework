<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { ElMessage } from 'element-plus';
import {
  admin,
  adminProfiles,
  adminTemplates,
  notifyError,
} from '@/api/client';
import type {
  AuthorizationProfileResponse,
  PermissionTemplateVersionResponse,
  ResourceScope,
} from '@/api/generated/api';
import { useAppStore } from '@/stores/app';
import { formatDateTime, formatId } from '@/utils/format';

const route = useRoute();
const appStore = useAppStore();
const profileId = computed(() => Number(route.params.id));

const profile = ref<AuthorizationProfileResponse | null>(null);
const templateVersion = ref<PermissionTemplateVersionResponse | null>(null);
const loading = ref(false);

const scopeDialogVisible = ref(false);
const scopeForm = reactive({
  rows: [] as { scopeType: string; scopeRefId: string; accessMode: 'READ' | 'WRITE' }[],
});

const editDialogVisible = ref(false);
const editForm = reactive({
  clientTypes: [] as string[],
  hasValidFrom: false,
  hasValidUntil: false,
  validFrom: null as Date | null,
  validUntil: null as Date | null,
  enabled: true,
});

async function load() {
  loading.value = true;
  try {
    profile.value = (await adminProfiles.getAuthorizationProfile({ profileId: profileId.value })).data;
    const profileValue = profile.value;
    templateVersion.value = (
      await adminTemplates.getPermissionTemplateVersion({ versionId: profileValue.templateVersionId })
    ).data;
  } catch (error) {
    notifyError(error);
  } finally {
    loading.value = false;
  }
}

function openScopeEditor() {
  const scopes = profile.value?.scopes ?? [];
  scopeForm.rows = scopes.map((scope) => ({
    scopeType: scope.scopeType,
    scopeRefId: scope.scopeRefId,
    accessMode: (scope.accessMode ?? 'READ') as 'READ' | 'WRITE',
  }));
  scopeDialogVisible.value = true;
}

function addScopeRow() {
  scopeForm.rows.push({ scopeType: '', scopeRefId: '', accessMode: 'READ' });
}

async function saveScopes() {
  const scopes: ResourceScope[] = scopeForm.rows
    .filter((row) => row.scopeType.trim() && row.scopeRefId.trim())
    .map((row) => ({ scopeType: row.scopeType.trim(), scopeRefId: row.scopeRefId.trim(), accessMode: row.accessMode }));
  try {
    await admin.replaceAuthorizationProfileScopes({
      profileId: profileId.value,
      scopeReplacementRequest: { scopes },
    });
    ElMessage.success('Scope 已保存');
    scopeDialogVisible.value = false;
    await load();
  } catch (error) {
    notifyError(error);
  }
}

function openEditor() {
  if (!profile.value) return;
  editForm.clientTypes = [...(profile.value.clientTypes ?? [])];
  editForm.enabled = profile.value.enabled;
  editForm.hasValidFrom = !!profile.value.validFrom;
  editForm.hasValidUntil = !!profile.value.validUntil;
  editForm.validFrom = profile.value.validFrom ? new Date(profile.value.validFrom) : null;
  editForm.validUntil = profile.value.validUntil ? new Date(profile.value.validUntil) : null;
  editDialogVisible.value = true;
}

function iso(value: Date | null): string | null {
  return value ? value.toISOString() : null;
}

async function saveProfile() {
  if (!profile.value) return;
  try {
    await admin.saveAuthorizationProfile({
      profileId: profileId.value,
      authorizationProfileRequest: {
        profileId: profile.value.profileId,
        userId: profile.value.userId,
        profileName: profile.value.profileName,
        templateVersionId: profile.value.templateVersionId,
        clientTypes: editForm.clientTypes,
        enabled: editForm.enabled,
        revoked: profile.value.revoked,
        validFrom: editForm.hasValidFrom ? iso(editForm.validFrom) : null,
        validUntil: editForm.hasValidUntil ? iso(editForm.validUntil) : null,
        scopes: profile.value.scopes ?? [],
      },
    });
    ElMessage.success('Profile 已保存');
    editDialogVisible.value = false;
    await load();
  } catch (error) {
    notifyError(error);
  }
}

onMounted(load);
</script>

<template>
  <div v-loading="loading">
    <h2>Profile 详情</h2>
    <el-card shadow="never">
      <el-descriptions v-if="profile" :column="2" border>
        <el-descriptions-item label="Profile ID">{{ formatId(profile.profileId) }}</el-descriptions-item>
        <el-descriptions-item label="User ID">{{ formatId(profile.userId) }}</el-descriptions-item>
        <el-descriptions-item label="Profile Name">{{ profile.profileName }}</el-descriptions-item>
        <el-descriptions-item label="Template Version">{{ formatId(profile.templateVersionId) }}</el-descriptions-item>
        <el-descriptions-item label="Client Types">
          {{ (profile.clientTypes ?? []).join(', ') || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="Enabled">
          <el-tag :type="profile.enabled ? 'success' : 'info'" size="small">
            {{ profile.enabled ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Valid From">{{ formatDateTime(profile.validFrom) }}</el-descriptions-item>
        <el-descriptions-item label="Valid Until">{{ formatDateTime(profile.validUntil) }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="appStore.can('iam.admin.profile.write')" style="margin-top: 12px">
        <el-button type="primary" plain @click="openEditor">编辑 Profile</el-button>
        <el-button type="warning" plain @click="openScopeEditor">编辑 Resource Scope</el-button>
      </div>
    </el-card>

    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>Resource Scopes（IAM 只识别 scopeType / scopeRefId）</template>
          <el-table :data="profile?.scopes ?? []" size="small">
            <el-table-column label="Scope Type" prop="scopeType" min-width="140" />
            <el-table-column label="Scope Ref ID" prop="scopeRefId" min-width="140" />
            <el-table-column label="Access Mode" prop="accessMode" width="110" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="never">
          <template #header>Effective Permissions（权限来源 = Template Version）</template>
          <p class="muted" v-if="templateVersion">
            Template v{{ templateVersion.versionNumber }}（{{ templateVersion.status }}）→ Permission Codes
          </p>
          <el-tag v-for="code in templateVersion?.permissions ?? []" :key="code" size="small" style="margin: 2px">
            {{ code }}
          </el-tag>
        </el-card>
      </el-col>
    </el-row>

    <el-dialog v-model="scopeDialogVisible" title="编辑 Resource Scope" width="620px">
      <el-table :data="scopeForm.rows" size="small">
        <el-table-column label="Type" width="200">
          <template #default="{ row }">
            <el-input v-model="row.scopeType" placeholder="如 PROJECT / DEPARTMENT" />
          </template>
        </el-table-column>
        <el-table-column label="Reference ID" min-width="160">
          <template #default="{ row }">
            <el-input v-model="row.scopeRefId" placeholder="如 101" />
          </template>
        </el-table-column>
        <el-table-column label="Access" width="110">
          <template #default="{ row }">
            <el-select v-model="row.accessMode">
              <el-option label="READ" value="READ" />
              <el-option label="WRITE" value="WRITE" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="" width="80">
          <template #default="{ $index }">
            <el-button link type="danger" @click="scopeForm.rows.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="addScopeRow">添加一行</el-button>
        <el-button type="primary" @click="saveScopes">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editDialogVisible" title="编辑 Profile" width="560px">
      <el-form label-width="120px">
        <el-form-item label="Client Types">
          <el-select v-model="editForm.clientTypes" multiple allow-create filterable default-first-option
            style="width: 100%">
            <el-option label="WEB" value="WEB" />
            <el-option label="MOBILE" value="MOBILE" />
            <el-option label="BACKEND" value="BACKEND" />
          </el-select>
        </el-form-item>
        <el-form-item label="Enabled">
          <el-switch v-model="editForm.enabled" />
        </el-form-item>
        <el-form-item label="Valid From">
          <el-switch v-model="editForm.hasValidFrom" />
          <el-date-picker
            v-if="editForm.hasValidFrom"
            v-model="editForm.validFrom"
            type="datetime"
            style="margin-left: 8px"
          />
        </el-form-item>
        <el-form-item label="Valid Until">
          <el-switch v-model="editForm.hasValidUntil" />
          <el-date-picker
            v-if="editForm.hasValidUntil"
            v-model="editForm.validUntil"
            type="datetime"
            style="margin-left: 8px"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveProfile">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.muted {
  color: var(--el-text-color-secondary);
}
</style>
