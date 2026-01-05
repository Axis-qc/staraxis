package com.staraxis.game.shared.world;

import java.util.Random;

/**
 * 种子转换工具类 (Seed conversion utility). 将字符串种子转换为长整型，支持随机种子生成。
 */
public class SeedUtil {

    /**
     * 将文本种子转换为 long 型。 如果文本为空或 null，则生成随机种子。
     *
     * @param seedText 种子文本
     * @return 转换后的 long 型种子
     */
    public static long resolveSeed(String seedText) {
        if (seedText == null || seedText.trim().isEmpty()) {
            return new Random().nextLong();
        }
        // 使用 String.hashCode() 作为基础转换，或者更复杂的哈希算法
        // 这里使用简单的 hashCode 确保确定性
        return (long) seedText.hashCode();
    }
}
