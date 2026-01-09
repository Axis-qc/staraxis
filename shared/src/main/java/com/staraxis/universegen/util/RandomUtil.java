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
        // 轻量级混合：保证同一 globalSeed 下，不同 salt 派生不同随机流。
        // 注意：该方法适用于一般派生；对“seed + HexCoord”建议使用 deriveFromHexCoord（避免仅线性相关）。
        long mixed = mix64(globalSeed ^ (salt * 0x9E3779B97F4A7C15L));
        return new SplittableRandom(mixed);
    }

    /**
     * 基于 HexCoord(q,r) 派生随机流：与遍历顺序/并行无关。
     */
    public static SplittableRandom deriveFromHexCoord(long globalSeed, int q, int r) {
        long packed = (((long) q) << 32) ^ (r & 0xffffffffL);
        long mixed = mix64(globalSeed) ^ mix64(packed);
        return new SplittableRandom(mixed);
    }

    /**
     * SplitMix64 混合函数（无状态）。
     */
    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}
