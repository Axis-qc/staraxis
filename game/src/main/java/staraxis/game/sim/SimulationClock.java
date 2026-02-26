package staraxis.game.sim;

/**
 * SimulationClock
 *
 * 时间轴常量定义（无状态工具类）喵。
 *
 * 职责边界：
 * - 仅提供统一时间换算与节拍常量。
 * - 不包含任何时间推进逻辑（推进由 {@link TimelineSystem}负责）喵。
 */
public final class SimulationClock {

    private SimulationClock() {
    }

    /**
     * 固定模拟节拍：每现实秒包含的 simulation tick 数喵。
     *
     * 口径：现实 1 秒 = 20 tick。
     */
    public static final int TICKS_PER_SECOND = 20;

    /**
     * 默认“现实秒 -> 游戏秒”基础倍率喵。
     *
     * 口径：1.0 表示现实 1 秒推进游戏 1 秒。
     * 实际推进还会叠加 timeScale：
     * dtGameSeconds = (gameSecondsPerRealSecond * timeScale) / TICKS_PER_SECOND 喵。
     */
    public static final double BASE_GAME_SECONDS_PER_REAL_SECOND = 1.0;

    /** 每分钟秒数喵。 */
    public static final int SECONDS_PER_MINUTE = 60;

    /** 每小时分钟数喵。 */
    public static final int MINUTES_PER_HOUR = 60;

    /** 每天小时数喵。 */
    public static final int HOURS_PER_DAY = 24;

    /** 每月天数（游戏历法）喵。 */
    public static final int DAYS_PER_MONTH = 30;

    /** 每年月份数（游戏历法）喵。 */
    public static final int MONTHS_PER_YEAR = 12;

    /** 每小时秒数喵。 */
    public static final int SECONDS_PER_HOUR = SECONDS_PER_MINUTE * MINUTES_PER_HOUR;

    /** 每天秒数喵。 */
    public static final int SECONDS_PER_DAY = SECONDS_PER_HOUR * HOURS_PER_DAY;

    /** 每月秒数（基于 30 天）喵。 */
    public static final int SECONDS_PER_MONTH = SECONDS_PER_DAY * DAYS_PER_MONTH;

    /** 每年秒数（基于 12 月）喵。 */
    public static final int SECONDS_PER_YEAR = SECONDS_PER_MONTH * MONTHS_PER_YEAR;
}
