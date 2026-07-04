import { createRouter, createWebHistory } from 'vue-router'
import RewriteIndex from '../views/Rewrite/index.vue'
import LoginIndex from '../views/Login/index.vue'
import DashboardIndex from '../views/Dashboard/index.vue'
import NewProjectIndex from '../views/NewProject/index.vue'
import PointsAdmin from '../views/PointsAdmin.vue'
import RechargeIndex from '../views/Recharge/index.vue'
import ComputerGenerator from '../views/ComputerGenerator/index.vue'

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
    },
    {
      path: '/computer-generator',
      name: 'ComputerGenerator',
      component: ComputerGenerator
    },
    {
      path: '/points-admin',
      name: 'PointsAdmin',
      component: PointsAdmin
    },
    {
      path: '/recharge',
      name: 'Recharge',
      component: RechargeIndex
    }
  ]
})

router.beforeEach((to) => {
  const loggedIn = Boolean(sessionStorage.getItem('dropai_token'))
  if (to.path !== '/login' && !loggedIn) return '/login'
  if (to.path === '/login' && loggedIn) return '/dashboard'
  if (to.path === '/points-admin' && sessionStorage.getItem('dropai_role')?.toLowerCase() !== 'admin') {
    return '/dashboard'
  }
})

export default router
