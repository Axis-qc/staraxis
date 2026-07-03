package staraxis.game.astro;

import staraxis.game.astro.def.AstroAssetRepository;
import staraxis.game.astro.def.OrbitPresetDef;
import staraxis.game.astro.def.PlanetTypeDef;
import staraxis.game.astro.def.StarTypeDef;
import staraxis.game.planet.PlanetSurface;
import staraxis.game.planet.def.PlanetAssetRepository;
import staraxis.game.space.SpacePosition;
import staraxis.game.util.WeightedRandomUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

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

    /** 第一轨道距离 = 恒星半径 * 此倍数。 */
    private static final double FIRST_ORBIT_MULTIPLIER = 6.0;

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
     * 第一轨道距离基于恒星半径（D.7），黄道面角度从恒星获取（D.8）。
     */
    private void generatePlanetsForSystem(StarSystem system, StarBody primaryStar) {
        OrbitPresetDef preset = assets.getOrbitPreset();
        if (preset == null) {
            return;
        }

        int planetCount = randomInt(preset.planetCountRange.get(0), preset.planetCountRange.get(1));
        // D.7: 第一轨道距离 = 恒星半径 * FIRST_ORBIT_MULTIPLIER
        double currentOrbitGU = primaryStar.radiusGU * FIRST_ORBIT_MULTIPLIER;

        Map<String, Integer> effectiveWeights = assets.getPlanetWeightsForStarType(primaryStar.starTypeId);
        for (int i = 0; i < planetCount; i++) {
            PlanetTypeDef type = WeightedRandomUtil.weightedRandom(assets.getPlanetTypes(),
                    t -> effectiveWeights.getOrDefault(t.typeId, 1), random);

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

            planet.ownerNationId = null;

            // 轨道字段
            planet.orbitCenterEntityId = primaryStar.entityId;
            planet.semiMajorAxisGU = currentOrbitGU;
            // 无累进缩放——第一轨道已基于恒星半径
            planet.eccentricity = randomDouble(preset.eccentricityRange.get(0), preset.eccentricityRange.get(1));
            // D.8: inclination 在黄道面基础上随机散布，longitudeOfAscendingNode 取恒星黄道面角度
            planet.inclinationDeg = randomDouble(preset.inclinationDegRange.get(0), preset.inclinationDegRange.get(1));
            planet.longitudeOfAscendingNodeDeg = primaryStar.eclipticAngleDeg;
            planet.periapsisArgDeg = randomDouble(0, 360);
            planet.meanAnomalyDegAtEpoch = randomDouble(0, 360);

            // 开普勒第三定律估算公转周期
            double aAU = planet.semiMajorAxisGU / staraxis.game.world.WorldConstants.AU_IN_GU;
            double pYears = Math.sqrt(aAU * aAU * aAU / primaryStar.massSolar);
            planet.orbitalPeriodDays = pYears * 365.25;

            // 初始化行星地表组件
            planet.surface = new PlanetSurface(planet.entityId);
            planet.surfaceComponentId = planet.entityId;
            long mixedSeed = staraxis.game.planet.surface.SurfaceNamingUtils.mixSeed(worldSeedHash, planet.entityId);
            planet.surface.initializeSurface(type, planetAssets, mixedSeed);

            system.planets.add(planet);

            // 为下一颗行星增加轨道距离
            double separationFactor = randomDouble(preset.orbitSeparationFactorRange.get(0),
                    preset.orbitSeparationFactorRange.get(1));
            currentOrbitGU *= separationFactor;
        }
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

        // TODO: sectorCoord 是 hex 时代的遗留，后续应改用 galaxy 区域分区替代快照分组
        staraxis.game.world.Vec2d pos2d = new staraxis.game.world.Vec2d(position.x(), position.z());
        system.sectorCoord = staraxis.game.world.WorldHexLayout.worldToSectorCoord(pos2d);
        system.galaxyPos = position;

        // 生成主星
        StarBody primaryStar = generateStar();
        primaryStar.entityId = starId;
        primaryStar.systemId = system.systemId;
        primaryStar.parentEntityId = system.barycenterEntityId;
        primaryStar.sectorCoord = system.sectorCoord;
        primaryStar.posWorldGU = new SpacePosition(position.x(), position.y(), position.z());
        // D.5: 单星系统恒星在原点
        primaryStar.systemPos = SpacePosition.ORIGIN;
        // 随机黄道面角度
        primaryStar.eclipticAngleDeg = randomDouble(0, 360);
        // 单星绕重心静止
        primaryStar.orbitCenterEntityId = 0L;
        primaryStar.orbitalElements = null;
        system.stars.add(primaryStar);

        // 生成行星
        generatePlanetsForSystem(system, primaryStar);

        // D.9: 重力井半径 = max(最远行星半长轴 * 1.5, 恒星半径 * 10)
        double farthestOrbit = 0;
        for (PlanetBody p : system.planets) {
            if (p.semiMajorAxisGU > farthestOrbit) {
                farthestOrbit = p.semiMajorAxisGU;
            }
        }
        double starMin = primaryStar.radiusGU * 10;
        system.gravityWellRadiusGU = Math.max(farthestOrbit * 1.5, starMin);

        return system;
    }

    private double randomDouble(double min, double max) {
        return min + (max - min) * random.nextDouble();
    }

    private int randomInt(int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }
}
