package com.staraxis.game.core.worldgen;

import java.util.ArrayList;
import java.util.List;

import com.staraxis.game.shared.net.worldgen.snapshot.HexCoordSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.HexTileSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.PlanetSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.StarSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.StarSystemSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.WorldGenStatsSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.WorldSnapshot;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.HexTile;
import com.staraxis.game.shared.world.WorldMap;
import com.staraxis.game.shared.world.stellar.Planet;
import com.staraxis.game.shared.world.stellar.Star;
import com.staraxis.game.shared.world.stellar.StarSystem;
import com.staraxis.game.shared.world.stellar.WorldGenStats;

public class WorldSnapshotMapper {

    public WorldSnapshot toSnapshot(WorldMap worldMap) {
        if (worldMap == null) {
            throw new IllegalArgumentException("worldMap 不能为空");
        }

        WorldSnapshot snapshot = new WorldSnapshot();
        snapshot.setSeedValue(worldMap.getConfig().getSeedValue());
        snapshot.setBoundsRadius(worldMap.getBoundsRadius());

        WorldGenStats stats = worldMap.getStats();
        if (stats != null) {
            WorldGenStatsSnapshot statsSnapshot = new WorldGenStatsSnapshot();
            statsSnapshot.setTileCount(stats.getTileCount());
            statsSnapshot.setSectorCounts(stats.getSectorCounts());
            statsSnapshot.setGalaxyTileCount(stats.getGalaxyTileCount());
            statsSnapshot.setStarCount(stats.getStarCount());
            statsSnapshot.setPlanetCount(stats.getPlanetCount());
            statsSnapshot.setStarsPerSystemMinMax(stats.getStarsPerSystemMinMax());
            snapshot.setStats(statsSnapshot);
        }

        List<HexTileSnapshot> tileSnapshots = new ArrayList<>();
        for (HexTile tile : worldMap.getTiles().values()) {
            HexTileSnapshot tileSnapshot = new HexTileSnapshot();

            HexCoord coord = tile.getCoord();
            HexCoordSnapshot coordSnapshot = new HexCoordSnapshot();
            coordSnapshot.setX(coord.getX());
            coordSnapshot.setY(coord.getY());
            coordSnapshot.setZ(coord.getZ());
            tileSnapshot.setCoord(coordSnapshot);

            tileSnapshot.setTypeId(tile.getTypeId());
            tileSnapshot.setHasHabitable(tile.isHasHabitable());

            StarSystem system = tile.getStarSystem();
            if (system != null) {
                StarSystemSnapshot systemSnapshot = new StarSystemSnapshot();
                systemSnapshot.setId(system.getId());

                List<StarSnapshot> stars = new ArrayList<>();
                for (Star star : system.getStars()) {
                    StarSnapshot starSnapshot = new StarSnapshot();
                    starSnapshot.setId(star.getId());
                    starSnapshot.setStarTypeId(star.getStarTypeId());

                    List<PlanetSnapshot> planets = new ArrayList<>();
                    for (Planet planet : star.getPlanets()) {
                        PlanetSnapshot planetSnapshot = new PlanetSnapshot();
                        planetSnapshot.setId(planet.getId());
                        planetSnapshot.setPlanetTypeId(planet.getPlanetTypeId());
                        planetSnapshot.setOrbitIndex(planet.getOrbitIndex() != null ? planet.getOrbitIndex() : 0);
                        planets.add(planetSnapshot);
                    }
                    starSnapshot.setPlanets(planets);
                    stars.add(starSnapshot);
                }

                systemSnapshot.setStars(stars);
                tileSnapshot.setStarSystem(systemSnapshot);
            }

            tileSnapshots.add(tileSnapshot);
        }

        snapshot.setTiles(tileSnapshots);
        return snapshot;
    }
}
