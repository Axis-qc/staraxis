/**
 * GameTimeManager（游戏时间管理器）喵。
 *
 * 作用：同步后端游戏时间，提供插值时间用于前端预测计算喵。
 * 后端通过快照推送权威时间（simulationTick, totalGameSeconds, deltaGameSeconds），
 * 前端使用 performance.now() 进行微秒级插值，在快照之间平滑推进游戏时间喵。
 *
 * 注意事项：
 * 1. 时间单位统一使用游戏秒（与后端一致）喵。
 * 2. 使用双精度浮点数，保持高精度喵。
 * 3. 处理时间缩放（timeScale）和暂停状态喵。
 */

export interface GameTimeSnapshot {
    /** 权威模拟 tick（整数，单调递增）喵。 */
    simulationTick: number;
    /** 权威游戏总秒数（向下取整）喵。 */
    totalGameSeconds: number;
    /** 本次 tick 推进的游戏秒数（Δt）喵。 */
    deltaGameSeconds: number;
    /** 游戏时间缩放比例（默认 1.0）喵。 */
    timeScale?: number;
}

export interface GameTimeState {
    /** 当前模拟 tick（来自最新快照）喵。 */
    currentTick: number;
    /** 当前游戏总秒数（插值结果）喵。 */
    currentGameSeconds: number;
    /** 自上次快照以来的实时流逝时间（毫秒）喵。 */
    realTimeSinceSnapshotMs: number;
    /** 游戏时间缩放比例喵。 */
    timeScale: number;
    /** 是否处于暂停状态喵。 */
    isPaused: boolean;
    /** 快照时间戳（performance.now()）喵。 */
    snapshotTimestampMs: number;
}

/**
 * 游戏时间管理器喵。
 */
export class GameTimeManager {
    private state: GameTimeState = {
        currentTick: 0,
        currentGameSeconds: 0,
        realTimeSinceSnapshotMs: 0,
        timeScale: 1.0,
        isPaused: false,
        snapshotTimestampMs: 0,
    };

    /**
     * 更新权威时间快照喵。
     * 每当收到新的后端快照时调用此方法喵。
     *
     * @param snapshot 时间快照数据
     */
    updateSnapshot(snapshot: GameTimeSnapshot): void {
        const now = performance.now();

        this.state.currentTick = snapshot.simulationTick;
        this.state.currentGameSeconds = snapshot.totalGameSeconds;
        this.state.realTimeSinceSnapshotMs = 0;
        this.state.timeScale = snapshot.timeScale ?? 1.0;
        this.state.snapshotTimestampMs = now;

        console.debug(`[GameTimeManager] 时间快照更新: tick=${snapshot.simulationTick}, totalSeconds=${snapshot.totalGameSeconds.toFixed(2)}, delta=${snapshot.deltaGameSeconds.toFixed(2)}, scale=${this.state.timeScale}喵。`);
    }

    /**
     * 更新插值时间喵。
     * 每帧调用此方法以更新当前游戏时间喵。
     *
     * @returns 当前游戏时间状态
     */
    update(): GameTimeState {
        if (this.state.isPaused || this.state.timeScale === 0) {
            // 暂停状态，时间不推进喵
            return this.state;
        }

        const now = performance.now();
        this.state.realTimeSinceSnapshotMs = now - this.state.snapshotTimestampMs;

        // 计算游戏时间推进：实时时间 × 时间缩放比例喵
        const realTimeSeconds = this.state.realTimeSinceSnapshotMs / 1000;
        const gameTimeDelta = realTimeSeconds * this.state.timeScale;

        this.state.currentGameSeconds += gameTimeDelta;

        // 更新快照时间戳，为下一帧计算做准备喵
        this.state.snapshotTimestampMs = now;

        return this.state;
    }

    /**
     * 获取当前游戏时间状态（不推进时间）喵。
     */
    getCurrentState(): GameTimeState {
        return { ...this.state };
    }

    /**
     * 获取当前游戏秒数（插值结果）喵。
     */
    getCurrentGameSeconds(): number {
        return this.state.currentGameSeconds;
    }

    /**
     * 获取当前 tick 喵。
     */
    getCurrentTick(): number {
        return this.state.currentTick;
    }

    /**
     * 获取时间缩放比例喵。
     */
    getTimeScale(): number {
        return this.state.timeScale;
    }

    /**
     * 设置时间缩放比例喵。
     *
     * @param scale 时间缩放比例（>= 0）
     */
    setTimeScale(scale: number): void {
        if (scale < 0) {
            console.warn(`[GameTimeManager] 时间缩放比例不能小于 0: ${scale}喵。`);
            return;
        }
        this.state.timeScale = scale;
        console.debug(`[GameTimeManager] 时间缩放比例设置为: ${scale}喵。`);
    }

    /**
     * 暂停/恢复游戏时间喵。
     *
     * @param paused true 暂停，false 恢复喵。
     */
    setPaused(paused: boolean): void {
        if (this.state.isPaused === paused) {
            return;
        }

        this.state.isPaused = paused;
        if (!paused) {
            // 恢复时重置快照时间戳，避免计算大的时间跳跃喵
            this.state.snapshotTimestampMs = performance.now();
        }
        console.debug(`[GameTimeManager] 游戏时间 ${paused ? '暂停' : '恢复'}喵。`);
    }

    /**
     * 检查是否处于暂停状态喵。
     */
    isPaused(): boolean {
        return this.state.isPaused;
    }

    /**
     * 计算从指定游戏时间到当前的时间差（游戏秒）喵。
     *
     * @param fromGameSeconds 起始游戏秒数
     * @returns 时间差（游戏秒）
     */
    getDeltaSince(fromGameSeconds: number): number {
        return this.state.currentGameSeconds - fromGameSeconds;
    }

    /**
     * 计算游戏时间间隔对应的实时时间间隔喵。
     *
     * @param gameSeconds 游戏秒数
     * @returns 实时毫秒数
     */
    gameSecondsToRealMs(gameSeconds: number): number {
        if (this.state.timeScale === 0) {
            return 0;
        }
        return (gameSeconds / this.state.timeScale) * 1000;
    }

    /**
     * 计算实时时间间隔对应的游戏时间间隔喵。
     *
     * @param realMs 实时毫秒数
     * @returns 游戏秒数
     */
    realMsToGameSeconds(realMs: number): number {
        return (realMs / 1000) * this.state.timeScale;
    }

    /**
     * 重置时间管理器喵。
     */
    reset(): void {
        this.state = {
            currentTick: 0,
            currentGameSeconds: 0,
            realTimeSinceSnapshotMs: 0,
            timeScale: 1.0,
            isPaused: false,
            snapshotTimestampMs: performance.now(),
        };
        console.debug(`[GameTimeManager] 已重置喵。`);
    }
}

/**
 * 默认全局时间管理器实例喵。
 */
export const defaultGameTimeManager = new GameTimeManager();