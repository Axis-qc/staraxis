package com.staraxis.game.shared.world;

import java.util.ArrayList;
import java.util.List;

import com.staraxis.game.shared.net.worldgen.snapshot.HexCoordSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.HexTileSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.PlanetSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.StarSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.StarSystemSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.WorldGenStatsSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.WorldSnapshot;
import com.staraxis.game.shared.world.stellar.Planet;
import com.staraxis.game.shared.world.stellar.Star;
import com.staraxis.game.shared.world.stellar.StarSystem;
import com.staraxis.game.shared.world.stellar.WorldGenStats;

public class WorldSnapshotConverter {

    public WorldMap toWorldMap(WorldSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot 不能为空");
        }

        WorldGenConfig config = new WorldGenConfig();
        config.setSeedValue(snapshot.getSeedValue());

        WorldMap worldMap = new WorldMap(config, snapshot.getBoundsRadius());

        WorldGenStatsSnapshot statsSnapshot = snapshot.getStats();
        if (statsSnapshot != null) {
            WorldGenStats stats = new WorldGenStats();
            stats.setTileCount(statsSnapshot.getTileCount());
            stats.setSectorCounts(statsSnapshot.getSectorCounts());
            stats.setGalaxyTileCount(statsSnapshot.getGalaxyTileCount());
            stats.setStarCount(statsSnapshot.getStarCount());
            stats.setPlanetCount(statsSnapshot.getPlanetCount());
            stats.setStarsPerSystemMinMax(statsSnapshot.getStarsPerSystemMinMax());
            worldMap.setStats(stats);
        }

        for (HexTileSnapshot tileSnapshot : snapshot.getTiles()) {
            if (tileSnapshot == null) {
                continue;
            }
            HexCoordSnapshot coordSnapshot = tileSnapshot.getCoord();
            if (coordSnapshot == null) {
                continue;
            }

            HexCoord coord = HexCoord.of(coordSnapshot.getX(), coordSnapshot.getY(), coordSnapshot.getZ());
            HexTile tile = new HexTile(coord, tileSnapshot.getTypeId());
            tile.setHasHabitable(tileSnapshot.isHasHabitable());

            StarSystemSnapshot starSystemSnapshot = tileSnapshot.getStarSystem();
            if (starSystemSnapshot != null) {
                StarSystem sys = new StarSystem();
                sys.setId(starSystemSnapshot.getId());

                List<Star> stars = new ArrayList<>();
                for (StarSnapshot starSnapshot : starSystemSnapshot.getStars()) {
                    Star star = new Star();
                    star.setId(starSnapshot.getId());
                    star.setStarTypeId(starSnapshot.getStarTypeId());

                    List<Planet> planets = new ArrayList<>();
                    for (PlanetSnapshot planetSnapshot : starSnapshot.getPlanets()) {
                        Planet planet = new Planet();
                        planet.setId(planetSnapshot.getId());
                        planet.setPlanetTypeId(planetSnapshot.getPlanetTypeId());
                        planet.setOrbitIndex(planetSnapshot.getOrbitIndex());
                        planets.add(planet);
                    }
                    star.setPlanets(planets);
                    stars.add(star);
                }

                sys.setStars(stars);
                tile.setStarSystem(sys);
            }

            worldMap.addTile(tile);
        }

        return worldMap;
    }
}
