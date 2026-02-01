package staraxis.webnet.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import staraxis.game.state.snapshot.EntitySnapshot;

import java.util.List;

/**
 * RealTimeSnapshotResponse
 *
 * 发送给前端的实时世界快照的顶层 DTO。
 * 采用扁平化的实体列表，便于前端处理和索引。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RealTimeSnapshotResponse {
    public final long simulationTick;
    public final int gameDatetimeDay;
    public final double accGameHoursInDay;
    public final int worldRadius;
    public final List<EntitySnapshot> entities;

    public RealTimeSnapshotResponse(long simulationTick, int gameDatetimeDay, double accGameHoursInDay, int worldRadius,
            List<EntitySnapshot> entities) {
        this.simulationTick = simulationTick;
        this.gameDatetimeDay = gameDatetimeDay;
        this.accGameHoursInDay = accGameHoursInDay;
        this.worldRadius = worldRadius;
        this.entities = entities;
    }
}
