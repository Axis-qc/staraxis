package staraxis.game.species;

import java.util.ArrayList;
import java.util.List;

/**
 * SpeciesDef
 *
 * 物种定义（Species Definition）：描述一个可被国家选择/主导的智慧物种（占位）。
 *
 * 说明：
 * - 本期用于新游戏国家系统占位与 UI 展示。
 * - 未来可扩展：人口学参数、生理特征、寿命、繁殖、迁移偏好、物种特质系统等。
 */
public class SpeciesDef {

    /**
     * id（主键）：物种的稳定唯一标识。
     */
    public String id;

    /**
     * nameKey（i18n Key）：物种名称的国际化 key。
     */
    public String nameKey;

    /**
     * descriptionKey（i18n Key）：物种描述的国际化 key。
     */
    public String descriptionKey;

    /**
     * traitIds：物种特质 id 列表（占位）。
     */
    public List<String> traitIds = new ArrayList<>();

    /**
     * 默认构造（供 JSON 反序列化/创建占位对象）。
     */
    public SpeciesDef() {
    }
}
