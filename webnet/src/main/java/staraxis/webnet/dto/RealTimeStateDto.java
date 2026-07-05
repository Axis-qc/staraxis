package staraxis.webnet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import staraxis.game.state.snapshot.EntitySnapshot;

import java.util.List;
import java.util.Map;

/**
 * RealTimeStateDto
 *
 * 实时世界状态 DTO：承载高频同步数据喵。
 *
 * 时间轴口径：
 * - simulationTick：权威模拟 tick。
 * - totalGameSeconds：权威累计游戏秒时间戳（向下取整）。
 * - deltaGameSeconds：本 tick 推进量（Δt）。
 * - year/month/day/hour/minute/second：由权威时间轴派生的人类可读字段喵。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RealTimeStateDto {

    public final long simulationTick;

    /** 权威累计游戏秒时间戳（向下取整）喵。 */
    public final long totalGameSeconds;
    public final double totalGameSecondsExact;

    /** 本 tick 推进的游戏秒数（Δt）喵。 */
    public final double deltaGameSeconds;

    public final int worldRadius;

    /** 世界类型喵。 */
    public final String worldType;

    /**
     * 现实 1 秒推进的游戏秒数（基础倍率，不含 timeScale）喵。
     *
     * 实际每 tick 推进量：
     * (gameSecondsPerRealSecond * timeScale) / TICKS_PER_SECOND 喵。
     */
    public final double gameSecondsPerRealSecond;

    /** 系统时间倍率（作用于基础倍率的线性放缩）喵。 */
    public final double timeScale;

    /** 结构化游戏日期时间：年（从 1 开始）喵。 */
    public final int year;

    /** 结构化游戏日期时间：月（从 1 开始）喵。 */
    public final int month;

    /** 结构化游戏日期时间：日（从 1 开始）喵。 */
    public final int day;

    /** 结构化游戏日期时间：时 [0,23] 喵。 */
    public final int hour;

    /** 结构化游戏日期时间：分 [0,59] 喵。 */
    public final int minute;

    /** 结构化游戏日期时间：秒 [0,59] 喵。 */
    public final int second;

    /**
     * 公开实体列表（兼容字段）。
     * 现已主要由 DailySettlementStateDto.publicEntityBaselinesBySectorKey 承载喵。
     */
    public final List<EntitySnapshot> entities;

    /**
     * 按情报等级分组的私有实体快照喵。
     * Key: 情报等级 (0-10)
     * Value: 该等级下对当前连接玩家可见的实体列表喵。
     */
    public final Map<Integer, List<EntitySnapshot>> privateEntitiesByIntelLevel;

    public RealTimeStateDto(long simulationTick, long totalGameSeconds, double totalGameSecondsExact, double deltaGameSeconds,
            int worldRadius,
            String worldType, double gameSecondsPerRealSecond, double timeScale,
            int year, int month, int day, int hour, int minute, int second,
            List<EntitySnapshot> entities,
            Map<Integer, List<EntitySnapshot>> privateEntitiesByIntelLevel) {
        this.simulationTick = simulationTick;
        this.totalGameSeconds = totalGameSeconds;
        this.totalGameSecondsExact = totalGameSecondsExact;
        this.deltaGameSeconds = deltaGameSeconds;
        this.worldRadius = worldRadius;
        this.worldType = worldType;
        this.gameSecondsPerRealSecond = gameSecondsPerRealSecond;
        this.timeScale = timeScale;
        this.year = year;
        this.month = month;
        this.day = day;
        this.hour = hour;
        this.minute = minute;
        this.second = second;
        this.entities = entities;
        this.privateEntitiesByIntelLevel = privateEntitiesByIntelLevel;
    }
}
