package com.staraxis.game.core.world.stellar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldGenDefinitions;
import com.staraxis.game.shared.world.stellar.Planet;
import com.staraxis.game.shared.world.stellar.Star;
import com.staraxis.game.shared.world.stellar.StarSystem;

/**
 * 恒星与行星生成器（StellarGenerator）。
 *
 * 作用（Purpose）：根据配置与随机源，为单个星系区块生成 StarSystem/Star/Planet。
 * 依赖（Dependencies）：shared 世界模型与定义加载器（WorldGenDefinitions）。 对外接口（Public
 * API）：generateStarSystem/generateStars/generatePlanets。
 */
public class StellarGenerator {

    public StarSystem generateStarSystem(HexCoord coord, WorldGenConfig config, Random random) {
        String systemId = "sys_" + coord.getX() + "_" + coord.getY() + "_" + coord.getZ();
        List<Star> stars = generateStars(config, random);

        for (int i = 0; i < stars.size(); i++) {
            Star star = stars.get(i);
            List<Planet> planets = generatePlanets(config, random);
            star.setPlanets(planets);
        }

        return new StarSystem(systemId, stars);
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
