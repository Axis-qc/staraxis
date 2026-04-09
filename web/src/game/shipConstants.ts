/**
 * 舰船物理常量（与后端保持一致）喵。
 *
 * 注意：这些值必须与 `game/src/main/java/staraxis/game/ship/ShipBody.java` 中的硬编码值完全一致喵。
 * 未来应该将这些值迁移到共享配置文件中喵。
 */

export const SHIP_CONSTANTS = {
    /** 最大速度（GU/游戏秒）喵。 */
    MAX_SPEED: 20.0,

    /** 基础加速度（GU/游戏秒²）喵。 */
    BASE_ACCELERATION: 5.0,

    /** 舰首朝向加速度加成（GU/游戏秒²）喵。 */
    BOW_ACCELERATION_BONUS: 5.0,

    /** 转向角速度（度/游戏秒）喵。 */
    TURN_RATE: 45.0,

    /** 侧向移动速度惩罚系数（0.0~1.0）喵。 */
    LATERAL_SPEED_PENALTY: 0.6,

    /** 反向移动速度惩罚系数（0.0~1.0）喵。 */
    REVERSE_SPEED_PENALTY: 0.3,

    /** 到达目标的距离阈值（GU）喵。 */
    TARGET_ARRIVAL_THRESHOLD: 20.0,

    /** 停止检查的最小速度阈值（GU/秒）喵。 */
    MIN_SPEED_THRESHOLD: 0.01,

    /** 速度差异阈值（GU/秒）喵。 */
    VELOCITY_DIFF_THRESHOLD: 0.01,
} as const;