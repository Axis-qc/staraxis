package staraxis.webnet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import staraxis.game.state.snapshot.EntitySnapshot;

import java.util.List;

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

    public final List<EntitySnapshot> entities;

    public RealTimeStateDto(long simulationTick, int gameDatetimeDay, double accGameHoursInDay, int worldRadius,
            List<SectorCenterDto> sectorCenters, List<EntitySnapshot> entities) {
        this.simulationTick = simulationTick;
        this.gameDatetimeDay = gameDatetimeDay;
        this.accGameHoursInDay = accGameHoursInDay;
        this.worldRadius = worldRadius;
        this.sectorCenters = sectorCenters;
        this.entities = entities;
    }
}
