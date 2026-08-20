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
    }
  ]
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  userStore.restore()
  if (to.path !== '/login' && !userStore.token) {
    return '/login'
  }
  return true
})

export default router
