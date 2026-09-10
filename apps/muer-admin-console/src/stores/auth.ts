import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { auth, capabilities } from '@/api/client';
import type { LoginResponse, PrincipalCapabilitiesResponse, PrincipalResponse } from '@/api/generated/api';

export interface StoredSession {
  accessToken: string;
  sessionId: string;
  principal: PrincipalResponse;
}

const TOKEN_KEY = 'iam.admin.session';

function readStored(): StoredSession | null {
  const raw = sessionStorage.getItem(TOKEN_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as StoredSession;
  } catch {
    sessionStorage.removeItem(TOKEN_KEY);
    return null;
  }
}

export const useAuthStore = defineStore('auth', () => {
  const stored = ref<StoredSession | null>(readStored());
  const principal = ref<PrincipalResponse | null>(stored.value?.principal ?? null);
  const caps = ref<PrincipalCapabilitiesResponse | null>(null);

  const isAuthenticated = computed(() => stored.value !== null);

  function persist(session: StoredSession) {
    stored.value = session;
    principal.value = session.principal;
    sessionStorage.setItem(TOKEN_KEY, JSON.stringify(session));
  }

  async function login(username: string, password: string, clientType: string): Promise<LoginResponse> {
    const response = await auth.login({
      loginRequest: { username, password, clientType },
    });
    const data = response.data;
    persist({
      accessToken: data.accessToken,
      sessionId: data.sessionId,
      principal: data.principal,
    });
    return data;
  }

  async function loadMe(): Promise<PrincipalResponse> {
    const me = (await auth.getCurrentPrincipal()).data;
    principal.value = me;
    return me;
  }

  async function loadCapabilities(): Promise<PrincipalCapabilitiesResponse> {
    const value = (await capabilities.getCurrentCapabilities()).data;
    caps.value = value;
    return value;
  }

  async function logout(): Promise<void> {
    try {
      await auth.logout();
    } catch {
      // The server session may already be gone; local clearing is mandatory anyway.
    }
    clearLocal();
  }

  function clearLocal() {
    stored.value = null;
    principal.value = null;
    caps.value = null;
    sessionStorage.removeItem(TOKEN_KEY);
  }

  return {
    stored,
    principal,
    capabilities: caps,
    isAuthenticated,
    persist,
    login,
    loadMe,
    loadCapabilities,
    logout,
    clearLocal,
  };
});
