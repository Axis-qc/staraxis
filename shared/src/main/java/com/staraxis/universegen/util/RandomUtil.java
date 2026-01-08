package com.staraxis.universegen.util;

import java.util.SplittableRandom;

/**
 * SplittableRandom 工具：根据主 seed 与派生 key 生成可复现随机流。
 */
public final class RandomUtil {

    private RandomUtil() {}

    public static SplittableRandom fromSeed(long seed) {
        return new SplittableRandom(seed);
    }

    public static SplittableRandom derive(long globalSeed, long salt) {
        long mixed = globalSeed ^ (salt * 0x9E3779B97F4A7C15L);
        return new SplittableRandom(mixed);
    }
}
