package staraxis.webnet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import staraxis.game.state.snapshot.EntitySnapshot;

import java.util.List;
import java.util.Map;

/**
 * RealTimeStateDto
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

    public final List<EntitySnapshot> entities;

    public RealTimeStateDto(long simulationTick, int gameDatetimeDay, double accGameHoursInDay, int worldRadius,
            String worldType, double gameSecondsPerRealSecond, double timeScale,
            int year, int month, int day, int hour, int minute, int second,
            List<SectorCenterDto> sectorCenters, Map<String, String> sectorOwnerNationIdByCoord,
            List<EntitySnapshot> entities) {
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
    }
}
