package staraxis.game.industry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RecipeRepositoryTest（配方仓库单元测试）
 *
 * 覆盖默认配方字段完整性、JSON 加载与默认路径兜底。
 */
class RecipeRepositoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void defaultRecipesContainWaterElectrolysisWithAllFields() {
        RecipeRepository repository = new RecipeRepository(objectMapper);
        repository.loadAll();

        RecipeDef recipe = repository.getRecipe(RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID);
        assertNotNull(recipe);
        assertNotNull(recipe.displayName);

        // 配方数据必须表达 G2.3 要求的全部要素
        assertNotNull(recipe.inputs);
        assertNotNull(recipe.outputs);
        assertNotNull(recipe.byproducts);
        assertEquals(RecipeRepository.FACILITY_TYPE_ELECTROLYZER, recipe.facilityType);
        assertTrue(recipe.energyCost > 0);
        assertTrue(recipe.processTime > 0);

        // 水电解：2H2O + 电力 -> 2H2 + O2
        RecipeItem water = recipe.findInput(SubstanceId.WATER);
        assertNotNull(water);
        assertEquals(2.0, water.amount);
        RecipeItem hydrogen = recipe.findOutput(SubstanceId.HYDROGEN);
        assertNotNull(hydrogen);
        assertEquals(2.0, hydrogen.amount);
        RecipeItem oxygen = recipe.findByproduct(SubstanceId.OXYGEN);
        assertNotNull(oxygen);
        assertEquals(1.0, oxygen.amount);
        assertFalse(recipe.hasTechnologyRequirement());
    }

    @Test
    void loadFromFileParsesRecipeJson(@TempDir Path tempDir) throws Exception {
        Path jsonFile = tempDir.resolve("recipes.json");
        Files.writeString(jsonFile, """
                [
                  {
                    "recipeId": "RECIPE_TEST",
                    "displayName": "测试配方",
                    "facilityType": "FURNACE",
                    "inputs": [ { "substanceId": "MINERAL_ORE", "amount": 3.0 } ],
                    "outputs": [ { "substanceId": "IRON", "amount": 2.0 } ],
                    "byproducts": [ { "substanceId": "SILICON", "amount": 0.5 } ],
                    "energyCost": 1.5,
                    "processTime": 2.0,
                    "technologyId": "TECH_REFINING"
                  }
                ]
                """);

        RecipeRepository repository = new RecipeRepository(objectMapper);
        List<RecipeDef> recipes = repository.loadFromFile(jsonFile.toFile());

        assertEquals(1, recipes.size());
        RecipeDef recipe = recipes.get(0);
        assertEquals("RECIPE_TEST", recipe.recipeId);
        assertEquals("FURNACE", recipe.facilityType);
        assertEquals(3.0, recipe.findInput(SubstanceId.MINERAL_ORE).amount);
        assertEquals(2.0, recipe.findOutput(SubstanceId.IRON).amount);
        assertEquals(0.5, recipe.findByproduct(SubstanceId.SILICON).amount);
        assertEquals(1.5, recipe.energyCost);
        assertEquals(2.0, recipe.processTime);
        assertTrue(recipe.hasTechnologyRequirement());
    }

    @Test
    void loadFromMissingPathFallsBackToDefaults() {
        RecipeRepository repository = new RecipeRepository(objectMapper);
        repository.loadAll();

        assertNotNull(repository.getRecipe(RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID));
        assertEquals(1, repository.getAllRecipes().size());
    }

    @Test
    void loadFromPathReturnsEmptyForMissingFile() {
        RecipeRepository repository = new RecipeRepository(objectMapper);
        assertTrue(repository.loadFromPath("assets/industry/not-exist.json").isEmpty());
    }

    @Test
    void emptyFileReturnsEmptyList(@TempDir Path tempDir) throws Exception {
        RecipeRepository repository = new RecipeRepository(objectMapper);
        Path emptyFile = tempDir.resolve("empty.json");
        Files.writeString(emptyFile, "");

        // 空文件（0 字节）属于合法空配方集，返回空列表
        assertTrue(repository.loadFromFile(emptyFile.toFile()).isEmpty());
        assertTrue(repository.loadFromPath(emptyFile.toString()).isEmpty());
    }

    @Test
    void emptyJsonArrayReturnsEmptyList(@TempDir Path tempDir) throws Exception {
        RecipeRepository repository = new RecipeRepository(objectMapper);
        Path arrayFile = tempDir.resolve("empty-array.json");
        Files.writeString(arrayFile, "[]");

        assertTrue(repository.loadFromFile(arrayFile.toFile()).isEmpty());
        assertTrue(repository.loadFromPath(arrayFile.toString()).isEmpty());
    }

    @Test
    void invalidJsonFileThrowsIOException(@TempDir Path tempDir) throws Exception {
        RecipeRepository repository = new RecipeRepository(objectMapper);
        Path brokenFile = tempDir.resolve("broken.json");
        Files.writeString(brokenFile, "{ not valid json ");

        // 文件存在但内容不是合法 JSON：解析失败必须抛出，不得静默吞掉
        assertThrows(IOException.class, () -> repository.loadFromFile(brokenFile.toFile()));
    }

    @Test
    void invalidJsonPathThrowsInsteadOfSilentFallback(@TempDir Path tempDir) throws Exception {
        RecipeRepository repository = new RecipeRepository(objectMapper);
        Path brokenFile = tempDir.resolve("broken.json");
        Files.writeString(brokenFile, "{ not valid json ");

        // 文件存在但解析失败属于配置错误：loadFromPath 必须抛异常暴露，
        // 不得返回空列表导致上层静默回退默认配方
        assertThrows(IllegalStateException.class, () -> repository.loadFromPath(brokenFile.toString()));
    }

    @Test
    void unknownFieldsAreIgnoredWhenParsing(@TempDir Path tempDir) throws Exception {
        // 未知字段兼容（单向兼容策略）：读取未来版本配方数据时忽略未知字段，不因未知字段解析失败
        RecipeRepository repository = new RecipeRepository(objectMapper);
        Path jsonFile = tempDir.resolve("future-recipes.json");
        Files.writeString(jsonFile, """
                [
                  {
                    "recipeId": "RECIPE_FUTURE",
                    "displayName": "未来配方",
                    "facilityType": "FURNACE",
                    "inputs": [ { "substanceId": "MINERAL_ORE", "amount": 3.0, "newInputField": 1 } ],
                    "outputs": [ { "substanceId": "IRON", "amount": 2.0 } ],
                    "energyCost": 1.5,
                    "processTime": 2.0,
                    "futureUnknownField": { "nested": true },
                    "anotherFutureList": [1, 2, 3]
                  }
                ]
                """);

        List<RecipeDef> recipes = repository.loadFromFile(jsonFile.toFile());

        assertEquals(1, recipes.size());
        RecipeDef recipe = recipes.get(0);
        assertEquals("RECIPE_FUTURE", recipe.recipeId);
        assertEquals(3.0, recipe.findInput(SubstanceId.MINERAL_ORE).amount);
        assertEquals(2.0, recipe.findOutput(SubstanceId.IRON).amount);
    }

    @Test
    void getRecipesForFacilityFiltersByType() {
        RecipeRepository repository = new RecipeRepository(objectMapper);
        repository.setRecipes(RecipeRepository.defaultRecipes());

        List<RecipeDef> electrolyzerRecipes = repository.getRecipesForFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER);
        assertEquals(1, electrolyzerRecipes.size());
        assertTrue(repository.getRecipesForFacility("FURNACE").isEmpty());
        assertNull(repository.getRecipe("NOT_EXIST"));
    }

    @Test
    void defaultRecipesPathFileExists() {
        // 数据驱动：默认路径的配方文件应存在于 assets 目录（项目根为工作目录时）
        File file = new File(RecipeRepository.DEFAULT_RECIPES_PATH);
        assertTrue(file.isFile(), "配方数据文件应存在: " + file.getAbsolutePath());
    }
}
