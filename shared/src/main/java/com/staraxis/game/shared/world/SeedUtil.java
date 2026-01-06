package com.staraxis.game.shared.world;

import java.util.Random;

/**
 * 种子转换工具类 (Seed conversion utility). 将字符串种子转换为长整型，支持随机种子生成。
 */
public class SeedUtil {

    /**
     * 将文本种子转换为 long 型。
     *
     * 规则（Rule）： - 当 seedText 为 null/空字符串/仅空白：生成随机 seedValue（不可复现）。 - 当 seedText
     * 为非空字符串：使用 String.hashCode() 做确定性映射（同字符串 -> 同 seedValue）。
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
