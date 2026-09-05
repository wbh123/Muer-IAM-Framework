<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { storeToRefs } from 'pinia';
import { authorization, mySessions, notifyError } from '@/api/client';
import type { AuthorizationProfileResponse, SessionResponse } from '@/api/generated/api';
import { useAuthStore } from '@/stores/auth';
import { formatDateTime } from '@/utils/format';

const authStore = useAuthStore();
const { principal } = storeToRefs(authStore);

const profiles = ref<AuthorizationProfileResponse[]>([]);
const sessions = ref<SessionResponse[]>([]);
const loading = ref(false);

async function load() {
  loading.value = true;
  try {
    profiles.value = (await authorization.listMyAuthorizationProfiles({})).data;
    sessions.value = (await mySessions.listMySessions({})).data.items ?? [];
  } catch (error) {
    notifyError(error);
  } finally {
    loading.value = false;
  }
}

async function switchProfile(profileId: number) {
  try {
    const response = (await authorization.switchAuthorizationProfile({ profileId })).data;
    authStore.persist({
      accessToken: response.accessToken,
      sessionId: response.sessionId,
      principal: response.principal,
    });
    ElMessage.success('Profile 已切换');
    await load();
  } catch (error) {
    notifyError(error);
  }
}

async function revokeMine(session: SessionResponse) {
  try {
    await ElMessageBox.confirm('确定要撤销该 Session 吗？', '撤销 Session', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await mySessions.revokeMySession({
      sessionId: session.sessionId,
      revokeRequest: { reason: 'USER_REQUEST' },
    });
    ElMessage.success('已撤销');
    await load();
  } catch (error) {
    notifyError(error);
  }
}

async function revokeOthers() {
  const current = sessions.value.find((session) => session.sessionId === authStore.stored?.sessionId);
  if (!current) return;
  try {
    await ElMessageBox.confirm('将撤销当前身份在其他设备上的全部 Session（本会话保留）。', '撤销其他 Session', {
      type: 'warning',
      confirmButtonText: '撤销',
    });
  } catch {
    return;
  }
  try {
    await mySessions.revokeOtherSessions({
      revokeOthersRequest: { currentSessionId: current.sessionId, reason: 'USER_REQUEST' },
    });
    ElMessage.success('其他 Session 已撤销');
    await load();
  } catch (error) {
    notifyError(error);
  }
}

onMounted(load);
</script>

<template>
  <div v-loading="loading">
    <h2>个人安全中心</h2>
    <el-card shadow="never">
      <template #header>当前 Principal</template>
      <el-descriptions v-if="principal" :column="2" border>
        <el-descriptions-item label="User ID">{{ principal.userId }}</el-descriptions-item>
        <el-descriptions-item label="Identity">{{ principal.identityId }}</el-descriptions-item>
        <el-descriptions-item label="Domain">{{ principal.identityDomain }}</el-descriptions-item>
        <el-descriptions-item label="Client Type">{{ principal.clientType }}</el-descriptions-item>
        <el-descriptions-item label="Active Profile">
          {{ principal.activeProfileId ?? '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="Authorization Version">
          {{ principal.authorizationVersion }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>我的 Profile（可切换）</template>
      <el-table :data="profiles" size="small">
        <el-table-column label="Profile ID" prop="profileId" width="110" />
        <el-table-column label="Profile Name" prop="profileName" min-width="180" />
        <el-table-column label="Template Version" width="140" prop="templateVersionId" />
        <el-table-column label="Client Types" min-width="150">
          <template #default="{ row }">{{ (row.clientTypes ?? []).join(', ') || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button
              size="small"
              type="primary"
              plain
              :disabled="row.profileId === principal?.activeProfileId"
              @click="switchProfile(row.profileId)"
            >
              {{ row.profileId === principal?.activeProfileId ? '当前' : '切换' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top: 16px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>我的 Session</span>
          <el-button size="small" type="danger" plain @click="revokeOthers">撤销其他 Session</el-button>
        </div>
      </template>
      <el-table :data="sessions" size="small">
        <el-table-column label="Session ID" prop="sessionId" min-width="200" show-overflow-tooltip />
        <el-table-column label="Client" prop="clientType" width="100" />
        <el-table-column label="IP" prop="ipAddress" width="130">
          <template #default="{ row }">{{ row.ipAddress || '-' }}</template>
        </el-table-column>
        <el-table-column label="登录时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.loginAt) }}</template>
        </el-table-column>
        <el-table-column label="过期" width="170">
          <template #default="{ row }">{{ formatDateTime(row.expiresAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button
              size="small"
              link
              type="danger"
              :disabled="row.sessionId === authStore.stored?.sessionId"
              @click="revokeMine(row)"
            >
              撤销
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>
