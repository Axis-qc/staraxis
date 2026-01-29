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
     * nameKey（i18n Key）：国家名称的国际化 key。
     */
    public String nameKey;

    /**
     * descriptionKey（i18n Key）：国家简介的国际化 key。
     */
    public String descriptionKey;

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
     * NationDef 默认构造（供 JSON 反序列化/创建占位对象）。
     */
    public NationDef() {
    }
}
