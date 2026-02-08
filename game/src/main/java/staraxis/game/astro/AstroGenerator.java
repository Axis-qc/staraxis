package staraxis.game.astro;

import staraxis.game.astro.def.AstroAssetRepository;
import staraxis.game.astro.def.OrbitPresetDef;
import staraxis.game.astro.def.PlanetTypeDef;
import staraxis.game.astro.def.StarTypeDef;
import staraxis.game.world.WorldGenConfig;
import staraxis.game.world.WorldMap;
import staraxis.game.world.WorldSector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AstroGenerator
 *
 * 静态生成宇宙中的星体（恒星系、恒星、行星）。
 * 严格使用 worldSeed 以保证生成结果的确定性。
 */
public final class AstroGenerator {

    private final AstroAssetRepository assets;
    private final Random random;
    private final AtomicLong idCounter = new AtomicLong(0);

    public AstroGenerator(AstroAssetRepository assets, String worldSeed) {
        this.assets = assets;
        // 如果种子为空，使用固定默认值以保证确定性
        long seed = (worldSeed == null || worldSeed.isBlank()) ? 0L : worldSeed.hashCode();
        this.random = new Random(seed);
    }

    /**
     * 为整个世界地图生成所有星系。
     */
    public List<StarSystem> generateSystemsForMap(WorldMap worldMap, WorldGenConfig config) {
        List<StarSystem> systems = new ArrayList<>();
        // 按 50% 概率在每个星区生成一个恒星系
        double systemSpawnChance = 0.5;

        for (WorldSector sector : worldMap.getSectorsView()) {
            if (random.nextDouble() < systemSpawnChance) {
                systems.add(generateSystemForSector(sector));
            }
        }
        return systems;
    }

    /**
     * 为单个星区生成一个恒星系。
     */
    private StarSystem generateSystemForSector(WorldSector sector) {
        StarSystem system = new StarSystem();
        system.systemId = idCounter.incrementAndGet();
        system.barycenterEntityId = idCounter.incrementAndGet(); // 预留重心实体ID
        system.sectorCoord = sector.coord;
        // 系统中心 = 星区中心
        system.centerWorldGU = sector.centerWorldGU;

        // 生成主星（第一版只支持单星）
        StarBody primaryStar = generateStar(system);
        system.stars.add(primaryStar);

        // 生成行星
        generatePlanetsForSystem(system, primaryStar);

        return system;
    }

    /**
     * 生成一颗随机恒星。
     */
    private StarBody generateStar(StarSystem system) {
        StarTypeDef type = weightedRandom(assets.getStarTypes(), t -> t.weight);

        StarBody star = new StarBody();
        star.entityId = idCounter.incrementAndGet();
        star.starTypeId = type.typeId;
        star.radiusGU = randomDouble(type.radiusGURange.get(0), type.radiusGURange.get(1));
        star.massSolar = randomDouble(type.massSolarRange.get(0), type.massSolarRange.get(1));
        star.temperatureK = randomInt(type.temperatureKRange.get(0), type.temperatureKRange.get(1));
        star.description = type.description;

        // 从 spriteCandidates 中确定性选择纹理
        if (type.spriteCandidates != null && !type.spriteCandidates.isEmpty()) {
            int idx = random.nextInt(type.spriteCandidates.size());
            star.surfaceTexturePath = type.spriteCandidates.get(idx);
        } else {
            star.surfaceTexturePath = null;
            System.out.println("[WARN AstroGenerator] Star type " + type.typeId + " has no spriteCandidates");
        }

        // 调试日志
        System.out.println("[DEBUG AstroGenerator] Generated star: typeId=" + type.typeId +
                         ", description='" + type.description + "'" +
                         ", spriteCandidates=" + type.spriteCandidates +
                         ", selectedTexture='" + star.surfaceTexturePath + "'");
        return star;
    }

    /**
     * 为恒星系生成一系列行星。
     */
    private void generatePlanetsForSystem(StarSystem system, StarBody primaryStar) {
        OrbitPresetDef preset = assets.getOrbitPreset();
        if (preset == null) {
            return;
        }

        int planetCount = randomInt(preset.planetCountRange.get(0), preset.planetCountRange.get(1));
        double currentOrbitGU = randomDouble(preset.firstOrbitGURange.get(0), preset.firstOrbitGURange.get(1));

        for (int i = 0; i < planetCount; i++) {
            PlanetTypeDef type = weightedRandom(assets.getPlanetTypes(),
                    t -> preset.planetTypeWeights.getOrDefault(t.typeId, 1));

            PlanetBody planet = new PlanetBody();
            planet.entityId = idCounter.incrementAndGet();
            planet.planetTypeId = type.typeId;
            planet.radiusGU = randomDouble(type.radiusGURange.get(0), type.radiusGURange.get(1));
            planet.rotationPeriodHours = randomDouble(preset.rotationPeriodHoursRange.get(0),
                    preset.rotationPeriodHoursRange.get(1));

            // 从 spriteCandidates 中确定性选择纹理
            if (type.spriteCandidates != null && !type.spriteCandidates.isEmpty()) {
                int idx = random.nextInt(type.spriteCandidates.size());
                planet.surfaceTexturePath = type.spriteCandidates.get(idx);
            } else {
                planet.surfaceTexturePath = null;
            }

            // 直接填充轨道字段到 PlanetBody
            planet.orbitCenterEntityId = primaryStar.entityId;
            planet.semiMajorAxisGU = currentOrbitGU;
            planet.eccentricity = randomDouble(preset.eccentricityRange.get(0), preset.eccentricityRange.get(1));
            planet.inclinationDeg = randomDouble(preset.inclinationDegRange.get(0), preset.inclinationDegRange.get(1));
            planet.periapsisArgDeg = randomDouble(0, 360);
            planet.meanAnomalyDegAtEpoch = randomDouble(0, 360);

            // 开普勒第三定律估算公转周期: P^2 = a^3 / M (P in years, a in AU, M in solar masses)
            double pYears = Math.sqrt(Math.pow(planet.semiMajorAxisGU / staraxis.game.world.WorldConstants.AU_IN_GU, 3)
                    / primaryStar.massSolar);
            planet.orbitalPeriodDays = pYears * 365.25; // 简化换算

            system.planets.add(planet);

            // 为下一颗行星增加轨道距离
            double separationFactor = randomDouble(preset.orbitSeparationFactorRange.get(0),
                    preset.orbitSeparationFactorRange.get(1));
            currentOrbitGU *= separationFactor;
        }
    }

    // --- Helper methods ---

    private <T> T weightedRandom(List<T> items, java.util.function.ToDoubleFunction<T> weightFunc) {
        double totalWeight = items.stream().mapToDouble(weightFunc).sum();
        double value = random.nextDouble() * totalWeight;
        double cumulativeWeight = 0;
        for (T item : items) {
            cumulativeWeight += weightFunc.applyAsDouble(item);
            if (value < cumulativeWeight) {
                return item;
            }
        }
        return items.get(items.size() - 1); // fallback
    }

    private double randomDouble(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    private int randomInt(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }
}
