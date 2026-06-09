export interface GameTimeSnapshot {
    simulationTick: number;
    totalGameSeconds: number;
    totalGameSecondsExact?: number;
    deltaGameSeconds: number;
    timeScale?: number;
    gameSecondsPerRealSecond?: number;
}

export interface GameTimeState {
    currentTick: number;
    currentGameSeconds: number;
    realTimeSinceSnapshotMs: number;
    timeScale: number;
    gameSecondsPerRealSecond: number;
    isPaused: boolean;
    snapshotTimestampMs: number;
}

export const AUTHORITY_LOGIC_FRAME_DURATION_MS = 50;

export class GameTimeManager {
    private state: GameTimeState = {
        currentTick: 0,
        currentGameSeconds: 0,
        realTimeSinceSnapshotMs: 0,
        timeScale: 1.0,
        gameSecondsPerRealSecond: 1.0,
        isPaused: false,
        snapshotTimestampMs: 0,
    };

    updateSnapshot(snapshot: GameTimeSnapshot): void {
        const now = performance.now();
        const snapshotGameSeconds = snapshot.totalGameSecondsExact ?? snapshot.totalGameSeconds;

        this.state.currentTick = snapshot.simulationTick;
        // 高频快照到达时间会有网络抖动喵，
        // 如果这里每次都把连续游戏时间硬重置回快照时间喵，
        // 渲染侧 `alpha`（插值比例）就会在每次收包时忽快忽慢喵。
        // 当前先把本地连续时间保持单调不回退喵，
        // 只在权威时间明显走到更前面时才前推基线喵。
        this.state.currentGameSeconds = Math.max(
            this.state.currentGameSeconds,
            snapshotGameSeconds,
        );
        this.state.realTimeSinceSnapshotMs = 0;
        this.state.timeScale = snapshot.timeScale ?? 1.0;
        this.state.gameSecondsPerRealSecond = snapshot.gameSecondsPerRealSecond ?? 1.0;
        this.state.snapshotTimestampMs = now;
    }

    update(): GameTimeState {
        if (this.state.isPaused || this.getEffectiveGameSecondsPerRealSecond() === 0) {
            return this.state;
        }

        const now = performance.now();
        this.state.realTimeSinceSnapshotMs = now - this.state.snapshotTimestampMs;

        const realTimeSeconds = this.state.realTimeSinceSnapshotMs / 1000;
        this.state.currentGameSeconds += realTimeSeconds * this.getEffectiveGameSecondsPerRealSecond();
        this.state.snapshotTimestampMs = now;

        return this.state;
    }

    getCurrentState(): GameTimeState {
        return { ...this.state };
    }

    getCurrentGameSeconds(): number {
        return this.state.currentGameSeconds;
    }

    getCurrentTick(): number {
        return this.state.currentTick;
    }

    getTimeScale(): number {
        return this.state.timeScale;
    }

    setTimeScale(scale: number): void {
        if (scale < 0) {
            return;
        }
        this.state.timeScale = scale;
    }

    setPaused(paused: boolean): void {
        if (this.state.isPaused === paused) {
            return;
        }

        this.state.isPaused = paused;
        if (!paused) {
            this.state.snapshotTimestampMs = performance.now();
        }
    }

    isPaused(): boolean {
        return this.state.isPaused;
    }

    getDeltaSince(fromGameSeconds: number): number {
        return this.state.currentGameSeconds - fromGameSeconds;
    }

    gameSecondsToRealMs(gameSeconds: number): number {
        const effectiveRate = this.getEffectiveGameSecondsPerRealSecond();
        if (effectiveRate === 0) {
            return 0;
        }
        return (gameSeconds / effectiveRate) * 1000;
    }

    realMsToGameSeconds(realMs: number): number {
        return (realMs / 1000) * this.getEffectiveGameSecondsPerRealSecond();
    }

    getLogicFrameDurationMs(): number {
        return AUTHORITY_LOGIC_FRAME_DURATION_MS;
    }

    reset(): void {
        this.state = {
            currentTick: 0,
            currentGameSeconds: 0,
            realTimeSinceSnapshotMs: 0,
            timeScale: 1.0,
            gameSecondsPerRealSecond: 1.0,
            isPaused: false,
            snapshotTimestampMs: performance.now(),
        };
    }

    private getEffectiveGameSecondsPerRealSecond(): number {
        return this.state.gameSecondsPerRealSecond * this.state.timeScale;
    }
}

export const defaultGameTimeManager = new GameTimeManager();
