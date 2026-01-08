package com.staraxis.universegen;

import com.staraxis.universegen.config.UniverseGenConfig;
import com.staraxis.universegen.util.RandomUtil;
import com.staraxis.universegen.util.ThreadPools;

import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 并行版本：按 Sector 级别并行。
 */
public class ParallelGalaxyGenerator {

    public com.staraxis.universegen.model.Galaxy generate(UniverseGenConfig cfg) {
        ExecutorService pool = ThreadPools.generationPool();
        SplittableRandom baseRng = RandomUtil.fromSeed(cfg.getSeed());
        List<com.staraxis.universegen.model.Sector> sectors = IntStream.range(0, cfg.getSectorCount())
                .parallel()
                .mapToObj(id -> {
                    SplittableRandom rng = RandomUtil.derive(baseRng.nextLong(), id);
                    SectorGenerator gen = new SectorGenerator();
                    return gen.generate(id, cfg, rng);
                })
                .collect(Collectors.toList());
        ThreadPools.shutdown(pool);
        return new com.staraxis.universegen.model.Galaxy(cfg.getSeed(), sectors);
    }
}
