package staraxis.game.tech;

/**
 * TechDef
 *
 * 科技定义（Technology Definition）：用于定义科技条目与其基础信息（占位）。
 *
 * 说明：
 * - 本期只用于“开局科技列表”的占位与 UI 展示。
 * - 未来可扩展：前置科技、解锁内容、研究成本、领域分类、效果系统等。
 */
public class TechDef {

    /**
     * id（主键）：科技的稳定唯一标识。
     */
    public String id;

    /**
     * nameKey（i18n Key）：科技名称的国际化 key。
     */
    public String nameKey;

    /**
     * descriptionKey（i18n Key）：科技描述的国际化 key。
     */
    public String descriptionKey;

    /**
     * tier：科技等级/阶段（占位）。
     */
    public int tier;

    /**
     * 默认构造（供 JSON 反序列化/创建占位对象）。
     */
    public TechDef() {
    }
}
