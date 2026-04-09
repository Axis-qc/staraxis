/**
 * ShipMovementSystemFrontend（前端舰船移动系统）喵。
 *
 * 作用：完整复制后端的移动算法，在前端进行视觉预测计算喵。
 * 算法必须与 `game/src/main/java/staraxis/game/ship/ShipMovementSystem.java` 完全一致喵。
 *
 * 注意事项：
 * 1. 使用相同的物理常量和计算公式喵。
 * 2. 时间单位使用游戏秒（与后端一致）喵。
 * 3. 角度使用度（0度朝+X方向）喵。
 * 4. 所有计算使用双精度浮点数喵。
 */

import { SHIP_CONSTANTS } from '../shipConstants.ts';

/** 二维向量接口（模拟后端的 Vec2d）喵。 */
export interface Vec2d {
    x: number;
    y: number;
}

/** 前端舰船状态接口（模拟后端的 ShipBody）喵。 */
export interface ShipState {
    /** 实体ID喵。 */
    entityId: number;

    /** 当前位置（世界坐标 GU）喵。 */
    position: Vec2d;

    /** 当前速度（GU/游戏秒）喵。 */
    velocity: Vec2d | null;

    /** 移动目标位置（世界坐标 GU）喵。 */
    movementTarget: Vec2d | null;

    /** 是否正在移动喵。 */
    isMoving: boolean;

    /** 当前舰首朝向（角度制，0度朝+X方向）喵。 */
    currentHeadingDeg: number;

    /** 目标朝向（角度制，0度朝+X方向）喵。 */
    targetHeadingDeg: number;

    /** 最大速度（GU/游戏秒）喵。 */
    maxSpeed: number;

    /** 基础加速度（GU/游戏秒²）喵。 */
    baseAcceleration: number;

    /** 舰首朝向加速度加成（GU/游戏秒²）喵。 */
    bowAccelerationBonus: number;

    /** 转向角速度（度/游戏秒）喵。 */
    turnRate: number;

    /** 侧向移动速度惩罚系数（0.0~1.0）喵。 */
    lateralSpeedPenalty: number;

    /** 反向移动速度惩罚系数（0.0~1.0）喵。 */
    reverseSpeedPenalty: number;
}

/** 世界状态接口（模拟后端的 WorldState）喵。 */
export interface WorldState {
    /** 游戏时间（秒）喵。 */
    gameTimeSeconds: number;

    /** 其他世界状态字段（占位）喵。 */
}

/**
 * 前端舰船移动系统喵。
 */
export class ShipMovementSystemFrontend {

    /**
     * 更新所有舰船的移动状态喵。
     *
     * @param shipStates 舰船状态数组（会被修改）
     * @param worldState 世界状态
     * @param dtGameSeconds 时间增量（游戏秒）
     */
    update(shipStates: ShipState[], worldState: WorldState, dtGameSeconds: number): void {
        for (const ship of shipStates) {
            this.updateShip(ship, worldState, dtGameSeconds);
        }
    }

    /**
     * 更新单个舰船的移动状态喵。
     */
    private updateShip(ship: ShipState, worldState: WorldState, dtGameSeconds: number): void {
        // 更新舰首朝向（始终转向目标朝向）喵
        this.updateHeading(ship, dtGameSeconds);

        if (!ship.isMoving || !ship.movementTarget) {
            // 不在移动状态时减速到停止喵
            this.decelerateToStop(ship, dtGameSeconds, worldState);
            return;
        }

        this.updateShipMovement(ship, dtGameSeconds, worldState);
    }

    /**
     * 更新舰首朝向喵。
     */
    private updateHeading(ship: ShipState, dtGameSeconds: number): void {
        const headingDiff = this.normalizeAngle(ship.targetHeadingDeg - ship.currentHeadingDeg);
        const maxTurn = ship.turnRate * dtGameSeconds;

        if (Math.abs(headingDiff) <= maxTurn) {
            ship.currentHeadingDeg = ship.targetHeadingDeg;
        } else {
            ship.currentHeadingDeg += Math.sign(headingDiff) * maxTurn;
        }
        ship.currentHeadingDeg = this.normalizeAngle(ship.currentHeadingDeg);
    }

    /**
     * 减速到停止喵。
     */
    private decelerateToStop(ship: ShipState, dtGameSeconds: number, _worldState: WorldState): void {
        if (!ship.velocity) {
            ship.velocity = { x: 0, y: 0 };
            return;
        }

        const currentSpeed = this.calculateSpeed(ship.velocity);

        if (currentSpeed < 1.0) {
            ship.velocity = { x: 0, y: 0 };
            return;
        }

        // 使用基础加速度减速喵
        const decelAmount = ship.baseAcceleration * dtGameSeconds;
        const newSpeed = Math.max(0, currentSpeed - decelAmount);
        const scale = newSpeed / currentSpeed;

        ship.velocity = {
            x: ship.velocity.x * scale,
            y: ship.velocity.y * scale,
        };

        this.applyVelocity(ship, dtGameSeconds, _worldState);
    }

    /**
     * 更新单个舰船的移动（全向移动）喵。
     */
    private updateShipMovement(ship: ShipState, dtGameSeconds: number, worldState: WorldState): void {
        const target = ship.movementTarget!;

        // 计算到目标的距离和期望移动方向喵
        const dx = target.x - ship.position.x;
        const dy = target.y - ship.position.y;
        const distanceToTarget = Math.sqrt(dx * dx + dy * dy);

        // 如果已到达目标（阈值 20 GU），停止移动喵
        if (distanceToTarget < SHIP_CONSTANTS.TARGET_ARRIVAL_THRESHOLD) {
            ship.isMoving = false;
            ship.velocity = { x: 0, y: 0 };
            return;
        }

        // 期望移动方向（单位向量）喵
        const moveDirX = dx / distanceToTarget;
        const moveDirY = dy / distanceToTarget;

        // 计算当前舰首朝向的单位向量喵
        const headingRad = this.degToRad(ship.currentHeadingDeg);
        const bowX = Math.cos(headingRad);
        const bowY = Math.sin(headingRad);

        // 计算移动方向与舰首朝向的夹角喵
        const dotProduct = moveDirX * bowX + moveDirY * bowY;
        const angleDiff = this.radToDeg(Math.acos(Math.max(-1.0, Math.min(1.0, dotProduct))));

        // 根据夹角确定移动类型和参数喵
        let effectiveMaxSpeed: number;
        let effectiveAcceleration: number;

        if (angleDiff < 45.0) {
            // 正向移动（与舰首方向夹角小于45度）喵
            // 舰首朝向加成：全额加速度和最高速度喵
            effectiveMaxSpeed = ship.maxSpeed;
            effectiveAcceleration = ship.baseAcceleration + ship.bowAccelerationBonus;
        } else if (angleDiff > 135.0) {
            // 反向移动（与舰首方向夹角大于135度）喵
            effectiveMaxSpeed = ship.maxSpeed * ship.reverseSpeedPenalty;
            effectiveAcceleration = ship.baseAcceleration;
        } else {
            // 侧向移动（夹角在45-135度之间）喵
            effectiveMaxSpeed = ship.maxSpeed * ship.lateralSpeedPenalty;
            effectiveAcceleration = ship.baseAcceleration;
        }

        // 检查是否需要减速（距离目标是否足够近）喵
        const currentSpeed = ship.velocity ? this.calculateSpeed(ship.velocity) : 0;

        const stopDistance = (currentSpeed * currentSpeed) / (2 * effectiveAcceleration);
        const needDecelerate = stopDistance >= distanceToTarget;

        // 计算目标速度矢量喵
        const targetVelX = moveDirX * effectiveMaxSpeed;
        const targetVelY = moveDirY * effectiveMaxSpeed;

        // 更新速度喵
        if (needDecelerate) {
            // 减速到目标喵
            const decelAmount = effectiveAcceleration * dtGameSeconds;
            const newSpeed = Math.max(0, currentSpeed - decelAmount);
            if (currentSpeed > SHIP_CONSTANTS.MIN_SPEED_THRESHOLD) {
                const scale = newSpeed / currentSpeed;
                ship.velocity = {
                    x: ship.velocity!.x * scale,
                    y: ship.velocity!.y * scale,
                };
            } else {
                ship.velocity = { x: 0, y: 0 };
            }
        } else {
            // 向目标速度加速喵
            const currentVelX = ship.velocity ? ship.velocity.x : 0;
            const currentVelY = ship.velocity ? ship.velocity.y : 0;

            const velDiffX = targetVelX - currentVelX;
            const velDiffY = targetVelY - currentVelY;
            const velDiff = Math.sqrt(velDiffX * velDiffX + velDiffY * velDiffY);

            if (velDiff < SHIP_CONSTANTS.VELOCITY_DIFF_THRESHOLD) {
                // 已接近目标速度喵
                ship.velocity = { x: targetVelX, y: targetVelY };
            } else {
                // 向目标速度加速喵
                const accelAmount = Math.min(velDiff, effectiveAcceleration * dtGameSeconds);
                const ratio = accelAmount / velDiff;
                ship.velocity = {
                    x: currentVelX + velDiffX * ratio,
                    y: currentVelY + velDiffY * ratio,
                };
            }
        }

        // 应用速度更新位置喵
        this.applyVelocity(ship, dtGameSeconds, worldState);
    }

    /**
     * 应用速度更新位置喵。
     */
    private applyVelocity(ship: ShipState, dtGameSeconds: number, _worldState: WorldState): void {
        if (!ship.velocity) {
            return;
        }

        const newX = ship.position.x + ship.velocity.x * dtGameSeconds;
        const newY = ship.position.y + ship.velocity.y * dtGameSeconds;
        ship.position = { x: newX, y: newY };

        // 注意：前端不处理跨星区检测，这是后端的职责喵。
    }

    /**
     * 计算速度大小喵。
     */
    private calculateSpeed(velocity: Vec2d): number {
        return Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y);
    }

    /**
     * 将角度标准化到 [-180, 180) 范围喵。
     */
    private normalizeAngle(angle: number): number {
        let normalized = angle;
        while (normalized >= 180.0) {
            normalized -= 360.0;
        }
        while (normalized < -180.0) {
            normalized += 360.0;
        }
        return normalized;
    }

    /**
     * 度转弧度喵。
     */
    private degToRad(degrees: number): number {
        return degrees * Math.PI / 180.0;
    }

    /**
     * 弧度转度喵。
     */
    private radToDeg(radians: number): number {
        return radians * 180.0 / Math.PI;
    }
}