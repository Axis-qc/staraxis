/**
 * @file snapshotInterpolationBuffer.ts
 *
 * @description
 * 高频快照插值窗口缓冲器喵。
 *
 * 只负责根据高频权威帧缓存计算当前渲染窗口喵。
 * 不负责实体姿态计算，也不会回写缓存世界喵。
 */

import type { HighFreqSnapshotFrame } from '../localVisibleWorldTypes'

/**
 * 插值层固定参数喵。
 *
 * 这些参数来自阶段 C 计划，先固定成可审阅的常量喵。
 */
export type SnapshotInterpolationConfig = {
  interpolationDelayMs: number
  maxExtrapolationMs: number
  teleportThresholdGU: number
  resumeResetThresholdMs: number
  maxBufferedHighFreqTicks: number
}

/**
 * 当前渲染窗口喵。
 *
 * `previousFrame` 和 `nextFrame` 用于插值喵。
 * 当 `mode === 'extrapolate'` 时只会使用 `previousFrame` 喵。
 */
export type SnapshotInterpolationWindow = {
  targetGameSeconds: number
  previousFrame: HighFreqSnapshotFrame | null
  nextFrame: HighFreqSnapshotFrame | null
  latestFrame: HighFreqSnapshotFrame
  didResetWindow: boolean
  mode: 'freeze' | 'interpolate' | 'extrapolate'
}

/**
 * 阶段 C 先固定一组保守参数喵。
 *
 * `teleportThresholdGU` 取 `40 GU`，只在明显断层时直接 snap 喵。
 */
export const DEFAULT_SNAPSHOT_INTERPOLATION_CONFIG: SnapshotInterpolationConfig = Object.freeze({
  interpolationDelayMs: 100,
  maxExtrapolationMs: 100,
  teleportThresholdGU: 40,
  resumeResetThresholdMs: 500,
  maxBufferedHighFreqTicks: 8,
})

const WINDOW_CACHE_BUCKET_MS = 8

type CachedWindowKey = {
  bucket: number
  firstTick: number
  lastTick: number
  frameCount: number
  latestReceivedAtClientMs: number
}

/**
 * 基于高频快照帧缓存构建渲染窗口喵。
 */
export class SnapshotInterpolationBuffer {
  private lastQueryClientMs: number | null = null
  private lastWindowCache:
    | {
        key: CachedWindowKey
        window: SnapshotInterpolationWindow
      }
    | null = null

  constructor(
    private readonly config: SnapshotInterpolationConfig = DEFAULT_SNAPSHOT_INTERPOLATION_CONFIG,
  ) {}

  /**
   * 清空插值层内部窗口状态喵。
   */
  reset(): void {
    this.lastQueryClientMs = null
    this.lastWindowCache = null
  }

  /**
   * 获取当前插值配置喵。
   */
  getConfig(): SnapshotInterpolationConfig {
    return this.config
  }

  /**
   * 根据最新高频权威帧生成本帧的渲染窗口喵。
   */
  getWindow(params: {
    frames: HighFreqSnapshotFrame[]
    realMsToGameSeconds: (realMs: number) => number
  }): SnapshotInterpolationWindow | null {
    const frames = this.normalizeFrames(params.frames)
    if (frames.length === 0) {
      return null
    }

    const now = performance.now()
    const cacheKey = this.buildCacheKey(now, frames)
    const cachedWindow = this.getCachedWindow(cacheKey)
    if (cachedWindow) {
      return cachedWindow
    }

    const latestFrame = frames[frames.length - 1]
    const shouldResetWindow =
      this.lastQueryClientMs !== null &&
      now - this.lastQueryClientMs > this.config.resumeResetThresholdMs

    this.lastQueryClientMs = now

    const delayGameSeconds = Math.max(
      0,
      params.realMsToGameSeconds(this.config.interpolationDelayMs),
    )
    const maxExtrapolationGameSeconds = Math.max(
      0,
      params.realMsToGameSeconds(this.config.maxExtrapolationMs),
    )
    const newestEstimatedGameSeconds =
      latestFrame.totalGameSecondsExact +
      Math.max(0, params.realMsToGameSeconds(now - latestFrame.receivedAtClientMs))
    const earliestGameSeconds = frames[0].totalGameSecondsExact

    let targetGameSeconds = latestFrame.totalGameSecondsExact
    if (!shouldResetWindow) {
      targetGameSeconds = newestEstimatedGameSeconds - delayGameSeconds
      targetGameSeconds = Math.max(targetGameSeconds, earliestGameSeconds)
      targetGameSeconds = Math.min(
        targetGameSeconds,
        latestFrame.totalGameSecondsExact + maxExtrapolationGameSeconds,
      )
    }

    const window = this.buildWindow(frames, targetGameSeconds, shouldResetWindow)
    this.lastWindowCache = {
      key: cacheKey,
      window,
    }
    return window
  }

  /**
   * 规范化帧列表喵。
   *
   * 这里会按 `simulationTick` 去重并只保留最近若干帧喵。
   */
  private normalizeFrames(frames: HighFreqSnapshotFrame[]): HighFreqSnapshotFrame[] {
    const byTick = new Map<number, HighFreqSnapshotFrame>()
    for (const frame of frames) {
      byTick.set(frame.simulationTick, frame)
    }

    const orderedFrames = Array.from(byTick.values()).sort(
      (left, right) => left.simulationTick - right.simulationTick,
    )
    if (orderedFrames.length <= this.config.maxBufferedHighFreqTicks) {
      return orderedFrames
    }

    return orderedFrames.slice(-this.config.maxBufferedHighFreqTicks)
  }

  /**
   * 生成窗口缓存键喵。
   *
   * 同一渲染帧里的重复查询会复用同一个窗口喵。
   */
  private buildCacheKey(
    now: number,
    frames: HighFreqSnapshotFrame[],
  ): CachedWindowKey {
    return {
      bucket: Math.floor(now / WINDOW_CACHE_BUCKET_MS),
      firstTick: frames[0].simulationTick,
      lastTick: frames[frames.length - 1].simulationTick,
      frameCount: frames.length,
      latestReceivedAtClientMs: frames[frames.length - 1].receivedAtClientMs,
    }
  }

  /**
   * 命中当前窗口缓存时直接复用喵。
   */
  private getCachedWindow(cacheKey: CachedWindowKey): SnapshotInterpolationWindow | null {
    if (!this.lastWindowCache) {
      return null
    }

    const previousKey = this.lastWindowCache.key
    if (
      previousKey.bucket !== cacheKey.bucket ||
      previousKey.firstTick !== cacheKey.firstTick ||
      previousKey.lastTick !== cacheKey.lastTick ||
      previousKey.frameCount !== cacheKey.frameCount ||
      previousKey.latestReceivedAtClientMs !== cacheKey.latestReceivedAtClientMs
    ) {
      return null
    }

    return this.lastWindowCache.window
  }

  /**
   * 构建当前应使用的插值窗口喵。
   */
  private buildWindow(
    frames: HighFreqSnapshotFrame[],
    targetGameSeconds: number,
    didResetWindow: boolean,
  ): SnapshotInterpolationWindow {
    const latestFrame = frames[frames.length - 1]
    if (didResetWindow) {
      return {
        targetGameSeconds: latestFrame.totalGameSecondsExact,
        previousFrame: latestFrame,
        nextFrame: latestFrame,
        latestFrame,
        didResetWindow: true,
        mode: 'freeze',
      }
    }

    if (targetGameSeconds > latestFrame.totalGameSecondsExact) {
      return {
        targetGameSeconds,
        previousFrame: latestFrame,
        nextFrame: null,
        latestFrame,
        didResetWindow: false,
        mode: 'extrapolate',
      }
    }

    let previousFrame = frames[0]
    let nextFrame = frames[0]

    for (const frame of frames) {
      if (frame.totalGameSecondsExact <= targetGameSeconds) {
        previousFrame = frame
      }
      if (frame.totalGameSecondsExact >= targetGameSeconds) {
        nextFrame = frame
        break
      }
      nextFrame = frame
    }

    return {
      targetGameSeconds,
      previousFrame,
      nextFrame,
      latestFrame,
      didResetWindow: false,
      mode: previousFrame !== nextFrame ? 'interpolate' : 'freeze',
    }
  }
}
