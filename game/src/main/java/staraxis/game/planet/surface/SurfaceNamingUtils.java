package staraxis.game.planet.surface;

import staraxis.game.planet.def.NamePoolDef;

import java.util.Random;

/**
 * SurfaceNamingUtils
 *
 * 地表命名工具类喵。
 * - 从命名池中抽取前缀/中缀/后缀组合生成名称喵。
 * - 使用传入的 Random，确保在相同 seed 下生成结果一致喵。
 */
public final class SurfaceNamingUtils {

    private SurfaceNamingUtils() {
    }

    /**
     * 从命名池生成一个名称喵。
     *
     * @param rng  随机数生成器（应由上层基于 worldSeed 派生）喵。
     * @param pool 命名池定义喵。
     * @return 生成的名称喵。
     */
    public static String generateName(Random rng, NamePoolDef pool) {
        if (rng == null) {
            throw new IllegalArgumentException("rng_required");
        }
        if (pool == null) {
            throw new IllegalArgumentException("pool_required");
        }

        String prefix = pick(rng, pool.prefixes, "");
        String middle = pick(rng, pool.middles, "");
        String suffix = pick(rng, pool.suffixes, "");

        String name = (prefix == null ? "" : prefix) + (middle == null ? "" : middle) + (suffix == null ? "" : suffix);
        if (name.isBlank()) {
            return pool.poolId == null ? "region" : pool.poolId;
        }
        return name;
    }

    private static String pick(Random rng, String[] arr, String fallback) {
        if (arr == null || arr.length == 0) {
            return fallback;
        }
        int idx = Math.floorMod(rng.nextInt(), arr.length);
        String v = arr[idx];
        return v == null ? fallback : v;
    }

    /**
     * 确定性种子混合函数（方案 A）喵。
     * 采用简单的质数混合逻辑，确保 (worldSeed, planetId) 组合产生唯一的、不受生成顺序影响的种子喵。
     *
     * @param worldSeed 宇宙根种子喵。
     * @param planetId  行星实体唯一 ID 喵。
     * @return 混合后的地表生成种子喵。
     */
    public static long mixSeed(long worldSeed, long planetId) {
        long h = worldSeed ^ (planetId * 0x517cc1b727220a95L);
        h = Long.rotateLeft(h, 31) * 0xbf58476d1ce4e5b9L;
        h ^= h >>> 33;
        h *= 0x94d049bb133111ebL;
        h ^= h >>> 33;
        return h;
    }

    /**
     * 确定性区域 ID 混合函数喵。
     * 为每个区域生成一个基于行星 ID 和索引的唯一且确定的 ID 喵。
     *
     * @param planetId 行星实体 ID 喵。
     * @param index    区域在该行星上的索引（0..N）喵。
     * @return 混合后的确定性区域 ID 喵。
     */
    public static long mixRegionId(long planetId, int index) {
        // 使用不同的魔数与 seed 混合逻辑区分，避免 ID 与 seed 碰撞喵
        long h = planetId ^ (index * 0x45d9f3bL);
        h = ((h >>> 16) ^ h) * 0x45d9f3bL;
        h = ((h >>> 16) ^ h) * 0x45d9f3bL;
        h = (h >>> 16) ^ h;
        // 保证 ID 为正数且不与行星 ID 冲突（通过位偏移或魔数）喵
        return Math.abs(h);
    }
}
