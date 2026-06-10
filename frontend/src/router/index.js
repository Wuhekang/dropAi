import { createRouter, createWebHistory } from 'vue-router'
import RewriteIndex from '../views/Rewrite/index.vue'
import LoginIndex from '../views/Login/index.vue'
import DashboardIndex from '../views/Dashboard/index.vue'
import NewProjectIndex from '../views/NewProject/index.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/dashboard'
    },
    {
      path: '/login',
      name: 'Login',
      component: LoginIndex
    },
    {
      path: '/dashboard',
      name: 'Dashboard',
      component: DashboardIndex
    },
    {
      path: '/rewrite',
      name: 'Rewrite',
      component: RewriteIndex
    },
    {
      path: '/new-project',
      name: 'NewProject',
      component: NewProjectIndex
    }
  ]
})

router.beforeEach((to) => {
  const loggedIn = Boolean(localStorage.getItem('dropai_token'))
  if (to.path !== '/login' && !loggedIn) return '/login'
  if (to.path === '/login' && loggedIn) return '/dashboard'
})

export default router
