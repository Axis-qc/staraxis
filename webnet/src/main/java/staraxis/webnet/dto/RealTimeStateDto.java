package staraxis.webnet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import staraxis.game.state.snapshot.EntitySnapshot;

import java.util.List;
import java.util.Map;

/**
 * RealTimeStateDto
 * 
 * 实时世界状态 DTO：负责承载高频同步数据喵。
 * 
 * 变更说明：
 * - 增加 privateEntitiesByIntelLevel 字段，支持按探测等级分层下发私有实体喵。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RealTimeStateDto {

    public final long simulationTick;
    public final int gameDatetimeDay;
    public final double accGameHoursInDay;
    public final int worldRadius;

    /** 世界类型喵。 */
    public final String worldType;

    /** 现实 1 秒推进的游戏秒数（不含 timeScale）喵。 */
    public final double gameSecondsPerRealSecond;

    /** 系统时间倍率喵。 */
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

    public final List<SectorCenterDto> sectorCenters;

    /** 星区归属映射："q,r" -> ownerNationId。 */
    public final Map<String, String> sectorOwnerNationIdByCoord;

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

    public RealTimeStateDto(long simulationTick, int gameDatetimeDay, double accGameHoursInDay, int worldRadius,
            String worldType, double gameSecondsPerRealSecond, double timeScale,
            int year, int month, int day, int hour, int minute, int second,
            List<SectorCenterDto> sectorCenters, Map<String, String> sectorOwnerNationIdByCoord,
            List<EntitySnapshot> entities,
            Map<Integer, List<EntitySnapshot>> privateEntitiesByIntelLevel) {
        this.simulationTick = simulationTick;
        this.gameDatetimeDay = gameDatetimeDay;
        this.accGameHoursInDay = accGameHoursInDay;
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
        this.sectorCenters = sectorCenters;
        this.sectorOwnerNationIdByCoord = sectorOwnerNationIdByCoord;
        this.entities = entities;
        this.privateEntitiesByIntelLevel = privateEntitiesByIntelLevel;
    }
}
