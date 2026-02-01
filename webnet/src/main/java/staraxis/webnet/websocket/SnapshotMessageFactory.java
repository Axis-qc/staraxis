package staraxis.webnet.websocket;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.world.Vec2d;
import staraxis.game.world.hex.SectorCoord;
import staraxis.webnet.dto.DailySettlementStateDto;
import staraxis.webnet.dto.RealTimeStateDto;
import staraxis.webnet.dto.SectorCenterDto;
import staraxis.webnet.dto.SnapshotMessageDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SnapshotMessageFactory {

    private SnapshotMessageFactory() {
    }

    public static SnapshotMessageDto buildSnapshotMessage(StarAxisGameRuntime runtime, long tickCostMs) {
        RealTimeWorldState rt = runtime.getRealTimeWorldStateReadonly();

        List<SectorCenterDto> sectorCenters = new ArrayList<>(rt.getSectorCentersWorldGUView().size());
        for (Map.Entry<SectorCoord, Vec2d> e : rt.getSectorCentersWorldGUView().entrySet()) {
            SectorCoord c = e.getKey();
            Vec2d p = e.getValue();
            sectorCenters.add(new SectorCenterDto(c.q(), c.r(), p.x(), p.y()));
        }

        RealTimeStateDto realTime = new RealTimeStateDto(
                rt.simulationTick,
                rt.gameDatetimeDay,
                rt.accGameHoursInDay,
                rt.worldRadius,
                sectorCenters,
                rt.getEntitySnapshotsView());

        var dailyActive = runtime.getDailySettlementStateBufferForReadonly().getActive();
        DailySettlementStateDto daily = new DailySettlementStateDto(dailyActive.settledDay, dailyActive.sectorCount);

        return SnapshotMessageDto.forSuccess(tickCostMs, realTime, daily);
    }

    public static SnapshotMessageDto buildWorldNotCreatedMessage() {
        return SnapshotMessageDto.forError("world_not_created");
    }
}
