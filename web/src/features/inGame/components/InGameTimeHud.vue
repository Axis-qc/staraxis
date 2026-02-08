<script setup lang="ts">
/**
 * @file InGameTimeHud.vue
 *
 * @description
 * 游戏内“时间”HUD（右上角）。
 *
 * 功能：
 * - 基于后端快照中的权威时间（`gameDatetimeDay` + `accGameHoursInDay`）展示当前游戏时间。
 * - 显示格式：`年-月-日-时`。
 * - 日历口径：1年=360天，1月=30天，1天=24时。
 *
 * 说明：
 * - 本组件只做展示，不负责推进时间。
 * - 若快照为空/未就绪，显示占位 `--年--月--日--时`。
 *
 * @usage
 * - 在 InGameView 中使用：`<InGameTimeHud :snapshot="hub.lastSnapshot.value" />`。
 *
 * @provides
 * - **时间显示**：右上角显示游戏时间字符串。
 * 
 */
import { computed, ref } from 'vue'
import type { SnapshotMessage, SnapshotWsClient } from '../../../net/snapshotWs'

const props = defineProps<{ snapshot: SnapshotMessage | null; wsClient?: SnapshotWsClient | null }>()

const SPEED_OPTIONS = [1, 5, 10, 30, 60, 720, 1440] as const
type SpeedOption = (typeof SPEED_OPTIONS)[number]

const speedIndex = ref<number>(Math.max(0, SPEED_OPTIONS.indexOf(1.0)))

function getSpeedLabel(s: SpeedOption) {
  if (s === 60) return '1h/s'
  if (s === 720) return '12h/s'
  if (s === 1440) return '1d/s'
  return `${s}m/s`
}

function sendSimTimeSpeed(minutesPerSecond: SpeedOption) {
  // 复用快照 WS 连接发送命令，避免短连接频繁建立/关闭导致 WS 不稳定。
  try {
    // 兼容旧后端字段名 scale（语义已变为 minutesPerSecond）喵
    props.wsClient?.send({ type: 'setSimTimeSpeed', minutesPerSecond })
  } catch {
  }
}

function onClickSpeedPrev() {
  const prevIndex = (speedIndex.value - 1 + SPEED_OPTIONS.length) % SPEED_OPTIONS.length
  speedIndex.value = prevIndex
  const prevScale = SPEED_OPTIONS[prevIndex]
  if (prevScale != null) sendSimTimeSpeed(prevScale)
}

function onClickSpeedNext() {
  const nextIndex = (speedIndex.value + 1) % SPEED_OPTIONS.length
  speedIndex.value = nextIndex
  const nextScale = SPEED_OPTIONS[nextIndex]
  if (nextScale != null) sendSimTimeSpeed(nextScale)
}

const text = computed(() => {
  const s = props.snapshot
  const rts = s?.realTimeWorldState
  if (!s || !s.ok || !rts) return '--.--.--.--:--'

  const gameDatetimeDay = Math.max(0, Number(rts.gameDatetimeDay ?? 0))
  const accGameHoursInDay = Math.max(0, Number(rts.accGameHoursInDay ?? 0))

  const totalHours = gameDatetimeDay * 24 + accGameHoursInDay

  const year = Math.floor(totalHours / (360 * 24)) + 1
  const hourOfYear = totalHours - (year - 1) * 360 * 24

  const month = Math.floor(hourOfYear / (30 * 24)) + 1
  const hourOfMonth = hourOfYear - (month - 1) * 30 * 24

  const day = Math.floor(hourOfMonth / 24) + 1
  const hour = Math.floor(hourOfMonth - (day - 1) * 24)
  const minute = Math.floor((hourOfMonth - (day - 1) * 24 - hour) * 60)

  return `${year}.${month}.${day}.${hour}:${String(minute).padStart(2, '0')}`
})
</script>

<template>
  <div class="time-hud" aria-label="Time HUD">
    <div class="time-text">{{ text }}</div>
    <div class="speed-control">
      <button class="speed-btn-side" type="button" @click="onClickSpeedPrev" aria-label="Previous speed">
        &lt;
      </button>
      <div class="speed-value">
        {{ SPEED_OPTIONS[speedIndex] != null ? getSpeedLabel(SPEED_OPTIONS[speedIndex]!) : '--' }}
      </div>
      <button class="speed-btn-side" type="button" @click="onClickSpeedNext" aria-label="Next speed">
        &gt;
      </button>
    </div>
  </div>
</template>

<style scoped>
.time-hud {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 40;

  display: flex;
  align-items: center;
  gap: 8px;

  padding: 6px 10px;
  border-radius: 10px;

  background: color-mix(in srgb, var(--background-color) 65%, rgba(0, 0, 0, 0.35));
  border: 1px solid color-mix(in srgb, var(--glow-color) 25%, transparent);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);

  font-size: 12px;
  letter-spacing: 1px;
  color: var(--text-color);
  pointer-events: auto;
}

.time-text {
  pointer-events: none;
}

.speed-control {
  display: flex;
  align-items: center;
}

.speed-btn-side {
  width: 22px;
  height: 22px;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--glow-color) 22%, transparent);
  background: transparent;
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  font-size: 12px;
  letter-spacing: 1px;
  cursor: pointer;
}

.speed-btn-side:hover {
  border-color: color-mix(in srgb, var(--glow-color) 58%, transparent);
  color: var(--text-color-hover);
}

.speed-value {
  min-width: 54px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 8px;
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--glow-color) 18%, transparent);
  background: color-mix(in srgb, var(--background-color) 85%, rgba(0, 0, 0, 0.15));
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  font-size: 12px;
  letter-spacing: 1px;
  pointer-events: none;
}
</style>
