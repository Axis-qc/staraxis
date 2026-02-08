package staraxis.game.sim;

/**
 * SimulationTime
 *
 * 权威模拟时间容器。
 */
public class SimulationTime {

    /**
     * 本地模拟 Tick；固定 25 tick/s；主循环 PrepareTick 阶段自增 +1。
     */
    public long simulationTick;

    /**
     * 游戏日序号 (从 1 开始)；当累计游戏小时数满 24h 后，在 Commit 阶段 +1。
     */
    public int gameDatetimeDay = 1;

    /**
     * 当日累计游戏小时数；范围 [0, 24)，是驱动日结算的核心变量。
     */
    public double accGameHoursInDay;

    /**
     * 玩家选择的时间推进速度（游戏分钟 / 现实秒）喵。
     * 可选档位：1, 5, 10, 30, 60(1h), 720(12h), 1440(1d) 喵。
     */
    public double playerTimeStep = 1.0;

    /**
     * 系统时间倍率喵。
     * 由系统根据性能、战斗状态等控制，不再由玩家直接修改喵。
     */
    public double timeScale = 1.0;

    public SimulationTime() {
    }
}
