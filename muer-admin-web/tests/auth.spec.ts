import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setActivePinia, createPinia } from 'pinia';

vi.mock('@/api/client', () => ({
  auth: {
    login: vi.fn(),
    logout: vi.fn(),
    getCurrentPrincipal: vi.fn(),
  },
  capabilities: {
    getCurrentCapabilities: vi.fn(),
  },
}));

import { useAuthStore } from '@/stores/auth';
import { auth, capabilities } from '@/api/client';

const TOKEN_KEY = 'iam.admin.session';

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    sessionStorage.clear();
    vi.clearAllMocks();
  });

  it('stores the opaque token in sessionStorage after login', async () => {
    vi.mocked(auth.login).mockResolvedValue({
      data: {
        accessToken: 'token-abc',
        sessionId: 'session-1',
        expiresAt: '2026-09-06T00:00:00Z',
        principal: { userId: 7, identityId: 'id-7', identityDomain: 'SECURITY', clientType: 'WEB', authorizationVersion: 1 },
      },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: {},
    } as never);
    const store = useAuthStore();

    await store.login('alice', 'secret', 'WEB');

    expect(auth.login).toHaveBeenCalledWith({
      loginRequest: { username: 'alice', password: 'secret', clientType: 'WEB' },
    });
    const stored = JSON.parse(sessionStorage.getItem(TOKEN_KEY) ?? '{}') as {
      accessToken: string;
      sessionId: string;
    };
    expect(stored.accessToken).toBe('token-abc');
    expect(store.isAuthenticated).toBe(true);
  });

  it('clears the local session after logout even when the server call fails', async () => {
    sessionStorage.setItem(
      TOKEN_KEY,
      JSON.stringify({ accessToken: 'token-abc', sessionId: 's', principal: { userId: 1 } }),
    );
    vi.mocked(auth.logout).mockRejectedValue(new Error('network'));
    const store = useAuthStore();

    await store.logout();

    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(store.isAuthenticated).toBe(false);
  });

  it('loads capabilities once the principal is known', async () => {
    vi.mocked(capabilities.getCurrentCapabilities).mockResolvedValue({
      data: {
        principal: {},
        activeProfileId: 1,
        templateVersionId: 2,
        permissions: new Set(['iam.admin.user.read']),
        scopes: [],
      },
      status: 200,
      statusText: 'OK',
      headers: {},
      config: {},
    } as never);
    const store = useAuthStore();

    const value = await store.loadCapabilities();

    expect(Array.from(value.permissions ?? [])).toEqual(['iam.admin.user.read']);
  });
});
