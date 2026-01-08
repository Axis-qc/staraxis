package com.staraxis.universegen;

import com.staraxis.universegen.config.UniverseGenConfig;
import com.staraxis.universegen.model.Galaxy;

/**
 * Facade 用于外部调用，内部选择串行或并行实现。
 */
public class GalaxyGeneratorFacade {

    private final ParallelGalaxyGenerator parallel = new ParallelGalaxyGenerator();

    public Galaxy generate(UniverseGenConfig cfg) {
        return parallel.generate(cfg);
    }
}
