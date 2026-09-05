import axios, { type AxiosInstance, type AxiosError } from 'axios';
import { ElMessage } from 'element-plus';
import {
  AdministrationApi,
  AuthenticationApi,
  AuthorizationApi,
  CapabilitiesApi,
  ManagementAuditApi,
  ManagementIdentityApi,
  ManagementOverviewApi,
  ManagementPermissionsApi,
  ManagementProfilesApi,
  ManagementSessionsApi,
  ManagementTemplatesApi,
  ManagementUsersApi,
  SessionsApi,
} from './generated/api';

/**
 * Single shared HTTP client. Every request carries the Bearer token; every
 * response 401 clears the local session and redirects to /login.
 */

export function apiBaseUrl(): string {
  const configured = (import.meta.env.VITE_IAM_API_BASE_URL as string | undefined) ?? '';
  return configured.endsWith('/') ? configured.slice(0, -1) : configured;
}

const http: AxiosInstance = axios.create({
  baseURL: apiBaseUrl(),
  timeout: 15000,
});

http.interceptors.request.use((config) => {
  const raw = sessionStorage.getItem('iam.admin.session');
  if (raw) {
    try {
      const token = (JSON.parse(raw) as { accessToken?: string }).accessToken;
      if (token) config.headers.Authorization = `Bearer ${token}`;
    } catch {
      sessionStorage.removeItem('iam.admin.session');
    }
  }
  return config;
});

http.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    const status = error.response?.status;
    const url = error.config?.url ?? '';
    const isLogin = url.includes('/iam/auth/login');
    if (status === 401 && !isLogin) {
      handleUnauthorized();
    }
    return Promise.reject(error);
  },
);

/**
 * Clears the stored opaque token and redirects to the login page after a 401.
 * Kept as a named export so the behavior is directly testable.
 */
export function handleUnauthorized(redirect: (url: string) => void = (url) => window.location.assign(url)): void {
  sessionStorage.removeItem('iam.admin.session');
  if (!window.location.pathname.startsWith('/login')) {
    redirect('/login?expired=1');
  }
}

/**
 * Translates a Spring ProblemDetail / plain error payload into a user message.
 */
export function errorText(error: unknown): string {
  const axiosError = error as AxiosError<{ title?: string; detail?: string; message?: string }>;
  const data = axiosError?.response?.data;
  if (data?.detail) return data.detail;
  if (data?.title) return data.title;
  if (data?.message) return data.message;
  if (axiosError?.message) return axiosError.message;
  return '请求失败，请稍后重试';
}

export function notifyError(error: unknown): void {
  ElMessage.error(errorText(error));
}

const auth = new AuthenticationApi(undefined, '', http);
const authorization = new AuthorizationApi(undefined, '', http);
const capabilities = new CapabilitiesApi(undefined, '', http);
const mySessions = new SessionsApi(undefined, '', http);
const admin = new AdministrationApi(undefined, '', http);
const adminUsers = new ManagementUsersApi(undefined, '', http);
const adminIdentities = new ManagementIdentityApi(undefined, '', http);
const adminPermissions = new ManagementPermissionsApi(undefined, '', http);
const adminTemplates = new ManagementTemplatesApi(undefined, '', http);
const adminProfiles = new ManagementProfilesApi(undefined, '', http);
const adminSessions = new ManagementSessionsApi(undefined, '', http);
const adminAudit = new ManagementAuditApi(undefined, '', http);
const adminOverview = new ManagementOverviewApi(undefined, '', http);

export {
  admin,
  adminAudit,
  adminIdentities,
  adminOverview,
  adminPermissions,
  adminProfiles,
  adminSessions,
  adminTemplates,
  adminUsers,
  auth,
  authorization,
  capabilities,
  mySessions,
  http,
};
