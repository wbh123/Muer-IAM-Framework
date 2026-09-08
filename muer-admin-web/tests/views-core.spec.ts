import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia, type Pinia } from 'pinia';
import { createRouter, createMemoryHistory } from 'vue-router';
import { ElMessageBox } from 'element-plus';
import { defineComponent } from 'vue';

vi.mock('@/api/client', () => ({
  admin: {
    replaceAuthorizationProfileScopes: vi.fn(),
    saveAuthorizationProfile: vi.fn(),
    forceRevokeSession: vi.fn(),
  },
  adminProfiles: {
    getAuthorizationProfile: vi.fn(),
    listUserAuthorizationProfiles: vi.fn(),
    getAuthorizationProfileScopes: vi.fn(),
  },
  adminTemplates: { getPermissionTemplateVersion: vi.fn() },
  adminSessions: { adminListSessions: vi.fn(), listUserSessions: vi.fn() },
  authorization: { evaluateAuthorization: vi.fn() },
  notifyError: vi.fn(),
}));

import * as client from '@/api/client';
import ProfileDetailView from '@/views/profiles/ProfileDetailView.vue';
import SessionsView from '@/views/sessions/SessionsView.vue';
import DiagnosticsView from '@/views/diagnostics/DiagnosticsView.vue';

async function profileRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/profiles/:id',
        component: defineComponent({ template: '<div />' }),
      },
    ],
  });
  await router.push('/profiles/31');
  await router.isReady();
  return router;
}

let pinia: Pinia;

function prepare() {
  pinia = createPinia();
  setActivePinia(pinia);
  vi.clearAllMocks();
}

describe('profile scope form', () => {
  beforeEach(prepare);

  it('loads a profile and lets the operator add scope rows before saving', async () => {
    const { useAppStore } = await import('@/stores/app');
    useAppStore().setPermissions(['iam.admin.profile.write', 'iam.admin.scope.write']);
    vi.mocked(client.adminProfiles.getAuthorizationProfile).mockResolvedValue({
      data: {
        profileId: 31,
        userId: 7,
        profileName: 'Operator',
        templateVersionId: 9,
        clientTypes: ['WEB'],
        enabled: true,
        revoked: false,
        scopes: [{ scopeType: 'PROJECT', scopeRefId: '101', accessMode: 'READ' }],
      },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: {},
    } as never);
    vi.mocked(client.adminTemplates.getPermissionTemplateVersion).mockResolvedValue({
      data: {
        versionId: 9,
        templateId: 2,
        versionNumber: 1,
        status: 'PUBLISHED',
        permissions: new Set(['document:read']),
      },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: {},
    } as never);

    const wrapper = mount(ProfileDetailView, {
      global: { plugins: [pinia, await profileRouter()] },
    });
    await flushPromises();

    expect(wrapper.text()).toContain('Operator');
    expect(wrapper.text()).toContain('document:read');

    const editButton = wrapper.findAll('button').find((button) => button.text().includes('编辑 Resource Scope'));
    expect(editButton).toBeTruthy();
    await editButton!.trigger('click');
    await flushPromises();

    const rowsBefore = wrapper.findAll('.el-table__row').length;
    const addButton = wrapper.findAll('button').find((button) => button.text().includes('添加一行'));
    expect(addButton).toBeTruthy();
    await addButton!.trigger('click');
    await flushPromises();
    expect(wrapper.findAll('.el-table__row').length).toBe(rowsBefore + 1);

    const saveButton = wrapper.findAll('button').find((button) => button.text().includes('保存'));
    await saveButton!.trigger('click');
    await flushPromises();
    expect(client.admin.replaceAuthorizationProfileScopes).toHaveBeenCalledWith({
      profileId: 31,
      scopeReplacementRequest: { scopes: [{ scopeType: 'PROJECT', scopeRefId: '101', accessMode: 'READ' }] },
    });
  });
});

describe('session revoke confirmation', () => {
  beforeEach(prepare);

  it('requires confirmation before revoking an active session', async () => {
    const { useAppStore } = await import('@/stores/app');
    useAppStore().setPermissions(['iam.admin.session.revoke']);
    vi.mocked(client.adminSessions.adminListSessions).mockResolvedValue({
      data: {
        items: [
          {
            sessionId: 'session-active',
            userId: 7,
            clientType: 'WEB',
            clientInstance: null,
            ipAddress: '10.0.0.1',
            userAgent: 'ua',
            loginAt: '2026-09-01T00:00:00Z',
            lastSeenAt: '2026-09-05T00:00:00Z',
            expiresAt: '2026-09-06T00:00:00Z',
            logoutAt: null,
            status: 'ACTIVE',
            revokeReason: null,
          },
        ],
        nextAfter: null,
      },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: {},
    } as never);
    const prompt = vi.spyOn(ElMessageBox, 'prompt').mockResolvedValue({ value: 'ADMIN_ACTION' } as never);
    vi.mocked(client.admin.forceRevokeSession).mockResolvedValue({
      status: 204,
      statusText: 'NO_CONTENT',
      headers: {},
      config: {},
      data: '',
    } as never);

    const wrapper = mount(SessionsView, { global: { plugins: [pinia] } });
    await flushPromises();
    expect(wrapper.text()).toContain('session-active');

    const revoke = wrapper.findAll('button').find((button) => button.text().includes('撤销'));
    expect(revoke).toBeTruthy();
    await revoke!.trigger('click');
    await flushPromises();

    expect(prompt).toHaveBeenCalled();
    expect(client.admin.forceRevokeSession).toHaveBeenCalledWith({
      sessionId: 'session-active',
      revokeRequest: { reason: 'ADMIN_ACTION' },
    });
  });
});

describe('diagnostics decision rendering', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('renders an ALLOW verdict with decision steps', async () => {
    vi.mocked(client.authorization.evaluateAuthorization).mockResolvedValue({
      data: {
        allowed: true,
        decisionCode: 'ALLOWED',
        steps: [
          { code: 'IDENTITY_DOMAIN', passed: true, reason: 'domain matches' },
          { code: 'ATOMIC_PERMISSION', passed: true, reason: 'permission granted' },
        ],
      },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: {},
    } as never);

    const wrapper = mount(DiagnosticsView, { global: { plugins: [createPinia()] } });
    const inputs = wrapper.findAll('input');
    await inputs[0].setValue('iam.admin.user.read');
    await inputs[1].setValue('SECURITY');
    await wrapper.findAll('button')[0].trigger('click');
    await flushPromises();

    expect(wrapper.text()).toContain('ALLOW');
    expect(wrapper.text()).toContain('IDENTITY_DOMAIN');
    expect(wrapper.text()).toContain('ATOMIC_PERMISSION');
  });
});
