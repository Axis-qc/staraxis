package com.staraxis.game.core.world.stellar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.staraxis.game.core.world.stellar.orbit.OrbitConflictDetector;
import com.staraxis.game.core.world.stellar.orbit.OrbitParamSampler;
import com.staraxis.game.core.world.stellar.orbit.OrbitStabilityChecker;
import com.staraxis.game.core.world.stellar.orbit.OrbitValidator;
import com.staraxis.game.core.world.stellar.surface.PlanetSurfaceMeshGenerator;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldGenDefinitions;
import com.staraxis.game.shared.world.stellar.Planet;
import com.staraxis.game.shared.world.stellar.Star;
import com.staraxis.game.shared.world.stellar.StarSystem;
import com.staraxis.game.shared.world.stellar.WorldGenDiagnostics;
import com.staraxis.game.shared.world.stellar.orbit.Orbit;
import com.staraxis.game.shared.world.stellar.orbit.OrbitCenterRef;
import com.staraxis.game.shared.world.stellar.surface.MeshResolutionLevel;
import com.staraxis.game.shared.world.stellar.surface.PlanetSurfaceMesh;

/**
 * 恒星与行星生成器（StellarGenerator）。
 *
 * 作用（Purpose）：根据配置与随机源，为单个星系区块生成 StarSystem/Star/Planet。
 * 依赖（Dependencies）：shared 世界模型与定义加载器（WorldGenDefinitions）。 对外接口（Public
 * API）：generateStarSystem/generateStars/generatePlanets。
 */
public class StellarGenerator {

    private static final int MAX_REPAIR_ATTEMPTS = 3;
    private static final float MIN_SCALE_SEPARATION = 0.5f;
    private static final float P_CIRCUMBINARY = 0.30f;

    public StarSystem generateStarSystem(HexCoord coord, WorldGenConfig config, Random random) {
        String systemId = "sys_" + coord.getX() + "_" + coord.getY() + "_" + coord.getZ();
        List<Star> stars = generateStars(config, random);

        StarSystem system = new StarSystem(systemId, stars);
        WorldGenDiagnostics diagnostics = new WorldGenDiagnostics();

        OrbitCenterRef barycenterRef = null;
        if (stars.size() == 2) {
            String barycenterId = "bary_0";
            system.setBarycenterIds(List.of(barycenterId));
            barycenterRef = new OrbitCenterRef(null, barycenterId);
            for (Star star : stars) {
                star.setOrbitCenterRef(new OrbitCenterRef(null, barycenterId));
            }
        }

        OrbitParamSampler orbitParamSampler = new OrbitParamSampler();
        OrbitStabilityChecker stabilityChecker = new OrbitStabilityChecker();
        PlanetSurfaceMeshGenerator meshGenerator = new PlanetSurfaceMeshGenerator();
        MeshResolutionLevel meshResolutionLevel = config != null ? config.getSurfaceMeshResolutionLevel() : MeshResolutionLevel.LOW;

        for (int i = 0; i < stars.size(); i++) {
            Star star = stars.get(i);
            List<Planet> planets = generatePlanets(config, random);
            star.setPlanets(planets);

            OrbitCenterRef starCenterRef = new OrbitCenterRef(star.getId(), null);
            for (Planet planet : planets) {
                OrbitCenterRef centerRef = starCenterRef;
                if (barycenterRef != null && random.nextFloat() < P_CIRCUMBINARY) {
                    centerRef = barycenterRef;
                }
                Orbit orbit = orbitParamSampler.samplePlanetOrbit(centerRef, planet.getOrbitIndex() == null ? 0 : planet.getOrbitIndex(), random);
                try {
                    OrbitValidator.requireValid(orbit);
                    
                    // 检查轨道稳定性（使用恒星质量作为中心质量，简化处理）
                    float starMass = 1.0f; // 默认质量，可以后续从 Star 类型获取
                    var stabilityResult = stabilityChecker.checkStability(orbit, starMass, 
                            star.getPlanets().stream().map(Planet::getOrbit).filter(o -> o != orbit).toList(), 
                            0.1f); // 碰撞半径
                    
                    if (!stabilityResult.isStable()) {
                        diagnostics.addMessage("Unstable orbit detected: " + String.join(", ", stabilityResult.getMessages()));
                    }
                } catch (RuntimeException ex) {
                    diagnostics.addMessage("Invalid orbit: " + ex.getMessage());
                }
                planet.setOrbit(orbit);

                PlanetSurfaceMesh surfaceMesh = meshGenerator.generate(meshResolutionLevel);
                planet.setSurfaceMesh(surfaceMesh);
            }
        }

        int attempts = 0;
        String conflictReason = findConflictReason(stars);
        while (conflictReason != null && attempts < MAX_REPAIR_ATTEMPTS) {
            attempts++;
            diagnostics.setRepairAttemptCount(attempts);
            diagnostics.addMessage("Orbit conflict detected, attempt=" + attempts + ": " + conflictReason);

            // Simple repair: resample orbits for all planets that share the conflicting centers.
            for (Star star : stars) {
                OrbitCenterRef starCenterRef = new OrbitCenterRef(star.getId(), null);
                for (Planet planet : star.getPlanets()) {
                    Orbit current = planet.getOrbit();
                    if (current == null || current.getCenterRef() == null) {
                        continue;
                    }
                    OrbitCenterRef centerRef = current.getCenterRef();
                    if (centerRef.getBarycenterId() != null && barycenterRef != null) {
                        centerRef = barycenterRef;
                    } else if (centerRef.getStarId() != null) {
                        centerRef = starCenterRef;
                    }
                    Orbit orbit = orbitParamSampler.samplePlanetOrbit(centerRef, planet.getOrbitIndex() == null ? 0 : planet.getOrbitIndex(), random);
                    planet.setOrbit(orbit);
                }
            }

            conflictReason = findConflictReason(stars);
        }

        if (conflictReason != null) {
            diagnostics.addMessage("Orbit conflict unresolved after " + MAX_REPAIR_ATTEMPTS + " attempts: " + conflictReason);
        }

        if (diagnostics.getRepairAttemptCount() > 0 || !diagnostics.getMessages().isEmpty() || !diagnostics.getDetails().isEmpty()) {
            system.setDiagnostics(diagnostics);
        }

        return system;
    }

    private String findConflictReason(List<Star> stars) {
        Map<String, List<Orbit>> byCenter = new HashMap<>();
        for (Star star : stars) {
            for (Planet planet : star.getPlanets()) {
                Orbit orbit = planet.getOrbit();
                if (orbit == null || orbit.getCenterRef() == null) {
                    continue;
                }
                OrbitCenterRef c = orbit.getCenterRef();
                String key;
                if (c.getStarId() != null) {
                    key = "star:" + c.getStarId();
                } else if (c.getBarycenterId() != null) {
                    key = "bary:" + c.getBarycenterId();
                } else {
                    continue;
                }
                byCenter.computeIfAbsent(key, k -> new ArrayList<>()).add(orbit);
            }
        }

        for (Map.Entry<String, List<Orbit>> e : byCenter.entrySet()) {
            String reason = OrbitConflictDetector.findFirstConflictReason(e.getValue(), MIN_SCALE_SEPARATION);
            if (reason != null) {
                return e.getKey() + ":" + reason;
            }
        }
        return null;
    }

    public List<Star> generateStars(WorldGenConfig config, Random random) {
        int starCount = sampleStarsPerSystem(random);
        Map<String, String> starTypes = WorldGenDefinitions.getStarTypes();
        List<String> starTypeIds = new ArrayList<>(starTypes.keySet());
        if (starTypeIds.isEmpty()) {
            starTypeIds.add("yellow_dwarf");
        }

        List<Star> stars = new ArrayList<>();
        for (int i = 0; i < starCount; i++) {
            String starId = "star_" + i;
            String starTypeId = starTypeIds.get(random.nextInt(starTypeIds.size()));
            Star star = new Star();
            star.setId(starId);
            star.setStarTypeId(starTypeId);
            stars.add(star);
        }
        return stars;
    }

    public List<Planet> generatePlanets(WorldGenConfig config, Random random) {
        int planetCount = samplePlanetsPerStar(config.getPlanetComplexity(), random);
        Map<String, String> planetTypes = WorldGenDefinitions.getPlanetTypes();
        List<String> planetTypeIds = new ArrayList<>(planetTypes.keySet());
        if (planetTypeIds.isEmpty()) {
            planetTypeIds.add("rocky");
        }

        List<Planet> planets = new ArrayList<>();
        for (int i = 0; i < planetCount; i++) {
            String planetId = "planet_" + i;
            String planetTypeId = planetTypeIds.get(random.nextInt(planetTypeIds.size()));

            Planet planet = new Planet();
            planet.setId(planetId);
            planet.setPlanetTypeId(planetTypeId);
            planet.setOrbitIndex(i);
            planets.add(planet);
        }
        return planets;
    }

    private int sampleStarsPerSystem(Random random) {
        float roll = random.nextFloat();
        if (roll < 0.70f) {
            return 1;
        }
        if (roll < 0.90f) {
            return 2;
        }
        return 3;
    }

    private int samplePlanetsPerStar(float planetComplexity, Random random) {
        float c = clamp01(planetComplexity);
        int mu = 1 + Math.round(c * 5.0f);
        int max = mu + 2;

        int sampled = Math.round((float) (random.nextGaussian() + mu));
        if (sampled < 0) {
            sampled = 0;
        }
        if (sampled > max) {
            sampled = max;
        }
        return sampled;
    }

    private float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
