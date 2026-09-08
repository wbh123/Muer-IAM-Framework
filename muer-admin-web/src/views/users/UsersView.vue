<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { admin, notifyError } from '@/api/client';
import type { UserResponse } from '@/api/generated/api';
import { formatId } from '@/utils/format';
import { useAppStore } from '@/stores/app';

const router = useRouter();
const appStore = useAppStore();
const loading = ref(false);
const items = ref<UserResponse[]>([]);
const afterUserId = ref(0);
const hasMore = ref(false);

const filters = reactive({ username: '', userType: '', enabled: undefined as boolean | undefined });

async function load(reset = true) {
  if (reset) afterUserId.value = 0;
  loading.value = true;
  try {
    const page = (
      await admin.listUsers({
        afterUserId: afterUserId.value,
        limit: 20,
        username: filters.username || undefined,
        userType: filters.userType || undefined,
        enabled: filters.enabled,
      })
    ).data;
    items.value = page.items ?? [];
    hasMore.value = page.nextAfterUserId !== undefined && page.nextAfterUserId !== null;
    afterUserId.value = page.nextAfterUserId ?? 0;
  } catch (error) {
    notifyError(error);
  } finally {
    loading.value = false;
  }
}

async function next() {
  if (hasMore.value) await load(false);
}

async function toggleEnabled(user: UserResponse) {
  try {
    await ElMessageBox.confirm(
      `确定要${user.enabled ? '禁用' : '启用'}用户 ${user.username} 吗？`,
      '确认',
      { type: 'warning' },
    );
  } catch {
    return;
  }
  try {
    await admin.saveUser({ userId: user.userId, userRequest: { username: user.username, userType: user.userType, enabled: !user.enabled } });
    ElMessage.success('已更新');
    await load();
  } catch (error) {
    notifyError(error);
  }
}

async function revokeAll(user: UserResponse) {
  try {
    await ElMessageBox.confirm(
      `确定要强制撤销用户 ${user.username} 的全部 Session 吗？`,
      '强制下线',
      { type: 'warning', confirmButtonText: '强制下线' },
    );
  } catch {
    return;
  }
  try {
    await admin.forceRevokeUserSessions({ userId: user.userId, revokeRequest: { reason: 'ADMIN_ACTION' } });
    ElMessage.success('已强制下线');
  } catch (error) {
    notifyError(error);
  }
}

function openDetail(user: UserResponse) {
  void router.push(`/users/${user.userId}`);
}

onMounted(load);
</script>

<template>
  <div>
    <h2>用户</h2>
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="用户名">
          <el-input v-model="filters.username" placeholder="模糊搜索" clearable style="width: 180px" @keyup.enter="load()" />
        </el-form-item>
        <el-form-item label="User Type">
          <el-input v-model="filters.userType" clearable style="width: 140px" @keyup.enter="load()" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.enabled" clearable placeholder="全部" style="width: 120px">
            <el-option label="启用" :value="true" />
            <el-option label="禁用" :value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load()">搜索</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="items" v-loading="loading" size="default">
        <el-table-column label="User ID" width="110">
          <template #default="{ row }">{{ formatId(row.userId) }}</template>
        </el-table-column>
        <el-table-column label="Username" prop="username" min-width="160" />
        <el-table-column label="User Type" prop="userType" width="120" />
        <el-table-column label="Enabled" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Authorization Version" width="160">
          <template #default="{ row }">{{ formatId(row.authorizationVersion) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button size="small" link :type="row.enabled ? 'danger' : 'success'" @click="toggleEnabled(row)">
              {{ row.enabled ? '禁用' : '启用' }}
            </el-button>
            <el-button
              v-if="appStore.can('iam.admin.session.revoke-user')"
              size="small"
              link
              type="danger"
              @click="revokeAll(row)"
            >
              撤销全部 Session
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
