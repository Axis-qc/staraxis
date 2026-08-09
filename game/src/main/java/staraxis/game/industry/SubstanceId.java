package staraxis.game.industry;

/**
 * SubstanceId（物质 ID 常量表）
 *
 * 元素化资源体系的最小物质集合（G2.4）：原始物质、基础元素与加工产物。
 * 统一以 ID 常量引用物质，避免代码中出现散落字符串。
 *
 * 说明：
 * - 原始资源以矿物或化合物形式采集，不假设所有元素都能单独开采。
 * - 物质 ID 与配方 JSON 中的 substanceId 字段保持一致。
 */
public final class SubstanceId {

    private SubstanceId() {
    }

    /** 水（H2O）：电解原料，也是维持生存的基础物质。 */
    public static final String WATER = "WATER";

    /** 金属矿（Metal Ore）：原始资源，冶炼后获得铁/铜/铝等金属。 */
    public static final String MINERAL_ORE = "MINERAL_ORE";

    /** 硅酸盐矿（Silicate Ore）：原始资源，冶炼后获得硅。 */
    public static final String SILICATE_ORE = "SILICATE_ORE";

    /** 碳质矿（Carbonaceous Ore）：原始资源，冶炼后获得碳。 */
    public static final String CARBON_ORE = "CARBON_ORE";

    /** 氢气（H2）：电解水主产物，可作为燃料或化工原料。 */
    public static final String HYDROGEN = "HYDROGEN";

    /** 氧气（O2）：电解水副产物，可用于维持生命或助燃。 */
    public static final String OXYGEN = "OXYGEN";

    /** 碳（C）：基础元素，冶炼碳质矿获得。 */
    public static final String CARBON = "CARBON";

    /** 铁（Fe）：基础金属元素。 */
    public static final String IRON = "IRON";

    /** 硅（Si）：基础半导体/建材元素。 */
    public static final String SILICON = "SILICON";

    /** 铝（Al）：基础轻金属元素。 */
    public static final String ALUMINUM = "ALUMINUM";

    /** 铜（Cu）：基础导电金属元素。 */
    public static final String COPPER = "COPPER";

    /** 能源（Energy）：电力/能源资源，加工设施的能量输入。 */
    public static final String ENERGY = "ENERGY";
}
