package com.staraxis.game.core.worldgen;

import com.staraxis.game.core.world.WorldGenerator;
import com.staraxis.game.shared.net.worldgen.snapshot.SectorTypes;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.HexTile;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldGenDefinitions;
import com.staraxis.game.shared.world.HexCoordinateConverter;
import com.staraxis.game.shared.world.WorldMap;

import java.util.*;

/**
 * 新版世界生成器：基于六边形星区网格，分阶段生成。
 * 
 * 生成顺序：
 * 1. 生成六边形星区（只创建坐标和空的 HexTile）
 * 2. 星区分配器预设恒星系（固定位置或随机位置）
 * 3. 在剩余的星区按开局设置比例分配普通类型（只分配星区类型id，不实际生成）
 * 4. 恒星系统生成器、星云生成器、资源点生成器等根据星区分配id进行实际的生成
 * 
 * 使用的接口: WorldGenConfig, WorldMap, HexTile, SectorContentGenerator
 * 提供的接口: 为 StartNewGameUseCase 提供世界生成功能
 */
public class HexSectorUniverseGenerator implements WorldGenerator {

    private final PresetStarSystemAllocator presetAllocator;
    private final List<SectorContentGenerator> contentGenerators;

    public HexSectorUniverseGenerator() {
        this.presetAllocator = new PresetStarSystemAllocator();
        this.contentGenerators = Arrays.asList(
                new StarSystemContentGenerator(),
                new NebulaContentGenerator(),
                new DeepSpaceContentGenerator()
        );
    }

    @Override
    public WorldMap generate(WorldGenConfig config) {
        long startMs = System.currentTimeMillis();

        int radius = WorldGenDefinitions.getRadius(config.getMapSizePresetId());
        WorldMap worldMap = new WorldMap(config, radius);
        
        // 创建坐标转换器
        HexCoordinateConverter coordinateConverter = new HexCoordinateConverter();
        
        // 阶段1: 生成六边形星区（包含物理世界坐标）
        Map<HexCoord, HexTile> tiles = generateHexSectors(radius, coordinateConverter);
        for (HexTile tile : tiles.values()) {
            worldMap.addTile(tile);
        }

        // 阶段2: 星区分配器预设恒星系（固定位置或随机位置）
        // 当前实现：随机分配一定数量的预设恒星系（可根据配置调整）
        int totalTiles = tiles.size();
        int presetStarSystemCount = (int) (totalTiles * config.getStarDensity());
        Set<HexCoord> presetStarSystems = presetAllocator.allocatePresetStarSystems(
                tiles,
                config.getSeedValue(),
                Collections.emptyList(), // 固定位置列表（当前为空，可扩展）
                presetStarSystemCount
        );

        // 阶段3: 在剩余的星区按开局设置比例分配普通类型（只分配星区类型id，不实际生成）
        SectorTypeDistributor distributor = new SectorTypeDistributor(
                config.getStarDensity(),
                config.getNebulaRatio()
        );
        Random rng = new Random(config.getSeedValue());
        for (Map.Entry<HexCoord, HexTile> entry : tiles.entrySet()) {
            HexTile tile = entry.getValue();
            // 跳过已预设的恒星系
            if (presetStarSystems.contains(entry.getKey())) {
                continue;
            }
            // 为剩余星区分配类型ID
            String sectorType = distributor.getSectorType(rng);
            tile.setTypeId(sectorType);
        }

        // 阶段4: 恒星系统生成器、星云生成器、资源点生成器等根据星区分配id进行实际的生成
        for (HexTile tile : tiles.values()) {
            String sectorType = tile.getTypeId();
            if (sectorType == null) {
                continue;
            }
            
            // 找到支持该类型的生成器并生成内容
            for (SectorContentGenerator generator : contentGenerators) {
                if (generator.supports(sectorType)) {
                    generator.generateContent(tile, config, config.getSeedValue());
                    break;
                }
            }
        }

        long durationMs = System.currentTimeMillis() - startMs;
        System.out.println("HexSectorUniverseGenerator: radius=" + radius + ", tiles=" + tiles.size() + ", durationMs=" + durationMs);

        return worldMap;
    }

    /**
     * 阶段1: 生成六边形星区（只创建坐标和空的 HexTile）。
     */
    private Map<HexCoord, HexTile> generateHexSectors(int radius, HexCoordinateConverter converter) {
        Map<HexCoord, HexTile> tiles = new HashMap<>();
        
        for (int q = -radius; q <= radius; q++) {
            int r1 = Math.max(-radius, -q - radius);
            int r2 = Math.min(radius, -q + radius);
            for (int r = r1; r <= r2; r++) {
                HexCoord coord = HexCoord.of(q, -q - r, r);
                // 使用转换器创建带有物理世界坐标的 HexTile
                HexTile tile = new HexTile(coord, SectorTypes.DEEP_SPACE, converter); // 临时默认类型
                tiles.put(coord, tile);
            }
        }
        
        return tiles;
    }
}
