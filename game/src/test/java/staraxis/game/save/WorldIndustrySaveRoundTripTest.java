package staraxis.game.save;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.astro.AstroData;
import staraxis.game.command.LoadWorldCommand;
import staraxis.game.industry.CargoTransfer;
import staraxis.game.industry.IndustryRegistry;
import staraxis.game.industry.LocalInventory;
import staraxis.game.industry.ProcessingFacility;
import staraxis.game.industry.RecipeRepository;
import staraxis.game.industry.ResourceExtractionFacility;
import staraxis.game.industry.SubstanceId;
import staraxis.game.sim.SimulationTime;
import staraxis.game.state.WorldState;

/**
 * WorldIndustrySaveRoundTripTest（工业注册表存档链路测试）喵。
 *
 * 覆盖：
 * - 存档 round-trip：WorldSaveService 写入 state.json -> 读取 -> LoadWorldCommand
 *   加载，验证库存 / 加工设施 / 采集设施 / 运输记录 / ID 生成器 / Map 顺序完整恢复。
 * - 旧格式兼容：world 段无 industry 字段（旧存档）时加载后保持空注册表，既有字段不受影响。
 * - 编解码器边界：空注册表编码后仍可解码为空注册表，null/缺失 industry 不抛异常。
 */
class WorldIndustrySaveRoundTripTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 构建一个轻量运行时（不依赖磁盘资源，注入默认配方兜底）喵。
     */
    private static StarAxisGameRuntime newRuntime() {
        SimulationTime time = new SimulationTime();
        time.simulationTick = 100L;
        WorldState world = new WorldState(time, 1000, new AstroData(List.of()));
        RecipeRepository repo = new RecipeRepository(MAPPER);
        repo.setRecipes(RecipeRepository.defaultRecipes());
        return new StarAxisGameRuntime(world, repo);
    }

    /**
     * 向注册表填充典型工业状态：两个库存、采集设施、加工设施、在途运输。
     * 建立可断言的数据面：库存1 存水/能源并预留水，库存2 存氢气/氧气。
     */
    private static IndustryRegistry populate(IndustryRegistry registry) {
        // 库存1（owner=1001）：存入水/能源，并预留部分水
        LocalInventory waterSource = registry.createInventory(1001L);
        waterSource.deposit(SubstanceId.WATER, 50.0, 10);
        waterSource.deposit(SubstanceId.ENERGY, 20.0, 10);
        waterSource.reserve(SubstanceId.WATER, 5.0, 11);

        // 库存2（owner=1002）：存加工产物
        LocalInventory productStore = registry.createInventory(1002L);
        productStore.deposit(SubstanceId.HYDROGEN, 3.0, 12);
        productStore.deposit(SubstanceId.OXYGEN, 1.5, 12);

        // 采集设施（facilityId=1），进入阻塞状态记录失败原因
        ResourceExtractionFacility extractor = registry.createExtractionFacility(
                ResourceExtractionFacility.TYPE_WATER_EXTRACTION, waterSource.inventoryId, 1001L,
                SubstanceId.WATER, 40.0);
        extractor.status = ResourceExtractionFacility.STATUS_BLOCKED;
        extractor.lastFailureReason = "库存容量不足";

        // 加工设施（facilityId=2），带加工进度与运行状态
        ProcessingFacility electrolyzer = registry.createFacility(
                RecipeRepository.FACILITY_TYPE_ELECTROLYZER, waterSource.inventoryId, 1001L,
                RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID);
        electrolyzer.progressDays = 0.5;
        electrolyzer.status = ProcessingFacility.STATUS_PROCESSING;

        // 在途运输（transferId=1）：从库存1 向库存2 运输水（出发即扣除源库存）
        CargoTransfer transfer = registry.startTransfer(waterSource.inventoryId, productStore.inventoryId,
                Map.of(SubstanceId.WATER, 10.0), 100L);
        if (transfer == null) {
            throw new IllegalStateException("transport setup failed");
        }
        return registry;
    }

    // ── round-trip 通过真实存档文件 ──────────────────────────────

    @Test
    void roundTripThroughSaveFileRestoresIndustryRegistry() throws Exception {
        StarAxisGameRuntime source = newRuntime();
        populate(source.getWorldStateForSimOnly().industryRegistry);
        long sourceNextEntityId = source.getWorldStateForSimOnly().getNextEntityId();

        String worldId = "it_world_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Path worldDir = Paths.get("gamedata", "saves", worldId);
        try {
            WorldSaveService.saveManual(source, worldId, "rt");

            // 按 webnet WorldSavesApi 的解析口径从存档文件构造 LoadWorldCommand
            Map<String, Object> state = MAPPER.readValue(
                    worldDir.resolve("state.json").toFile(), new TypeReference<Map<String, Object>>() {
                    });
            Map<String, Object> worldData = castMap(state.get("world"));
            // 写侧校验：industry 段已挂载在 world 下（新版本格式）
            assertTrue(worldData.containsKey("industry"));
            List<Map<String, Object>> nations = castList(state.get("nations"));
            List<Map<String, Object>> entities = castList(state.get("entities"));
            long nextEntityId = state.get("nextEntityId") instanceof Number n ? n.longValue() : 1L;

            // 载入全新运行时
            StarAxisGameRuntime loaded = newRuntime();
            loaded.executeCommandImmediately(new LoadWorldCommand(worldData, nations, entities, nextEntityId));

            IndustryRegistry restored = loaded.getWorldStateForSimOnly().industryRegistry;
            assertRestored(restored);
            assertEquals(sourceNextEntityId, loaded.getWorldStateForSimOnly().getNextEntityId());
        } finally {
            deleteRecursively(worldDir);
        }
    }

    // ── 旧格式兼容（world 段无 industry 字段） ────────────────────

    @Test
    void loadWithoutIndustryFieldKeepsRegistryEmpty() {
        // 模拟旧存档 world 段：仅有时间轴字段，无 industry
        Map<String, Object> worldData = new LinkedHashMap<>();
        worldData.put("simulationTick", 50L);
        worldData.put("totalGameSeconds", 12345.0);
        worldData.put("timeScale", 1.0);
        worldData.put("gameSecondsPerRealSecond", 3600.0);

        StarAxisGameRuntime loaded = newRuntime();
        loaded.executeCommandImmediately(new LoadWorldCommand(worldData, List.of(), List.of(), 100L));

        WorldState ws = loaded.getWorldStateForSimOnly();
        // 旧存档无 industry：加载后保持空注册表
        assertTrue(ws.industryRegistry.isEmpty());
        assertEquals(1L, ws.industryRegistry.getNextInventoryId());
        assertEquals(1L, ws.industryRegistry.getNextFacilityId());
        assertEquals(1L, ws.industryRegistry.getNextTransferId());
        // 既有时间轴字段不受影响
        assertEquals(50L, ws.time.simulationTick);
    }

    // ── 编解码器边界 ──────────────────────────────────────────────

    @Test
    void emptyRegistryEncodesToFullStructureAndDecodesBackEmpty() {
        IndustryRegistry registry = new IndustryRegistry();
        Map<String, Object> encoded = IndustryStateCodec.encode(registry);

        assertEquals(1L, encoded.get("nextInventoryId"));
        assertTrue(((List<?>) encoded.get("inventories")).isEmpty());
        assertTrue(((List<?>) encoded.get("processingFacilities")).isEmpty());
        assertTrue(((List<?>) encoded.get("extractionFacilities")).isEmpty());
        assertTrue(((List<?>) encoded.get("transfers")).isEmpty());

        IndustryRegistry restored = new IndustryRegistry();
        IndustryStateCodec.apply(encoded, restored);
        assertTrue(restored.isEmpty());
        assertEquals(1L, restored.getNextInventoryId());
        assertEquals(1L, restored.getNextFacilityId());
        assertEquals(1L, restored.getNextTransferId());
    }

    @Test
    void applyNullOrEmptyIndustryIsNoOp() {
        IndustryRegistry registry = new IndustryRegistry();
        IndustryStateCodec.apply(null, registry);
        assertTrue(registry.isEmpty());

        IndustryStateCodec.apply(new LinkedHashMap<>(), registry);
        assertTrue(registry.isEmpty());
    }

    // ── 断言辅助 ─────────────────────────────────────────────────

    private static void assertRestored(IndustryRegistry restored) {
        // 库存数量与 Map 顺序（创建顺序 = ID 分配顺序）
        assertEquals(2, restored.inventoriesById.size());
        assertEquals(List.of(1L, 2L), new ArrayList<>(restored.inventoriesById.keySet()));

        LocalInventory inv1 = restored.inventoriesById.get(1L);
        assertNotNull(inv1);
        assertEquals(1001L, inv1.ownerEntityId);
        assertEquals(LocalInventory.DEFAULT_CAPACITY, inv1.capacity);
        // 运输已扣除 10 水：50 - 10
        assertEquals(40.0, inv1.getAmount(SubstanceId.WATER));
        assertEquals(20.0, inv1.getAmount(SubstanceId.ENERGY));
        assertEquals(5.0, inv1.getReservedAmount(SubstanceId.WATER));

        LocalInventory inv2 = restored.inventoriesById.get(2L);
        assertNotNull(inv2);
        assertEquals(1002L, inv2.ownerEntityId);
        assertEquals(3.0, inv2.getAmount(SubstanceId.HYDROGEN));
        assertEquals(1.5, inv2.getAmount(SubstanceId.OXYGEN));

        // 采集设施
        assertEquals(1, restored.extractionFacilitiesById.size());
        ResourceExtractionFacility extractor = restored.extractionFacilitiesById.get(1L);
        assertNotNull(extractor);
        assertEquals(ResourceExtractionFacility.TYPE_WATER_EXTRACTION, extractor.facilityType);
        assertEquals(1L, extractor.inventoryId);
        assertEquals(1001L, extractor.locationEntityId);
        assertEquals(SubstanceId.WATER, extractor.resourceId);
        assertEquals(40.0, extractor.amountPerDay);
        assertEquals(ResourceExtractionFacility.STATUS_BLOCKED, extractor.status);
        assertEquals("库存容量不足", extractor.lastFailureReason);

        // 加工设施
        assertEquals(1, restored.facilitiesById.size());
        ProcessingFacility electrolyzer = restored.facilitiesById.get(2L);
        assertNotNull(electrolyzer);
        assertEquals(RecipeRepository.FACILITY_TYPE_ELECTROLYZER, electrolyzer.facilityType);
        assertEquals(1L, electrolyzer.inventoryId);
        assertEquals(1001L, electrolyzer.locationEntityId);
        assertEquals(RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID, electrolyzer.activeRecipeId);
        assertEquals(0.5, electrolyzer.progressDays);
        assertEquals(RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID, electrolyzer.progressRecipeId);
        assertEquals(ProcessingFacility.STATUS_PROCESSING, electrolyzer.status);

        // 运输记录
        assertEquals(1, restored.transfersById.size());
        CargoTransfer transfer = restored.transfersById.get(1L);
        assertNotNull(transfer);
        assertEquals(1L, transfer.sourceInventoryId);
        assertEquals(2L, transfer.targetInventoryId);
        assertEquals(10.0, transfer.goods.get(SubstanceId.WATER));
        assertEquals(CargoTransfer.STATUS_IN_TRANSIT, transfer.status);
        assertEquals(100L, transfer.departedAtTick);
        assertEquals(0L, transfer.arrivedAtTick);

        // ID 生成器
        assertEquals(3L, restored.getNextInventoryId());
        assertEquals(3L, restored.getNextFacilityId());
        assertEquals(2L, restored.getNextTransferId());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return (Map<String, Object>) o;
    }

    private static List<Map<String, Object>> castList(Object o) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> l) {
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
        }
        return out;
    }

    /**
     * 递归删除测试临时存档目录喵。
     */
    private static void deleteRecursively(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }
}
