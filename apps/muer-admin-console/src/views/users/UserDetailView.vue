<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { admin, adminAudit, adminProfiles, adminSessions, adminUsers, notifyError } from '@/api/client';
import type {
  AuditEventResponse,
  AuthorizationProfileResponse,
  IdentityResponse,
  SessionResponse,
  UserResponse,
} from '@/api/generated/api';
import { useAppStore } from '@/stores/app';
import { formatDateTime, formatId, yesNo } from '@/utils/format';

const route = useRoute();
const appStore = useAppStore();
const userId = computed(() => Number(route.params.id));

const user = ref<UserResponse | null>(null);
const identities = ref<IdentityResponse[]>([]);
const profiles = ref<AuthorizationProfileResponse[]>([]);
const sessions = ref<SessionResponse[]>([]);
const audits = ref<AuditEventResponse[]>([]);
const loading = ref(false);
const activeTab = ref('basic');

async function load() {
  loading.value = true;
  const tasks: Promise<unknown>[] = [];
  tasks.push(
    adminUsers
      .getUser({ userId: userId.value })
      .then((response) => (user.value = response.data))
      .catch(notifyError),
  );
  if (appStore.can('iam.admin.identity.read')) {
    tasks.push(
      admin
        .listUserIdentities({ userId: userId.value })
        .then((response) => (identities.value = response.data))
        .catch(() => (identities.value = [])),
    );
  }
  if (appStore.can('iam.admin.profile.read')) {
    tasks.push(
      adminProfiles
        .listUserAuthorizationProfiles({ userId: userId.value })
        .then((response) => (profiles.value = response.data))
        .catch(() => (profiles.value = [])),
    );
  }
  if (appStore.can('iam.admin.session.read')) {
    tasks.push(
      adminSessions
        .listUserSessions({ userId: userId.value })
        .then((response) => (sessions.value = response.data))
        .catch(() => (sessions.value = [])),
    );
  }
  if (appStore.can('iam.admin.audit.read')) {
    tasks.push(
      adminAudit
        .listAuditEvents({ userId: userId.value, limit: 30 })
        .then((response) => (audits.value = response.data.items ?? []))
        .catch(() => (audits.value = [])),
    );
  }
  await Promise.all(tasks);
  loading.value = false;
}

onMounted(load);
</script>

<template>
  <div v-loading="loading">
    <h2>用户详情</h2>
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="basic">
          <el-descriptions v-if="user" :column="2" border>
            <el-descriptions-item label="User ID">{{ formatId(user.userId) }}</el-descriptions-item>
            <el-descriptions-item label="Username">{{ user.username }}</el-descriptions-item>
            <el-descriptions-item label="User Type">{{ user.userType }}</el-descriptions-item>
            <el-descriptions-item label="Enabled">{{ yesNo(user.enabled) }}</el-descriptions-item>
            <el-descriptions-item label="Authorization Version">
              {{ formatId(user.authorizationVersion) }}
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="Identity" name="identities">
          <el-table :data="identities" size="small">
            <el-table-column label="Identity ID" prop="identityId" min-width="200" />
            <el-table-column label="Identity Key" prop="identityKey" min-width="200" />
            <el-table-column label="Domain" prop="domain" width="140" />
            <el-table-column label="Enabled" width="100">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="Profile" name="profiles">
          <el-table :data="profiles" size="small">
            <el-table-column label="Profile ID" width="110">
              <template #default="{ row }">{{ formatId(row.profileId) }}</template>
            </el-table-column>
            <el-table-column label="Profile Name" prop="profileName" min-width="180" />
            <el-table-column label="Template Version" width="130">
              <template #default="{ row }">{{ formatId(row.templateVersionId) }}</template>
            </el-table-column>
            <el-table-column label="Client Types" min-width="140">
              <template #default="{ row }">{{ (row.clientTypes ?? []).join(', ') || '-' }}</template>
            </el-table-column>
            <el-table-column label="Enabled" width="90">
              <template #default="{ row }">{{ yesNo(row.enabled) }}</template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="Session" name="sessions">
          <el-table :data="sessions" size="small">
            <el-table-column label="Session ID" prop="sessionId" min-width="200" />
            <el-table-column label="Client" prop="clientType" width="100" />
            <el-table-column label="IP" prop="ipAddress" width="130" />
            <el-table-column label="登录时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.loginAt) }}</template>
            </el-table-column>
            <el-table-column label="过期时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.expiresAt) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="Audit" name="audit">
          <el-table :data="audits" size="small">
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
            </el-table-column>
            <el-table-column label="事件" prop="action" min-width="160" />
            <el-table-column label="资源" min-width="150">
              <template #default="{ row }">{{ row.resourceType }} / {{ row.resourceId }}</template>
            </el-table-column>
            <el-table-column label="结果" prop="result" width="110" />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>
