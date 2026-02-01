<script setup lang="ts">
/**
 * @file InGameBuildBar.vue
 *
 * @description
 * 游戏内底部主控制栏（分类入口）。
 *
 * 目标：
 * - 将底部 UI 改为水平居中布局，提供各系统入口：开发/军事/科技/内政/外交。
 * - 点击某个分类后，由父组件决定打开/切换对应面板。
 *
 * 说明：
 * - 该组件仅负责渲染与交互事件抛出，不直接修改权威模拟。
 *
 * @usage
 * - 父组件传入 `activeTab`，用于高亮当前激活分类。
 * - 父组件监听 `select` 事件，接收 tabKey（'development'|'military'|'tech'|'domestic'|'diplomacy'）。
 *
 * @provides
 * - 游戏内各系统面板的底部入口。
 */

type InGameBottomTab = 'development' | 'military' | 'tech' | 'domestic' | 'diplomacy'

const props = defineProps<{ activeTab: InGameBottomTab | null }>()

const emit = defineEmits<{
  (e: 'select', tab: InGameBottomTab): void
}>()

function onSelect(tab: InGameBottomTab) {
  emit('select', tab)
}

function isActive(tab: InGameBottomTab) {
  return props.activeTab === tab
}
</script>

<template>
  <div class="bottom-bar" role="complementary" aria-label="Bottom Bar">
    <div class="bar-inner">
      <button class="tab" :class="{ active: isActive('development') }" type="button" @click="onSelect('development')">开发</button>
      <button class="tab" :class="{ active: isActive('military') }" type="button" @click="onSelect('military')">军事</button>
      <button class="tab" :class="{ active: isActive('tech') }" type="button" @click="onSelect('tech')">科技</button>
      <button class="tab" :class="{ active: isActive('domestic') }" type="button" @click="onSelect('domestic')">内政</button>
      <button class="tab" :class="{ active: isActive('diplomacy') }" type="button" @click="onSelect('diplomacy')">外交</button>
    </div>
  </div>
</template>

<style scoped>
.bottom-bar {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  right: auto;
  bottom: 12px;
  z-index: 25;
  pointer-events: auto;

  width: max-content;
  height: max-content;
  padding: 12px;
  border-radius: 14px;
  background: color-mix(in srgb, var(--background-color) 65%, rgba(0, 0, 0, 0.35));
  border: 1px solid color-mix(in srgb, var(--glow-color) 25%, transparent);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 0 18px color-mix(in srgb, var(--glow-color) 18%, transparent);

  display: flex;
  align-items: center;
  justify-content: center;
}

.bar-inner {
  width: auto;
  max-width: 100%;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.tab {
  height: 34px;
  padding: 0 14px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.18);
  border: 1px solid color-mix(in srgb, var(--glow-color) 28%, transparent);
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  font-size: 12px;
  cursor: pointer;
  transition: transform 0.12s ease, border-color 0.12s ease, color 0.12s ease, background 0.12s ease;
}

.tab:hover {
  transform: translateY(-1px);
  border-color: color-mix(in srgb, var(--glow-color) 58%, transparent);
  color: var(--text-color-hover);
}

.tab.active {
  background: color-mix(in srgb, var(--glow-color) 14%, rgba(0, 0, 0, 0.18));
  border-color: color-mix(in srgb, var(--glow-color) 70%, transparent);
  color: var(--text-color-hover);
}
</style>
