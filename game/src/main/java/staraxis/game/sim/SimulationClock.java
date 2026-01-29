package staraxis.game.sim;

/**
 * SimulationClock
 *
 * 单机版“模拟时间推进”纯逻辑实现。
 */
public final class SimulationClock {

    private SimulationClock() {
    }

    public static final int TICKS_PER_SECOND = 25;

    /**
     * 每游戏小时对应的标准 Tick 数；仅用于 timeScale=1.0 的基准换算。
     */
    public static final int TICKS_PER_HOUR = 25;

    /**
     * baseDtGameHours = 1 / ticksPerHour = 1/25 游戏小时。
     */
    public static final double BASE_DT_GAME_HOURS = 1.0 / TICKS_PER_HOUR;

    /**
     * PrepareTick 阶段：推进 simulationTick，自增当日累计游戏小时数。
     */
    public static double prepareTick(SimulationTime time) {
        if (time == null) {
            throw new IllegalArgumentException("time_required");
        }

        time.simulationTick += 1;

        double dtGameHours = BASE_DT_GAME_HOURS * time.timeScale;
        time.accGameHoursInDay += dtGameHours;

        return dtGameHours;
    }

    /**
     * Commit 阶段：跨日判断与日序号推进；返回本 tick 是否触发了日结算。
     */
    public static boolean commitTick(SimulationTime time) {
        if (time == null) {
            throw new IllegalArgumentException("time_required");
        }

        if (time.accGameHoursInDay >= 24.0) {
            time.accGameHoursInDay -= 24.0;
            time.gameDatetimeDay += 1;
            return true;
        }

        return false;
    }
}
