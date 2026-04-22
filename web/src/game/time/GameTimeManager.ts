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

        this.state.currentTick = snapshot.simulationTick;
        this.state.currentGameSeconds = snapshot.totalGameSecondsExact ?? snapshot.totalGameSeconds;
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

    advanceByGameSeconds(deltaGameSeconds: number): number {
        if (!Number.isFinite(deltaGameSeconds) || deltaGameSeconds <= 0) {
            return 0;
        }

        this.state.currentGameSeconds += deltaGameSeconds;
        this.state.snapshotTimestampMs = performance.now();
        return deltaGameSeconds;
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
