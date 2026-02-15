package staraxis.game.sim;

import staraxis.game.world.WorldType;

/**
 * SimulationTime
 *
 * 权威模拟时间容器。
 */
public class SimulationTime {

    /**
     * worldType：世界类型，用于控制时间推进速度的权限策略喵。
     */
    public WorldType worldType = WorldType.SINGLE_PLAYER;

    /**
     * 本地模拟 Tick；固定 25 tick/s；主循环 PrepareTick 阶段自增 +1。
     */
    public long simulationTick;

    /**
     * 游戏日序号 (从 1 开始)；当累计游戏小时数满 24h 后，在 Commit 阶段 +1。
     *
     * 说明：月份与年份按规则派生（30 天=1 月，12 月=1 年），避免重复权威状态喵。
     */
    public int gameDatetimeDay = 1;

    /**
     * 当日累计游戏小时数；范围 [0, 24)，是驱动日结算的核心变量。
     */
    public double accGameHoursInDay;

    /**
     * 玩家选择的时间推进速度（旧口径：游戏分钟 / 现实秒）喵。
     *
     * 新口径为 1:1（现实 1 秒 -> 游戏 1 秒）后，该字段不再参与权威时间推进。
     * 仍保留用于兼容旧 UI/协议与潜在的“非权威展示层速度选择”喵。
     */
    public double playerTimeStep = 1.0;

    /**
     * 系统时间倍率喵。
     * 由系统根据性能、战斗状态等控制；在新口径下仍会影响权威时间推进（线性倍速）喵。
     */
    public double timeScale = 1.0;

    /**
     * 现实秒 -> 游戏秒 的推进比例喵。
     * 例如：1.0 表示 1 现实秒推进 1 游戏秒；3600 表示 1 现实秒推进 1 游戏小时喵。
     *
     * 规则：
     * - 单人/多人世界可被玩家指令修改（经模拟层校验）喵。
     * - 服务器世界禁止被玩家指令修改，仅允许服务器/系统内部调整喵。
     */
    public double gameSecondsPerRealSecond = 1.0;

    public SimulationTime() {
    }

    /**
     * 派生：当前游戏月序号（从 1 开始），规则：30 天=1 月喵。
     */
    public int getGameDatetimeMonth() {
        return ((gameDatetimeDay - 1) / SimulationClock.DAYS_PER_MONTH) + 1;
    }

    /**
     * 派生：当前游戏年序号（从 1 开始），规则：12 月=1 年喵。
     */
    public int getGameDatetimeYear() {
        int monthIndex0 = (gameDatetimeDay - 1) / SimulationClock.DAYS_PER_MONTH;
        return (monthIndex0 / SimulationClock.MONTHS_PER_YEAR) + 1;
    }

    /**
     * 获取当日内的小时整数部分 [0, 23] 喵。
     */
    public int getHour() {
        return (int) Math.floor(accGameHoursInDay);
    }

    /**
     * 获取当前分钟整数部分 [0, 59] 喵。
     */
    public int getMinute() {
        double minuteFloat = (accGameHoursInDay - getHour()) * 60.0;
        return (int) Math.floor(minuteFloat);
    }

    /**
     * 获取当前秒整数部分 [0, 59] 喵。
     */
    public int getSecond() {
        double minuteFloat = (accGameHoursInDay - getHour()) * 60.0;
        double secondFloat = (minuteFloat - Math.floor(minuteFloat)) * 60.0;
        return (int) Math.floor(secondFloat);
    }

    /**
     * 获取当前月份的天序号（从 1 开始）喵。
     */
    public int getDayOfMonth() {
        return ((gameDatetimeDay - 1) % SimulationClock.DAYS_PER_MONTH) + 1;
    }
}
