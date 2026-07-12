import { createRouter, createWebHistory } from 'vue-router'
import HomeIndex from '../views/Home/index.vue'
import RewriteIndex from '../views/Rewrite/index.vue'
import LoginIndex from '../views/Login/index.vue'
import DashboardIndex from '../views/Dashboard/index.vue'
import NewProjectIndex from '../views/NewProject/index.vue'
import ResultIndex from '../views/Result/index.vue'
import PointsAdmin from '../views/PointsAdmin.vue'
import RechargeIndex from '../views/Recharge/index.vue'
import ComputerGenerator from '../views/ComputerGenerator/index.vue'
import WritingGenerator from '../views/WritingGenerator/index.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'Home',
      component: HomeIndex
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
      path: '/result',
      name: 'Result',
      component: ResultIndex
    },
    {
      path: '/computer-generator',
      name: 'ComputerGenerator',
      alias: '/project-generator',
      component: ComputerGenerator
    },
    {
      path: '/writing-generator',
      name: 'WritingGenerator',
      component: WritingGenerator
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
  if (!['/', '/login'].includes(to.path) && !loggedIn) return '/login'
  if (to.path === '/login' && loggedIn) return '/dashboard'
  if (to.path === '/points-admin' && sessionStorage.getItem('dropai_role')?.toLowerCase() !== 'admin') {
    return '/dashboard'
  }
})

export default router
