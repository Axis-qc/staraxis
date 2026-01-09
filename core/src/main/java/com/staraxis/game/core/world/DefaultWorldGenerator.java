package com.staraxis.game.core.world;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

import com.staraxis.game.core.world.scale.GalaxyBlockCoordinator;
import com.staraxis.game.core.world.scale.GalaxyScaleConfigLoader;
import com.staraxis.game.core.world.scale.WorldBlockScaleConfigLoader;
import com.staraxis.game.core.world.stellar.StellarGenerator;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.HexCoordinateConverter;
import com.staraxis.game.shared.world.HexTile;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldGenDefinitions;
import com.staraxis.game.shared.world.WorldMap;
import com.staraxis.game.shared.world.scale.GalaxyScaleConfig;
import com.staraxis.game.shared.world.scale.GalaxyScaleRange;
import com.staraxis.game.shared.world.scale.WorldBlockScaleConfig;
import com.staraxis.game.shared.world.scale.WorldBlockScaleRange;
import com.staraxis.game.shared.world.stellar.Star;
import com.staraxis.game.shared.world.stellar.StarSystem;
import com.staraxis.game.shared.world.stellar.WorldGenDiagnostics;
import com.staraxis.game.shared.world.stellar.WorldGenStats;

/**
 * 默认世界生成器实现 (Default world generator implementation). 保证在相同种子和配置下生成确定的地图。
 */
@Deprecated(since = "013", forRemoval = true)
public class DefaultWorldGenerator implements WorldGenerator {

    private static final Logger LOGGER = Logger.getLogger(DefaultWorldGenerator.class.getName());

    @Override
    public WorldMap generate(WorldGenConfig config) {
        long startTime = System.currentTimeMillis();
        
        // 加载星系规模配置（如果存在）
        GalaxyScaleConfig galaxyScaleConfig = null;
        if (config.getGalaxyScaleConfig() != null) {
            galaxyScaleConfig = config.getGalaxyScaleConfig();
        } else {
            // 如果没有配置，尝试从默认配置加载
            GalaxyScaleConfigLoader loader = new GalaxyScaleConfigLoader();
            try {
                galaxyScaleConfig = loader.loadConfig("medium", null); // 默认使用中型
            } catch (Exception e) {
                LOGGER.warning("无法加载默认星系规模配置: " + e.getMessage());
            }
        }
        
        // 加载区块规模配置（如果存在）
        WorldBlockScaleConfig blockScaleConfig = null;
        WorldBlockScaleRange blockRange = null;
        if (config.getWorldBlockScaleConfig() != null) {
            blockScaleConfig = config.getWorldBlockScaleConfig();
            WorldBlockScaleConfigLoader blockLoader = new WorldBlockScaleConfigLoader();
            if (blockScaleConfig.getPresetId() != null) {
                blockRange = blockLoader.getPresetRange(blockScaleConfig.getPresetId());
            } else {
                blockRange = blockScaleConfig.getCustomRange();
            }
        }
        
        // 协调星系规模和区块规模
        if (galaxyScaleConfig != null && blockScaleConfig != null) {
            GalaxyBlockCoordinator coordinator = new GalaxyBlockCoordinator();
            var coordinationResult = coordinator.coordinate(galaxyScaleConfig, blockScaleConfig);
            
            if (coordinationResult.hasWarnings()) {
                for (String warning : coordinationResult.getWarnings()) {
                    LOGGER.warning("规模协调警告: " + warning);
                }
            }
            
            // 如果协调器建议调整，可以在这里应用调整
            if (coordinationResult.isAdjusted() && coordinationResult.getAdjustedDensity() != null) {
                LOGGER.info("规模协调已自动调整密度: " + coordinationResult.getAdjustedDensity());
            }
        }
        
        // 计算半径：优先使用区块规模配置，否则使用预设
        int radius;
        if (blockRange != null) {
            // 从区块规模计算半径：使用宽度和高度中的较大值，转换为半径
            // 六边形网格的半径 = max(width, height) / 2（近似）
            int maxDimension = Math.max(blockRange.getWidth(), blockRange.getHeight());
            radius = (int) Math.ceil(maxDimension / 2.0);
            LOGGER.info("使用区块规模配置计算半径: width=" + blockRange.getWidth() 
                    + ", height=" + blockRange.getHeight() + ", radius=" + radius);
        } else {
            radius = WorldGenDefinitions.getRadius(config.getMapSizePresetId());
        }
        
        WorldMap worldMap = new WorldMap(config, radius);

        StellarGenerator stellarGenerator = new StellarGenerator();
        Map<String, Integer> sectorCounts = new HashMap<>();
        int tileCount = 0;
        int galaxyTileCount = 0;
        int starCount = 0;
        int planetCount = 0;
        int starsPerSystemMin = Integer.MAX_VALUE;
        int starsPerSystemMax = Integer.MIN_VALUE;

        // 获取星系规模范围（如果配置了）
        GalaxyScaleRange scaleRange = null;
        int targetStarSystemCount = -1; // -1 表示无限制
        if (galaxyScaleConfig != null) {
            GalaxyScaleConfigLoader loader = new GalaxyScaleConfigLoader();
            if (galaxyScaleConfig.getPresetId() != null) {
                scaleRange = loader.getPresetRange(galaxyScaleConfig.getPresetId());
            } else {
                scaleRange = galaxyScaleConfig.getCustomRange();
            }
            if (scaleRange != null) {
                // 使用默认值或随机值在范围内
                if (scaleRange.getDefaultStarSystems() != null) {
                    targetStarSystemCount = scaleRange.getDefaultStarSystems();
                } else {
                    // 使用范围中点
                    targetStarSystemCount = (scaleRange.getMinStarSystems() + scaleRange.getMaxStarSystems()) / 2;
                }
            }
        }

        // 创建坐标转换器用于计算物理世界坐标
        HexCoordinateConverter coordinateConverter = new HexCoordinateConverter();
        
        // 遍历六边形范围生成瓦片 (T025)
        int generatedStarSystemCount = 0;
        for (int q = -radius; q <= radius; q++) {
            int r1 = Math.max(-radius, -q - radius);
            int r2 = Math.min(radius, -q + radius);
            for (int r = r1; r <= r2; r++) {
                HexCoord coord = HexCoord.of(q, -q - r, r);

                Random tileRandom = createTileRandom(config.getSeedValue(), coord);
                float roll = tileRandom.nextFloat();
                String typeId = sampleSectorTypeId(config, roll);

                HexTile tile = new HexTile(coord, typeId, coordinateConverter);
                sectorCounts.merge(typeId, 1, Integer::sum);

                if ("galaxy".equals(typeId)) {
                    galaxyTileCount++;
                    
                    // 检查是否已达到规模配置的最大星系数量
                    if (targetStarSystemCount > 0 && generatedStarSystemCount >= targetStarSystemCount) {
                        // 已达到目标数量，跳过生成星系
                        continue;
                    }

                    if (tileRandom.nextFloat() < config.getHabitableRatio()) {
                        tile.setHasHabitable(true);
                    }

                    StarSystem starSystem = stellarGenerator.generateStarSystem(coord, config, tileRandom);
                    tile.setStarSystem(starSystem);
                    generatedStarSystemCount++;

                    WorldGenDiagnostics diagnostics = starSystem.getDiagnostics();
                    if (diagnostics != null) {
                        boolean hasDiagnostics = diagnostics.getRepairAttemptCount() > 0
                                || !diagnostics.getMessages().isEmpty()
                                || !diagnostics.getDetails().isEmpty();
                        LOGGER.info(String.format(
                                "StarSystem diagnostics: systemId=%s, repairAttempts=%d, messageCount=%d, detailCount=%d, hasDiagnostics=%b",
                                starSystem.getId(), diagnostics.getRepairAttemptCount(), diagnostics.getMessages().size(), diagnostics.getDetails().size(), hasDiagnostics));
                    }

                    int starsInSystem = starSystem.getStars().size();
                    starsPerSystemMin = Math.min(starsPerSystemMin, starsInSystem);
                    starsPerSystemMax = Math.max(starsPerSystemMax, starsInSystem);
                    starCount += starsInSystem;

                    for (Star star : starSystem.getStars()) {
                        planetCount += star.getPlanets().size();
                    }
                }

                worldMap.addTile(tile);
                tileCount++;
            }
        }

        WorldGenStats stats = new WorldGenStats();
        stats.setTileCount(tileCount);
        stats.setSectorCounts(sectorCounts);
        stats.setGalaxyTileCount(galaxyTileCount);
        stats.setStarCount(starCount);
        stats.setPlanetCount(planetCount);
        if (galaxyTileCount > 0) {
            stats.setStarsPerSystemMinMax("min=" + starsPerSystemMin + ",max=" + starsPerSystemMax);
        } else {
            stats.setStarsPerSystemMinMax("min=0,max=0");
        }
        worldMap.setStats(stats);

        long duration = System.currentTimeMillis() - startTime;
        LOGGER.info(String.format(
                "Generated world: radius=%d, tiles=%d, duration=%dms, seed=%d, sectorCounts=%s, starCount=%d, planetCount=%d",
                radius, tileCount, duration, config.getSeedValue(), sectorCounts, starCount, planetCount));

        return worldMap;
    }

    private Random createTileRandom(long seedValue, HexCoord coord) {
        long mixed = seedValue;
        mixed ^= ((long) coord.getX() * 73856093L);
        mixed ^= ((long) coord.getY() * 19349663L);
        mixed ^= ((long) coord.getZ() * 83492791L);
        return new Random(mixed);
    }

    private String sampleSectorTypeId(WorldGenConfig config, float roll) {
        float g = clamp01(config.getStarDensity());
        float n = clamp01(config.getNebulaRatio());

        float pGalaxy;
        float pNebula;
        if (g + n <= 1.0f) {
            pGalaxy = g;
            pNebula = n;
        } else {
            float sum = g + n;
            pGalaxy = g / sum;
            pNebula = n / sum;
        }

        if (roll < pGalaxy) {
            return "galaxy";
        }
        if (roll < pGalaxy + pNebula) {
            return "nebula";
        }
        return "deep_space";
    }

    private float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
