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

    public final List<SectorCenterDto> sectorCenters;

    /** 星区归属映射："q,r" -> ownerNationId。 */
    public final Map<String, String> sectorOwnerNationIdByCoord;

    public final List<EntitySnapshot> entities;

    public RealTimeStateDto(long simulationTick, int gameDatetimeDay, double accGameHoursInDay, int worldRadius,
            List<SectorCenterDto> sectorCenters, Map<String, String> sectorOwnerNationIdByCoord,
            List<EntitySnapshot> entities) {
        this.simulationTick = simulationTick;
        this.gameDatetimeDay = gameDatetimeDay;
        this.accGameHoursInDay = accGameHoursInDay;
        this.worldRadius = worldRadius;
        this.sectorCenters = sectorCenters;
        this.sectorOwnerNationIdByCoord = sectorOwnerNationIdByCoord;
        this.entities = entities;
    }
}
