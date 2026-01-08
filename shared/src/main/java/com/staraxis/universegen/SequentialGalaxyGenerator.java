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
        List<Sector> sectors = new ArrayList<>(cfg.getSectorCount());
        SplittableRandom rng = RandomUtil.fromSeed(cfg.getSeed());
        for (int i = 0; i < cfg.getSectorCount(); i++) {
            SectorGenerator gen = new SectorGenerator();
            sectors.add(gen.generate(i, cfg, rng));
        }
        return new Galaxy(cfg.getSeed(), sectors);
    }

}
