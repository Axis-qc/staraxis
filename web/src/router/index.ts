import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'

import { useAuthStore } from '../stores/auth'

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

        {
            path: '/new-game/nation',
            name: 'new-game-nation',
            component: () => import('../views/NewGameNationSelectView.vue'),
            meta: { requiresAuth: true },
        },
        {
            path: '/new-game/world-settings',
            name: 'new-game-world-settings',
            component: () => import('../views/NewGameWorldSettingsView.vue'),
            meta: { requiresAuth: true },
        },
        {
            path: '/in-game',
            name: 'in-game',
            component: () => import('../views/InGameView.vue'),
            meta: { requiresAuth: true },
        },
    ],
})

router.beforeEach((to) => {
    const requiresAuth = !!to.meta?.requiresAuth
    if (!requiresAuth) {
        return true
    }

    const auth = useAuthStore()

    if (!auth.isLoggedIn) {
        return { name: 'home' }
    }

    return true
})

export default router
