<script setup lang="ts">
/**
 * @file InGameEscMenu.vue
 *
 * @description
 * 游戏内 ESC 菜单弹层。
 *
 * 用途：
 * - 替代 InGame 顶部标题栏的返回按钮与入口操作。
 * - 由父组件通过 `Esc` 键控制开关。
 *
 * 说明：
 * - “保存游戏/加载游戏”当前仅触发开发中提示（无后端逻辑）。
 * - “退出游戏”由父组件实现路由跳转（当前约定：`router.push('/main-menu')`）。
 *
 * @usage
 * - 父组件通过 `v-model:open` 控制显示/隐藏。
 * - 父组件监听：`resume/save/load/quit` 事件。
 *
 * @provides
 * - **返回游戏**：关闭菜单。
 * - **保存游戏**：触发 save 事件。
 * - **加载游戏**：触发 load 事件。
 * - **退出游戏**：触发 quit 事件。
 */

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{
  (e: 'update:open', v: boolean): void
  (e: 'resume'): void
  (e: 'save'): void
  (e: 'load'): void
  (e: 'quit'): void
}>()

function close() {
  emit('update:open', false)
}

function onClickResume() {
  emit('resume')
  close()
}

function onClickSave() {
  emit('save')
}

function onClickLoad() {
  emit('load')
}

function onClickQuit() {
  emit('quit')
}
</script>

<template>
  <div v-if="props.open" class="menu-backdrop" role="dialog" aria-label="In Game Menu">
    <div class="menu-panel">
      <div class="menu-header">
        <div class="menu-title">菜单</div>
        <button class="menu-close" type="button" @click="close">×</button>
      </div>

      <div class="menu-body">
        <button class="menu-item" type="button" @click="onClickResume">返回游戏</button>
        <button class="menu-item" type="button" @click="onClickSave">保存游戏</button>
        <button class="menu-item" type="button" @click="onClickLoad">加载游戏</button>
        <button class="menu-item danger" type="button" @click="onClickQuit">退出游戏</button>

        <div class="menu-hint">
          将此站点加入 浏览器设置 &gt; 外观 &gt; 浏览器行为和功能 &gt; 配置鼠标手势 &gt; 自定义行为，禁用此站点的鼠标手势中体验更佳
        </div>
      </div>

      <div class="menu-footer">ESC：关闭</div>
    </div>
  </div>
</template>

<style scoped>
.menu-backdrop {
  position: absolute;
  inset: 0;
  z-index: 50;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  pointer-events: auto;
}

.menu-panel {
  width: 360px;
  max-width: calc(100vw - 24px);
  border-radius: 14px;
  background: color-mix(in srgb, var(--background-color) 70%, rgba(0, 0, 0, 0.35));
  border: 1px solid color-mix(in srgb, var(--glow-color) 25%, transparent);
  box-shadow: 0 0 18px color-mix(in srgb, var(--glow-color) 18%, transparent);
  overflow: hidden;
}

.menu-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--glow-color) 18%, transparent);
}

.menu-title {
  font-size: 12px;
  letter-spacing: 1px;
  color: var(--text-color-hover);
  text-shadow: 0 0 6px color-mix(in srgb, var(--glow-color) 35%, transparent);
  font-family: 'Orbitron', sans-serif;
}

.menu-close {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--glow-color) 22%, transparent);
  background: transparent;
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  cursor: pointer;
}

.menu-close:hover {
  border-color: color-mix(in srgb, var(--glow-color) 58%, transparent);
  color: var(--text-color-hover);
}

.menu-body {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.menu-item {
  height: 42px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.18);
  border: 1px solid color-mix(in srgb, var(--glow-color) 28%, transparent);
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  font-size: 12px;
  cursor: pointer;
  transition: transform 0.12s ease, border-color 0.12s ease, color 0.12s ease;
}

.menu-item:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--glow-color) 58%, transparent);
  color: var(--text-color-hover);
}

.menu-item.danger {
  border-color: color-mix(in srgb, #ff4d4f 45%, transparent);
}

.menu-hint {
  margin-top: 8px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.14);
  border: 1px solid color-mix(in srgb, var(--glow-color) 18%, transparent);
  font-size: 12px;
  line-height: 1.35;
  color: color-mix(in srgb, var(--text-color) 72%, transparent);
}

.menu-footer {
  padding: 10px 12px 12px;
  font-size: 12px;
  color: color-mix(in srgb, var(--text-color) 65%, transparent);
}
</style>
