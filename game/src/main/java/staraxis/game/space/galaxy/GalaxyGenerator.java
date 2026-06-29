package staraxis.game.space.galaxy;

/**
 * GalaxyGenerator（星系生成器接口）。
 *
 * 根据配置生成星系中所有恒星的位置数据。
 * 不同 GalaxyType 对应不同实现。
 *
 * 确定性保证：相同 seed + 相同配置 -> 完全相同的星系。
 */
public interface GalaxyGenerator {

    /**
     * 生成星系数据。
     *
     * @param config 星系生成配置
     * @return 包含所有恒星位置数据的 GalaxyData
     */
    GalaxyData generate(GalaxyConfig config);
}
