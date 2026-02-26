package staraxis.game.sim;

/**
 * TimelineSystem
 *
 * 独立时间轴系统：负责权威时间推进与时间派生字段回写喵。
 */
public final class TimelineSystem {

    private TimelineSystem() {
    }

    /**
     * 推进一个 simulation tick，并回写权威时间字段喵。
     *
     * 口径：
     * - 现实 1 秒 = 20 tick。
     * - 默认现实 1 秒 -> 游戏 1 秒（可由 gameSecondsPerRealSecond 与 timeScale 共同放缩）喵。
     */
    public static TickAdvance advanceOneTick(SimulationTime time) {
        if (time == null) {
            throw new IllegalArgumentException("time_required");
        }

        int previousDay = time.gameDatetimeDay;

        time.simulationTick += 1;

        double effectiveGameSecondsPerRealSecond = time.gameSecondsPerRealSecond * time.timeScale;
        double dtGameSeconds = effectiveGameSecondsPerRealSecond / SimulationClock.TICKS_PER_SECOND;
        if (dtGameSeconds < 0) {
            dtGameSeconds = 0;
        }

        time.lastDeltaGameSeconds = dtGameSeconds;
        time.totalGameSecondsAcc += dtGameSeconds;

        recalcDerivedCalendarFields(time);

        boolean dayChanged = time.gameDatetimeDay != previousDay;
        double dtGameHours = dtGameSeconds / SimulationClock.SECONDS_PER_HOUR;

        return new TickAdvance(dtGameSeconds, dtGameHours, dayChanged);
    }

    /**
     * 基于 totalGameSecondsAcc 统一重算派生字段，保证时间源单一喵。
     */
    public static void recalcDerivedCalendarFields(SimulationTime time) {
        if (time == null) {
            throw new IllegalArgumentException("time_required");
        }

        double totalSeconds = Math.max(0.0, time.totalGameSecondsAcc);

        long dayIndex0 = (long) Math.floor(totalSeconds / SimulationClock.SECONDS_PER_DAY);
        double secondsInDay = totalSeconds - (dayIndex0 * (double) SimulationClock.SECONDS_PER_DAY);

        // 数值误差保护喵
        if (secondsInDay < 0) {
            secondsInDay = 0;
        }
        if (secondsInDay >= SimulationClock.SECONDS_PER_DAY) {
            secondsInDay = SimulationClock.SECONDS_PER_DAY - 1e-9;
        }

        time.gameDatetimeDay = (int) dayIndex0 + 1;
        time.accGameHoursInDay = secondsInDay / SimulationClock.SECONDS_PER_HOUR;
    }

    /**
     * 单 tick 推进结果喵。
     */
    public static final class TickAdvance {
        public final double dtGameSeconds;
        public final double dtGameHours;
        public final boolean dayChanged;

        public TickAdvance(double dtGameSeconds, double dtGameHours, boolean dayChanged) {
            this.dtGameSeconds = dtGameSeconds;
            this.dtGameHours = dtGameHours;
            this.dayChanged = dayChanged;
        }
    }
}
