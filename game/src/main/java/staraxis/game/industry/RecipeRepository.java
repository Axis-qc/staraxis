package staraxis.game.industry;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RecipeRepository（配方配置仓库）
 *
 * 加载加工配方定义（assets/industry/recipes.json，遵循 base + 兜底口径）：
 * - 配置文件存在且非空：以文件为唯一真源。
 * - 配置文件缺失或为空（0 字节 / 空 JSON 数组）：使用内置默认配方（水电解）兜底，保证无数据文件也能运行。
 * - 配置文件存在但 JSON 解析失败：视为配置错误，抛出异常暴露问题，禁止静默回退默认配方掩盖错误。
 *
 * 未知字段兼容策略（单向兼容）：反序列化时忽略未来新增的未知字段，保证旧版本客户端读取新版本配方数据不受影响。
 *
 * 内置默认配方定义于 {@link #defaultRecipes()}，并通过 {@link #DEFAULT_ELECTROLYSIS_RECIPE_ID}
 * 暴露供代码引用。
 */
public class RecipeRepository {

    /** 默认配方文件相对路径（相对项目根目录）。 */
    public static final String DEFAULT_RECIPES_PATH = "assets/industry/recipes.json";

    /** 内置默认水电解配方 ID。 */
    public static final String DEFAULT_ELECTROLYSIS_RECIPE_ID = "RECIPE_WATER_ELECTROLYSIS";

    /** 水电解设施类型：电解槽。 */
    public static final String FACILITY_TYPE_ELECTROLYZER = "ELECTROLYZER";

    private final ObjectMapper objectMapper;
    private List<RecipeDef> recipes = List.of();

    /**
     * 构造配方仓库。
     *
     * @param objectMapper Jackson 对象映射器
     */
    public RecipeRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 加载配方：优先从默认路径加载，缺失/为空时回退到内置默认配方。
     * 默认路径文件存在但 JSON 解析失败时抛出异常（配置错误，不静默回退）。
     *
     * @throws IllegalStateException 默认路径文件存在但解析失败时抛出
     */
    public void loadAll() {
        List<RecipeDef> loaded = loadFromPath(DEFAULT_RECIPES_PATH);
        recipes = (loaded == null || loaded.isEmpty()) ? defaultRecipes() : loaded;
    }

    /**
     * 从指定路径加载配方文件。
     * 路径为 null/空白或文件缺失时返回空列表（合法场景，可由调用方回退默认配方）；
     * 文件存在但 JSON 解析失败时抛出异常（配置错误，不得静默回退）。
     *
     * @param path 文件路径
     * @return 配方列表（文件缺失或为空时为空列表）
     * @throws IllegalStateException 文件存在但解析失败时抛出
     */
    public List<RecipeDef> loadFromPath(String path) {
        if (path == null || path.isBlank()) {
            return List.of();
        }
        File file = new File(path);
        if (!file.isFile()) {
            return List.of();
        }
        try {
            return loadFromFile(file);
        } catch (IOException e) {
            // 文件存在但无法解析：属于数据配置错误，必须暴露而非静默回退默认配方
            throw new IllegalStateException("配方文件解析失败: " + file.getAbsolutePath(), e);
        }
    }

    /**
     * 从文件解析配方列表。
     * 文件缺失、空文件（0 字节）或空 JSON 数组时返回空列表（视为合法空配方集）；
     * 文件存在但内容不是合法 JSON 数组时抛出异常。
     *
     * @param file 配方 JSON 文件
     * @return 配方列表（JSON 数组，可为空）
     * @throws IOException 文件存在但解析失败时抛出
     */
    public List<RecipeDef> loadFromFile(File file) throws IOException {
        if (file == null || !file.isFile() || file.length() == 0L) {
            return List.of();
        }
        RecipeDef[] array = objectMapper.readValue(file, RecipeDef[].class);
        if (array == null || array.length == 0) {
            return List.of();
        }
        List<RecipeDef> list = new ArrayList<>(array.length);
        Collections.addAll(list, array);
        return list;
    }

    /**
     * 覆盖设置当前配方集（测试/程序化注入用）。
     *
     * @param recipes 配方列表
     */
    public void setRecipes(List<RecipeDef> recipes) {
        this.recipes = recipes == null ? List.of() : List.copyOf(recipes);
    }

    /**
     * 获取全部配方。
     *
     * @return 配方列表（不可变）
     */
    public List<RecipeDef> getAllRecipes() {
        return recipes;
    }

    /**
     * 按配方 ID 查询。
     *
     * @param recipeId 配方 ID
     * @return 配方定义，未找到返回 null
     */
    public RecipeDef getRecipe(String recipeId) {
        if (recipeId == null) {
            return null;
        }
        for (RecipeDef recipe : recipes) {
            if (recipe != null && recipeId.equals(recipe.recipeId)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * 按设施类型查询可用配方。
     *
     * @param facilityType 设施类型
     * @return 该设施可执行的配方列表（不可变）
     */
    public List<RecipeDef> getRecipesForFacility(String facilityType) {
        if (facilityType == null) {
            return List.of();
        }
        List<RecipeDef> matched = new ArrayList<>();
        for (RecipeDef recipe : recipes) {
            if (recipe != null && facilityType.equals(recipe.facilityType)) {
                matched.add(recipe);
            }
        }
        return Collections.unmodifiableList(matched);
    }

    /**
     * 内置默认配方集合（assets 配方文件缺失时的兜底）。
     *
     * 第一条化学生产线（G2.5）：2H2O + 电力 -> 2H2 + O2。
     * - 产物：氢气 2 单位。
     * - 副产物：氧气 1 单位（支持副产物流入库存）。
     * - 能源：每次消耗 1 单位能源。
     * - 时间：1 游戏日完成一批。
     *
     * @return 默认配方列表
     */
    public static List<RecipeDef> defaultRecipes() {
        RecipeDef electrolysis = new RecipeDef();
        electrolysis.recipeId = DEFAULT_ELECTROLYSIS_RECIPE_ID;
        electrolysis.displayName = "水电解";
        electrolysis.facilityType = FACILITY_TYPE_ELECTROLYZER;
        electrolysis.inputs = List.of(new RecipeItem(SubstanceId.WATER, 2.0));
        electrolysis.outputs = List.of(new RecipeItem(SubstanceId.HYDROGEN, 2.0));
        electrolysis.byproducts = List.of(new RecipeItem(SubstanceId.OXYGEN, 1.0));
        electrolysis.energyCost = 1.0;
        electrolysis.processTime = 1.0;
        electrolysis.technologyId = null;
        return List.of(electrolysis);
    }
}
