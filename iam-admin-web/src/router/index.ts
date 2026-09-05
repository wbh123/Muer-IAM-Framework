import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { useAppStore } from '@/stores/app';

export interface RoutePermissionMeta {
  /** Page-level capability hint. UI-only; the AuthorizationEngine stays the boundary. */
  permission?: string;
  public?: boolean;
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { public: true },
  },
  {
    path: '/',
    component: () => import('@/layouts/AdminLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { permission: 'iam.admin.overview.read' },
      },
      {
        path: 'users',
        name: 'users',
        component: () => import('@/views/users/UsersView.vue'),
        meta: { permission: 'iam.admin.user.read' },
      },
      {
        path: 'users/:id',
        name: 'user-detail',
        component: () => import('@/views/users/UserDetailView.vue'),
        meta: { permission: 'iam.admin.user.read' },
      },
      {
        path: 'permissions',
        name: 'permissions',
        component: () => import('@/views/permissions/PermissionsView.vue'),
        meta: { permission: 'iam.admin.permission.read' },
      },
      {
        path: 'templates',
        name: 'templates',
        component: () => import('@/views/templates/TemplatesView.vue'),
        meta: { permission: 'iam.admin.template.read' },
      },
      {
        path: 'templates/:id',
        name: 'template-detail',
        component: () => import('@/views/templates/TemplateDetailView.vue'),
        meta: { permission: 'iam.admin.template.read' },
      },
      {
        path: 'profiles',
        name: 'profiles',
        component: () => import('@/views/profiles/ProfilesView.vue'),
        meta: { permission: 'iam.admin.profile.read' },
      },
      {
        path: 'profiles/:id',
        name: 'profile-detail',
        component: () => import('@/views/profiles/ProfileDetailView.vue'),
        meta: { permission: 'iam.admin.profile.read' },
      },
      {
        path: 'sessions',
        name: 'sessions',
        component: () => import('@/views/sessions/SessionsView.vue'),
        meta: { permission: 'iam.admin.session.read' },
      },
      {
        path: 'audit',
        name: 'audit',
        component: () => import('@/views/audit/AuditView.vue'),
        meta: { permission: 'iam.admin.audit.read' },
      },
      {
        path: 'diagnostics',
        name: 'diagnostics',
        component: () => import('@/views/diagnostics/DiagnosticsView.vue'),
        meta: { permission: 'iam.admin.diagnostics' },
      },
      // /account only requires a session; it never needs an iam.admin.* permission.
      {
        path: 'account',
        name: 'account',
        component: () => import('@/views/account/AccountView.vue'),
      },
    ],
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/error/ForbiddenView.vue'),
    meta: { public: true },
  },
  {
    path: '/not-found',
    name: 'not-found',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { public: true },
  },
  { path: '/:pathMatch(.*)*', redirect: '/not-found' },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async (to) => {
  const authStore = useAuthStore();
  if (to.meta.public) return true;
  if (!authStore.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } };
  }
  // Resolve the principal once; a real 401 here means the session is gone.
  if (!authStore.principal) {
    try {
      await authStore.loadMe();
    } catch {
      authStore.clearLocal();
      return { name: 'login' };
    }
  }
  // Warm capabilities once. Failures degrade to an empty permission set (admin
  // pages → /403); /account stays reachable because it needs only a session.
  if (!authStore.capabilities) {
    try {
      const value = await authStore.loadCapabilities();
      useAppStore().setPermissions(value ? Array.from(value.permissions ?? []) : []);
    } catch {
      useAppStore().setPermissions([]);
    }
  }
  const permission = (to.meta as RoutePermissionMeta).permission;
  if (permission && !useAppStore().can(permission)) {
    return { name: 'forbidden' };
  }
  return true;
});

export default router;
