package staraxis.game.ship;

import staraxis.game.space.SpacePosition;

/**
 * MovementCommand（移动指令）喵。
 *
 * 作用：存储舰船的移动指令信息，用于简化后端计算喵。
 * 前端进行完整的视觉计算，后端只需存储指令和进行路径推测喵。
 *
 * 简化计算原理：
 * 1. 当玩家发出移动命令时，记录起始状态（位置、速度、朝向、时间）喵。
 * 2. 后端不再每tick执行完整物理计算，而是基于指令推测当前位置喵。
 * 3. 如果中途变更指令，根据上次指令发出后的移动时间计算出当前位置，然后开始新的指令计算喵。
 */
public class MovementCommand {

    /** 指令类型：移动到目标位置喵。 */
    public static final int TYPE_MOVE_TO = 1;
    /** 指令类型：停止移动喵。 */
    public static final int TYPE_STOP = 2;

    /** 指令类型喵。 */
    public final int commandType;
    public final String clientCommandId;

    /** 目标位置（世界坐标 GU），仅当 commandType == TYPE_MOVE_TO 时有效喵。 */
    public final SpacePosition targetPosition;

    /** 起始位置（世界坐标 GU）喵。 */
    public final SpacePosition startPosition;

    /** 起始速度（GU/游戏秒）喵。 */
    public final SpacePosition startVelocity;

    /** 起始朝向（角度制，0度朝+X方向）喵。 */
    public final double startHeadingDeg;

    /** 起始游戏时间（游戏秒）喵。 */
    public final double startGameSeconds;

    /** 起始模拟 tick 喵。 */
    public final int startSimulationTick;

    /** 最大速度（GU/游戏秒）喵。 */
    public final double maxSpeed;

    /** 基础加速度（GU/游戏秒²）喵。 */
    public final double baseAcceleration;

    /** 舰首朝向加速度加成（GU/游戏秒²）喵。 */
    public final double bowAccelerationBonus;

    /** 转向角速度（度/游戏秒）喵。 */
    public final double turnRate;

    /** 侧向移动速度惩罚系数（0.0~1.0）喵。 */
    public final double lateralSpeedPenalty;

    /** 反向移动速度惩罚系数（0.0~1.0）喵。 */
    public final double reverseSpeedPenalty;

    /** 私有构造函数，使用 Builder 模式创建实例喵。 */
    private MovementCommand(Builder builder) {
        this.commandType = builder.commandType;
        this.clientCommandId = builder.clientCommandId;
        this.targetPosition = builder.targetPosition;
        this.startPosition = builder.startPosition;
        this.startVelocity = builder.startVelocity;
        this.startHeadingDeg = builder.startHeadingDeg;
        this.startGameSeconds = builder.startGameSeconds;
        this.startSimulationTick = builder.startSimulationTick;
        this.maxSpeed = builder.maxSpeed;
        this.baseAcceleration = builder.baseAcceleration;
        this.bowAccelerationBonus = builder.bowAccelerationBonus;
        this.turnRate = builder.turnRate;
        this.lateralSpeedPenalty = builder.lateralSpeedPenalty;
        this.reverseSpeedPenalty = builder.reverseSpeedPenalty;
    }

    /**
     * 创建移动到目标位置的指令喵。
     *
     * @param targetPosition 目标位置
     * @param ship 舰船实体
     * @param gameSeconds 当前游戏秒数
     * @param simulationTick 当前模拟 tick
     * @return 移动指令
     */
    public static MovementCommand createMoveTo(SpacePosition targetPosition, ShipBody ship,
                                               String clientCommandId, double gameSeconds, int simulationTick) {
        return new Builder(TYPE_MOVE_TO)
                .clientCommandId(clientCommandId)
                .targetPosition(targetPosition)
                .startPosition(ship.posWorldGU != null ? ship.posWorldGU : SpacePosition.ORIGIN)
                .startVelocity(ship.velWorldGU != null ? ship.velWorldGU : SpacePosition.ORIGIN)
                .startHeadingDeg(ship.currentHeadingDeg)
                .startGameSeconds(gameSeconds)
                .startSimulationTick(simulationTick)
                .maxSpeed(ship.maxSpeed)
                .baseAcceleration(ship.baseAcceleration)
                .bowAccelerationBonus(ship.bowAccelerationBonus)
                .turnRate(ship.turnRate)
                .lateralSpeedPenalty(ship.lateralSpeedPenalty)
                .reverseSpeedPenalty(ship.reverseSpeedPenalty)
                .build();
    }

    /**
     * 创建停止移动指令喵。
     *
     * @param ship 舰船实体
     * @param gameSeconds 当前游戏秒数
     * @param simulationTick 当前模拟 tick
     * @return 停止指令
     */
    public static MovementCommand createStop(ShipBody ship, String clientCommandId, double gameSeconds, int simulationTick) {
        return new Builder(TYPE_STOP)
                .clientCommandId(clientCommandId)
                .targetPosition(null)
                .startPosition(ship.posWorldGU != null ? ship.posWorldGU : SpacePosition.ORIGIN)
                .startVelocity(ship.velWorldGU != null ? ship.velWorldGU : SpacePosition.ORIGIN)
                .startHeadingDeg(ship.currentHeadingDeg)
                .startGameSeconds(gameSeconds)
                .startSimulationTick(simulationTick)
                .maxSpeed(ship.maxSpeed)
                .baseAcceleration(ship.baseAcceleration)
                .bowAccelerationBonus(ship.bowAccelerationBonus)
                .turnRate(ship.turnRate)
                .lateralSpeedPenalty(ship.lateralSpeedPenalty)
                .reverseSpeedPenalty(ship.reverseSpeedPenalty)
                .build();
    }

    /**
     * 检查指令是否已完成喵。
     *
     * @param currentPosition 当前位置
     * @param currentGameSeconds 当前游戏秒数
     * @return 如果指令已完成返回 true
     */
    public boolean isCompleted(SpacePosition currentPosition, double currentGameSeconds) {
        if (commandType == TYPE_STOP) {
            // 停止指令：当速度接近0时完成喵
            double speed = startVelocity.length();
            return speed < 0.01;
        } else if (commandType == TYPE_MOVE_TO) {
            // 移动指令：当接近目标时完成喵
            double distance = currentPosition.distanceTo(targetPosition);
            return distance < 20.0; // 与 ShipMovementSystem 中的阈值一致喵
        }
        return true;
    }

    /**
     * 计算从指令开始到现在的游戏时间差喵。
     *
     * @param currentGameSeconds 当前游戏秒数
     * @return 时间差（游戏秒）
     */
    public double getElapsedGameSeconds(double currentGameSeconds) {
        return Math.max(0, currentGameSeconds - startGameSeconds);
    }

    /**
     * Builder 模式用于构建 MovementCommand 实例喵。
     */
    public static class Builder {
        private final int commandType;
        private String clientCommandId;
        private SpacePosition targetPosition;
        private SpacePosition startPosition = SpacePosition.ORIGIN;
        private SpacePosition startVelocity = SpacePosition.ORIGIN;
        private double startHeadingDeg = 0.0;
        private double startGameSeconds = 0.0;
        private int startSimulationTick = 0;
        private double maxSpeed = 20.0;
        private double baseAcceleration = 5.0;
        private double bowAccelerationBonus = 5.0;
        private double turnRate = 45.0;
        private double lateralSpeedPenalty = 0.6;
        private double reverseSpeedPenalty = 0.3;

        public Builder(int commandType) {
            this.commandType = commandType;
        }

        public Builder clientCommandId(String clientCommandId) {
            this.clientCommandId = clientCommandId;
            return this;
        }

        public Builder targetPosition(SpacePosition targetPosition) {
            this.targetPosition = targetPosition;
            return this;
        }

        public Builder startPosition(SpacePosition startPosition) {
            this.startPosition = startPosition;
            return this;
        }

        public Builder startVelocity(SpacePosition startVelocity) {
            this.startVelocity = startVelocity;
            return this;
        }

        public Builder startHeadingDeg(double startHeadingDeg) {
            this.startHeadingDeg = startHeadingDeg;
            return this;
        }

        public Builder startGameSeconds(double startGameSeconds) {
            this.startGameSeconds = startGameSeconds;
            return this;
        }

        public Builder startSimulationTick(int startSimulationTick) {
            this.startSimulationTick = startSimulationTick;
            return this;
        }

        public Builder maxSpeed(double maxSpeed) {
            this.maxSpeed = maxSpeed;
            return this;
        }

        public Builder baseAcceleration(double baseAcceleration) {
            this.baseAcceleration = baseAcceleration;
            return this;
        }

        public Builder bowAccelerationBonus(double bowAccelerationBonus) {
            this.bowAccelerationBonus = bowAccelerationBonus;
            return this;
        }

        public Builder turnRate(double turnRate) {
            this.turnRate = turnRate;
            return this;
        }

        public Builder lateralSpeedPenalty(double lateralSpeedPenalty) {
            this.lateralSpeedPenalty = lateralSpeedPenalty;
            return this;
        }

        public Builder reverseSpeedPenalty(double reverseSpeedPenalty) {
            this.reverseSpeedPenalty = reverseSpeedPenalty;
            return this;
        }

        public MovementCommand build() {
            return new MovementCommand(this);
        }
    }
}
