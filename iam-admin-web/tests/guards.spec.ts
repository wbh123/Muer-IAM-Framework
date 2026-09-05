import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';

vi.mock('@/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/client')>();
  return {
    ...actual,
    auth: { login: vi.fn(), logout: vi.fn(), getCurrentPrincipal: vi.fn() },
    capabilities: { getCurrentCapabilities: vi.fn() },
  };
});

import { useAuthStore } from '@/stores/auth';
import { handleUnauthorized } from '@/api/client';
import router from '@/router/index';

const TOKEN_KEY = 'iam.admin.session';

describe('route guard', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    sessionStorage.clear();
    vi.clearAllMocks();
  });

  it('redirects anonymous visitors to /login', async () => {
    const store = useAuthStore();
    expect(store.isAuthenticated).toBe(false);
    await router.push('/dashboard');
    expect(router.currentRoute.value.name).toBe('login');
    await router.replace('/login');
  });

  it('allows an authenticated visitor to reach a protected route', async () => {
    const store = useAuthStore();
    store.persist({
      accessToken: 'token-x',
      sessionId: 's',
      principal: {
        userId: 1,
        identityId: 'i',
        identityDomain: 'D',
        clientType: 'WEB',
        authorizationVersion: 1,
      } as never,
    });
    await router.push('/dashboard');
    expect(router.currentRoute.value.name).toBe('dashboard');
    await router.replace('/login');
  });
});

describe('API 401 handling', () => {
  beforeEach(() => {
    window.history.replaceState({}, '', '/');
    sessionStorage.clear();
    sessionStorage.setItem(
      TOKEN_KEY,
      JSON.stringify({ accessToken: 'token', sessionId: 's', principal: { userId: 1 } }),
    );
  });

  it('clears the token and redirects to /login', () => {
    const assign = vi.fn();

    handleUnauthorized(assign);

    expect(sessionStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(assign).toHaveBeenCalledWith('/login?expired=1');
  });
});
