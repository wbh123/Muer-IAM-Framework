import { beforeEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import { createPinia, setActivePinia, type Pinia } from 'pinia';

const mocks = vi.hoisted(() => ({
  listUsers: vi.fn(),
  saveUser: vi.fn(),
  forceRevokeUserSessions: vi.fn(),
}));

vi.mock('@/api/client', () => ({
  admin: {
    listUsers: mocks.listUsers,
    saveUser: mocks.saveUser,
    forceRevokeUserSessions: mocks.forceRevokeUserSessions,
  },
  notifyError: vi.fn(),
}));

import UsersView from '@/views/users/UsersView.vue';
import { useAuthStore } from '@/stores/auth';

let pinia: Pinia;

describe('UsersView', () => {
  beforeEach(() => {
    pinia = createPinia();
    setActivePinia(pinia);
    vi.clearAllMocks();
    const auth = useAuthStore();
    auth.persist({
      accessToken: 't',
      sessionId: 's',
      principal: {
        userId: 1,
        identityId: 'i',
        identityDomain: 'D',
        clientType: 'WEB',
        authorizationVersion: 1,
      } as never,
    });
  });

  it('renders the user rows returned by the list API', async () => {
    mocks.listUsers.mockResolvedValue({
      data: {
        items: [
          { userId: 7, username: 'alex', userType: 'MEMBER', enabled: true, authorizationVersion: 3 },
          { userId: 8, username: 'bob', userType: 'MEMBER', enabled: false, authorizationVersion: 2 },
        ],
        nextAfterUserId: null,
      },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: {},
    } as never);

    const wrapper = mount(UsersView, { global: { plugins: [pinia] } });
    await flushPromises();

    expect(mocks.listUsers).toHaveBeenCalled();
    expect(wrapper.text()).toContain('alex');
    expect(wrapper.text()).toContain('bob');
    expect(wrapper.text()).toContain('禁用');
  });

  it('forwards the username filter when searching', async () => {
    mocks.listUsers.mockResolvedValue({
      data: { items: [], nextAfterUserId: null },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: {},
    } as never);
    const wrapper = mount(UsersView, { global: { plugins: [pinia] } });
    await flushPromises();
    vi.clearAllMocks();
    mocks.listUsers.mockResolvedValue({
      data: { items: [], nextAfterUserId: null },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: {},
    } as never);

    await wrapper.findAll('input')[0].setValue('alex');
    const searchButton = wrapper.findAll('button').find((button) => button.text().includes('搜索'));
    await searchButton!.trigger('click');
    await flushPromises();

    const call = mocks.listUsers.mock.calls[0][0] as { username?: string };
    expect(call.username).toBe('alex');
  });
});
