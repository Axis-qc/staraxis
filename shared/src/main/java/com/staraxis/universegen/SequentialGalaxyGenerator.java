package com.staraxis.universegen;

import com.staraxis.universegen.config.UniverseGenConfig;
import com.staraxis.universegen.model.Galaxy;
import com.staraxis.universegen.model.Sector;
import com.staraxis.universegen.util.RandomUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

/**
 * 单线程生成实现，方便调试与性能基线。
 */
public class SequentialGalaxyGenerator {

    public Galaxy generate(UniverseGenConfig cfg) {
        SectorLocatorService locator = new SectorLocatorService(cfg.getHexRadiusLy());
        List<Long> sectorIds = locator.generateSectorIdsByRadius(cfg.getGalaxyRadiusR());

        List<Sector> sectors = new ArrayList<>(sectorIds.size());
        for (long sectorId : sectorIds) {
            int q = (int) (sectorId >> 32);
            int r = (int) (sectorId & 0xffffffffL);
            SplittableRandom rng = RandomUtil.deriveFromHexCoord(cfg.getSeed(), q, r);
            SectorGenerator gen = new SectorGenerator();
            sectors.add(gen.generate(sectorId, q, r, cfg, rng));
        }
        return new Galaxy(cfg.getSeed(), sectors);
    }

}
