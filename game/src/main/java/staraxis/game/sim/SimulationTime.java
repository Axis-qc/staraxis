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
     * 全局时间倍率 (时间速度)；合成自玩家档位、技能倍率、系统上限的最终值。
     */
    public double timeScale = 1.0;

    public SimulationTime() {
    }
}
