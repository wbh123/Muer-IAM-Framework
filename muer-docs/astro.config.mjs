// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

const site = process.env.SITE_URL ?? 'https://muer.cloud';
const base = process.env.BASE_PATH ?? '/';

// https://astro.build/config
export default defineConfig({
  site,
  base,
  integrations: [
    starlight({
      title: 'Muer',
      description:
        'A lightweight identity and access management framework for modern applications.',
      editLink: {
        baseUrl: 'https://github.com/wbh123/Muer-IAM-Framework/edit/main/muer-docs',
      },
      components: {},
      sidebar: [
        {
          label: '简介',
          items: [
            { label: 'IAM 是什么', slug: 'intro/what-is-iam' },
            { label: '核心能力', slug: 'intro/core-capabilities' },
            { label: '适用场景', slug: 'intro/use-cases' },
            { label: '架构概览', slug: 'intro/architecture' },
          ],
        },
        {
          label: '开始使用',
          items: [
            { label: '10~15 分钟快速开始', slug: 'getting-started/quick-start' },
            { label: '从零接入 Muer', slug: 'getting-started/from-zero-tutorial' },
            { label: '安装', slug: 'getting-started/installation' },
            { label: '基础配置', slug: 'getting-started/configuration' },
            { label: '定义权限', slug: 'getting-started/define-permissions' },
            { label: '权限管理', slug: 'getting-started/permission-management' },
            { label: '手动部署', slug: 'getting-started/manual-deployment' },
            { label: '工程结构', slug: 'getting-started/project-structure' },
          ],
        },
        {
          label: '核心概念',
          items: [
            { label: 'Identity 与 Principal', slug: 'concepts/identity-principal' },
            { label: 'Permission', slug: 'concepts/permission' },
            { label: 'Permission Template', slug: 'concepts/permission-template' },
            { label: 'Profile', slug: 'concepts/profile' },
            { label: 'Resource 与 Scope', slug: 'concepts/resource-scope' },
            { label: 'Session', slug: 'concepts/session' },
            { label: 'Authorization Version', slug: 'concepts/authorization-version' },
          ],
        },
        {
          label: '认证',
          items: [
            { label: '登录', slug: 'authentication/login' },
            { label: 'IdentityAuthenticator', slug: 'authentication/identity-authenticator' },
            { label: 'Client Type', slug: 'authentication/client-type' },
            { label: '当前用户', slug: 'authentication/current-user' },
            { label: 'Opaque Token', slug: 'authentication/opaque-token' },
          ],
        },
        {
          label: '授权',
          items: [
            { label: '授权模型', slug: 'authorization/model' },
            { label: '@RequirePermission', slug: 'authorization/require-permission' },
            { label: 'MvcResourceDescriptorResolver', slug: 'authorization/mvc-resource-descriptor-resolver' },
            { label: 'Resource Scope', slug: 'authorization/resource-scope' },
            { label: 'AuthorizationEngine', slug: 'authorization/authorization-engine' },
            { label: 'AuthorizationPolicy', slug: 'authorization/authorization-policy' },
          ],
        },
        {
          label: 'Profile 与 Session',
          items: [
            { label: 'Profile', slug: 'profile-session/profile' },
            { label: 'Profile Switch', slug: 'profile-session/profile-switch' },
            { label: 'Session Management', slug: 'profile-session/session-management' },
            { label: 'Session Revoke', slug: 'profile-session/session-revoke' },
            { label: 'Authorization Version', slug: 'profile-session/authorization-version' },
          ],
        },
        {
          label: '诊断与审计',
          items: [
            { label: 'Authorization Diagnostics', slug: 'diagnostics/authorization-diagnostics' },
            { label: 'Audit', slug: 'diagnostics/audit' },
            { label: '自定义错误处理', slug: 'diagnostics/custom-error-handling' },
          ],
        },
        {
          label: '已有系统接入',
          items: [
            { label: 'Migration Overview', slug: 'migration/overview' },
            { label: 'Shadow Mode', slug: 'migration/shadow-mode' },
            { label: '数据投影', slug: 'migration/data-projection' },
            { label: 'Rollback', slug: 'migration/rollback' },
          ],
        },
        {
          label: '运维与安全',
          items: [
            { label: 'MySQL', slug: 'operations/mysql' },
            { label: 'Redis', slug: 'operations/redis' },
            { label: 'Reverse Proxy', slug: 'operations/reverse-proxy' },
            { label: 'Security Model', slug: 'operations/security-model' },
            { label: 'Production Checklist', slug: 'operations/production-checklist' },
            { label: '可观测性', slug: 'operations/observability' },
            { label: '排障', slug: 'operations/troubleshooting' },
          ],
        },
        {
          label: '管理控制台',
          items: [
            { label: 'Muer Admin Console', slug: 'management/console' },
            { label: '第一次使用管理控制台', slug: 'management/first-admin-tutorial' },
            { label: '安装与部署', slug: 'management/deploy' },
          ],
        },
        {
          label: '参考',
          items: [
            { label: 'Configuration', slug: 'reference/configuration' },
            { label: 'Public API', slug: 'reference/public-api' },
            { label: 'HTTP API', slug: 'reference/http-api' },
            { label: 'Error Codes', slug: 'reference/error-codes' },
            { label: 'Modules', slug: 'reference/modules' },
            { label: '术语表', slug: 'reference/glossary' },
          ],
        },
        {
          label: '资源',
          items: [
            { label: 'Example', slug: 'resources/example' },
            { label: 'FAQ', slug: 'resources/faq' },
            { label: 'Release Notes', slug: 'resources/release-notes' },
            { label: 'Changelog', slug: 'resources/changelog' },
            { label: 'Contributing', slug: 'resources/contributing' },
          ],
        },
      ],
    }),
  ],
});
