import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/projects' },
    { path: '/login', name: 'login', component: () => import('@/views/LoginView.vue') },
    { path: '/projects', name: 'projects', component: () => import('@/views/ProjectListView.vue') },
    { path: '/users', name: 'users', component: () => import('@/views/UserManageView.vue') },
    {
      path: '/projects/:projectId/board',
      name: 'board',
      component: () => import('@/views/BoardView.vue')
    },
    {
      path: '/projects/:projectId/table',
      name: 'table',
      component: () => import('@/views/TableView.vue')
    },
    {
      path: '/projects/:projectId/defects/:issueId?',
      name: 'defects',
      component: () => import('@/views/DefectDetailView.vue')
    },
    {
      path: '/projects/:projectId/dashboard',
      name: 'dashboard',
      component: () => import('@/views/DashboardView.vue')
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
