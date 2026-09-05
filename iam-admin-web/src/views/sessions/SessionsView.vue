<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminSessions, admin, notifyError } from '@/api/client';
import type { SessionResponse } from '@/api/generated/api';
import { formatDateTime, formatId } from '@/utils/format';
import { useAppStore } from '@/stores/app';

const appStore = useAppStore();
const loading = ref(false);
const items = ref<SessionResponse[]>([]);
const nextAfter = ref<string | null>(null);

const filters = reactive({
  userId: undefined as number | undefined,
  clientType: '',
  active: undefined as boolean | undefined,
});

async function load(reset = true) {
  if (reset) nextAfter.value = null;
  loading.value = true;
  try {
    const page = (
      await adminSessions.adminListSessions({
        userId: filters.userId || undefined,
        clientType: filters.clientType || undefined,
        active: filters.active,
        after: nextAfter.value ?? undefined,
        limit: 30,
      })
    ).data;
    items.value = page.items ?? [];
    nextAfter.value = page.nextAfter ?? null;
  } catch (error) {
    notifyError(error);
  } finally {
    loading.value = false;
  }
}

async function next() {
  if (nextAfter.value) await load(false);
}

async function revoke(session: SessionResponse) {
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入撤销原因（ADMIN_ACTION / SECURITY_INCIDENT / USER_REQUEST / ACCOUNT_DISABLED / OTHER）',
      `撤销 Session ${session.sessionId.slice(0, 8)}…`,
      {
        confirmButtonText: '确认撤销',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputValue: 'ADMIN_ACTION',
        type: 'warning',
        inputValidator: (text) => (text && text.trim() ? true : '原因不能为空'),
      },
    );
    await admin.forceRevokeSession({
      sessionId: session.sessionId,
      revokeRequest: { reason: String(value).trim() },
    });
    ElMessage.success('Session 已撤销');
    await load();
  } catch {
    // cancelled by the operator
  }
}

onMounted(load);
</script>

<template>
  <div>
    <h2>Session 管理</h2>
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="用户 ID">
          <el-input-number v-model="filters.userId" :controls="false" placeholder="用户 ID" style="width: 140px" />
        </el-form-item>
        <el-form-item label="Client Type">
          <el-input v-model="filters.clientType" style="width: 130px" @keyup.enter="load()" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.active" clearable placeholder="全部" style="width: 110px">
            <el-option label="活跃" :value="true" />
            <el-option label="已结束" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load()">搜索</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="items" v-loading="loading" size="small">
        <el-table-column label="Session ID" prop="sessionId" min-width="200" show-overflow-tooltip />
        <el-table-column label="User" width="100">
          <template #default="{ row }">{{ formatId(row.userId) }}</template>
        </el-table-column>
        <el-table-column label="Client" prop="clientType" width="100" />
        <el-table-column label="Client Instance" prop="clientInstance" min-width="130">
          <template #default="{ row }">{{ row.clientInstance || '-' }}</template>
        </el-table-column>
        <el-table-column label="IP" prop="ipAddress" width="130">
          <template #default="{ row }">{{ row.ipAddress || '-' }}</template>
        </el-table-column>
        <el-table-column label="User Agent" prop="userAgent" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.userAgent || '-' }}</template>
        </el-table-column>
        <el-table-column label="登录时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.loginAt) }}</template>
        </el-table-column>
        <el-table-column label="最后活跃" width="160">
          <template #default="{ row }">{{ formatDateTime(row.lastSeenAt) }}</template>
        </el-table-column>
        <el-table-column label="过期" width="160">
          <template #default="{ row }">{{ formatDateTime(row.expiresAt) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="appStore.can('iam.admin.session.revoke')" label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link type="danger" size="small" :disabled="row.status !== 'ACTIVE'" @click="revoke(row)">
              撤销
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 12px; text-align: right">
        <el-button :disabled="!nextAfter" @click="next">加载更多</el-button>
      </div>
      <el-alert
        type="info"
        :closable="false"
        title="Session 列表永不返回 token / Redis key；撤销必须在确认后执行。"
        style="margin-top: 12px"
      />
    </el-card>
  </div>
</template>
