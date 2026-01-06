package com.staraxis.game.core.world.stellar.orbit;

import java.util.Random;

import com.staraxis.game.shared.world.stellar.orbit.Orbit;
import com.staraxis.game.shared.world.stellar.orbit.OrbitCenterRef;

public class OrbitParamSampler {

    public Orbit samplePlanetOrbit(OrbitCenterRef centerRef, int orbitIndex, Random random) {
        if (centerRef == null) {
            throw new IllegalArgumentException("centerRef 不能为空");
        }
        if (orbitIndex < 0) {
            throw new IllegalArgumentException("orbitIndex 必须 >= 0");
        }
        if (random == null) {
            throw new IllegalArgumentException("random 不能为空");
        }

        float baseScale = 1.0f + orbitIndex * 1.25f;
        float scaleJitter = 0.85f + random.nextFloat() * 0.3f;
        float scale = baseScale * scaleJitter;

        float e = random.nextFloat() * 0.35f;
        float phase = random.nextFloat() * (float) (Math.PI * 2.0);
        
        // 创建轨道并设置新的开普勒参数
        Orbit orbit = new Orbit(centerRef, e, phase, scale, null);
        
        // 设置半长轴（使用 scale 作为半长轴）
        orbit.setSemiMajorAxis(scale);
        
        // 设置其他开普勒参数（可选，使用随机值）
        orbit.setInclination(random.nextFloat() * 0.1f - 0.05f); // 小倾角
        orbit.setLongitudeOfAscendingNode(random.nextFloat() * (float) (Math.PI * 2.0));
        orbit.setArgumentOfPeriapsis(random.nextFloat() * (float) (Math.PI * 2.0));
        orbit.setTrueAnomaly(phase); // 使用相位作为初始真近点角
        
        return orbit;
    }
}
