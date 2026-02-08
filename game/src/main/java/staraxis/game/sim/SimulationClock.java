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
     * 默认时间口径：1 现实秒 = 25 tick = 1 游戏分钟喵。
     */
    public static final double BASE_GAME_MINUTES_PER_REAL_SECOND = 1.0;

    /**
     * PrepareTick 阶段：推进 simulationTick，自增当日累计游戏小时数。
     */
    public static double prepareTick(SimulationTime time) {
        if (time == null) {
            throw new IllegalArgumentException("time_required");
        }

        time.simulationTick += 1;

        // 玩家档位：每现实秒推进的游戏分钟数（minutes/realSecond）喵。
        // 系统倍率：timeScale，由系统控制（例如性能限制/战斗加速）喵。
        double effectiveGameMinutesPerRealSecond = time.playerTimeStep * time.timeScale;

        // 固定口径：25 tick / 现实秒；所以每 tick 推进的游戏小时数为：
        // (minutes/realSecond) / 60 / 25 = minutes / 1500 喵。
        double dtGameHours = (effectiveGameMinutesPerRealSecond / 60.0) / TICKS_PER_SECOND;
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
