import { createRouter, createWebHistory } from 'vue-router'
import MenuViewVue from '../views/MenuView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: MenuViewVue
    },
    {
      path: '/me',
      name: 'account',
      component: () => import('../views/AccountView.vue')
    }
  ]
})

export default router
