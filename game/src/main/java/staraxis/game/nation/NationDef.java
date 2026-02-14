package staraxis.game.nation;

import java.util.ArrayList;
import java.util.List;

/**
 * NationDef
 *
 * 国家定义（Nation Definition）：用于“预设国家”与“玩家自定义国家”的统一数据结构。
 *
 * 说明：
 * - 这是“内容/配置层”的定义对象（definition），不是运行时状态（state）。
 * - 仅包含用于开局与 UI 展示所需的最小字段，后续可逐步扩展。
 * - 字段命名遵循统一术语：以 Id/Ids 结尾的字段表示引用关系。
 */
public class NationDef {

    /**
     * id（主键）：国家的稳定唯一标识。
     */
    public String id;

    /**
     * name：国家名称（玩家可直接输入的纯文本）。
     */
    public String name;

    /**
     * description：国家简介（玩家可直接输入的纯文本，占位）。
     */
    public String description;

    /**
     * governmentId：政体定义的引用 id。
     */
    public String governmentId;

    /**
     * speciesIds：该国家可用/主导物种的引用 id 列表（占位）。
     */
    public List<String> speciesIds = new ArrayList<>();

    /**
     * startingTechIds：开局已解锁科技的引用 id 列表（占位）。
     */
    public List<String> startingTechIds = new ArrayList<>();

    /**
     * spawnStrategy：出生点策略。
     * 可选模式：preset (预设星系), random (随机未占用星系)。
     */
    public SpawnStrategy spawnStrategy = new SpawnStrategy();

    public static class SpawnStrategy {
        /**
         * mode：模式。使用 MODE_PRESET / MODE_RANDOM 常量以避免硬编码。
         */
        public String mode = MODE_RANDOM;

        /** 出生模式：预设星系。 */
        public static final String MODE_PRESET = "preset";

        /** 出生模式：随机未占用星系。 */
        public static final String MODE_RANDOM = "random";

        /**
         * presetSystemId：当 mode 为 preset 时指定的预设星系 ID（非 entityId，是配置 ID）。
         */
        public String presetSystemId;
    }

    /**
     * NationDef 默认构造（供 JSON 反序列化/创建占位对象）。
     */
    public NationDef() {
    }
}
