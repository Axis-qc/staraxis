<script setup lang="ts">
/**
 * ShipDesignerView.vue
 *
 * 文件作用：
 * - 舰船设计器视图（/ship-designer）。
 * - 提供玩家自定义舰船蓝图的 UI：左侧模块列表、中间设计画布、右侧数据面板、保存/读取按钮。
 * - 开发模式：读取纹理、设置模块功能坐标（引擎挂载点、开火挂载点、炮塔中心点）。
 *
 * 提供的接口 API：
 * - 占位按钮：保存蓝图、读取蓝图、开发模式切换。
 * - 布局组件：左侧模块列表、中间画布、右侧数据面板。
 *
 * 使用方式：
 * - 通过 Vue Router 路由进入（例如 `router.push('/ship-designer')`）。
 * - 依赖 i18n 文案：所有可见文本通过 `t('...')` 获取。
 *
 * 注意事项：
 * - 当前为 UI 占位，不包含实际功能逻辑。
 * - 开发模式仅用于内部调试，未来可配置化或隐藏。
 */
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()

function goBack() {
  router.push('/main-menu')
}

function saveDesign() {
  // 占位：保存蓝图逻辑
  console.log('Save design (placeholder)')
}

function loadDesign() {
  // 占位：读取蓝图逻辑
  console.log('Load design (placeholder)')
}

function goToDevMode() {
  router.push('/ship-designer/dev')
}
</script>

<template>
  <div class="ship-designer-page">
    <!-- 顶部工具栏 -->
    <header class="designer-header">
      <button class="back-btn" @click="goBack">
        <span class="icon">←</span>
        <span>{{ t('shipDesigner.back') }}</span>
      </button>

      <h1 class="page-title">{{ t('shipDesigner.title') }}</h1>

      <div class="header-actions">
        <button class="action-btn" @click="saveDesign">
          {{ t('shipDesigner.save') }}
        </button>
        <button class="action-btn" @click="loadDesign">
          {{ t('shipDesigner.load') }}
        </button>
        <button class="action-btn dev-mode-btn" @click="goToDevMode">
          {{ t('shipDesigner.devMode') }}
        </button>
      </div>
    </header>

    <!-- 主体布局：左中右三栏 -->
    <main class="designer-main">
      <!-- 左侧：模块列表 -->
      <aside class="module-panel">
        <h2 class="panel-title">{{ t('shipDesigner.modules.title') }}</h2>
        <div class="module-list">
          <!-- 占位：模块列表项 -->
          <div class="module-item" v-for="i in 8" :key="i">
            <div class="module-icon" />
            <div class="module-info">
              <div class="module-name">Module {{ i }}</div>
              <div class="module-type">ENGINE</div>
            </div>
          </div>
        </div>
      </aside>

      <!-- 中间：设计画布 -->
      <section class="canvas-panel">
        <h2 class="panel-title">{{ t('shipDesigner.canvas.title') }}</h2>
        <div class="design-canvas">
          <!-- 占位：画布内容 -->
          <div class="canvas-placeholder">
            <p>{{ t('shipDesigner.canvas.placeholder') }}</p>
          </div>
        </div>
      </section>

      <!-- 右侧：舰船数据 -->
      <aside class="data-panel">
        <h2 class="panel-title">{{ t('shipDesigner.data.title') }}</h2>
        <div class="ship-data">
          <!-- 占位：舰船数据项 -->
          <div class="data-item">
            <span class="data-label">{{ t('shipDesigner.data.name') }}</span>
            <input class="data-input" placeholder="Ship Name" />
          </div>
          <div class="data-item">
            <span class="data-label">{{ t('shipDesigner.data.mass') }}</span>
            <span class="data-value">0</span>
          </div>
          <div class="data-item">
            <span class="data-label">{{ t('shipDesigner.data.cost') }}</span>
            <span class="data-value">0</span>
          </div>
          <div class="data-item">
            <span class="data-label">{{ t('shipDesigner.data.hp') }}</span>
            <span class="data-value">0</span>
          </div>
          <div class="data-item">
            <span class="data-label">{{ t('shipDesigner.data.power') }}</span>
            <span class="data-value">0</span>
          </div>
        </div>
      </aside>
    </main>
  </div>
</template>

<style scoped>
.ship-designer-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
  background-color: var(--background-color);
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  overflow: hidden;
}

/* 顶部工具栏 */
.designer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 2rem;
  background: color-mix(in srgb, var(--glow-color) 8%, rgba(255, 255, 255, 0.02));
  border-bottom: 1px solid color-mix(in srgb, var(--glow-color) 20%, transparent);
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: transparent;
  border: none;
  color: var(--text-color);
  font-size: 1rem;
  cursor: pointer;
  transition: color 0.3s ease;
}

.back-btn:hover {
  color: var(--text-color-hover);
}

.page-title {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--text-color-hover);
  text-shadow: 0 0 4px var(--glow-color);
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 1rem;
}

.action-btn {
  padding: 0.5rem 1rem;
  background: color-mix(in srgb, var(--glow-color) 12%, rgba(255, 255, 255, 0.05));
  border: 1px solid color-mix(in srgb, var(--glow-color) 30%, transparent);
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.3s ease;
  border-radius: 4px;
}

.action-btn:hover {
  background: color-mix(in srgb, var(--glow-color) 20%, rgba(255, 255, 255, 0.08));
  border-color: var(--glow-color);
  color: var(--text-color-hover);
}

.dev-mode-btn.active {
  background: color-mix(in srgb, var(--glow-color) 30%, rgba(255, 255, 255, 0.1));
  border-color: var(--glow-color);
  color: var(--text-color-hover);
}

/* 主体布局 */
.designer-main {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 左中右面板通用样式 */
.module-panel,
.canvas-panel,
.data-panel {
  display: flex;
  flex-direction: column;
  padding: 1.5rem;
  border-right: 1px solid color-mix(in srgb, var(--glow-color) 12%, transparent);
}

.data-panel {
  border-right: none;
  border-left: 1px solid color-mix(in srgb, var(--glow-color) 12%, transparent);
}

.panel-title {
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--text-color-hover);
  margin: 0 0 1rem 0;
  text-transform: uppercase;
  letter-spacing: 1px;
}

/* 左侧：模块列表 */
.module-panel {
  width: 280px;
  flex-shrink: 0;
}

.module-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.module-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  background: color-mix(in srgb, var(--glow-color) 6%, rgba(255, 255, 255, 0.02));
  border: 1px solid color-mix(in srgb, var(--glow-color) 15%, transparent);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.module-item:hover {
  background: color-mix(in srgb, var(--glow-color) 12%, rgba(255, 255, 255, 0.05));
  border-color: var(--glow-color);
}

.module-icon {
  width: 32px;
  height: 32px;
  background: color-mix(in srgb, var(--glow-color) 20%, rgba(255, 255, 255, 0.1));
  border-radius: 4px;
  flex-shrink: 0;
}

.module-info {
  flex: 1;
  min-width: 0;
}

.module-name {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-color);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.module-type {
  font-size: 0.75rem;
  color: var(--glow-color);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* 中间：画布 */
.canvas-panel {
  flex: 1;
  min-width: 0;
}

.design-canvas {
  flex: 1;
  background: color-mix(in srgb, var(--glow-color) 4%, rgba(255, 255, 255, 0.01));
  border: 1px solid color-mix(in srgb, var(--glow-color) 15%, transparent);
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.canvas-placeholder {
  text-align: center;
  color: var(--text-color);
  opacity: 0.6;
}



/* 右侧：数据面板 */
.data-panel {
  width: 320px;
  flex-shrink: 0;
}

.ship-data {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.data-item {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.data-label {
  font-size: 0.9rem;
  color: var(--text-color);
  min-width: 80px;
  flex-shrink: 0;
}

.data-input {
  flex: 1;
  padding: 0.4rem 0.6rem;
  background: color-mix(in srgb, var(--glow-color) 8%, rgba(255, 255, 255, 0.02));
  border: 1px solid color-mix(in srgb, var(--glow-color) 20%, transparent);
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  font-size: 0.9rem;
  border-radius: 3px;
  transition: all 0.3s ease;
}

.data-input:focus {
  outline: none;
  border-color: var(--glow-color);
  background: color-mix(in srgb, var(--glow-color) 12%, rgba(255, 255, 255, 0.04));
}

.data-value {
  font-size: 0.9rem;
  color: var(--glow-color);
  font-weight: 600;
}
</style>