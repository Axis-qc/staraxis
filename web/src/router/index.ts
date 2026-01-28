import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

const router = createRouter({
    history: createWebHistory(import.meta.env.BASE_URL),
    routes: [
        {
            path: '/',
            name: 'home',
            component: HomeView,
        },
        {
            path: '/main-menu',
            name: 'main-menu',
            component: () => import('../views/MainMenuView.vue'),
        },

        {
            path: '/ship-designer',
            name: 'ship-designer',
            component: () => import('../views/DevelopingView.vue'),
        },
        {
            path: '/load-game',
            name: 'load-game',
            component: () => import('../views/DevelopingView.vue'),
        },
        {
            path: '/multiplayer',
            name: 'multiplayer',
            component: () => import('../views/DevelopingView.vue'),
        },
    ],
})

export default router
