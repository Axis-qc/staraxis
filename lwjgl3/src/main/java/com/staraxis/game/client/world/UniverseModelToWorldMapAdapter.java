package com.staraxis.game.client.world;

import java.util.ArrayList;

import com.staraxis.game.shared.net.worldgen.snapshot.StarSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.StarSystemSnapshot;
import com.staraxis.game.shared.world.HexTile;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldMap;
import com.staraxis.game.shared.world.stellar.Star;
import com.staraxis.game.shared.world.stellar.StarSystem;
import com.staraxis.game.shared.world.stellar.WorldGenStats;

/**
 * 临时适配器：UniverseModel -> WorldMap
 * 
 * 目的：在 UniverseScreen 中复用现有 HexGridRenderer/WorldOverlayRenderer。
 * 注意：这是客户端侧的“渲染适配”，不代表服务端模拟数据结构。
 */
public class UniverseModelToWorldMapAdapter {

    public WorldMap toWorldMap(UniverseModel universe) {
        if (universe == null) {
            throw new IllegalArgumentException("universe 不能为空");
        }

        WorldGenConfig cfg = new WorldGenConfig();
        cfg.setSeedValue(universe.getSeedValue());
        WorldMap map = new WorldMap(cfg, universe.getBoundsRadius());

        int tileCount = 0;
        int galaxyCount = 0;
        int starCount = 0;
        int planetCount = 0;

        for (SectorModel sector : universe.getSectors().values()) {
            HexTile tile = new HexTile(sector.getCoord(), sector.getSectorType());
            StarSystemSnapshot sysSnap = sector.getStarSystem();
            if (sysSnap != null) {
                StarSystem sys = new StarSystem();
                sys.setId(sysSnap.getId());

                ArrayList<Star> stars = new ArrayList<>();
                if (sysSnap.getStars() != null) {
                    for (StarSnapshot starSnap : sysSnap.getStars()) {
                        Star star = new Star();
                        star.setId(starSnap.getId());
                        star.setStarTypeId(starSnap.getStarTypeId());
                        // planets 暂不映射（最小可用）
                        stars.add(star);
                    }
                }

                // StarSystem 要求 stars.size 在 [1,3]，若为空则补一个占位
                if (stars.isEmpty()) {
                    Star star = new Star();
                    star.setId(sys.getId() + "-star-0");
                    star.setStarTypeId("unknown");
                    stars.add(star);
                }

                sys.setStars(stars);
                tile.setStarSystem(sys);

                galaxyCount++;
                starCount += stars.size();
            }

            map.addTile(tile);
            tileCount++;
        }

        WorldGenStats stats = new WorldGenStats();
        stats.setTileCount(tileCount);
        stats.setGalaxyTileCount(galaxyCount);
        stats.setStarCount(starCount);
        stats.setPlanetCount(planetCount);
        stats.setStarsPerSystemMinMax("min=0,max=0");
        map.setStats(stats);

        return map;
    }
}
