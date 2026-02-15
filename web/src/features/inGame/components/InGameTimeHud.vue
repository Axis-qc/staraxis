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
import { computed } from 'vue'
import type { SnapshotMessage, SnapshotWsClient } from '../../../net/snapshotWs'

const props = defineProps<{ snapshot: SnapshotMessage | null; wsClient?: SnapshotWsClient | null }>()

const SPEED_OPTIONS = [
  1, // 1 游戏秒 / 现实秒（1:1）喵
  5,
  10,
  30,
  60, // 1 游戏分钟 / 现实秒喵
  300, // 5 游戏分钟 / 现实秒喵
  600, // 10 游戏分钟 / 现实秒喵
  1800, // 30 游戏分钟 / 现实秒喵
  3600, // 1 游戏小时 / 现实秒喵
  43200, // 12 游戏小时 / 现实秒喵
  86400 // 1 游戏日 / 现实秒喵
] as const

type SpeedOption = (typeof SPEED_OPTIONS)[number]

// 移除本地维护的 speedIndex ref，改为基于快照同步的 computed 索引喵
const currentGsprs = computed(() => props.snapshot?.realTimeWorldState?.gameSecondsPerRealSecond ?? 1.0)

const speedIndex = computed(() => {
  const current = currentGsprs.value
  // 寻找最接近的档位索引，默认 0 档喵
  const idx = SPEED_OPTIONS.findIndex(s => Math.abs(s - current) < 0.1)
  return idx === -1 ? 0 : idx
})

function getSpeedLabel(s: number) {
  if (s === 1) return '1s/s'
  if (s === 60) return '1m/s'
  if (s === 300) return '5m/s'
  if (s === 600) return '10m/s'
  if (s === 1800) return '30m/s'
  if (s === 3600) return '1h/s'
  if (s === 43200) return '12h/s'
  if (s === 86400) return '1d/s'
  return `${s}s/s`
}

function sendSimTimeSpeed(gameSecondsPerRealSecond: SpeedOption) {
  // 复用快照 WS 连接发送命令，避免短连接频繁建立/关闭导致 WS 不稳定。
  try {
    props.wsClient?.send({ type: 'setSimTimeSpeed', gameSecondsPerRealSecond })
  } catch {
  }
}

function onClickSpeedPrev() {
  const prevIndex = (speedIndex.value - 1 + SPEED_OPTIONS.length) % SPEED_OPTIONS.length
  const prevScale = SPEED_OPTIONS[prevIndex]
  if (prevScale != null) sendSimTimeSpeed(prevScale)
}

function onClickSpeedNext() {
  const nextIndex = (speedIndex.value + 1) % SPEED_OPTIONS.length
  const nextScale = SPEED_OPTIONS[nextIndex]
  if (nextScale != null) sendSimTimeSpeed(nextScale)
}

const canAdjustSpeed = computed(() => {
  const wt = props.snapshot?.realTimeWorldState?.worldType
  return wt === 'SINGLE_PLAYER' || wt === 'MULTI_PLAYER'
})

const text = computed(() => {
  const s = props.snapshot
  const rts = s?.realTimeWorldState
  if (!s || !s.ok || !rts || rts.year === undefined) return '--.--.--.--:--'

  const year = rts.year
  const month = String(rts.month).padStart(2, '0')
  const day = String(rts.day).padStart(2, '0')
  const hour = String(rts.hour).padStart(2, '0')
  const minute = String(rts.minute).padStart(2, '0')
  const second = String(rts.second).padStart(2, '0')

  return `${year}-${month}-${day}日-${hour}:${minute}:${second}`
})
</script>

<template>
  <div class="time-hud" aria-label="Time HUD">
    <div class="time-text">{{ text }}</div>
    <div v-if="canAdjustSpeed" class="speed-control">
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
