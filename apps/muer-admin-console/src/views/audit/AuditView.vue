<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { adminAudit, notifyError } from '@/api/client';
import type { AuditEventResponse } from '@/api/generated/api';
import { formatDateTime } from '@/utils/format';

const loading = ref(false);
const items = ref<AuditEventResponse[]>([]);
const after = ref(0);
const hasMore = ref(false);
const drawerVisible = ref(false);
const detail = ref<AuditEventResponse | null>(null);
const detailLoading = ref(false);

const filters = reactive({
  range: null as [Date, Date] | null,
  userId: undefined as number | undefined,
  identityId: '',
  sessionId: '',
  eventType: '',
  resourceType: '',
  resourceId: '',
});

async function load(reset = true) {
  if (reset) after.value = 0;
  loading.value = true;
  try {
    const page = (
      await adminAudit.listAuditEvents({
        userId: filters.userId || undefined,
        identityId: filters.identityId || undefined,
        sessionId: filters.sessionId || undefined,
        eventType: filters.eventType || undefined,
        resourceType: filters.resourceType || undefined,
        resourceId: filters.resourceId || undefined,
        from: filters.range?.[0] ? filters.range[0].toISOString() : undefined,
        to: filters.range?.[1] ? filters.range[1].toISOString() : undefined,
        after: after.value || undefined,
        limit: 50,
      })
    ).data;
    items.value = page.items ?? [];
    hasMore.value = page.nextAfterAuditLogId !== undefined && page.nextAfterAuditLogId !== null;
    after.value = page.nextAfterAuditLogId ?? 0;
  } catch (error) {
    notifyError(error);
  } finally {
    loading.value = false;
  }
}

async function next() {
  if (hasMore.value) await load(false);
}

async function openDetail(event: AuditEventResponse) {
  detail.value = event;
  drawerVisible.value = true;
  detailLoading.value = true;
  try {
    detail.value = (await adminAudit.getAuditEvent({ eventId: event.eventId })).data;
  } catch (error) {
    notifyError(error);
  } finally {
    detailLoading.value = false;
  }
}

function formatMap(map?: Record<string, string>): string {
  return map && Object.keys(map).length > 0 ? JSON.stringify(map, null, 2) : '{}';
}

onMounted(load);
</script>

<template>
  <div>
    <h2>Audit（只读）</h2>
    <el-card shadow="never">
      <el-form inline>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="filters.range"
            type="datetimerange"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 340px"
          />
        </el-form-item>
        <el-form-item label="用户 ID">
          <el-input-number v-model="filters.userId" :controls="false" style="width: 120px" />
        </el-form-item>
        <el-form-item label="事件类型">
          <el-input v-model="filters.eventType" clearable style="width: 150px" @keyup.enter="load()" />
        </el-form-item>
        <el-form-item label="资源类型">
          <el-input v-model="filters.resourceType" clearable style="width: 150px" @keyup.enter="load()" />
        </el-form-item>
        <el-form-item label="资源 ID">
          <el-input v-model="filters.resourceId" clearable style="width: 150px" @keyup.enter="load()" />
        </el-form-item>
        <el-form-item label="Session ID">
          <el-input v-model="filters.sessionId" clearable style="width: 160px" @keyup.enter="load()" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load()">搜索</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="items" v-loading="loading" size="small">
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
        </el-table-column>
        <el-table-column label="事件" prop="action" min-width="150" />
        <el-table-column label="操作者" prop="actor" min-width="150" />
        <el-table-column label="资源" min-width="160">
          <template #default="{ row }">{{ row.resourceType }} / {{ row.resourceId }}</template>
        </el-table-column>
        <el-table-column label="结果" prop="result" width="110" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="margin-top: 12px; text-align: right">
        <el-button :disabled="!hasMore" @click="next">加载更多</el-button>
      </div>
    </el-card>

    <el-drawer v-model="drawerVisible" title="Audit Event 详情" size="560px">
      <div v-loading="detailLoading" v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="Event ID">{{ detail.eventId }}</el-descriptions-item>
          <el-descriptions-item label="Time">{{ formatDateTime(detail.occurredAt) }}</el-descriptions-item>
          <el-descriptions-item label="Actor">{{ detail.actor }}</el-descriptions-item>
          <el-descriptions-item label="Action">{{ detail.action }}</el-descriptions-item>
          <el-descriptions-item label="Resource">
            {{ detail.resourceType }} / {{ detail.resourceId }}
          </el-descriptions-item>
          <el-descriptions-item label="Result">{{ detail.result }}</el-descriptions-item>
          <el-descriptions-item label="Request ID">{{ detail.requestId }}</el-descriptions-item>
        </el-descriptions>
        <h4>Subjects</h4>
        <el-table :data="detail.subjects ?? []" size="small">
          <el-table-column label="Type" prop="subjectType" width="120" />
          <el-table-column label="ID" prop="subjectId" min-width="120" />
          <el-table-column label="Relation" prop="relation" width="110" />
        </el-table>
        <h4>Metadata</h4>
        <pre class="json-block">{{ formatMap(detail.metadata) }}</pre>
        <h4>Before / After</h4>
        <pre class="json-block">before: {{ formatMap(detail.before) }}

after: {{ formatMap(detail.after) }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.json-block {
  background: #f6f8fa;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  padding: 8px;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
