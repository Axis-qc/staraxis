package staraxis.game.util;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.function.ToDoubleFunction;

/**
 * WeightedRandomUtil（加权随机抽取工具）
 *
 * 提供泛型加权随机选择方法，用于按权重从列表中抽取元素。
 * 支持 List 和 Map 两种输入形式。
 *
 * 使用场景：
 * - 恒星类型按光谱分布权重随机
 * - 行星类型按恒星类型对应的权重随机
 * - 地表区域类型按行星类型对应的权重随机
 */
public final class WeightedRandomUtil {

    private WeightedRandomUtil() {
    }

    /**
     * 从列表中按权重随机选取一个元素。
     *
     * @param items      候选元素列表
     * @param weightFunc 权重提取函数
     * @param rng        随机数生成器（java.util.Random）
     * @param <T>        元素类型
     * @return 选中的元素
     */
    public static <T> T weightedRandom(List<T> items, ToDoubleFunction<T> weightFunc, Random rng) {
        double totalWeight = items.stream().mapToDouble(weightFunc).sum();
        double value = rng.nextDouble() * totalWeight;
        double cumulativeWeight = 0;
        for (T item : items) {
            cumulativeWeight += weightFunc.applyAsDouble(item);
            if (value < cumulativeWeight) {
                return item;
            }
        }
        return items.get(items.size() - 1);
    }

    /**
     * 从列表中按权重随机选取一个元素。
     *
     * @param items      候选元素列表
     * @param weightFunc 权重提取函数
     * @param rng        随机数生成器（java.util.SplittableRandom）
     * @param <T>        元素类型
     * @return 选中的元素
     */
    public static <T> T weightedRandom(List<T> items, ToDoubleFunction<T> weightFunc, SplittableRandom rng) {
        double totalWeight = items.stream().mapToDouble(weightFunc).sum();
        double value = rng.nextDouble() * totalWeight;
        double cumulativeWeight = 0;
        for (T item : items) {
            cumulativeWeight += weightFunc.applyAsDouble(item);
            if (value < cumulativeWeight) {
                return item;
            }
        }
        return items.get(items.size() - 1);
    }

    /**
     * 从 Map 中按 value 权重随机选取一个 key。
     * 适用于 Map&lt;String, Double&gt; 形式的权重配置。
     *
     * @param weightMap 权重映射（key=候选标识，value=权重值）
     * @param rng       随机数生成器
     * @param <K>       key 类型
     * @return 选中的 key
     */
    public static <K> K weightedKey(Map<K, Double> weightMap, Random rng) {
        double totalWeight = 0;
        for (double w : weightMap.values()) {
            totalWeight += w;
        }
        double value = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (Map.Entry<K, Double> entry : weightMap.entrySet()) {
            cumulative += entry.getValue();
            if (value < cumulative) {
                return entry.getKey();
            }
        }
        // fallback: 返回最后一个 key
        K last = null;
        for (K k : weightMap.keySet()) {
            last = k;
        }
        return last;
    }

    /**
     * 从 Map&lt;String, Integer&gt; 中按 value 权重随机选取一个 key。
     * 适用于 JSON 中 integer 权重的场景。
     *
     * @param weightMap 权重映射（key=候选标识，value=权重值）
     * @param rng       随机数生成器
     * @param <K>       key 类型
     * @return 选中的 key
     */
    public static <K> K weightedKeyInt(Map<K, Integer> weightMap, Random rng) {
        double totalWeight = 0;
        for (int w : weightMap.values()) {
            totalWeight += w;
        }
        double value = rng.nextDouble() * totalWeight;
        double cumulative = 0;
        for (Map.Entry<K, Integer> entry : weightMap.entrySet()) {
            cumulative += entry.getValue();
            if (value < cumulative) {
                return entry.getKey();
            }
        }
        K last = null;
        for (K k : weightMap.keySet()) {
            last = k;
        }
        return last;
    }
}
