import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/projects' },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/projects', name: 'projects', component: () => import('@/views/ProjectListView.vue') },
    { path: '/users', name: 'users', component: () => import('@/views/UserManageView.vue') },
    { path: '/rbac', name: 'rbac', component: () => import('@/views/RbacView.vue') },
    { path: '/profile', name: 'profile', component: () => import('@/views/ProfileView.vue') },
    {
      path: '/projects/:projectId',
      component: () => import('@/views/ProjectLayout.vue'),
      children: [
        { path: '', redirect: 'overview' },
        { path: 'overview', name: 'overview', component: () => import('@/views/OverviewView.vue') },
        { path: 'requirements', name: 'requirements', component: () => import('@/views/RequirementsView.vue') },
        { path: 'tasks', name: 'tasks', component: () => import('@/views/TasksView.vue') },
        { path: 'defects', name: 'defects', component: () => import('@/views/DefectManageView.vue') },
        { path: 'defects/:issueId', name: 'defect-detail', component: () => import('@/views/DefectDetailView.vue') },
        { path: 'iterations', name: 'iterations', component: () => import('@/views/IterationsView.vue') },
        { path: 'milestones', name: 'milestones', component: () => import('@/views/MilestonesView.vue') },
        { path: 'risks', name: 'risks', component: () => import('@/views/RisksView.vue') },
        { path: 'metrics', name: 'metrics', component: () => import('@/views/MetricsView.vue') },
        { path: 'board', name: 'board', component: () => import('@/views/BoardView.vue') },
        { path: 'table', name: 'table', component: () => import('@/views/TableView.vue') },
        { path: 'settings', name: 'settings', component: () => import('@/views/SettingsView.vue') }
      ]
    },
    // 兜底路由：未匹配任何规则时渲染 404 页，避免直接白屏
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') }
  ]
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  userStore.restore()
  if (to.path !== '/login' && !userStore.isLoggedIn) {
    return '/login'
  }
  // 管理员判定与后端口径一致：由登录接口下发的 isAdmin 决定（is_admin 字段或种子管理员）
  if ((to.path === '/users' || to.path === '/rbac') && !userStore.isAdmin) {
    return '/projects'
  }
  return true
})

export default router
