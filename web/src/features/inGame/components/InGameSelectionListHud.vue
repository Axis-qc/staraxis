<script setup lang="ts">
/**
 * @file InGameSelectionListHud.vue
 *
 * @description
 * 游戏内“选择列表”HUD（左侧垂直居中）。
 *
 * 功能：
 * - 当存在选中实体时，显示一个左侧垂直列表（可滚动）。
 * - 展示当前框选/点击选中的实体集合（当前展示：type + id + subtype）。
 * - 支持交互：
 *   - 单击：触发 `open` 事件（用于打开实体 UI，待接入）。
 *   - 双击：触发 `focus` 事件（用于聚焦相机/视角）。
 *
 * 说明：
 * - 本组件不直接依赖渲染器，仅消费 `selectedIds + entities`。
 * - 交互采用“延迟单击判定”：在一定时间窗口内若触发双击，则不触发单击。
 *
 * @usage
 * - 在 InGameView 中使用：
 *   - 传入 `:selected-ids="selection.selectedIds.value"`
 *   - 传入 `:entities="hub.entities.value"`
 *   - 监听 `@open` / `@focus`
 *
 * @provides
 * - **HUD 列表**：左侧垂直居中展示选中实体，超出屏幕高度时滚动。
 * - **事件**：`open(entityId)` / `focus(entityId)`。
 */
import { computed, ref } from 'vue'
import type { EntitySnapshot } from '../../../net/snapshotWs'

const props = defineProps<{
  selectedIds: number[]
  entities: EntitySnapshot[]
}>()

const emit = defineEmits<{
  (e: 'open', payload: { entityId: number }): void
  (e: 'focus', payload: { entityId: number }): void
}>()

const selectedItems = computed(() => {
  const byId = new Map<number, EntitySnapshot>()
  for (const e of props.entities) {
    byId.set(e.entityId, e)
  }

  const out: Array<{ id: number; type: string; subtitle: string }> = []
  for (const id of props.selectedIds) {
    const e = byId.get(id)
    if (!e) continue

    const type = e.entityType
    let subtitle = ''
    if (type === 'STAR') {
      const d: any = e.details
      subtitle = d?.starTypeId ? String(d.starTypeId) : ''
    } else if (type === 'PLANET') {
      const d: any = e.details
      subtitle = d?.planetTypeId ? String(d.planetTypeId) : ''
    }

    out.push({ id, type, subtitle })
  }
  return out
})

const isVisible = computed(() => selectedItems.value.length > 0)

const clickTimer = ref<number | null>(null)
const pendingClickId = ref<number | null>(null)

const DOUBLE_CLICK_WINDOW_MS = 250

function onRowClick(entityId: number) {
  if (clickTimer.value != null) {
    window.clearTimeout(clickTimer.value)
    clickTimer.value = null
  }

  pendingClickId.value = entityId

  clickTimer.value = window.setTimeout(() => {
    if (pendingClickId.value != null) {
      emit('open', { entityId: pendingClickId.value })
    }
    pendingClickId.value = null
    clickTimer.value = null
  }, DOUBLE_CLICK_WINDOW_MS)
}

function onRowDblClick(entityId: number) {
  if (clickTimer.value != null) {
    window.clearTimeout(clickTimer.value)
    clickTimer.value = null
  }
  pendingClickId.value = null
  emit('focus', { entityId })
}
</script>

<template>
  <aside v-if="isVisible" class="selhud" aria-label="Selection HUD">
    <div class="selhud-panel">
      <div class="selhud-header">
        <div class="selhud-title">已选中</div>
        <div class="selhud-count">{{ selectedItems.length }}</div>
      </div>

      <div class="selhud-list" role="list">
        <button
          v-for="it in selectedItems"
          :key="it.id"
          class="selhud-row"
          type="button"
          role="listitem"
          @click="onRowClick(it.id)"
          @dblclick.stop.prevent="onRowDblClick(it.id)"
        >
          <div class="selhud-row-main">
            <div class="selhud-row-title">{{ it.type }} #{{ it.id }}</div>
            <div v-if="it.subtitle" class="selhud-row-sub">{{ it.subtitle }}</div>
          </div>
        </button>
      </div>
    </div>
  </aside>
</template>

<style scoped>
.selhud {
  position: absolute;
  left: 12px;
  top: 0;
  bottom: 0;
  z-index: 35;

  display: flex;
  align-items: center;

  pointer-events: none;
}

.selhud-panel {
  width: 240px;
  max-height: calc(100vh - 24px);

  display: flex;
  flex-direction: column;

  border-radius: 14px;
  background: color-mix(in srgb, var(--background-color) 65%, rgba(0, 0, 0, 0.35));
  border: 1px solid color-mix(in srgb, var(--glow-color) 25%, transparent);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 0 18px color-mix(in srgb, var(--glow-color) 18%, transparent);

  color: var(--text-color);
  pointer-events: auto;
}

.selhud-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-bottom: 1px solid color-mix(in srgb, var(--glow-color) 18%, transparent);
}

.selhud-title {
  font-size: 12px;
  letter-spacing: 1px;
  color: var(--text-color-hover);
  text-shadow: 0 0 6px color-mix(in srgb, var(--glow-color) 35%, transparent);
}

.selhud-count {
  font-size: 12px;
  opacity: 0.9;
}

.selhud-list {
  overflow: auto;
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.selhud-row {
  appearance: none;
  width: 100%;
  text-align: left;

  padding: 8px 10px;
  border-radius: 10px;
  border: 1px solid color-mix(in srgb, var(--glow-color) 18%, transparent);
  background: color-mix(in srgb, var(--background-color) 85%, rgba(0, 0, 0, 0.15));

  color: inherit;
  cursor: pointer;
}

.selhud-row:hover {
  border-color: color-mix(in srgb, var(--glow-color) 55%, transparent);
  box-shadow: 0 0 14px color-mix(in srgb, var(--glow-color) 18%, transparent);
}

.selhud-row:active {
  transform: translateY(1px);
}

.selhud-row-title {
  font-size: 12px;
  letter-spacing: 0.4px;
}

.selhud-row-sub {
  margin-top: 2px;
  font-size: 11px;
  opacity: 0.75;
}
</style>
