/**
 * PredictionCorrector（预测纠正机制）喵。
 *
 * 作用：比较前端预测位置与后端权威位置，检测偏差并进行平滑纠正喵。
 * 当预测偏差超过容忍阈值时，以平滑的方式将显示位置调整到权威位置，避免视觉跳跃喵。
 *
 * 纠正策略：
 * 1. 检测偏差：计算预测位置与权威位置的欧氏距离喵。
 * 2. 阈值判断：使用用户指定的容忍阈值（默认 1.0 GU）喵。
 * 3. 平滑纠正：如果偏差超过阈值，使用插值速度逐渐纠正喵。
 * 4. 立即纠正：如果偏差极大（> 最大纠正距离），立即跳跃到权威位置喵。
 */

export interface Vec2d {
    x: number;
    y: number;
}

export interface EntityPredictionState {
    /** 实体ID喵。 */
    entityId: number;
    /** 预测位置（前端计算）喵。 */
    predictedPosition: Vec2d;
    /** 权威位置（后端快照）喵。 */
    authoritativePosition: Vec2d;
    /** 预测速度（前端计算）喵。 */
    predictedVelocity: Vec2d | null;
    /** 权威速度（后端快照）喵。 */
    authoritativeVelocity: Vec2d | null;
    /** 最后更新时间戳（performance.now()）喵。 */
    lastUpdateTimeMs: number;
}

export interface CorrectionResult {
    /** 纠正后的显示位置喵。 */
    displayPosition: Vec2d;
    /** 纠正后的显示速度（可选）喵。 */
    displayVelocity: Vec2d | null;
    /** 是否进行了纠正喵。 */
    corrected: boolean;
    /** 偏差距离（GU）喵。 */
    deviation: number;
    /** 纠正类型：'none' | 'smooth' | 'immediate'喵。 */
    correctionType: 'none' | 'smooth' | 'immediate';
}

export interface PredictionCorrectorConfig {
    /** 容忍阈值（GU）：偏差小于此值时忽略喵。 */
    toleranceThreshold: number;
    /** 最大纠正距离（GU）：偏差超过此值时立即跳跃喵。 */
    maxCorrectionDistance: number;
    /** 平滑纠正速度（GU/毫秒）喵。 */
    smoothCorrectionSpeed: number;
    /** 启用调试日志喵。 */
    debugLogging: boolean;
}

/**
 * 默认配置喵。
 */
const DEFAULT_CONFIG: PredictionCorrectorConfig = {
    toleranceThreshold: 1.0,      // 1.0 GU 容忍阈值（用户选择）
    maxCorrectionDistance: 100.0, // 超过100GU立即跳跃（与现有 MAX_INTERPOLATION_DISTANCE_GU 一致）
    smoothCorrectionSpeed: 0.05,  // 0.05 GU/毫秒（与现有 INTERPOLATION_SPEED_GU_PER_MS 一致）
    debugLogging: false,
};

/**
 * 预测纠正器喵。
 */
export class PredictionCorrector {
    private config: PredictionCorrectorConfig;
    private predictionStates = new Map<number, EntityPredictionState>();

    constructor(config: Partial<PredictionCorrectorConfig> = {}) {
        this.config = { ...DEFAULT_CONFIG, ...config };
    }

    /**
     * 更新实体预测状态喵。
     * 当收到新快照或前端预测更新时调用喵。
     *
     * @param entityId 实体ID
     * @param predictedPosition 预测位置
     * @param authoritativePosition 权威位置
     * @param predictedVelocity 预测速度（可选）
     * @param authoritativeVelocity 权威速度（可选）
     */
    updateEntityState(
        entityId: number,
        predictedPosition: Vec2d,
        authoritativePosition: Vec2d,
        predictedVelocity?: Vec2d | null,
        authoritativeVelocity?: Vec2d | null
    ): void {
        const now = performance.now();
        const state: EntityPredictionState = {
            entityId,
            predictedPosition,
            authoritativePosition,
            predictedVelocity: predictedVelocity ?? null,
            authoritativeVelocity: authoritativeVelocity ?? null,
            lastUpdateTimeMs: now,
        };
        this.predictionStates.set(entityId, state);

        if (this.config.debugLogging) {
            const deviation = this.calculateDistance(predictedPosition, authoritativePosition);
            if (deviation > this.config.toleranceThreshold) {
                console.debug(`[PredictionCorrector] 实体 ${entityId} 预测偏差: ${deviation.toFixed(2)} GU喵。`);
            }
        }
    }

    /**
     * 移除实体状态喵。
     * 当实体被销毁或不再需要预测时调用喵。
     *
     * @param entityId 实体ID
     */
    removeEntityState(entityId: number): void {
        this.predictionStates.delete(entityId);
    }

    /**
     * 清除所有实体状态喵。
     */
    clearAllStates(): void {
        this.predictionStates.clear();
    }

    /**
     * 计算纠正结果喵。
     * 每帧调用此方法获取纠正后的显示位置喵。
     *
     * @param entityId 实体ID
     * @param deltaTimeMs 时间增量（毫秒）
     * @returns 纠正结果
     */
    calculateCorrection(entityId: number, deltaTimeMs: number): CorrectionResult {
        const state = this.predictionStates.get(entityId);
        if (!state) {
            // 无预测状态，使用权威位置喵
            return {
                displayPosition: { x: 0, y: 0 }, // 占位，实际应由调用者提供权威位置
                displayVelocity: null,
                corrected: false,
                deviation: 0,
                correctionType: 'none',
            };
        }

        const deviation = this.calculateDistance(state.predictedPosition, state.authoritativePosition);

        // 偏差在容忍阈值内，使用预测位置喵
        if (deviation <= this.config.toleranceThreshold) {
            return {
                displayPosition: { ...state.predictedPosition },
                displayVelocity: state.predictedVelocity ? { ...state.predictedVelocity } : null,
                corrected: false,
                deviation,
                correctionType: 'none',
            };
        }

        // 偏差超过最大纠正距离，立即跳跃到权威位置喵
        if (deviation > this.config.maxCorrectionDistance) {
            if (this.config.debugLogging) {
                console.debug(`[PredictionCorrector] 实体 ${entityId} 偏差过大 (${deviation.toFixed(2)} GU)，立即跳跃到权威位置喵。`);
            }
            return {
                displayPosition: { ...state.authoritativePosition },
                displayVelocity: state.authoritativeVelocity ? { ...state.authoritativeVelocity } : null,
                corrected: true,
                deviation,
                correctionType: 'immediate',
            };
        }

        // 平滑纠正：以固定速度向权威位置移动喵
        const direction = this.calculateDirection(state.predictedPosition, state.authoritativePosition);
        const correctionAmount = Math.min(deviation, this.config.smoothCorrectionSpeed * deltaTimeMs);

        const displayPosition = {
            x: state.predictedPosition.x + direction.x * correctionAmount,
            y: state.predictedPosition.y + direction.y * correctionAmount,
        };

        // 速度平滑过渡：如果权威速度存在，逐渐向权威速度靠拢喵
        let displayVelocity = state.predictedVelocity ? { ...state.predictedVelocity } : null;
        if (state.authoritativeVelocity && state.predictedVelocity) {
            const velDeviation = this.calculateDistance(state.predictedVelocity, state.authoritativeVelocity);
            if (velDeviation > 0.1) { // 速度偏差阈值
                const velRatio = Math.min(1.0, (this.config.smoothCorrectionSpeed * deltaTimeMs * 10) / velDeviation);
                displayVelocity = {
                    x: state.predictedVelocity.x + (state.authoritativeVelocity.x - state.predictedVelocity.x) * velRatio,
                    y: state.predictedVelocity.y + (state.authoritativeVelocity.y - state.predictedVelocity.y) * velRatio,
                };
            }
        }

        if (this.config.debugLogging) {
            console.debug(`[PredictionCorrector] 实体 ${entityId} 平滑纠正: 偏差 ${deviation.toFixed(2)} GU, 纠正量 ${correctionAmount.toFixed(2)} GU喵。`);
        }

        return {
            displayPosition,
            displayVelocity,
            corrected: true,
            deviation,
            correctionType: 'smooth',
        };
    }

    /**
     * 批量计算纠正结果喵。
     *
     * @param entityIds 实体ID数组
     * @param deltaTimeMs 时间增量（毫秒）
     * @returns 纠正结果映射
     */
    calculateBatchCorrection(entityIds: number[], deltaTimeMs: number): Map<number, CorrectionResult> {
        const results = new Map<number, CorrectionResult>();
        for (const entityId of entityIds) {
            results.set(entityId, this.calculateCorrection(entityId, deltaTimeMs));
        }
        return results;
    }

    /**
     * 获取当前预测状态喵。
     *
     * @param entityId 实体ID
     * @returns 预测状态或 undefined
     */
    getEntityState(entityId: number): EntityPredictionState | undefined {
        return this.predictionStates.get(entityId);
    }

    /**
     * 更新配置喵。
     *
     * @param newConfig 新配置（部分）
     */
    updateConfig(newConfig: Partial<PredictionCorrectorConfig>): void {
        this.config = { ...this.config, ...newConfig };
        console.debug(`[PredictionCorrector] 配置已更新:`, this.config);
    }

    /**
     * 获取当前配置喵。
     */
    getConfig(): PredictionCorrectorConfig {
        return { ...this.config };
    }

    /**
     * 计算偏差统计喵。
     *
     * @returns 统计信息
     */
    getDeviationStats(): {
        totalEntities: number;
        maxDeviation: number;
        averageDeviation: number;
        entitiesAboveThreshold: number;
    } {
        let totalDeviation = 0;
        let maxDeviation = 0;
        let entitiesAboveThreshold = 0;

        for (const state of this.predictionStates.values()) {
            const deviation = this.calculateDistance(state.predictedPosition, state.authoritativePosition);
            totalDeviation += deviation;
            maxDeviation = Math.max(maxDeviation, deviation);
            if (deviation > this.config.toleranceThreshold) {
                entitiesAboveThreshold++;
            }
        }

        const totalEntities = this.predictionStates.size;
        const averageDeviation = totalEntities > 0 ? totalDeviation / totalEntities : 0;

        return {
            totalEntities,
            maxDeviation,
            averageDeviation,
            entitiesAboveThreshold,
        };
    }

    /**
     * 计算两点之间的距离喵。
     */
    private calculateDistance(a: Vec2d, b: Vec2d): number {
        const dx = a.x - b.x;
        const dy = a.y - b.y;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /**
     * 计算从起点到终点的方向单位向量喵。
     */
    private calculateDirection(from: Vec2d, to: Vec2d): Vec2d {
        const dx = to.x - from.x;
        const dy = to.y - from.y;
        const distance = Math.sqrt(dx * dx + dy * dy);
        if (distance === 0) {
            return { x: 0, y: 0 };
        }
        return { x: dx / distance, y: dy / distance };
    }
}

/**
 * 默认全局纠正器实例喵。
 */
export const defaultPredictionCorrector = new PredictionCorrector();