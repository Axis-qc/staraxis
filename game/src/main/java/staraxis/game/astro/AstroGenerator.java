package staraxis.game.astro;

import staraxis.game.astro.def.AstroAssetRepository;
import staraxis.game.astro.def.OrbitPresetDef;
import staraxis.game.astro.def.PlanetTypeDef;
import staraxis.game.astro.def.StarTypeDef;
import staraxis.game.astro.def.PresetStarSystemDef;
import staraxis.game.astro.def.PresetStarSystemRepository;
import staraxis.game.planet.PlanetSurface;
import staraxis.game.planet.def.PlanetAssetRepository;
import staraxis.game.space.SpacePosition;
import staraxis.game.world.WorldGenConfig;
import staraxis.game.world.WorldHexLayout;
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
    private final PlanetAssetRepository planetAssets;
    private final Random random;
    private final long worldSeedHash;
    private final AtomicLong idCounter = new AtomicLong(0);

    /** 记录预设 ID 到生成出的系统实体 ID 的映射喵。 */
    private final java.util.Map<String, Long> presetToSystemId = new java.util.HashMap<>();

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
     * 获取预设 ID 到系统实体 ID 的映射喵。
     */
    public java.util.Map<String, Long> getPresetToSystemIdMap() {
        return java.util.Collections.unmodifiableMap(presetToSystemId);
    }

    /**
     * 为整个世界地图生成所有星系。
     */
    public List<StarSystem> generateSystemsForMap(WorldMap worldMap, WorldGenConfig config) {
        List<StarSystem> systems = new ArrayList<>();

        // 预设星系：固定坐标不受 seed 影响，随机坐标随 seed 变化喵
        PresetStarSystemRepository presetRepo = new PresetStarSystemRepository(
                new com.fasterxml.jackson.databind.ObjectMapper());
        presetRepo.loadAll();

        java.util.Set<String> occupied = new java.util.HashSet<>();

        // 1) 先放置固定坐标预设喵
        for (PresetStarSystemDef preset : presetRepo.getPresets()) {
            if (preset == null || preset.position == null || preset.system == null)
                continue;
            if (!"fixedSector".equals(preset.position.mode))
                continue;
            if (preset.position.q == null || preset.position.r == null)
                continue;

            WorldSector sector = worldMap
                    .getSector(new staraxis.game.world.hex.SectorCoord(preset.position.q, preset.position.r));
            if (sector == null)
                continue;
            String key = preset.position.q + "," + preset.position.r;
            if (occupied.contains(key))
                continue;

            StarSystem sys = generateSystemForPreset(sector, preset, null); // 生成阶段不绑定玩家国家，保持天体初始无主（除预设明确指定的
                                                                            // ownerNationId）
            systems.add(sys);
            occupied.add(key);
            if (preset.presetId != null) {
                presetToSystemId.put(preset.presetId, sys.systemId);
            }
        }

        // 2) 再放置随机坐标预设（按 seed + presetId 确定性选点）喵
        java.util.List<WorldSector> allSectors = new java.util.ArrayList<>();
        for (WorldSector s : worldMap.getSectorsView())
            allSectors.add(s);

        for (PresetStarSystemDef preset : presetRepo.getPresets()) {
            if (preset == null || preset.position == null || preset.system == null)
                continue;
            if (!"randomSector".equals(preset.position.mode))
                continue;
            if (preset.presetId == null || preset.presetId.isBlank())
                continue;

            // 构造确定性随机源喵
            long mix = worldSeedHash ^ (long) preset.presetId.hashCode();
            Random rr = new Random(mix);

            // 候选集合：按 randomRadius 限制到中心附近，否则全图喵
            java.util.List<WorldSector> candidates = new java.util.ArrayList<>();
            Integer rad = preset.position.randomRadius;
            if (rad != null && rad > 0) {
                for (WorldSector s : allSectors) {
                    int q = s.coord.q();
                    int r = s.coord.r();
                    if (Math.abs(q) <= rad && Math.abs(r) <= rad && Math.abs(q + r) <= rad) {
                        candidates.add(s);
                    }
                }
            } else {
                candidates.addAll(allSectors);
            }
            if (candidates.isEmpty())
                continue;

            // 反复尝试选未占用星区喵
            for (int attempt = 0; attempt < Math.min(32, candidates.size()); attempt++) {
                WorldSector sector = candidates.get(rr.nextInt(candidates.size()));
                String key = sector.coord.q() + "," + sector.coord.r();
                if (occupied.contains(key))
                    continue;
                StarSystem sys = generateSystemForPreset(sector, preset, null); // 生成阶段不绑定玩家国家，保持天体初始无主（除预设明确指定的
                                                                                // ownerNationId）
                systems.add(sys);
                occupied.add(key);
                if (preset.presetId != null) {
                    presetToSystemId.put(preset.presetId, sys.systemId);
                }
                break;
            }
        }

        // 3) 最后对剩余星区按概率生成随机星系喵
        double systemSpawnChance = 0.5;
        for (WorldSector sector : worldMap.getSectorsView()) {
            String key = sector.coord.q() + "," + sector.coord.r();
            if (occupied.contains(key)) {
                continue;
            }
            if (random.nextDouble() < systemSpawnChance) {
                systems.add(generateSystemForSector(sector, null)); // 生成阶段不绑定玩家国家，保持天体初始无主（除预设明确指定的 ownerNationId）
            }
        }

        return systems;
    }

    /**
     * 根据预设定义生成恒星系喵。
     */
    private StarSystem generateSystemForPreset(WorldSector sector, PresetStarSystemDef def, String playerNationId) {
        StarSystem system = new StarSystem();
        system.systemId = idCounter.incrementAndGet();
        system.barycenterEntityId = idCounter.incrementAndGet();
        system.sectorCoord = sector.coord;
        system.centerWorldGU = sector.centerWorldGU;

        // 1. 生成恒星喵
        for (PresetStarSystemDef.StarDef sDef : def.system.stars) {
            StarBody star = generateStarFromDef(sDef, playerNationId);
            star.systemId = system.systemId;
            star.parentEntityId = system.barycenterEntityId;
            star.sectorCoord = system.sectorCoord;
            star.posWorldGU = new staraxis.game.space.SpacePosition(system.centerWorldGU.x(), 0, system.centerWorldGU.y());
            system.stars.add(star);
        }

        // 如果预设没给星，补一颗随机的喵
        if (system.stars.isEmpty()) {
            StarBody primaryStar = generateStar(system, playerNationId);
            system.stars.add(primaryStar);
        }

        // 2. 生成行星喵
        StarBody primary = system.stars.get(0);
        for (PresetStarSystemDef.PlanetDef pDef : def.system.planets) {
            PlanetBody planet = generatePlanetFromDef(pDef, primary, playerNationId);
            planet.systemId = system.systemId;
            planet.parentEntityId = system.barycenterEntityId;
            planet.sectorCoord = system.sectorCoord;
            planet.posWorldGU = new staraxis.game.space.SpacePosition(system.centerWorldGU.x(), 0, system.centerWorldGU.y());
            system.planets.add(planet);
        }

        return system;
    }

    private StarBody generateStarFromDef(PresetStarSystemDef.StarDef sDef, String playerNationId) {
        StarTypeDef type = assets.getStarTypes().stream()
                .filter(t -> t.typeId != null && t.typeId.equals(sDef.starTypeId))
                .findFirst()
                .orElse(null);
        if (type == null) {
            type = weightedRandom(assets.getStarTypes(), t -> t.weight);
        }

        StarBody star = new StarBody();
        star.entityId = idCounter.incrementAndGet();
        star.starTypeId = type.typeId;
        star.radiusGU = sDef.radiusGU != null ? sDef.radiusGU
                : randomDouble(type.radiusGURange.get(0), type.radiusGURange.get(1));
        star.massSolar = sDef.massSolar != null ? sDef.massSolar
                : randomDouble(type.massSolarRange.get(0), type.massSolarRange.get(1));
        star.temperatureK = sDef.temperatureK != null ? sDef.temperatureK
                : randomInt(type.temperatureKRange.get(0), type.temperatureKRange.get(1));
        star.description = type.description;
        // 生成阶段仅使用预设中显式声明的 ownerNationId，不再默认绑定到玩家国家ID喵。
        star.ownerNationId = sDef.ownerNationId;

        if (sDef.surfaceTexturePath != null) {
            star.surfaceTexturePath = sDef.surfaceTexturePath;
        } else if (type.spriteCandidates != null && !type.spriteCandidates.isEmpty()) {
            int idx = random.nextInt(type.spriteCandidates.size());
            star.surfaceTexturePath = type.spriteCandidates.get(idx);
        }

        return star;
    }

    private PlanetBody generatePlanetFromDef(PresetStarSystemDef.PlanetDef pDef, StarBody primary,
            String playerNationId) {
        PlanetTypeDef type = assets.getPlanetTypes().stream()
                .filter(t -> t.typeId != null && t.typeId.equals(pDef.planetTypeId))
                .findFirst()
                .orElse(null);
        OrbitPresetDef orbitPreset = assets.getOrbitPreset();

        PlanetBody planet = new PlanetBody();
        planet.entityId = idCounter.incrementAndGet();
        planet.planetTypeId = type != null ? type.typeId : "rocky_terran";

        double baseRadius = type != null ? randomDouble(type.radiusGURange.get(0), type.radiusGURange.get(1)) : 400;
        planet.radiusGU = pDef.radiusGU != null ? pDef.radiusGU : baseRadius;

        double baseRot = orbitPreset != null
                ? randomDouble(orbitPreset.rotationPeriodHoursRange.get(0), orbitPreset.rotationPeriodHoursRange.get(1))
                : 24;
        planet.rotationPeriodHours = pDef.rotationPeriodHours != null ? pDef.rotationPeriodHours : baseRot;

        if (pDef.surfaceTexturePath != null) {
            planet.surfaceTexturePath = pDef.surfaceTexturePath;
        } else if (type != null && type.spriteCandidates != null && !type.spriteCandidates.isEmpty()) {
            int idx = random.nextInt(type.spriteCandidates.size());
            planet.surfaceTexturePath = type.spriteCandidates.get(idx);
        }

        // 生成阶段仅使用预设中显式声明的 ownerNationId，不再默认绑定到玩家国家ID喵。
        planet.ownerNationId = pDef.ownerNationId;
        planet.orbitCenterEntityId = primary.entityId;

        if (pDef.orbit != null) {
            planet.semiMajorAxisGU = pDef.orbit.semiMajorAxisGU != null ? pDef.orbit.semiMajorAxisGU : 20000;
            planet.eccentricity = pDef.orbit.eccentricity != null ? pDef.orbit.eccentricity : 0;
            planet.inclinationDeg = pDef.orbit.inclinationDeg != null ? pDef.orbit.inclinationDeg : 0;
            planet.periapsisArgDeg = pDef.orbit.periapsisArgDeg != null ? pDef.orbit.periapsisArgDeg
                    : randomDouble(0, 360);
            planet.meanAnomalyDegAtEpoch = pDef.orbit.meanAnomalyDegAtEpoch != null ? pDef.orbit.meanAnomalyDegAtEpoch
                    : randomDouble(0, 360);
        } else {
            planet.semiMajorAxisGU = 30000;
            planet.eccentricity = 0;
            planet.inclinationDeg = 0;
            planet.periapsisArgDeg = randomDouble(0, 360);
            planet.meanAnomalyDegAtEpoch = randomDouble(0, 360);
        }

        double pYears = Math.sqrt(
                Math.pow(planet.semiMajorAxisGU / staraxis.game.world.WorldConstants.AU_IN_GU, 3) / primary.massSolar);
        planet.orbitalPeriodDays = pYears * 365.25;

        planet.surface = new staraxis.game.planet.PlanetSurface(planet.entityId);
        planet.surfaceComponentId = planet.entityId;
        long mixedSeed = staraxis.game.planet.surface.SurfaceNamingUtils.mixSeed(worldSeedHash, planet.entityId);
        if (type != null) {
            planet.surface.initializeSurface(type, planetAssets, mixedSeed);
        }

        return planet;
    }

    /**
     * 为单个星区生成一个恒星系。
     */
    private StarSystem generateSystemForSector(WorldSector sector, String playerNationId) {
        StarSystem system = new StarSystem();
        system.systemId = idCounter.incrementAndGet();
        system.barycenterEntityId = idCounter.incrementAndGet(); // 预留重心实体ID
        system.sectorCoord = sector.coord;
        // 系统中心 = 星区中心
        system.centerWorldGU = sector.centerWorldGU;

        // 生成主星（第一版只支持单星）
        StarBody primaryStar = generateStar(system, playerNationId);
        system.stars.add(primaryStar);

        // 生成行星
        generatePlanetsForSystem(system, primaryStar, playerNationId);

        return system;
    }

    /**
     * 生成一颗随机恒星。
     */
    private StarBody generateStar(StarSystem system, String playerNationId) {
        StarTypeDef type = weightedRandom(assets.getStarTypes(), t -> t.weight);

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
     */
    private void generatePlanetsForSystem(StarSystem system, StarBody primaryStar, String playerNationId) {
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

            // 随机生成阶段不再默认绑定玩家国家ID，保持行星初始无主喵。
            planet.ownerNationId = null;

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

            // 初始化行星地表组件喵
            planet.surface = new PlanetSurface(planet.entityId);
            planet.surfaceComponentId = planet.entityId; // 暂时让组件 ID 等于实体 ID 喵

            // 方案 A：使用混合种子确保确定性，不再受生成顺序影响喵
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
     * 在指定 3D 位置生成一个恒星系（用于星系生成器策略模式）喵。
     */
    public StarSystem generateSystemAtPosition(SpacePosition position, long starId) {
        StarSystem system = new StarSystem();
        system.systemId = idCounter.incrementAndGet();
        system.barycenterEntityId = idCounter.incrementAndGet();

        // 计算所属星区
        staraxis.game.world.Vec2d pos2d = new staraxis.game.world.Vec2d(position.x(), position.z());
        system.sectorCoord = WorldHexLayout.worldToSectorCoord(pos2d);
        system.centerWorldGU = pos2d;

        // 生成主星
        StarBody primaryStar = generateStar(system, null);
        primaryStar.entityId = starId;
        primaryStar.systemId = system.systemId;
        primaryStar.parentEntityId = system.barycenterEntityId;
        primaryStar.sectorCoord = system.sectorCoord;
        primaryStar.posWorldGU = new SpacePosition(position.x(), position.y(), position.z());
        system.stars.add(primaryStar);

        // 生成行星
        generatePlanetsForSystem(system, primaryStar, null);

        return system;
    }

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
