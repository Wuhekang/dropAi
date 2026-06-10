import { createRouter, createWebHistory } from 'vue-router'
import RewriteIndex from '../views/Rewrite/index.vue'
import LoginIndex from '../views/Login/index.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/rewrite'
    },
    {
      path: '/login',
      name: 'Login',
      component: LoginIndex
    },
    {
      path: '/rewrite',
      name: 'Rewrite',
      component: RewriteIndex
    }
  ]
})

router.beforeEach((to) => {
  const loggedIn = Boolean(localStorage.getItem('dropai_token'))
  if (to.path !== '/login' && !loggedIn) return '/login'
  if (to.path === '/login' && loggedIn) return '/rewrite'
})

export default router
