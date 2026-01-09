package com.staraxis.universegen;

import com.staraxis.universegen.config.UniverseGenConfig;
import com.staraxis.universegen.model.Galaxy;
import com.staraxis.universegen.util.KryoSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

public class GalaxyGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(GalaxyGenerator.class);

    private final ParallelGalaxyGenerator parallel = new ParallelGalaxyGenerator();
    private final KryoSerializer serializer = new KryoSerializer();

    /**
     * 根据配置生成 Galaxy，并保存到目标文件。
     */
    public Galaxy generateAndSave(UniverseGenConfig cfg, Path output) throws GenerationException {
        try {
            long start = System.currentTimeMillis();
            IntermediateGalaxy intermediate = parallel.generate(cfg);
            Galaxy galaxy = intermediate.galaxy();
            long genCost = System.currentTimeMillis() - start;
            serializer.write(output, galaxy);
            LOG.info("Galaxy generated: sectors={} file={} cost={}ms", cfg.getSectorCount(), output, genCost);
            return galaxy;
        } catch (IOException | RuntimeException e) {
            LOG.error("Galaxy generation failed", e);
            throw new GenerationException("Galaxy generation failed", e);
        }
    }

    /** 仅生成到内存，不落盘 */
    public Galaxy generate(UniverseGenConfig cfg) throws GenerationException {
        try {
            return parallel.generate(cfg).galaxy();
        } catch (RuntimeException e) {
            LOG.error("Galaxy generation failed", e);
            throw new GenerationException("Galaxy generation failed", e);
        }
    }
}
