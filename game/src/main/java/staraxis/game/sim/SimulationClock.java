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
     * 默认时间口径：1 现实秒 = 25 tick = 1 游戏秒喵。
     */
    public static final double BASE_GAME_SECONDS_PER_REAL_SECOND = 1.0;

    /**
     * 统一时间换算常量喵。
     * 现实 1 秒 -> 游戏 1 秒；1 分钟 60 秒；1 小时 60 分钟；1 天 24 小时；1 月 30 天；1 年 12 月喵。
     */
    public static final int SECONDS_PER_MINUTE = 60;

    public static final int MINUTES_PER_HOUR = 60;

    public static final int HOURS_PER_DAY = 24;

    public static final int DAYS_PER_MONTH = 30;

    public static final int MONTHS_PER_YEAR = 12;

    public static final int SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR;

    public static final int SECONDS_PER_DAY = SECONDS_PER_HOUR * HOURS_PER_DAY;

    public static final int SECONDS_PER_MONTH = SECONDS_PER_DAY * DAYS_PER_MONTH;

    public static final int SECONDS_PER_YEAR = SECONDS_PER_MONTH * MONTHS_PER_YEAR;

    /**
     * PrepareTick 阶段：推进 simulationTick，自增当日累计游戏小时数喵。
     *
     * 新口径：1 现实秒 = 1 游戏秒；因此每 tick 推进的游戏秒数为：
     * (seconds/realSecond) / TICKS_PER_SECOND 喵。
     *
     * 返回值仍保持为 dtGameHours（本 tick 推进的游戏小时数），供各系统以“小时”为单位推进喵。
     */
    public static double prepareTick(SimulationTime time) {
        if (time == null) {
            throw new IllegalArgumentException("time_required");
        }

        time.simulationTick += 1;

        // 系统倍率：timeScale，由系统控制（例如性能限制/战斗加速）喵。
        // 玩家档位：playerTimeStep 在新口径下不再参与权威时间推进（保留字段以兼容旧指令与 UI）喵。
        double effectiveGameSecondsPerRealSecond = time.gameSecondsPerRealSecond * time.timeScale;

        double dtGameSeconds = effectiveGameSecondsPerRealSecond / TICKS_PER_SECOND;
        double dtGameHours = dtGameSeconds / SECONDS_PER_HOUR;

        time.accGameHoursInDay += dtGameHours;

        return dtGameHours;
    }

    /**
     * Commit 阶段：跨日判断与日序号推进；返回本 tick 是否触发了日结算喵。
     *
     * 口径：1 天 24 小时；月份与年份由上层按 day 推导（30 天=1 月，12 月=1 年）喵。
     */
    public static boolean commitTick(SimulationTime time) {
        if (time == null) {
            throw new IllegalArgumentException("time_required");
        }

        if (time.accGameHoursInDay >= HOURS_PER_DAY) {
            time.accGameHoursInDay -= HOURS_PER_DAY;
            time.gameDatetimeDay += 1;
            return true;
        }

        return false;
    }
}
