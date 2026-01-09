package com.staraxis.universegen;

import com.staraxis.universegen.config.UniverseGenConfig;
import com.staraxis.game.shared.net.worldgen.snapshot.UniverseSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.UniverseSnapshotConverter;
import com.staraxis.universegen.config.UniverseGenConfig;

/**
 * Facade 用于外部调用，内部选择串行或并行实现。
 */
public class GalaxyGeneratorFacade {

    private final ParallelGalaxyGenerator parallel = new ParallelGalaxyGenerator();
    private final UniverseSnapshotConverter converter = new UniverseSnapshotConverter();

    public UniverseSnapshot generate(UniverseGenConfig cfg) {
        IntermediateGalaxy intermediate = parallel.generate(cfg);
        return converter.convert(intermediate.galaxy(), cfg, intermediate.presetOccupancy());
    }
}
