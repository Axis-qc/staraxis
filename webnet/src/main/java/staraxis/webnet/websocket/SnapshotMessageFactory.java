package staraxis.webnet.websocket;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.world.Vec2d;
import staraxis.game.world.hex.SectorCoord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SnapshotMessageFactory {

    private SnapshotMessageFactory() {
    }

    public static Map<String, Object> buildSnapshotMessage(StarAxisGameRuntime runtime, long tickCostMs) {
        RealTimeWorldState rt = runtime.getRealTimeWorldStateReadonly();

        List<Map<String, Object>> sectorCenters = new ArrayList<>(rt.getSectorCentersWorldGUView().size());
        for (Map.Entry<SectorCoord, Vec2d> e : rt.getSectorCentersWorldGUView().entrySet()) {
            SectorCoord c = e.getKey();
            Vec2d p = e.getValue();
            sectorCenters.add(Map.of(
                    "q", c.q(),
                    "r", c.r(),
                    "x", p.x(),
                    "y", p.y()));
        }

        Map<String, Object> realTime = new LinkedHashMap<>();
        realTime.put("simulationTick", rt.simulationTick);
        realTime.put("gameDatetimeDay", rt.gameDatetimeDay);
        realTime.put("accGameHoursInDay", rt.accGameHoursInDay);
        realTime.put("worldRadius", rt.worldRadius);
        realTime.put("sectorCenters", sectorCenters);

        Map<String, Object> daily = new LinkedHashMap<>();
        daily.put("settledDay", runtime.getDailySettlementStateBufferForReadonly().getActive().settledDay);
        daily.put("sectorCount", runtime.getDailySettlementStateBufferForReadonly().getActive().sectorCount);

        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("type", "snapshot");
        msg.put("ok", true);
        msg.put("tickCostMs", tickCostMs);
        msg.put("realTimeWorldState", realTime);
        msg.put("dailySettlementState", daily);

        return msg;
    }

    public static String buildWorldNotCreatedJson() {
        return "{\"type\":\"snapshot\",\"ok\":false,\"error\":\"world_not_created\"}";
    }
}
