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
        baseUrl: 'https://github.com/wbh123/Muer-IAM-Framework/edit/main/apps/muer-docs-site',
      },
      components: {},
      sidebar: [
        {
          label: '简介',
          items: [
            { label: 'Muer 是什么', slug: 'intro/what-is-iam' },
            { label: '核心能力', slug: 'intro/core-capabilities' },
            { label: '适合哪些项目', slug: 'intro/use-cases' },
            { label: '架构概览', slug: 'intro/architecture' },
          ],
        },
        {
          label: '开始使用',
          items: [
            { label: '10~15 分钟快速开始', slug: 'getting-started/quick-start' },
            { label: '从零接入自己的项目', slug: 'getting-started/from-zero-tutorial' },
            { label: '安装与环境准备', slug: 'getting-started/installation' },
            { label: '基础配置', slug: 'getting-started/configuration' },
            { label: '第一次配置权限', slug: 'getting-started/define-permissions' },
            { label: '在控制台管理权限', slug: 'getting-started/permission-management' },
            { label: '手动部署', slug: 'getting-started/manual-deployment' },
            { label: '工程结构', slug: 'getting-started/project-structure' },
          ],
        },
        {
          label: '理解 Muer',
          collapsed: true,
          items: [
            {
              label: '四个核心心智模型',
              items: [
                { label: '身份与当前用户', slug: 'concepts/identity-principal' },
                { label: '用户如何获得权限', slug: 'concepts/how-permissions-work' },
                { label: '资源与权限范围', slug: 'concepts/resource-scope' },
                { label: '会话与权限失效', slug: 'concepts/session' },
              ],
            },
            {
              label: '深入：授权数据模型',
              collapsed: true,
              items: [
                { label: '权限（Permission）', slug: 'concepts/permission' },
                { label: '权限模板（Permission Template）', slug: 'concepts/permission-template' },
                { label: '用户授权身份（Profile）', slug: 'concepts/profile' },
                { label: '权限变更与旧 Token', slug: 'concepts/authorization-version' },
              ],
            },
          ],
        },
        {
          label: '认证',
          items: [
            { label: '登录流程', slug: 'authentication/login' },
            { label: '接入现有登录系统', slug: 'authentication/identity-authenticator' },
            { label: '客户端类型', slug: 'authentication/client-type' },
            { label: '获取当前用户', slug: 'authentication/current-user' },
            { label: '访问令牌', slug: 'authentication/opaque-token' },
          ],
        },
        {
          label: '授权',
          items: [
            { label: 'Muer 如何判断权限', slug: 'authorization/model' },
            { label: '保护业务接口', slug: 'authorization/require-permission' },
            { label: '把请求映射成业务资源', slug: 'authorization/mvc-resource-descriptor-resolver' },
            { label: '资源权限范围', slug: 'authorization/resource-scope' },
            { label: '授权决策流程', slug: 'authorization/authorization-engine' },
            { label: '自定义授权规则', slug: 'authorization/authorization-policy' },
          ],
        },
        {
          label: '会话',
          items: [
            { label: '切换授权身份', slug: 'profile-session/profile-switch' },
            { label: '会话管理', slug: 'profile-session/session-management' },
            { label: '撤销登录会话', slug: 'profile-session/session-revoke' },
          ],
        },
        {
          label: '诊断与审计',
          items: [
            { label: '为什么被拒绝：授权诊断', slug: 'diagnostics/authorization-diagnostics' },
            { label: '审计记录', slug: 'diagnostics/audit' },
            { label: '自定义认证与授权错误响应', slug: 'diagnostics/custom-error-handling' },
          ],
        },
        {
          label: '已有系统接入',
          items: [
            { label: '已有系统如何接入', slug: 'migration/overview' },
            { label: '影子模式：先验证、不拦截', slug: 'migration/shadow-mode' },
            { label: '用户与权限数据如何映射', slug: 'migration/data-projection' },
            { label: '接入失败如何回滚', slug: 'migration/rollback' },
          ],
        },
        {
          label: '运维与上线',
          items: [
            { label: 'MySQL 配置', slug: 'operations/mysql' },
            { label: 'Redis 配置', slug: 'operations/redis' },
            { label: '反向代理部署', slug: 'operations/reverse-proxy' },
            { label: '安全模型', slug: 'operations/security-model' },
            { label: '上线前检查清单', slug: 'operations/production-checklist' },
            { label: '可观测性', slug: 'operations/observability' },
            { label: '常见问题排查', slug: 'operations/troubleshooting' },
          ],
        },
        {
          label: '管理控制台',
          items: [
            { label: '管理控制台介绍', slug: 'management/console' },
            { label: '初始化第一个管理员', slug: 'management/bootstrap-first-admin' },
            { label: '第一次使用管理控制台', slug: 'management/first-admin-tutorial' },
            { label: '部署管理控制台', slug: 'management/deploy' },
          ],
        },
        {
          label: '接口参考',
          items: [
            { label: '配置项参考', slug: 'reference/configuration' },
            { label: 'Java 公共接口', slug: 'reference/public-api' },
            { label: 'HTTP 接口', slug: 'reference/http-api' },
            { label: '错误码', slug: 'reference/error-codes' },
            { label: '项目模块说明', slug: 'reference/modules' },
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
