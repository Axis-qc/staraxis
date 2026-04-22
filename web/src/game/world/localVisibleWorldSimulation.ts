import { getLocalVisibleWorld } from './localVisibleWorld'
import { defaultGameTimeManager } from '../time/GameTimeManager'

export interface WorldSimulationConfig {
  maxDeltaGameSeconds: number
  enableTimeScaling: boolean
  timeScale: number
}

const DEFAULT_CONFIG: WorldSimulationConfig = {
  maxDeltaGameSeconds: 0.5,
  enableTimeScaling: false,
  timeScale: 1.0,
}

export interface WorldSimulationState {
  isRunning: boolean
  lastUpdateTimeMs: number | null
  accumulatedGameSeconds: number
  config: WorldSimulationConfig
  frameHandle: number | null
}

export class LocalVisibleWorldSimulation {
  private state: WorldSimulationState

  constructor(config: Partial<WorldSimulationConfig> = {}) {
    this.state = {
      isRunning: false,
      lastUpdateTimeMs: null,
      accumulatedGameSeconds: 0,
      config: { ...DEFAULT_CONFIG, ...config },
      frameHandle: null,
    }
  }

  start(): void {
    if (this.state.isRunning) {
      return
    }

    this.state.isRunning = true
    this.state.lastUpdateTimeMs = null
    this.state.accumulatedGameSeconds = 0
    this.scheduleNextFrame()
  }

  stop(): void {
    if (!this.state.isRunning) {
      return
    }

    this.state.isRunning = false
    this.state.lastUpdateTimeMs = null
    this.state.accumulatedGameSeconds = 0
    if (this.state.frameHandle !== null) {
      cancelAnimationFrame(this.state.frameHandle)
      this.state.frameHandle = null
    }
  }

  reset(): void {
    this.state.lastUpdateTimeMs = null
    this.state.accumulatedGameSeconds = 0
    getLocalVisibleWorld().clearPredictedShips()
  }

  update(currentTimeMs: number): number {
    if (!this.state.isRunning) {
      return 0
    }

    if (this.state.lastUpdateTimeMs === null) {
      this.state.lastUpdateTimeMs = currentTimeMs
      return 0
    }

    const deltaRealMs = currentTimeMs - this.state.lastUpdateTimeMs
    this.state.lastUpdateTimeMs = currentTimeMs

    let deltaGameSeconds = defaultGameTimeManager.realMsToGameSeconds(deltaRealMs)
    if (this.state.config.enableTimeScaling) {
      deltaGameSeconds *= this.state.config.timeScale
    }

    if (deltaGameSeconds > this.state.config.maxDeltaGameSeconds) {
      deltaGameSeconds = this.state.config.maxDeltaGameSeconds
    }

    if (deltaGameSeconds <= 0) {
      return 0
    }

    this.state.accumulatedGameSeconds += deltaGameSeconds

    defaultGameTimeManager.advanceByGameSeconds(deltaGameSeconds)
    getLocalVisibleWorld().advancePredictedShips(deltaGameSeconds)
    return deltaGameSeconds
  }

  advanceBy(deltaGameSeconds: number): void {
    if (!this.state.isRunning || deltaGameSeconds <= 0) {
      return
    }

    const actualDelta = Math.min(deltaGameSeconds, this.state.config.maxDeltaGameSeconds)
    defaultGameTimeManager.advanceByGameSeconds(actualDelta)
    getLocalVisibleWorld().advancePredictedShips(actualDelta)
    this.state.accumulatedGameSeconds += actualDelta
  }

  updateConfig(config: Partial<WorldSimulationConfig>): void {
    this.state.config = { ...this.state.config, ...config }
  }

  getConfig(): WorldSimulationConfig {
    return { ...this.state.config }
  }

  getState(): WorldSimulationState {
    return { ...this.state }
  }

  getAccumulatedGameSeconds(): number {
    return this.state.accumulatedGameSeconds
  }

  isRunning(): boolean {
    return this.state.isRunning
  }

  private scheduleNextFrame(): void {
    this.state.frameHandle = requestAnimationFrame((timeMs) => {
      if (!this.state.isRunning) {
        this.state.frameHandle = null
        return
      }

      this.update(timeMs)
      this.scheduleNextFrame()
    })
  }
}

let globalSimulationInstance: LocalVisibleWorldSimulation | null = null

export function getLocalVisibleWorldSimulation(): LocalVisibleWorldSimulation {
  if (!globalSimulationInstance) {
    globalSimulationInstance = new LocalVisibleWorldSimulation()
  }
  return globalSimulationInstance
}

export function resetLocalVisibleWorldSimulation(): void {
  globalSimulationInstance = null
}
