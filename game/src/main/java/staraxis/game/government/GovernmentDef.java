package staraxis.game.government;

import java.util.HashMap;
import java.util.Map;

/**
 * GovernmentDef
 *
 * 政体定义（Government Definition）：描述国家的统治形式与特性。
 *
 * 说明：
 * - 政体影响国家治理效率、政策选项、外交关系等核心机制。
 * - 使用 modifiers 存储游戏性修正，便于数据驱动。
 */
public class GovernmentDef {

    /**
     * id（主键）：政体的稳定唯一标识。
     */
    public String id;

    /**
     * nameKey（i18n Key）：政体名称的国际化 key。
     */
    public String nameKey;

    /**
     * descriptionKey（i18n Key）：政体描述的国际化 key。
     */
    public String descriptionKey;

    /**
     * modifiers：政体带来的游戏性修正（占位）。
     *
     * 示例：
     * - "resource.production.bonus": 0.15
     * - "influence.monthly": 1.5
     * - "leader.capacity": 2
     */
    public Map<String, Double> modifiers = new HashMap<>();

    /**
     * 默认构造（供 JSON 反序列化/创建占位对象）。
     */
    public GovernmentDef() {
    }
}
