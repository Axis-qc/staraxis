<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import ThemePicker from './components/ThemePicker.vue'
import DevTooltip from './components/DevTooltip.vue'
import AiAssistantFloatingBall from './components/AiAssistantFloatingBall.vue'
import { useDevTooltip } from './composables/useDevTooltip'

import { useAuthStore } from './stores/auth'

const tooltip = useDevTooltip()
const auth = useAuthStore()

// Establish the global WebSocket connection when the app mounts if logged in.
onMounted(() => {
  if (auth.isLoggedIn) {
    auth.setAuth(auth.$state) // 触发 setAuth 中的 connect 逻辑喵
  }
})
</script>

<template>
  <RouterView />
  <ThemePicker />
  <DevTooltip :visible="tooltip.state.visible" :message="tooltip.state.message" :x="tooltip.state.x"
    :y="tooltip.state.y" />
  <AiAssistantFloatingBall />
</template>
