package com.staraxis.game.core.worldgen;

import com.staraxis.game.shared.net.worldgen.snapshot.SectorTypes;

import java.util.Random;

/**
 * 星区类型分配器：根据 galaxy/nebula/deep_space 比例随机决定星区类型。
 */
public class SectorTypeDistributor {

    private final float galaxyRatio;
    private final float nebulaRatio;
    private final float deepSpaceRatio;

    public SectorTypeDistributor(float galaxyRatio, float nebulaRatio) {
        this.galaxyRatio = clamp01(galaxyRatio);
        this.nebulaRatio = clamp01(nebulaRatio);
        float sum = this.galaxyRatio + this.nebulaRatio;
        this.deepSpaceRatio = sum < 1.0f ? (1.0f - sum) : 0.0f;
    }

    public String getSectorType(Random rng) {
        float roll = rng.nextFloat();
        if (roll < galaxyRatio) {
            return SectorTypes.STAR_SYSTEM;
        }
        if (roll < galaxyRatio + nebulaRatio) {
            return SectorTypes.NEBULA;
        }
        return SectorTypes.DEEP_SPACE;
    }

    private float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}