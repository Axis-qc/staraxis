package staraxis.game.industry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * RecipeDef（配方定义）
 *
 * 加工配方的数据定义，从 JSON 加载（assets/industry/recipes.json），
 * 字段覆盖 G2.3 要求的全部配方要素。
 *
 * 字段口径：
 * - inputs（消耗物）：生产时从本地库存扣除的物质。
 * - outputs（产物）：生产时写回本地库存的主产物。
 * - byproducts（副产物）：生产时写回本地库存的副产物。
 * - energyCost（能源消耗）：每次生产消耗的能源量（从库存 ENERGY 物质扣除）。
 * - processTime（加工时间）：完成一批所需游戏日。
 * - facilityType（设施类型）：可执行该配方的设施类型。
 * - technologyId（科技条件）：所需科技 ID，为空表示无科技要求。
 *
 * 未知字段兼容策略：忽略未来新增的未知字段（@JsonIgnoreProperties），
 * 保证读取未来版本配方数据（单向兼容）不受影响。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RecipeDef {

    /** 配方 ID（全局唯一）。 */
    public String recipeId;

    /** 显示名称。 */
    public String displayName;

    /** 可执行该配方的设施类型（如 ELECTROLYZER 电解槽）。 */
    public String facilityType;

    /** 消耗物列表（从库存扣除）。 */
    public List<RecipeItem> inputs = new ArrayList<>();

    /** 产物列表（写回库存）。 */
    public List<RecipeItem> outputs = new ArrayList<>();

    /** 副产物列表（写回库存）。 */
    public List<RecipeItem> byproducts = new ArrayList<>();

    /** 能源消耗（单位），每次生产从库存 ENERGY 物质扣除。 */
    public double energyCost;

    /** 加工时间（游戏日），完成一批所需时间。 */
    public double processTime;

    /** 科技条件 ID，null 或空表示无科技要求。 */
    public String technologyId;

    /**
     * 默认构造（Jackson 反序列化用）。
     */
    public RecipeDef() {
    }

    /**
     * 是否存在科技条件。
     *
     * @return technologyId 非空时返回 true
     */
    public boolean hasTechnologyRequirement() {
        return technologyId != null && !technologyId.isBlank();
    }

    /**
     * 查找消耗物条目。
     *
     * @param substanceId 物质 ID
     * @return 匹配的输入条目，未找到返回 null
     */
    public RecipeItem findInput(String substanceId) {
        return findIn(inputs, substanceId);
    }

    /**
     * 查找产物条目。
     *
     * @param substanceId 物质 ID
     * @return 匹配的产物条目，未找到返回 null
     */
    public RecipeItem findOutput(String substanceId) {
        return findIn(outputs, substanceId);
    }

    /**
     * 查找副产物条目。
     *
     * @param substanceId 物质 ID
     * @return 匹配的副产物条目，未找到返回 null
     */
    public RecipeItem findByproduct(String substanceId) {
        return findIn(byproducts, substanceId);
    }

    /**
     * 在物品列表内按物质 ID 查找。
     */
    private RecipeItem findIn(List<RecipeItem> items, String substanceId) {
        for (RecipeItem item : items) {
            if (item != null && item.substanceId != null && item.substanceId.equals(substanceId)) {
                return item;
            }
        }
        return null;
    }

    /**
     * 获取输出物品项的不可变视图（含产物与副产物），便于 UI 展示。
     *
     * @return 输出物品项列表（不可变）
     */
    public List<RecipeItem> getAllOutputItems() {
        List<RecipeItem> all = new ArrayList<>(outputs.size() + byproducts.size());
        all.addAll(outputs);
        all.addAll(byproducts);
        return Collections.unmodifiableList(all);
    }
}
