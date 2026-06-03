import { createRouter, createWebHistory } from 'vue-router'
import RewriteIndex from '../views/Rewrite/index.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      redirect: '/rewrite'
    },
    {
      path: '/rewrite',
      name: 'Rewrite',
      component: RewriteIndex
    }
  ]
})

export default router
