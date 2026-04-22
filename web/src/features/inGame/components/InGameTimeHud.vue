<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import type { SnapshotWsClient } from '../../../net/snapshotWs'
import { defaultGameTimeManager } from '../../../game/time/GameTimeManager'
import type { LowFreqWorldState } from '../../../game/world'

const props = defineProps<{ lowFreqState: LowFreqWorldState | null; wsClient?: SnapshotWsClient | null }>()

const SPEED_OPTIONS = [
  1,
  5,
  10,
  30,
  60,
  300,
  600,
  1800,
  3600,
  43200,
  86400,
] as const

type SpeedOption = (typeof SPEED_OPTIONS)[number]

const SECONDS_PER_MINUTE = 60
const SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE
const SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR
const DAYS_PER_MONTH = 30
const MONTHS_PER_YEAR = 12
const DAYS_PER_YEAR = DAYS_PER_MONTH * MONTHS_PER_YEAR
const SECONDS_PER_MONTH = DAYS_PER_MONTH * SECONDS_PER_DAY
const SECONDS_PER_YEAR = DAYS_PER_YEAR * SECONDS_PER_DAY

const currentGameSeconds = ref(defaultGameTimeManager.getCurrentGameSeconds())
let hudClockTimer: number | null = null

function refreshCurrentGameSeconds(): void {
  defaultGameTimeManager.update()
  currentGameSeconds.value = defaultGameTimeManager.getCurrentGameSeconds()
}

onMounted(() => {
  refreshCurrentGameSeconds()
  hudClockTimer = window.setInterval(() => {
    refreshCurrentGameSeconds()
  }, 100)
})

onUnmounted(() => {
  if (hudClockTimer !== null) {
    window.clearInterval(hudClockTimer)
    hudClockTimer = null
  }
})

const currentGsprs = computed(() => props.lowFreqState?.gameSecondsPerRealSecond ?? 1.0)

const speedIndex = computed(() => {
  const current = currentGsprs.value
  const idx = SPEED_OPTIONS.findIndex((speed) => Math.abs(speed - current) < 0.1)
  return idx === -1 ? 0 : idx
})

function getSpeedLabel(speed: number): string {
  if (speed === 1) return '1s/s'
  if (speed === 60) return '1m/s'
  if (speed === 300) return '5m/s'
  if (speed === 600) return '10m/s'
  if (speed === 1800) return '30m/s'
  if (speed === 3600) return '1h/s'
  if (speed === 43200) return '12h/s'
  if (speed === 86400) return '1d/s'
  return `${speed}s/s`
}

function sendSimTimeSpeed(gameSecondsPerRealSecond: SpeedOption): void {
  try {
    props.wsClient?.send({ type: 'setSimTimeSpeed', gameSecondsPerRealSecond })
  } catch {
  }
}

function onClickSpeedPrev(): void {
  const prevIndex = (speedIndex.value - 1 + SPEED_OPTIONS.length) % SPEED_OPTIONS.length
  const prevScale = SPEED_OPTIONS[prevIndex]
  if (prevScale != null) {
    sendSimTimeSpeed(prevScale)
  }
}

function onClickSpeedNext(): void {
  const nextIndex = (speedIndex.value + 1) % SPEED_OPTIONS.length
  const nextScale = SPEED_OPTIONS[nextIndex]
  if (nextScale != null) {
    sendSimTimeSpeed(nextScale)
  }
}

const canAdjustSpeed = computed(() => {
  const worldType = props.lowFreqState?.worldType
  return worldType === 'SINGLE_PLAYER' || worldType === 'MULTI_PLAYER'
})

function getSnapshotBaseGameSeconds(lowFreqState: LowFreqWorldState | null): number | null {
  if (!lowFreqState) {
    return null
  }

  if (
    lowFreqState.year == null ||
    lowFreqState.month == null ||
    lowFreqState.day == null ||
    lowFreqState.hour == null ||
    lowFreqState.minute == null ||
    lowFreqState.second == null
  ) {
    return null
  }

  const yearIndex = Math.max(0, lowFreqState.year - 1)
  const monthIndex = Math.max(0, lowFreqState.month - 1)
  const dayIndex = Math.max(0, lowFreqState.day - 1)
  return (
    yearIndex * SECONDS_PER_YEAR +
    monthIndex * SECONDS_PER_MONTH +
    dayIndex * SECONDS_PER_DAY +
    lowFreqState.hour * SECONDS_PER_HOUR +
    lowFreqState.minute * SECONDS_PER_MINUTE +
    lowFreqState.second
  )
}

function decomposeGameSeconds(totalGameSeconds: number): {
  year: number
  month: number
  day: number
  hour: number
  minute: number
  second: number
} {
  const safeSeconds = Math.max(0, Math.floor(totalGameSeconds))
  const year = Math.floor(safeSeconds / SECONDS_PER_YEAR) + 1
  const secondsInYear = safeSeconds % SECONDS_PER_YEAR
  const month = Math.floor(secondsInYear / SECONDS_PER_MONTH) + 1
  const secondsInMonth = secondsInYear % SECONDS_PER_MONTH
  const day = Math.floor(secondsInMonth / SECONDS_PER_DAY) + 1
  const secondsInDay = secondsInMonth % SECONDS_PER_DAY
  const hour = Math.floor(secondsInDay / SECONDS_PER_HOUR)
  const minute = Math.floor((secondsInDay % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE)
  const second = secondsInDay % SECONDS_PER_MINUTE
  return { year, month, day, hour, minute, second }
}

const text = computed(() => {
  const lowFreqState = props.lowFreqState
  if (!lowFreqState || lowFreqState.year == null) {
    return '--.--.-- --:--:--'
  }

  const snapshotBaseGameSeconds = getSnapshotBaseGameSeconds(lowFreqState)
  const displayGameSeconds = snapshotBaseGameSeconds === null
    ? currentGameSeconds.value
    : Math.max(snapshotBaseGameSeconds, currentGameSeconds.value)
  const displayTime = decomposeGameSeconds(displayGameSeconds)
  const year = displayTime.year
  const month = String(displayTime.month).padStart(2, '0')
  const day = String(displayTime.day).padStart(2, '0')
  const hour = String(displayTime.hour).padStart(2, '0')
  const minute = String(displayTime.minute).padStart(2, '0')
  const second = String(displayTime.second).padStart(2, '0')
  return `${year}-${month}-${day} ${hour}:${minute}:${second}`
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
