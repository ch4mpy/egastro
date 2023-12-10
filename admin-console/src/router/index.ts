import { createRouter, createWebHistory } from 'vue-router'
import RealmsListView from '../views/RealmsListView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: RealmsListView
    },
    {
      path: '/me',
      name: 'account',
      component: () => import('../views/AccountView.vue')
    },
    {
      path: '/realms/:realm',
      name: 'realm-details',
      component: () => import('../views/RealmDetailsView.vue')
    }
  ]
})

export default router
