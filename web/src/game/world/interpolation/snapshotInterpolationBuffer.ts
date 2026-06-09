/**
 * @file snapshotInterpolationBuffer.ts
 *
 * @description
 * 高频快照插值窗口缓冲器喵。
 *
 * 只负责根据高频权威帧缓存维护“双逻辑帧缓冲”渲染窗口喵。
 * 不负责实体姿态计算，也不会回写缓存世界喵。
 */

import { AUTHORITY_LOGIC_FRAME_DURATION_MS } from '../../time/GameTimeManager'
import type { HighFreqSnapshotFrame } from '../localVisibleWorldTypes'

/**
 * 插值层固定参数喵。
 *
 * 这些参数来自“双逻辑帧缓冲”计划中的固定口径喵。
 */
export type SnapshotInterpolationConfig = {
  logicFrameDurationMs: number
  maxExtrapolationMs: number
  teleportThresholdGU: number
  resumeResetThresholdMs: number
  maxBufferedHighFreqTicks: number
}

/**
 * 当前渲染窗口喵。
 *
 * `currentSnapshot`（当前逻辑帧）在当前渲染周期内必须保持固定喵。
 * `nextSnapshotBuffer`（下一逻辑帧缓冲）允许被同周期高频快照持续刷新喵。
 */
export type SnapshotInterpolationWindow = {
  currentSnapshot: HighFreqSnapshotFrame
  nextSnapshotBuffer: HighFreqSnapshotFrame | null
  latestFrame: HighFreqSnapshotFrame
  renderAlpha: number
  renderGameSeconds: number
  didResetWindow: boolean
  mode: 'freeze' | 'interpolate' | 'extrapolate'
}

/**
 * 插值调试状态喵。
 *
 * 只暴露调试面板需要的最小窗口信息喵。
 */
export type SnapshotInterpolationDebugState = {
  currentTick: number
  nextTick: number | null
  latestTick: number
  renderAlpha: number
  renderGameSeconds: number
  mode: SnapshotInterpolationWindow['mode']
  didResetWindow: boolean
}

/**
 * 当前阶段固定一组可审阅参数喵。
 *
 * `teleportThresholdGU` 取 `40 GU`，只在明显断层时直接 snap 喵。
 */
export const DEFAULT_SNAPSHOT_INTERPOLATION_CONFIG: SnapshotInterpolationConfig = Object.freeze({
  logicFrameDurationMs: AUTHORITY_LOGIC_FRAME_DURATION_MS,
  maxExtrapolationMs: 100,
  teleportThresholdGU: 40,
  resumeResetThresholdMs: 500,
  maxBufferedHighFreqTicks: 8,
})

/**
 * 基于高频快照帧缓存构建双缓冲渲染窗口喵。
 */
export class SnapshotInterpolationBuffer {
  private currentSnapshot: HighFreqSnapshotFrame | null = null
  private nextSnapshotBuffer: HighFreqSnapshotFrame | null = null
  private bufferedFutureSnapshot: HighFreqSnapshotFrame | null = null
  private cycleStartClientMs: number | null = null
  private cycleDurationMs: number = AUTHORITY_LOGIC_FRAME_DURATION_MS
  private lastQueryClientMs: number | null = null

  private readonly config: SnapshotInterpolationConfig

  constructor(config?: SnapshotInterpolationConfig) {
    this.config = config ?? DEFAULT_SNAPSHOT_INTERPOLATION_CONFIG
  }

  /**
   * 清空插值层内部窗口状态喵。
   */
  reset(): void {
    this.currentSnapshot = null
    this.nextSnapshotBuffer = null
    this.bufferedFutureSnapshot = null
    this.cycleStartClientMs = null
    this.cycleDurationMs = AUTHORITY_LOGIC_FRAME_DURATION_MS
    this.lastQueryClientMs = null
  }

  /**
   * 获取当前插值配置喵。
   */
  getConfig(): SnapshotInterpolationConfig {
    return this.config
  }

  /**
   * 根据最新高频权威帧生成本帧的双缓冲渲染窗口喵。
   */
  getWindow(params: {
    frames: HighFreqSnapshotFrame[]
    realMsToGameSeconds: (realMs: number) => number
    currentGameSeconds: number
    nowMs: number
  }): SnapshotInterpolationWindow | null {
    const frames = this.normalizeFrames(params.frames)
    if (frames.length === 0) {
      return null
    }

    const now = params.nowMs
    const latestFrame = frames[frames.length - 1]!
    const shouldResetWindow =
      this.lastQueryClientMs !== null &&
      now - this.lastQueryClientMs > this.config.resumeResetThresholdMs

    this.lastQueryClientMs = now

    if (this.currentSnapshot === null) {
      this.seedInitialWindow(frames, now)
    }

    if (
      shouldResetWindow ||
      !this.currentSnapshot ||
      latestFrame!.simulationTick < this.currentSnapshot.simulationTick
    ) {
      this.resetToLatestFrame(frames, now)
      return this.buildWindow({
        latestFrame: latestFrame!,
        didResetWindow: true,
        nowMs: now,
        realMsToGameSeconds: params.realMsToGameSeconds,
      })
    }

    this.syncCurrentCycleState(frames, now)

    // 必须在 buildWindow 之前做升格喵。
    // 旧逻辑是先 buildWindow 再 promote，导致 elapsed≥50ms 时
    // buildWindow 先返回 alpha=1.0 的卡帧窗口，promote 才升格——
    // 下一帧 alpha 从 0 开始，就产生了"停一帧→跳一下"的节奏抖动喵。
    this.promoteCompletedCycles(frames, now)

    const window = this.buildWindow({
      latestFrame: latestFrame!,
      didResetWindow: false,
      nowMs: now,
      realMsToGameSeconds: params.realMsToGameSeconds,
    })
    return window
  }

  /**
   * 获取当前插值窗口的调试快照喵。
   */
  getDebugState(params: {
    frames: HighFreqSnapshotFrame[]
    realMsToGameSeconds: (realMs: number) => number
    currentGameSeconds: number
    nowMs: number
  }): SnapshotInterpolationDebugState | null {
    const window = this.getWindow(params)
    if (!window) {
      return null
    }

    return {
      currentTick: window.currentSnapshot.simulationTick,
      nextTick: window.nextSnapshotBuffer?.simulationTick ?? null,
      latestTick: window.latestFrame.simulationTick,
      renderAlpha: window.renderAlpha,
      renderGameSeconds: window.renderGameSeconds,
      mode: window.mode,
      didResetWindow: window.didResetWindow,
    }
  }

  /**
   * 规范化帧列表喵。
   *
   * 这里会按 `simulationTick` 去重并只保留最近若干帧喵。
   * 同 Tick 的高频快照只保留最新内容喵。
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
   * 初次进入插值层时优先用最近一对相邻逻辑帧建立窗口喵。
   */
  private seedInitialWindow(
    frames: HighFreqSnapshotFrame[],
    nowMs: number,
  ): void {
    const latestFrame = frames[frames.length - 1] ?? null
    const previousFrame = frames.length >= 2 ? frames[frames.length - 2] : null
    const beforePreviousFrame = frames.length >= 3 ? frames[frames.length - 3] : null

    if (!latestFrame || !previousFrame) {
      this.resetToLatestFrame(frames, nowMs)
      return
    }

    this.currentSnapshot = beforePreviousFrame ?? previousFrame
    this.nextSnapshotBuffer = beforePreviousFrame ? previousFrame : latestFrame
    this.bufferedFutureSnapshot = beforePreviousFrame ? latestFrame : null
    this.cycleStartClientMs = nowMs
    this.cycleDurationMs = this.calculateCycleDurationMs(
      this.currentSnapshot,
      this.nextSnapshotBuffer,
    )
  }

  /**
   * 重置为最新权威帧并清空下一逻辑帧缓冲喵。
   */
  private resetToLatestFrame(frames: HighFreqSnapshotFrame[], nowMs: number): void {
    const latestFrame = frames[frames.length - 1]!
    const previousFrame = frames.length >= 2 ? frames[frames.length - 2]! : null
    const beforePreviousFrame = frames.length >= 3 ? frames[frames.length - 3]! : null
    this.currentSnapshot = beforePreviousFrame ?? previousFrame ?? latestFrame
    this.nextSnapshotBuffer =
      beforePreviousFrame
        ? previousFrame
        : previousFrame
          ? latestFrame
          : null
    this.bufferedFutureSnapshot =
      beforePreviousFrame
        ? latestFrame
        : null
    this.cycleStartClientMs = nowMs
    this.cycleDurationMs = this.calculateCycleDurationMs(
      this.currentSnapshot,
      this.nextSnapshotBuffer,
    )
  }

  /**
   * 同步当前双缓冲窗口状态喵。
   *
   * 当前逻辑帧固定喵，下一逻辑帧缓冲在当前周期内允许被刷新喵。
   */
  private syncCurrentCycleState(
    frames: HighFreqSnapshotFrame[],
    nowMs: number,
  ): void {
    if (!this.currentSnapshot) {
      return
    }

    if (this.nextSnapshotBuffer) {
      const refreshedNextSnapshot = this.findFrameByTick(
        frames,
        this.nextSnapshotBuffer.simulationTick,
      )
      if (refreshedNextSnapshot) {
        this.nextSnapshotBuffer = refreshedNextSnapshot
      }
    }

    if (this.bufferedFutureSnapshot) {
      const refreshedBufferedSnapshot = this.findFrameByTick(
        frames,
        this.bufferedFutureSnapshot.simulationTick,
      )
      if (refreshedBufferedSnapshot) {
        this.bufferedFutureSnapshot = refreshedBufferedSnapshot
      }
    }

    if (!this.currentSnapshot) {
      return
    }

    if (!this.nextSnapshotBuffer) {
      const nextSnapshot = this.findNextFrameAfter(frames, this.currentSnapshot.simulationTick)
      if (!nextSnapshot) {
        return
      }

      this.nextSnapshotBuffer = nextSnapshot
      this.cycleStartClientMs = nowMs
      this.cycleDurationMs = this.calculateCycleDurationMs(
        this.currentSnapshot,
        this.nextSnapshotBuffer,
      )
    }

    if (!this.bufferedFutureSnapshot && this.nextSnapshotBuffer) {
      this.bufferedFutureSnapshot = this.findNextFrameAfter(
        frames,
        this.nextSnapshotBuffer.simulationTick,
      )
    }
  }

  /**
   * 当当前渲染周期走完时，把下一逻辑帧缓冲升格为当前逻辑帧喵。
   */
  private promoteCompletedCycles(
    frames: HighFreqSnapshotFrame[],
    nowMs: number,
  ): void {
    if (this.cycleStartClientMs === null) {
      this.cycleStartClientMs = nowMs
    }

    if (
      this.currentSnapshot &&
      this.nextSnapshotBuffer &&
      this.cycleStartClientMs !== null &&
      nowMs - this.cycleStartClientMs >= this.cycleDurationMs
    ) {
      this.currentSnapshot = this.nextSnapshotBuffer
      this.nextSnapshotBuffer = this.bufferedFutureSnapshot
      this.bufferedFutureSnapshot = null
      // 当前段播完以后喵，下一段从“现在”重新开始它自己的完整播放周期喵，
      // 绝不能把上一段的超出时间结转进来喵。
      this.cycleStartClientMs = nowMs

      if (this.nextSnapshotBuffer) {
        this.bufferedFutureSnapshot = this.findNextFrameAfter(
          frames,
          this.nextSnapshotBuffer.simulationTick,
        )
        this.cycleDurationMs = this.calculateCycleDurationMs(
          this.currentSnapshot,
          this.nextSnapshotBuffer,
        )
      } else {
        // 升格后 nextSnapshotBuffer 为 null（bufferedFutureSnapshot 也为空）喵。
        // 尝试从帧缓存里找下一帧，避免进入冻结/外推模式喵。
        this.nextSnapshotBuffer = this.findNextFrameAfter(
          frames,
          this.currentSnapshot.simulationTick,
        )
        if (this.nextSnapshotBuffer) {
          this.cycleStartClientMs = nowMs
          this.cycleDurationMs = this.calculateCycleDurationMs(
            this.currentSnapshot,
            this.nextSnapshotBuffer,
          )
        }
      }
    }
  }

  /**
   * 构建当前应使用的插值窗口喵。
   */
  private buildWindow(params: {
    latestFrame: HighFreqSnapshotFrame
    didResetWindow: boolean
    nowMs: number
    realMsToGameSeconds: (realMs: number) => number
  }): SnapshotInterpolationWindow {
    const currentSnapshot = this.currentSnapshot ?? params.latestFrame
    const nextSnapshotBuffer = this.nextSnapshotBuffer
    const cycleStartClientMs = this.cycleStartClientMs ?? params.nowMs
    const cycleElapsedMs = Math.max(0, params.nowMs - cycleStartClientMs)

    if (nextSnapshotBuffer) {
      const renderAlpha = clamp01(cycleElapsedMs / this.cycleDurationMs)
      const renderGameSeconds = lerp(
        currentSnapshot.totalGameSecondsExact,
        nextSnapshotBuffer.totalGameSecondsExact,
        renderAlpha,
      )
      return {
        currentSnapshot,
        nextSnapshotBuffer,
        latestFrame: params.latestFrame,
        renderAlpha,
        renderGameSeconds,
        didResetWindow: params.didResetWindow,
        mode: 'interpolate',
      }
    }

    const extrapolationGameSeconds = Math.max(
      0,
      Math.min(
        params.realMsToGameSeconds(
          Math.max(0, cycleElapsedMs - this.cycleDurationMs),
        ),
        params.realMsToGameSeconds(this.config.maxExtrapolationMs),
      ),
    )

    return {
      currentSnapshot,
      nextSnapshotBuffer: null,
      latestFrame: params.latestFrame,
      renderAlpha: 1,
      renderGameSeconds: currentSnapshot.totalGameSecondsExact + extrapolationGameSeconds,
      didResetWindow: params.didResetWindow,
      mode:
        !params.didResetWindow && extrapolationGameSeconds > 0
          ? 'extrapolate'
          : 'freeze',
    }
  }

  /**
   * 根据两帧之间的游戏时间差计算插值周期喵。
   * 用游戏时间而非固定值，因为后端每 Tick 推进固定游戏时间增量（0.05s）喵。
   * 游戏时间差通过 gameSecondsPerRealSecond 换算为真实毫秒喵。
   */
  private calculateCycleDurationMs(
    from: HighFreqSnapshotFrame | null,
    to: HighFreqSnapshotFrame | null,
  ): number {
    if (!from || !to) {
      return this.config.logicFrameDurationMs
    }
    const gameDeltaSeconds = to.totalGameSecondsExact - from.totalGameSecondsExact
    if (gameDeltaSeconds <= 0) {
      return this.config.logicFrameDurationMs
    }
    // gameSecondsPerRealSecond 在低频快照里，这里用默认 1:1 换算喵。
    // 后端 1 Tick = 0.05 游戏秒 = 50 真实毫秒，所以直接 ×1000 喵。
    const durationMs = gameDeltaSeconds * 1000
    // 钳位到合理范围，避免异常帧导致周期过短或过长喵。
    return Math.max(20, Math.min(200, durationMs))
  }

  /**
   * 按 Tick 查找指定高频帧喵。
   */
  private findFrameByTick(
    frames: HighFreqSnapshotFrame[],
    simulationTick: number,
  ): HighFreqSnapshotFrame | null {
    for (let index = frames.length - 1; index >= 0; index -= 1) {
      const frame = frames[index]
      if (frame && frame.simulationTick === simulationTick) {
        return frame
      }
    }
    return null
  }

  /**
   * 查找某个 Tick 之后最早到达的权威帧喵。
   *
   * 说明喵：
   * - 理想情况下这里会命中 `currentTick + 1` 喵。
   * - 若网络/广播节流导致跳 Tick 喵，也要允许窗口接到“下一份更新后的权威帧”喵，避免永远卡死在旧帧喵。
   */
  private findNextFrameAfter(
    frames: HighFreqSnapshotFrame[],
    simulationTick: number,
  ): HighFreqSnapshotFrame | null {
    for (const frame of frames) {
      if (frame.simulationTick > simulationTick) {
        return frame
      }
    }
    return null
  }
}

function clamp01(value: number): number {
  if (value <= 0) {
    return 0
  }
  if (value >= 1) {
    return 1
  }
  return value
}

function lerp(from: number, to: number, alpha: number): number {
  return from + (to - from) * alpha
}
