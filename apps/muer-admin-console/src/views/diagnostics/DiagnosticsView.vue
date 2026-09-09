<script setup lang="ts">
import { reactive, ref } from 'vue';
import { authorization, notifyError } from '@/api/client';
import type { AuthorizationDecisionResponse } from '@/api/generated/api';

const form = reactive({
  permissionCode: '',
  domain: '',
  clientType: 'WEB',
  resourceType: '',
  resourceId: '',
  scopeAccess: 'READ' as 'READ' | 'WRITE',
});

const submitting = ref(false);
const result = ref<AuthorizationDecisionResponse | null>(null);
const evaluated = ref(false);

async function evaluate() {
  submitting.value = true;
  result.value = null;
  evaluated.value = false;
  try {
    result.value = (
      await authorization.evaluateAuthorization({
        authorizationEvaluationRequest: {
          permissionCode: form.permissionCode,
          domain: form.domain,
          clientType: form.clientType,
          resourceType: form.resourceType,
          resourceId: form.resourceId,
          scopeAccess: form.scopeAccess,
        },
      })
    ).data;
  } catch (error) {
    notifyError(error);
  } finally {
    submitting.value = false;
    evaluated.value = true;
  }
}
</script>

<template>
  <div>
    <h2>Authorization Playground</h2>
    <el-alert
      type="warning"
      show-icon
      :closable="false"
      title="诊断基于当前登录身份的授权配置，回答「我的当前 Profile 能否访问目标资源」；所有判断仍由后端 AuthorizationEngine 执行。"
      style="margin-bottom: 12px"
    />
    <el-row :gutter="16">
      <el-col :span="10">
        <el-card shadow="never">
          <el-form label-width="120px">
            <el-form-item label="Permission Code">
              <el-input v-model="form.permissionCode" placeholder="例如 iam.admin.user.read" />
            </el-form-item>
            <el-form-item label="Domain">
              <el-input v-model="form.domain" placeholder="Identity Domain" />
            </el-form-item>
            <el-form-item label="Client Type">
              <el-select v-model="form.clientType" style="width: 100%">
                <el-option label="WEB" value="WEB" />
                <el-option label="MOBILE" value="MOBILE" />
                <el-option label="BACKEND" value="BACKEND" />
              </el-select>
            </el-form-item>
            <el-form-item label="Resource Type">
              <el-input v-model="form.resourceType" placeholder="例如 ASSET / IAM_USER" />
            </el-form-item>
            <el-form-item label="Resource ID">
              <el-input v-model="form.resourceId" placeholder="例如 101" />
            </el-form-item>
            <el-form-item label="Scope Access">
              <el-select v-model="form.scopeAccess" style="width: 100%">
                <el-option label="READ" value="READ" />
                <el-option label="WRITE" value="WRITE" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="evaluate">执行诊断</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>决策结果</template>
          <template v-if="!evaluated">
            <el-empty description="输入授权请求后执行诊断" />
          </template>
          <template v-else-if="result">
            <div :class="result.allowed ? 'verdict allow' : 'verdict deny'">
              {{ result.allowed ? 'ALLOW' : 'DENY' }} · {{ result.decisionCode }}
            </div>
            <el-table :data="result.steps ?? []" size="small" style="margin-top: 12px">
              <el-table-column label="" width="70">
                <template #default="{ row }">
                  <el-tag :type="row.passed ? 'success' : 'danger'" size="small" effect="dark">
                    {{ row.passed ? '✓' : '✗' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="决策步骤" prop="code" min-width="170" />
              <el-table-column label="说明" prop="reason" min-width="240" />
            </el-table>
          </template>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.verdict {
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 2px;
}
.verdict.allow {
  color: var(--el-color-success);
}
.verdict.deny {
  color: var(--el-color-danger);
}
</style>
