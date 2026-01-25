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
            path: '/settings',
            name: 'settings',
            // route level code-splitting
            // this generates a separate chunk (About.[hash].js) for this route
            // which is lazy-loaded when the route is visited.
            component: () => import('../views/SettingsView.vue'),
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
