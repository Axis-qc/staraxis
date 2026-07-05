/*
 * MovementCommand
 *
 * 文件作用：
 * - 存储舰船的移动指令信息，记录目标位置和指令识别。
 * - 用于追踪客户端下发指令的执行状态。
 *
 * 使用方式：
 * - MoveShipHandler 创建指令写入 ship.movementCommand。
 * - ShipMovementSystem 在到达目标后清除 ship.movementCommand。
 *
 * 注意事项：
 * - 仅用于指令追踪和到达判定，不再参与物理计算。
 */

package staraxis.game.ship;

import staraxis.game.space.SpacePosition;

/**
 * MovementCommand（移动指令）。
 *
 * 存储舰船的移动指令信息，用于追踪指令的完整生命周期。
 */
public class MovementCommand {

    /** 指令类型：移动到目标位置。 */
    public static final int TYPE_MOVE_TO = 1;

    /** 指令类型。 */
    public final int commandType;
    public final String clientCommandId;

    /** 目标位置（世界坐标 GU）。 */
    public final SpacePosition targetPosition;

    /** 起始位置（世界坐标 GU）。 */
    public final SpacePosition startPosition;

    /** 起始速度（GU/游戏秒）。 */
    public final SpacePosition startVelocity;

    /** 起始游戏时间（游戏秒）。 */
    public final double startGameSeconds;

    /** 起始模拟 tick。 */
    public final int startSimulationTick;

    private MovementCommand(Builder builder) {
        this.commandType = builder.commandType;
        this.clientCommandId = builder.clientCommandId;
        this.targetPosition = builder.targetPosition;
        this.startPosition = builder.startPosition;
        this.startVelocity = builder.startVelocity;
        this.startGameSeconds = builder.startGameSeconds;
        this.startSimulationTick = builder.startSimulationTick;
    }

    /**
     * 创建移动到目标位置的指令。
     */
    public static MovementCommand createMoveTo(SpacePosition targetPosition, ShipBody ship,
                                               String clientCommandId, double gameSeconds, int simulationTick) {
        return new Builder(TYPE_MOVE_TO)
                .clientCommandId(clientCommandId)
                .targetPosition(targetPosition)
                .startPosition(ship.posWorldGU != null ? ship.posWorldGU : SpacePosition.ORIGIN)
                .startVelocity(ship.velWorldGU != null ? ship.velWorldGU : SpacePosition.ORIGIN)
                .startGameSeconds(gameSeconds)
                .startSimulationTick(simulationTick)
                .build();
    }

    /**
     * 计算从指令开始到现在的游戏时间差。
     */
    public double getElapsedGameSeconds(double currentGameSeconds) {
        return Math.max(0, currentGameSeconds - startGameSeconds);
    }

    /**
     * Builder 模式。
     */
    public static class Builder {
        private final int commandType;
        private String clientCommandId;
        private SpacePosition targetPosition;
        private SpacePosition startPosition = SpacePosition.ORIGIN;
        private SpacePosition startVelocity = SpacePosition.ORIGIN;
        private double startGameSeconds = 0.0;
        private int startSimulationTick = 0;

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

        public Builder startGameSeconds(double startGameSeconds) {
            this.startGameSeconds = startGameSeconds;
            return this;
        }

        public Builder startSimulationTick(int startSimulationTick) {
            this.startSimulationTick = startSimulationTick;
            return this;
        }

        public MovementCommand build() {
            return new MovementCommand(this);
        }
    }
}
