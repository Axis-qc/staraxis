<script setup lang="ts">
/**
 * @file MainMenuView.vue
 *
 * @description
 * StarAxis 的 Web 主菜单视图（/main-menu）。提供一个 HUD 风格的主菜单入口，并在背景渲染动态星空。
 * 当前各菜单项多为占位：点击后通过对话框提示“开发中”。
 *
 * @usage
 * - 通过 Vue Router 路由进入（例如 `router.push('/main-menu')`）。
 * - 依赖 i18n 文案：本页面所有可见文本均通过 `t('...')` 获取。
 *
 * @provides
 * - **动态背景**：使用 `useStarfield(canvasRef)` 在 `<canvas>` 上绘制星空。
 * - **主菜单交互**：按钮列表（新游戏/加载/多人/舰船设计器/设置/返回）。
 * - **对话框提示**：`SciFiDialog` 用于展示占位功能提示（developing）。
 *
 * @api
 * - **无直接 HTTP/WS 调用**：本视图本身不请求后端。
 * - **间接依赖**：`useStarfield` 可能使用 `requestAnimationFrame` 等浏览器 API 驱动动画。
 *
 * @resources
 * - **组件**：`SciFiDialog`（`@/components/SciFiDialog.vue`）。
 * - **组合式函数**：`useStarfield`（`../composables/useStarfield`）。
 * - **字体**：Orbitron（通过 CSS `@import` 从 Google Fonts 引入）。
 * - **主题变量**：使用全局 CSS 变量（如 `--background-color`、`--text-color`、`--glow-color`）。
 *
 * @potential_issues
 * - **性能**：Canvas 星空动画在低端设备上可能增加 CPU/GPU 占用；可考虑在设置中提供开关。
 * - **字体加载**：CSS `@import` 可能阻塞/重复加载；建议后续迁移到全局一次性加载或本地化资源。
 * - **布局溢出**：如果使用 `100vw/100vh` + 大 padding，可能在某些环境触发溢出；目前已通过页面自身 `overflow: hidden` 控制。
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import SciFiDialog from '@/components/SciFiDialog.vue'
import { useStarfield } from '../composables/useStarfield'

const { t } = useI18n()
const router = useRouter()

// --- Dialog State ---
const dialogVisible = ref(false)
const dialogTitle = ref('')
const dialogMessage = ref('')

function developing(label: string) {
  dialogTitle.value = t('dialog.developing.title')
  dialogMessage.value = `${t('dialog.developing.text')}: ${label}`
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
}

// --- Canvas Background ---
const canvasRef = ref<HTMLCanvasElement | null>(null)
useStarfield(canvasRef)

</script>

<template>
  <div class="main-menu-page">
    <canvas ref="canvasRef" class="background-canvas"></canvas>

    <div class="hud">
      <header class="hud-header">
        <h1 class="game-title">{{ t('app.title') }}</h1>
        <p class="game-subtitle">{{ t('mainMenu.web.subtitle') }}</p>
      </header>

      <nav class="hud-nav">
        <button class="menu-item" @click="developing(t('mainMenu.newGame'))">
          <span class="bullet" />
          <span>{{ t('mainMenu.newGame') }}</span>
        </button>

        <button class="menu-item" @click="developing(t('mainMenu.loadGame'))">
          <span class="bullet" />
          <span>{{ t('mainMenu.loadGame') }}</span>
          <span class="tag-developing">{{ t('mainMenu.tag.developing') }}</span>
        </button>

        <button class="menu-item" @click="developing(t('mainMenu.multiplayer'))">
          <span class="bullet" />
          <span>{{ t('mainMenu.multiplayer') }}</span>
          <span class="tag-developing">{{ t('mainMenu.tag.developing') }}</span>
        </button>

        <button class="menu-item" @click="developing(t('mainMenu.shipDesigner'))">
          <span class="bullet" />
          <span>{{ t('mainMenu.shipDesigner') }}</span>
          <span class="tag-developing">{{ t('mainMenu.tag.developing') }}</span>
        </button>

        <button class="menu-item" @click="developing(t('mainMenu.settings'))">
          <span class="bullet" />
          <span>{{ t('mainMenu.settings') }}</span>
        </button>

        <button class="menu-item" @click="router.push('/')">
          <span class="bullet" />
          <span>{{ t('mainMenu.exit') }}</span>
        </button>
      </nav>
    </div>

    <div class="version-info">v0.0.1</div>

    <SciFiDialog
      :visible="dialogVisible"
      :title="dialogTitle"
      :message="dialogMessage"
      @close="closeDialog"
    />
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Orbitron:wght@400;700&display=swap');


/* --- Entry Animations --- */
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.main-menu-page {
  display: flex;
  height: 100vh;
  width: 100vw;
  background-color: transparent; /* Let canvas show through */
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  overflow: hidden;
  position: relative;
  padding: 4rem;
  box-sizing: border-box;
  animation: fadeIn 1s ease-out;
}

.background-canvas {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: -1;
  background-color: var(--background-color); /* Fallback background */
}

.hud {
  position: relative;
  z-index: 1;
  width: 400px;
  height: 100%;
}

/* Staggered animation for HUD elements */
.hud-header, .menu-item {
  opacity: 0; /* Start hidden */
  animation: fadeInUp 0.6s ease-out forwards;
}

.hud-header {
  animation-delay: 0.5s;
}

/* Stagger the menu items */
.menu-item:nth-child(1) { animation-delay: 0.8s; }
.menu-item:nth-child(2) { animation-delay: 0.9s; }
.menu-item:nth-child(3) { animation-delay: 1.0s; }
.menu-item:nth-child(4) { animation-delay: 1.1s; }
.menu-item:nth-child(5) { animation-delay: 1.2s; }
.menu-item:nth-child(6) { animation-delay: 1.3s; }


.hud-header {
  margin-bottom: 4rem;
}

.game-title {
  font-size: 3.5rem;
  font-weight: 700;
  color: var(--text-color-hover);
  text-shadow: 0 0 8px var(--glow-color), 0 0 15px var(--glow-color);
  margin: 0;
}

.game-subtitle {
  font-size: 1rem;
  color: var(--glow-color);
  margin: 0;
  letter-spacing: 3px;
  text-transform: uppercase;
}

.hud-nav {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.menu-item {
  background: transparent;
  border: none;
  color: var(--text-color);
  padding: 10px 0;
  font-family: 'Orbitron', sans-serif;
  font-size: 1.3rem;
  cursor: pointer;
  transition: color 0.3s ease, transform 0.3s ease;
  text-transform: uppercase;
  letter-spacing: 2px;
  display: flex;
  align-items: center;
  gap: 15px;
  text-align: left;
}

.menu-item:hover {
  color: var(--text-color-hover);
  transform: translateX(10px);
}

.menu-item:hover .bullet {
  background-color: var(--glow-color);
  box-shadow: 0 0 5px var(--glow-color), 0 0 10px var(--glow-color);
}

.bullet {
  width: 8px;
  height: 8px;
  background-color: var(--text-color);
  border-radius: 50%;
  transition: all 0.3s ease;
}

.tag-developing {
  font-size: 0.7rem;
  background-color: color-mix(in srgb, var(--glow-color) 24%, rgba(255, 255, 255, 0.05));
  color: var(--text-color-hover);
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: bold;
  margin-left: auto;
  align-self: center;
}

.version-info {
  position: absolute;
  bottom: 2rem;
  right: 2rem;
  font-size: 0.8rem;
  color: color-mix(in srgb, var(--text-color) 42%, transparent);
  letter-spacing: 1px;
  z-index: 1;
  opacity: 0;
  animation: fadeIn 1s ease-out 1.5s forwards;
}
</style>