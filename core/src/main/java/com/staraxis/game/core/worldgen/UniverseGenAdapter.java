package com.staraxis.game.core.worldgen;

import com.staraxis.game.shared.world.astronomical.AstronomicalUnit;
import com.staraxis.game.shared.world.stellar.Planet;
import com.staraxis.game.shared.world.stellar.Star;
import com.staraxis.game.shared.world.stellar.StarSystem;

import java.util.ArrayList;
import java.util.List;

/**
 * universegen -> shared.world.stellar 适配器。
 *
 * 目标：不再依赖旧 StellarGenerator，直接使用 universegen 产出的真实数据。
 */
public class UniverseGenAdapter {

    // 1 AU = 149,597,870.7 km
    private static final double AU_IN_KM = 149_597_870.7;

    public StarSystem toSharedStarSystem(com.staraxis.universegen.model.StarSystem src) {
        if (src == null) {
            return null;
        }

        StarSystem sys = new StarSystem();
        sys.setId(src.name());

        List<Star> stars = new ArrayList<>();
        for (com.staraxis.universegen.model.Star s : src.stars()) {
            stars.add(toSharedStar(s));
        }

        // StarSystem 强约束：stars.size in [1,3]，这里保证 universegen 已经满足
        sys.setStars(stars);
        return sys;
    }

    private Star toSharedStar(com.staraxis.universegen.model.Star src) {
        Star s = new Star();
        s.setId(src.name());
        s.setStarTypeId(src.type() != null ? src.type() : "unknown");

        List<Planet> planets = new ArrayList<>();
        List<com.staraxis.universegen.model.Planet> srcPlanets = src.planets();
        if (srcPlanets != null) {
            for (int i = 0; i < srcPlanets.size(); i++) {
                planets.add(toSharedPlanet(srcPlanets.get(i), i));
            }
        }
        s.setPlanets(planets);
        return s;
    }

    private Planet toSharedPlanet(com.staraxis.universegen.model.Planet src, int orbitIndex) {
        Planet p = new Planet();
        p.setId(src.name());
        p.setOrbitIndex(orbitIndex);

        // planetTypeId：暂用 unknown（后续可按 radius/轨道等分类并数据驱动）
        p.setPlanetTypeId("unknown");

        // 半径：km -> AU
        if (src.radiusKm() > 0) {
            double radiusAu = src.radiusKm() / AU_IN_KM;
            p.setRadius(AstronomicalUnit.fromAU(radiusAu));
        }

        // Orbit 暂不接入（shared.world.stellar.Orbit 结构更复杂），后续可对齐
        return p;
    }
}
