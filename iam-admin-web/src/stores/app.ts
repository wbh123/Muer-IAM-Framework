import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

export interface MenuItem {
  key: string;
  path: string;
  label: string;
  permissions?: string[];
}

const ALL_MENUS: MenuItem[] = [
  { key: 'dashboard', path: '/dashboard', label: '概览', permissions: ['iam.admin.overview.read'] },
  { key: 'users', path: '/users', label: '用户', permissions: ['iam.admin.user.read'] },
  { key: 'permissions', path: '/permissions', label: 'Permission', permissions: ['iam.admin.permission.read'] },
  { key: 'templates', path: '/templates', label: 'Permission Template', permissions: ['iam.admin.template.read'] },
  { key: 'profiles', path: '/profiles', label: 'Profile', permissions: ['iam.admin.profile.read'] },
  { key: 'sessions', path: '/sessions', label: 'Session', permissions: ['iam.admin.session.read'] },
  { key: 'audit', path: '/audit', label: 'Audit', permissions: ['iam.admin.audit.read'] },
  { key: 'diagnostics', path: '/diagnostics', label: 'Diagnostics', permissions: ['iam.admin.diagnostics'] },
];

export const useAppStore = defineStore('app', () => {
  const granted = ref<string[]>([]);

  function setPermissions(permissions: string[]) {
    granted.value = [...permissions];
  }

  const menus = computed<MenuItem[]>(() =>
    ALL_MENUS.filter(
      (menu) => !menu.permissions || menu.permissions.some((permission) => granted.value.includes(permission)),
    ),
  );

  const can = (permission: string) => granted.value.includes(permission);

  return { granted, setPermissions, menus, can };
});
