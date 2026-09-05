import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { useAppStore } from '@/stores/app';

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
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/dashboard/DashboardView.vue') },
      { path: 'users', name: 'users', component: () => import('@/views/users/UsersView.vue') },
      { path: 'users/:id', name: 'user-detail', component: () => import('@/views/users/UserDetailView.vue') },
      { path: 'permissions', name: 'permissions', component: () => import('@/views/permissions/PermissionsView.vue') },
      { path: 'templates', name: 'templates', component: () => import('@/views/templates/TemplatesView.vue') },
      { path: 'templates/:id', name: 'template-detail', component: () => import('@/views/templates/TemplateDetailView.vue') },
      { path: 'profiles', name: 'profiles', component: () => import('@/views/profiles/ProfilesView.vue') },
      { path: 'profiles/:id', name: 'profile-detail', component: () => import('@/views/profiles/ProfileDetailView.vue') },
      { path: 'sessions', name: 'sessions', component: () => import('@/views/sessions/SessionsView.vue') },
      { path: 'audit', name: 'audit', component: () => import('@/views/audit/AuditView.vue') },
      { path: 'diagnostics', name: 'diagnostics', component: () => import('@/views/diagnostics/DiagnosticsView.vue') },
      { path: 'account', name: 'account', component: () => import('@/views/account/AccountView.vue'), meta: { self: true } },
    ],
  },
  { path: '/403', name: 'forbidden', component: () => import('@/views/error/ForbiddenView.vue'), meta: { public: true } },
  { path: '/not-found', name: 'not-found', component: () => import('@/views/error/NotFoundView.vue'), meta: { public: true } },
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
  // Warm the capabilities once; the result only drives UI, never security.
  if (!authStore.capabilities && !authStore.principal) {
    try {
      await authStore.loadMe();
    } catch {
      authStore.clearLocal();
      return { name: 'login' };
    }
  }
  if (!authStore.capabilities) {
    try {
      const value = await authStore.loadCapabilities();
      useAppStore().setPermissions(value ? Array.from(value.permissions ?? []) : []);
    } catch {
      // Unknown capabilities mean the menu degrades to /account only.
      useAppStore().setPermissions([]);
    }
  }
  return true;
});

export default router;
