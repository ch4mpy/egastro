import { createRouter, createWebHistory } from 'vue-router'
import RestaurantsListViewVue from '../views/RestaurantsListView.vue'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: RestaurantsListViewVue
    },
    {
      path: '/me',
      name: 'account',
      component: () => import('../views/AccountView.vue')
    },
    {
      path: '/realms/:realm',
      name: 'realm-details',
      component: () => import('../views/RestaurantDetailsView.vue')
    }
  ]
})

export default router
