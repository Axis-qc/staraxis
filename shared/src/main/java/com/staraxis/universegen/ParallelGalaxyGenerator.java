package com.staraxis.universegen;

import com.staraxis.universegen.config.UniverseGenConfig;
import com.staraxis.universegen.util.RandomUtil;
import com.staraxis.universegen.util.ThreadPools;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

/**
 * 并行版本：按 Sector 级别并行。
 */
public class ParallelGalaxyGenerator {

    public IntermediateGalaxy generate(UniverseGenConfig cfg) {
        ExecutorService pool = ThreadPools.generationPool();

        // 1) 生成星区集合（由 R 决定）
        SectorLocatorService locator = new SectorLocatorService(cfg.getHexRadiusLy());
        List<Long> sectorIds = locator.generateSectorIdsByRadius(cfg.getGalaxyRadiusR());

        // 2) 应用预设占用（后来者覆盖）
        Map<Long, String> presetOccupancy = PresetApplicator.applyPresets(cfg, sectorIds);

        // 3) 计算“剩余星区”并按比例做确定性分配（T015/T016）
        Map<Long, String> allocation = SectorContentAllocator.allocate(cfg, sectorIds, presetOccupancy);

        // 4) 生成 sector（临时仍沿用旧 SectorGenerator 的 starSystem 生成行为；
        // 后续 T018 会改为占位符生成 starSystemId）
        List<com.staraxis.universegen.model.Sector> sectors = sectorIds.parallelStream()
                .map(sectorId -> {
                    int q = (int) (sectorId >> 32);
                    int r = (int) (sectorId & 0xffffffffL);

                    String finalType = allocation.getOrDefault(sectorId, "deep_space");

                    SplittableRandom rng = RandomUtil.deriveFromHexCoord(cfg.getSeed(), q, r);
                    SectorGenerator gen = new SectorGenerator();
                    com.staraxis.universegen.model.Sector sector = gen.generate(sectorId, q, r, cfg, rng);

                    // 覆盖为最终类型（包含预设占用 + 分配结果）
                    if (!finalType.equals(sector.sectorType())) {
                        return new com.staraxis.universegen.model.Sector(
                                sector.id(),
                                sector.hexCoord(),
                                finalType,
                                sector.starSystem());
                    }

                    return sector;
                })
                .collect(Collectors.toList());

        ThreadPools.shutdown(pool);
        return new IntermediateGalaxy(new com.staraxis.universegen.model.Galaxy(cfg.getSeed(), sectors), presetOccupancy);
    }
}
