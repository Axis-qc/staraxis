package com.staraxis.game.core.world.stellar.orbit;

import java.util.ArrayList;
import java.util.List;

import com.staraxis.game.shared.world.stellar.Planet;
import com.staraxis.game.shared.world.stellar.Star;
import com.staraxis.game.shared.world.stellar.StarSystem;
import com.staraxis.game.shared.world.stellar.orbit.Orbit;
import com.staraxis.game.shared.world.stellar.orbit.OrbitPath;
import com.staraxis.game.shared.world.stellar.orbit.OrbitPrecisionLevel;

public class OrbitPathService {

    private final OrbitPathSampler sampler;

    public OrbitPathService() {
        this(new OrbitPathSampler());
    }

    public OrbitPathService(OrbitPathSampler sampler) {
        if (sampler == null) {
            throw new IllegalArgumentException("sampler 不能为空");
        }
        this.sampler = sampler;
    }

    public List<OrbitPath> generateOrbitPaths(StarSystem system, OrbitPrecisionLevel precisionLevel) {
        if (system == null) {
            throw new IllegalArgumentException("system 不能为空");
        }
        if (precisionLevel == null) {
            throw new IllegalArgumentException("precisionLevel 不能为空");
        }

        List<OrbitPath> paths = new ArrayList<>();
        for (Star star : system.getStars()) {
            for (Planet planet : star.getPlanets()) {
                Orbit orbit = planet.getOrbit();
                if (orbit == null) {
                    continue;
                }
                String orbitId = system.getId() + ":" + star.getId() + ":" + planet.getId();
                paths.add(sampler.sample(orbitId, orbit, precisionLevel));
            }
        }
        return paths;
    }
}
