package staraxis.game.astro;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

import staraxis.game.astro.def.AstroAssetRepository;
import staraxis.game.astro.def.OrbitPresetDef;
import staraxis.game.astro.def.OrbitZoneWeightDef;
import staraxis.game.astro.def.PlanetTypeDef;
import staraxis.game.astro.def.StarTypeDef;
import staraxis.game.entity.EntityType;
import staraxis.game.planet.PlanetSurface;
import staraxis.game.planet.def.PlanetAssetRepository;
import staraxis.game.space.SpacePosition;
import staraxis.game.util.WeightedRandomUtil;

/**
 * AstroGenerator
 *
 * 静态生成宇宙中的星体（恒星系、恒星、行星）。
 * 严格使用 worldSeed 以保证生成结果的确定性。
 *
 * 入口为 generateSystemAtPosition(SpacePosition, long)，
 * 由星系生成器（GalaxyGenerator）为每颗恒星调用一次。
 *
 * 单星系统：恒星在系统原点 (0,0,0)，绕重心静止。
 * 黄道面角度随机，行星以此为基准散布。
 */
public final class AstroGenerator {

    private final AstroAssetRepository assets;
    private final PlanetAssetRepository planetAssets;
    private final Random random;
    private final long worldSeedHash;
    private final AtomicLong idCounter = new AtomicLong(0);

    public AstroGenerator(AstroAssetRepository assets, PlanetAssetRepository planetAssets, String worldSeed) {
        this.assets = assets;
        this.planetAssets = planetAssets;
        // 如果种子为空，使用固定默认值以保证确定性
        this.worldSeedHash = (worldSeed == null || worldSeed.isBlank()) ? 0L : worldSeed.hashCode();
        this.random = new Random(this.worldSeedHash);
    }

    public long getWorldSeedHash() {
        return worldSeedHash;
    }

    /**
     * 生成一颗随机恒星。
     */
    private StarBody generateStar() {
        StarTypeDef type = WeightedRandomUtil.weightedRandom(assets.getStarTypes(), t -> t.weight, random);

        StarBody star = new StarBody();
        star.entityId = idCounter.incrementAndGet();
        star.starTypeId = type.typeId;
        star.radiusGU = randomDouble(type.radiusGURange.get(0), type.radiusGURange.get(1));
        star.massSolar = randomDouble(type.massSolarRange.get(0), type.massSolarRange.get(1));
        star.temperatureK = randomInt(type.temperatureKRange.get(0), type.temperatureKRange.get(1));
        star.description = type.description;
        // 随机生成阶段不再默认绑定玩家国家ID，保持恒星初始无主喵。
        star.ownerNationId = null;

        // 从 spriteCandidates 中确定性选择纹理
        if (type.spriteCandidates != null && !type.spriteCandidates.isEmpty()) {
            int idx = random.nextInt(type.spriteCandidates.size());
            star.surfaceTexturePath = type.spriteCandidates.get(idx);
        } else {
            star.surfaceTexturePath = null;
            System.out.println("[WARN AstroGenerator] Star type " + type.typeId + " has no spriteCandidates");
        }

        // 调试日志
        staraxis.game.log.GameLog.logThrottled("astro_gen_star",
                "[DEBUG AstroGenerator] Generated star: typeId=" + type.typeId +
                        ", description='" + type.description + "'" +
                        ", spriteCandidates=" + type.spriteCandidates +
                        ", selectedTexture='" + star.surfaceTexturePath + "'");
        return star;
    }

    /**
     * 为恒星系生成一系列行星。
     * 第一轨道距离基于 systemRadiusGU 的随机分数，而非恒星半径的固定倍数。
     * 上限为 systemRadiusGU * 0.9，下限为 starRadius * 2.0（防落入恒星）。
     */
    private void generatePlanetsForSystem(StarSystem system, StarBody primaryStar, double systemRadiusGU) {
        OrbitPresetDef preset = assets.getOrbitPreset();
        if (preset == null) {
            return;
        }

        int planetCount = randomInt(preset.planetCountRange.get(0), preset.planetCountRange.get(1));
        double firstOrbitFraction = randomDouble(0.03, 0.08);
        double currentOrbitGU = systemRadiusGU * firstOrbitFraction;
        double maxAllowedGU = systemRadiusGU * 0.9;
        double minOrbitGU = primaryStar.radiusGU * 2.0;

        // 获取全局权重表（优先级：星型专属 > orbitPreset 全局）
        Map<String, Integer> defaultWeights = assets.getPlanetWeightsForStarType(primaryStar.starTypeId);
        for (int i = 0; i < planetCount && currentOrbitGU <= maxAllowedGU; i++) {
            // 下限保护：防止行星落入恒星内部
            if (currentOrbitGU < minOrbitGU) {
                currentOrbitGU = minOrbitGU;
            }

            // 根据轨道比例选取分区权重（排除小行星类型，只用于普通行星生成）
            Map<String, Integer> currentWeights = selectWeightsForOrbit(currentOrbitGU, systemRadiusGU, preset, defaultWeights);
            List<PlanetTypeDef> planetCandidates = assets.getPlanetTypes().stream()
                .filter(t -> !"ASTEROID".equals(t.typeId) && !"ICE_ASTEROID".equals(t.typeId))
                .toList();
            PlanetTypeDef type = WeightedRandomUtil.weightedRandom(planetCandidates,
                    t -> currentWeights.getOrDefault(t.typeId, 1), random);

            PlanetBody planet = generateSinglePlanet(type, primaryStar, currentOrbitGU, preset);
            system.planets.add(planet);

            // 为当前行星生成卫星（巨行星较高概率，类地行星低概率）
            generateMoonsForPlanet(planet, system, primaryStar);

            // 在当前行星与下一颗行星之间的轨道间隙生成小行星簇
            generateAsteroidClusterBetween(currentOrbitGU, planet, primaryStar, system, maxAllowedGU);

            // 为下一颗行星增加轨道距离
            double separationFactor = randomDouble(preset.orbitSeparationFactorRange.get(0),
                    preset.orbitSeparationFactorRange.get(1));
            currentOrbitGU *= separationFactor;
        }
    }

    /**
     * 生成单颗行星实体（封装行星创建与地表初始化逻辑）。
     */
    private PlanetBody generateSinglePlanet(PlanetTypeDef type, StarBody primaryStar,
                                             double orbitGU, OrbitPresetDef preset) {
        PlanetBody planet = new PlanetBody();
        planet.entityId = idCounter.incrementAndGet();
        planet.entityType = EntityType.PLANET;
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

        planet.ownerNationId = null;

        // 轨道字段
        planet.orbitCenterEntityId = primaryStar.entityId;
        planet.semiMajorAxisGU = orbitGU;
        planet.eccentricity = randomDouble(preset.eccentricityRange.get(0), preset.eccentricityRange.get(1));
        planet.inclinationDeg = randomDouble(preset.inclinationDegRange.get(0), preset.inclinationDegRange.get(1));
        planet.longitudeOfAscendingNodeDeg = primaryStar.eclipticAngleDeg;
        planet.periapsisArgDeg = randomDouble(0, 360);
        planet.meanAnomalyDegAtEpoch = randomDouble(0, 360);

        // 开普勒第三定律估算公转周期
        double aAU = orbitGU / staraxis.game.world.WorldConstants.AU_IN_GU;
        double pYears = Math.sqrt(aAU * aAU * aAU / primaryStar.massSolar);
        planet.orbitalPeriodDays = pYears * 365.25;

        // 非小天体类型才初始化地表组件
        if (!"ASTEROID".equals(type.typeId) && !"ICE_ASTEROID".equals(type.typeId)) {
            planet.surface = new PlanetSurface(planet.entityId);
            planet.surfaceComponentId = planet.entityId;
            long mixedSeed = staraxis.game.planet.surface.SurfaceNamingUtils.mixSeed(worldSeedHash, planet.entityId);
            planet.surface.initializeSurface(type, planetAssets, mixedSeed);
        }

        return planet;
    }

    /**
     * 在行星轨道间隙生成小行星簇。
     * 概率约 30%，产生 2~4 颗小行星散布在当前轨道外侧。
     */
    private void generateAsteroidClusterBetween(double currentOrbitGU, PlanetBody innerPlanet,
                                                 StarBody primaryStar, StarSystem system, double maxAllowedGU) {
        if (random.nextDouble() >= 0.30) return;

        int count = 2 + random.nextInt(3); // 2~4
        double baseOrbit = currentOrbitGU * (1.05 + random.nextDouble() * 0.25);
        if (baseOrbit >= maxAllowedGU) return;

        // 根据轨道比例决定类型：外层偏向冰小行星
        double orbitFraction = baseOrbit / (primaryStar.radiusGU * 50); // rough scale
        String asteroidTypeId = orbitFraction > 0.35 ? "ICE_ASTEROID" : "ASTEROID";
        PlanetTypeDef astDef = assets.getPlanetType(asteroidTypeId);
        if (astDef == null) return;

        for (int j = 0; j < count; j++) {
            double astOrbit = baseOrbit * (1.0 + (random.nextDouble() - 0.5) * 0.2);
            if (astOrbit >= maxAllowedGU) break;

            PlanetBody asteroid = new PlanetBody();
            asteroid.entityId = idCounter.incrementAndGet();
            asteroid.entityType = EntityType.ASTEROID;
            asteroid.planetTypeId = asteroidTypeId;
            asteroid.radiusGU = randomDouble(astDef.radiusGURange.get(0), astDef.radiusGURange.get(1));
            asteroid.rotationPeriodHours = randomDouble(8, 48);

            if (astDef.spriteCandidates != null && !astDef.spriteCandidates.isEmpty()) {
                int idx = random.nextInt(astDef.spriteCandidates.size());
                asteroid.surfaceTexturePath = astDef.spriteCandidates.get(idx);
            }

            asteroid.ownerNationId = null;

            // 绕恒星公转
            asteroid.orbitCenterEntityId = primaryStar.entityId;
            asteroid.semiMajorAxisGU = astOrbit;
            asteroid.eccentricity = randomDouble(0, 0.2);
            asteroid.inclinationDeg = randomDouble(0, 15); // 小行星带倾角散布较大
            asteroid.longitudeOfAscendingNodeDeg = primaryStar.eclipticAngleDeg;
            asteroid.periapsisArgDeg = randomDouble(0, 360);
            asteroid.meanAnomalyDegAtEpoch = randomDouble(0, 360);

            double aAU = astOrbit / staraxis.game.world.WorldConstants.AU_IN_GU;
            double pYears = Math.sqrt(aAU * aAU * aAU / primaryStar.massSolar);
            asteroid.orbitalPeriodDays = pYears * 365.25;

            // 小天体无地表组件
            asteroid.surface = null;
            asteroid.surfaceComponentId = 0;

            system.asteroids.add(asteroid);

            // 簇内散布
            baseOrbit *= 1.03 + random.nextDouble() * 0.08;
        }
    }

    /**
     * 为行星生成卫星。
     * - GAS_GIANT / ICE_GIANT：80% 概率，1~4 颗
     * - TERRESTRIAL / OCEAN_WORLD：15% 概率，0~1 颗
     * - 其他类型：不生成
     */
    private void generateMoonsForPlanet(PlanetBody planet, StarSystem system, StarBody primaryStar) {
        int maxMoons;
        double chance;

        switch (planet.planetTypeId) {
            case "GAS_GIANT" -> { chance = 0.80; maxMoons = 4; }
            case "ICE_GIANT" -> { chance = 0.60; maxMoons = 3; }
            case "TERRESTRIAL", "OCEAN_WORLD" -> { chance = 0.15; maxMoons = 1; }
            default -> { return; }
        }

        if (random.nextDouble() >= chance) return;

        int moonCount = 1 + (maxMoons > 1 ? random.nextInt(maxMoons) : 0);
        String moonTypeId = "ROCKY_BARREN";
        // 冰巨星周围的卫星偏冰
        if ("ICE_GIANT".equals(planet.planetTypeId) && random.nextDouble() < 0.5) {
            moonTypeId = "ICE_ASTEROID";
        }
        PlanetTypeDef moonDef = assets.getPlanetType(moonTypeId);
        if (moonDef == null) return;

        // 卫星轨道半径基于行星半径
        double moonOrbit = planet.radiusGU * (2.0 + random.nextDouble() * 4.0); // 2~6 倍行星半径

        for (int i = 0; i < moonCount; i++) {
            PlanetBody moon = new PlanetBody();
            moon.entityId = idCounter.incrementAndGet();
            moon.entityType = EntityType.MOON;
            moon.planetTypeId = moonTypeId;
            moon.radiusGU = randomDouble(moonDef.radiusGURange.get(0), moonDef.radiusGURange.get(1));
            moon.rotationPeriodHours = randomDouble(8, 48);

            if (moonDef.spriteCandidates != null && !moonDef.spriteCandidates.isEmpty()) {
                int idx = random.nextInt(moonDef.spriteCandidates.size());
                moon.surfaceTexturePath = moonDef.spriteCandidates.get(idx);
            }

            moon.ownerNationId = null;

            // 绕行星公转
            moon.orbitCenterEntityId = planet.entityId;
            moon.semiMajorAxisGU = moonOrbit;
            moon.eccentricity = randomDouble(0, 0.1);
            moon.inclinationDeg = randomDouble(0, 10);
            moon.longitudeOfAscendingNodeDeg = primaryStar.eclipticAngleDeg;
            moon.periapsisArgDeg = randomDouble(0, 360);
            moon.meanAnomalyDegAtEpoch = randomDouble(0, 360);

            // 卫星轨道周期：简化，与半长轴 1.5 次方成正比（相对行星参考系）
            moon.orbitalPeriodDays = planet.orbitalPeriodDays * 0.01 + moonOrbit / planet.radiusGU * 0.001;

            // 无地表组件
            moon.surface = null;
            moon.surfaceComponentId = 0;

            system.moons.add(moon);

            // 间距
            moonOrbit *= 1.5 + random.nextDouble() * 0.5;
        }
    }

    /**
     * 根据轨道位置选择对应的分区权重表。
     *
     * 遍历 preset.zoneWeights，按 maxOrbitFraction 递增顺序匹配第一个满足
     * currentOrbitGU / systemRadiusGU <= zone.maxOrbitFraction 的分区。
     * 若未配置 zoneWeights 或无分区匹配，则回退到 defaultWeights。
     *
     * @param currentOrbitGU 当前轨道半径（GU）
     * @param systemRadiusGU 恒星系半径（GU），用于计算轨道比例
     * @param preset 轨道预设配置（可能包含 zoneWeights）
     * @param defaultWeights 全局/星型专属权重表（回退用）
     * @return 用于本次行星类型选择的权重表
     */
    private Map<String, Integer> selectWeightsForOrbit(double currentOrbitGU, double systemRadiusGU,
                                                        OrbitPresetDef preset, Map<String, Integer> defaultWeights) {
        if (preset.zoneWeights != null && !preset.zoneWeights.isEmpty()) {
            double orbitFraction = currentOrbitGU / systemRadiusGU;
            for (OrbitZoneWeightDef zone : preset.zoneWeights) {
                if (orbitFraction <= zone.maxOrbitFraction) {
                    return zone.planetTypeWeights;
                }
            }
        }
        return defaultWeights;
    }

    // --- Helper methods ---

    /**
     * 在指定 3D 位置生成一个恒星系（GalaxyGenerator 策略模式主入口）。
     * 单星系统，恒星在系统原点，随机黄道面角度。
     * 行星生成完毕后计算重力井半径。
     */
    public StarSystem generateSystemAtPosition(SpacePosition position, long starId) {
        StarSystem system = new StarSystem();
        system.systemId = idCounter.incrementAndGet();
        system.barycenterEntityId = idCounter.incrementAndGet();

        system.galaxyPos = position;

        // 生成主星
        StarBody primaryStar = generateStar();
        primaryStar.entityId = starId;
        primaryStar.systemId = system.systemId;
        primaryStar.parentEntityId = system.barycenterEntityId;
        primaryStar.posWorldGU = new SpacePosition(position.x(), position.y(), position.z());
        // D.5: 单星系统恒星在原点
        primaryStar.systemPos = SpacePosition.ORIGIN;
        // 随机黄道面角度
        primaryStar.eclipticAngleDeg = randomDouble(0, 360);
        // 单星绕重心静止
        primaryStar.orbitCenterEntityId = 0L;
        primaryStar.orbitalElements = null;
        system.stars.add(primaryStar);

        // 获取恒星类型的 systemRadiusGURange，抽取随机值作为行星生成基准
        StarTypeDef typeDef = assets.getStarType(primaryStar.starTypeId);
        double systemRadiusGU = randomDouble(typeDef.systemRadiusGURange.get(0), typeDef.systemRadiusGURange.get(1));
        generatePlanetsForSystem(system, primaryStar, systemRadiusGU);

        // 重力井基于 systemRadius（势井范围与系统整体挂钩，而非最远行星）
        system.gravityWellRadiusGU = systemRadiusGU * 1.5;

        return system;
    }

    private double randomDouble(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    private int randomInt(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }
}
