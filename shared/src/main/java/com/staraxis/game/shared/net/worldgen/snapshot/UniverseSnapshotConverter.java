package com.staraxis.game.shared.net.worldgen.snapshot;

import com.staraxis.universegen.CoordinateSystem;
import com.staraxis.universegen.SectorLocatorService;
import com.staraxis.universegen.config.UniverseGenConfig;
import com.staraxis.universegen.model.Galaxy;
import com.staraxis.universegen.model.Sector;
import com.staraxis.universegen.model.Star;
import com.staraxis.game.shared.util.UnitConverter;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将 universegen 模块的领域对象，转换为 shared 快照 DTO。
 */
public class UniverseSnapshotConverter {

    public UniverseSnapshot convert(Galaxy galaxy, UniverseGenConfig config, Map<Long, String> presetOccupancy) {
        UniverseSnapshot snapshot = new UniverseSnapshot();
        snapshot.setSeedValue(galaxy.seed());
        snapshot.setBoundsRadius(config.getGalaxyRadiusR());

        SectorLocatorService locator = new SectorLocatorService(config.getHexRadiusLy());

        List<SectorSnapshot> sectorSnapshots = galaxy.sectors().stream()
                .sorted(Comparator.comparingLong(Sector::id))
                .map(sector -> toSectorSnapshot(sector, locator, presetOccupancy != null && presetOccupancy.containsKey(sector.id())))
                .collect(Collectors.toList());

        snapshot.setSectors(sectorSnapshots);

        // TODO: 填充 stats
        snapshot.setStats(new WorldGenStatsSnapshot());

        return snapshot;
    }

    private SectorSnapshot toSectorSnapshot(Sector sector, SectorLocatorService locator, boolean isPreset) {
        SectorSnapshot snapshot = new SectorSnapshot();

        HexCoordSnapshot coord = new HexCoordSnapshot();
        coord.setX(sector.hexCoord().getX());
        coord.setY(sector.hexCoord().getY());
        coord.setZ(sector.hexCoord().getZ());
        snapshot.setCoord(coord);

        snapshot.setSectorType(sector.sectorType());

        CoordinateSystem worldCoord = locator.locateCenter(sector.id());
        // 将公里坐标转换为光年坐标
        double worldXLy = UnitConverter.kmToLightYears(worldCoord.getXKm());
        double worldYLy = UnitConverter.kmToLightYears(worldCoord.getYKm());
        snapshot.setWorldPositionLy(worldXLy, worldYLy);

        if (sector.starSystem() != null) {
            snapshot.setStarSystem(toStarSystemSnapshot(sector.starSystem()));
        }

        snapshot.setOccupancySource(isPreset ? "preset" : "allocated");

        return snapshot;
    }

    private StarSystemSnapshot toStarSystemSnapshot(com.staraxis.universegen.model.StarSystem system) {
        if (system == null) return null;
        StarSystemSnapshot snapshot = new StarSystemSnapshot();
        snapshot.setId(system.name()); // Use name as ID for placeholder

        List<StarSnapshot> starSnapshots = system.stars().stream()
                .map(this::toStarSnapshot)
                .collect(Collectors.toList());
        snapshot.setStars(starSnapshots);

        return snapshot;
    }

    private StarSnapshot toStarSnapshot(Star star) {
        if (star == null) return null;
        StarSnapshot snapshot = new StarSnapshot();
        snapshot.setId(star.name());
        snapshot.setStarTypeId(star.type());
        // Planets are empty for placeholder
        snapshot.setPlanets(List.of());
        return snapshot;
    }
}
