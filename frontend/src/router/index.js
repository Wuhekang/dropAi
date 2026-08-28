import { createRouter, createWebHistory } from 'vue-router'
import HomeIndex from '../views/Home/index.vue'
import RewriteIndex from '../views/Rewrite/index.vue'
import LoginIndex from '../views/Login/index.vue'
import DashboardIndex from '../views/Dashboard/index.vue'
import ResultIndex from '../views/Result/index.vue'
import PointsAdmin from '../views/PointsAdmin.vue'
import RechargeIndex from '../views/Recharge/index.vue'
import PointsCenter from '../views/PointsCenter/index.vue'
import ProjectCenter from '../views/ProjectCenter/index.vue'
import ComputerGenerator from '../views/ComputerGenerator/index.vue'
import ReferenceSearch from '../views/ReferenceSearch/index.vue'
import LiteratureCenter from '../views/LiteratureCenter/index.vue'
import WritingCenterV2 from '../views/WritingCenterV2/index.vue'
import WritingOutlineV2 from '../views/WritingOutlineV2/index.vue'
import WritingGenerationV2 from '../views/WritingGenerationV2/index.vue'
import WritingExportV2 from '../views/WritingExportV2/index.vue'
import WritingMaterialsV2 from '../views/WritingMaterialsV2/index.vue'
import PptGenerator from '../views/PptGenerator/index.vue'
import WordFormatter from '../views/WordFormatter/index.vue'
import SchoolStatistics from '../views/SchoolStatistics/index.vue'
import DiagramStudio from '../views/Diagram/index.vue'
import MechanicalDesign from '../views/MechanicalDesign/index.vue'
import { getAuthRole, isAuthenticated } from '../utils/authStorage'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/school-admin', name: 'SchoolAdmin', redirect: '/points-admin?tab=schools'
    },
    {
      path: '/school-statistics', name: 'SchoolStatistics', component: SchoolStatistics
    },
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
      path: '/projects',
      name: 'ProjectCenter',
      component: ProjectCenter
    },
    {
      path: '/rewrite',
      name: 'Rewrite',
      component: RewriteIndex
    },
    {
      path: '/mechanical-design',
      name: 'MechanicalDesign',
      component: MechanicalDesign
    },
    {
      path: '/new-project',
      redirect: '/mechanical-design'
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
      path: '/ppt-generator',
      name: 'PptGenerator',
      component: PptGenerator
    },
    {
      path: '/word-formatter',
      name: 'WordFormatter',
      component: WordFormatter
    },
    {
      path: '/writing-generator',
      redirect: '/writing'
    },
    {
      path: '/writing',
      name: 'WritingCenterV2',
      component: WritingCenterV2
    },
    {
      path: '/literature',
      name: 'LiteratureCenter',
      component: LiteratureCenter
    },
    {
      path: '/reference-search',
      name: 'ReferenceSearch',
      component: ReferenceSearch
    },
    {
      path: '/writing-generator/materials',
      name: 'WritingMaterialsV2',
      component: WritingMaterialsV2
    },
    {
      path: '/writing-generator/outline',
      name: 'WritingOutlineV2',
      component: WritingOutlineV2
    },
    {
      path: '/writing-generator/generate',
      name: 'WritingGenerationV2',
      component: WritingGenerationV2
    },
    {
      path: '/writing-generator/export',
      name: 'WritingExportV2',
      component: WritingExportV2
    },
    {
      path: '/drawing', name: 'DiagramStudio', component: DiagramStudio
    },
    {
      path: '/points-admin',
      name: 'PointsAdmin',
      component: PointsAdmin
    },
    {
      path: '/account',
      name: 'AccountCenter',
      component: RechargeIndex
    },
    {
      path: '/points',
      name: 'PointsCenter',
      alias: '/recharge',
      component: PointsCenter
    }
  ]
})

router.beforeEach((to) => {
  const loggedIn = isAuthenticated()
  const role = getAuthRole()?.toUpperCase()
  if (!['/', '/login'].includes(to.path) && !loggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (to.path === '/login' && loggedIn) {
    const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : ''
    if (role !== 'SCHOOL_VIEWER' && redirect.startsWith('/') && !redirect.startsWith('//')) return redirect
    return role === 'SCHOOL_VIEWER' ? '/school-statistics' : '/dashboard'
  }
  if (role === 'SCHOOL_VIEWER' && to.path !== '/school-statistics') return '/school-statistics'
  if (to.path === '/school-statistics' && role !== 'SCHOOL_VIEWER') return '/dashboard'
  if (to.path === '/points-admin' && getAuthRole()?.toLowerCase() !== 'admin') {
    return '/dashboard'
  }
  if (to.path === '/school-admin' && role !== 'ADMIN') return '/dashboard'
})

export default router
