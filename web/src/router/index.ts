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
            path: '/settings',
            name: 'settings',
            component: () => import('../views/SettingsView.vue'),
        },
        {
            path: '/main-menu',
            name: 'main-menu',
            component: () => import('../views/MainMenuView.vue'),
        },

        {
            path: '/ship-designer',
            name: 'ship-designer',
            component: () => import('../views/ShipDesignerView.vue'),
        },
        {
            path: '/ship-designer/dev',
            name: 'ship-designer-dev',
            component: () => import('../views/ShipDesignerDevView.vue'),
        },
        {
            path: '/worlds',
            name: 'worlds',
            component: () => import('../views/WorldSavesView.vue'),
            meta: { requiresAuth: true, viewMode: 'worlds' },
        },
        {
            path: '/load-game',
            name: 'load-game',
            component: () => import('../views/WorldSavesView.vue'),
            meta: { requiresAuth: true, viewMode: 'saves' },
        },
        {
            path: '/multiplayer',
            name: 'multiplayer',
            component: () => import('../views/DevelopingView.vue'),
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
