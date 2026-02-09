package staraxis.game.planet.def;

/**
 * NamePoolDef（命名池定义）
 *
 * 随机命名词根池的配置定义，从JSON加载喵。
 */
public class NamePoolDef {

    /** 命名池ID，例如 "continent"、"ocean"喵。 */
    public String poolId;

    /** 前缀列表喵。 */
    public String[] prefixes;

    /** 中缀列表喵。 */
    public String[] middles;

    /** 后缀列表喵。 */
    public String[] suffixes;

    /**
     * 验证命名池定义是否有效喵。
     *
     * @return 如果poolId不为空且至少有一个词根列表不为空，返回true喵。
     */
    public boolean isValid() {
        return poolId != null && !poolId.isEmpty() &&
                ((prefixes != null && prefixes.length > 0) ||
                        (suffixes != null && suffixes.length > 0));
    }
}
