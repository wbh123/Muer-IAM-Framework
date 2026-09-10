import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia, setActivePinia, type Pinia } from 'pinia';

vi.mock('@/api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/api/client')>();
  return {
    ...actual,
    auth: { login: vi.fn(), logout: vi.fn(), getCurrentPrincipal: vi.fn() },
    capabilities: { getCurrentCapabilities: vi.fn() },
  };
});

import { useAuthStore, type StoredSession } from '@/stores/auth';
import { handleUnauthorized } from '@/api/client';
import * as clientModule from '@/api/client';
import router from '@/router/index';

const TOKEN_KEY = 'iam.admin.session';
let pinia: Pinia;

function signedIn(session: Partial<StoredSession> = {}) {
  const store = useAuthStore();
  store.persist({
    accessToken: 'token-x',
    sessionId: 's',
    principal: {
      userId: 1,
      identityId: 'identity-1',
      identityDomain: 'D',
      activeProfileId: 1,
      templateVersionId: 2,
      clientType: 'WEB',
      authorizationVersion: 1,
    },
    ...session,
  });
}

function mockCapabilities(permissions: string[]) {
  vi.mocked(clientModule.capabilities.getCurrentCapabilities).mockResolvedValue({
    data: {
      principal: {},
      activeProfileId: 1,
      templateVersionId: 2,
      permissions: new Set(permissions),
      scopes: [],
    },
    status: 200,
    statusText: 'OK',
    headers: {},
    config: {},
  } as never);
}

async function go(path: string) {
  await router.push(path);
  return router.currentRoute.value.name;
}

describe('route permission guard', () => {
  beforeEach(() => {
    pinia = createPinia();
    setActivePinia(pinia);
    sessionStorage.clear();
    window.history.replaceState({}, '', '/');
    vi.clearAllMocks();
  });

  afterEach(async () => {
    if (router.currentRoute.value.name !== 'login') await router.replace('/login');
  });

  it('redirects anonymous visitors away from protected pages to /login', async () => {
    expect(useAuthStore().isAuthenticated).toBe(false);
    expect(await go('/users')).toBe('login');
  });

  it('lets an authenticated user with iam.admin.user.read open /users', async () => {
    signedIn();
    mockCapabilities(['iam.admin.user.read']);
    expect(await go('/users')).toBe('users');
  });

  it('sends an authenticated user without iam.admin.user.read to /403', async () => {
    signedIn();
    mockCapabilities(['iam.admin.overview.read']);
    expect(await go('/users')).toBe('forbidden');
  });

  it('lets any authenticated user open /account without an admin permission', async () => {
    signedIn();
    mockCapabilities([]);
    expect(await go('/account')).toBe('account');
  });

  it('routes /diagnostics to /403 when iam.admin.diagnostics is missing', async () => {
    signedIn();
    mockCapabilities(['iam.admin.overview.read']);
    expect(await go('/diagnostics')).toBe('forbidden');
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
