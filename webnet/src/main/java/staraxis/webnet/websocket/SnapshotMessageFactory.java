package staraxis.webnet.websocket;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.astro.PlanetBody;
import staraxis.game.astro.StarBody;
import staraxis.game.astro.StarSystem;
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

        List<Map<String, Object>> starSystems = new ArrayList<>(rt.getStarSystemsView().size());
        for (StarSystem sys : rt.getStarSystemsView()) {
            List<Map<String, Object>> stars = new ArrayList<>(sys.stars.size());
            for (StarBody star : sys.stars) {
                stars.add(Map.of(
                        "id", star.id,
                        "typeId", star.typeId,
                        "radiusKm", star.radiusKm,
                        "massSolar", star.massSolar,
                        "temperatureK", star.temperatureK));
            }

            List<Map<String, Object>> planets = new ArrayList<>(sys.planets.size());
            for (PlanetBody planet : sys.planets) {
                Map<String, Object> orbit = planet.orbit == null ? null
                        : Map.of(
                                "semiMajorAxisAU", planet.orbit.semiMajorAxisAU,
                                "eccentricity", planet.orbit.eccentricity,
                                "inclinationDeg", planet.orbit.inclinationDeg,
                                "meanAnomalyDegAtEpoch", planet.orbit.meanAnomalyDegAtEpoch,
                                "orbitalPeriodDays", planet.orbit.orbitalPeriodDays);

                Map<String, Object> planetObj = new LinkedHashMap<>();
                planetObj.put("id", planet.id);
                planetObj.put("typeId", planet.typeId);
                planetObj.put("radiusKm", planet.radiusKm);
                planetObj.put("rotationPeriodHours", planet.rotationPeriodHours);
                planetObj.put("orbit", orbit);
                planets.add(planetObj);
            }

            Map<String, Object> sysObj = new LinkedHashMap<>();
            sysObj.put("id", sys.id);
            sysObj.put("sectorCoord", Map.of("q", sys.sectorCoord.q(), "r", sys.sectorCoord.r()));
            sysObj.put("centerWorldGU", Map.of("x", sys.centerWorldGU.x(), "y", sys.centerWorldGU.y()));
            sysObj.put("stars", stars);
            sysObj.put("planets", planets);
            starSystems.add(sysObj);
        }

        Map<String, Object> realTime = new LinkedHashMap<>();
        realTime.put("simulationTick", rt.simulationTick);
        realTime.put("gameDatetimeDay", rt.gameDatetimeDay);
        realTime.put("accGameHoursInDay", rt.accGameHoursInDay);
        realTime.put("worldRadius", rt.worldRadius);
        realTime.put("sectorCenters", sectorCenters);
        realTime.put("starSystems", starSystems);

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
