<script setup lang="ts">
import { onMounted } from 'vue'
import { RouterView } from 'vue-router'
import ThemePicker from './components/ThemePicker.vue'
import DevTooltip from './components/DevTooltip.vue'
import { useDevTooltip } from './composables/useDevTooltip'
import { wsClient } from './services/ws'

const tooltip = useDevTooltip()

// Establish the global WebSocket connection when the app mounts.
onMounted(() => {
  wsClient.connect()
})
</script>

<template>
  <RouterView />
  <ThemePicker />
  <DevTooltip 
    :visible="tooltip.state.visible"
    :message="tooltip.state.message"
    :x="tooltip.state.x"
    :y="tooltip.state.y"
  />
</template>
