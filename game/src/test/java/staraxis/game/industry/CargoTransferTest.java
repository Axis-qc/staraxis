package staraxis.game.industry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * CargoTransferTest（货物运输单元测试）
 *
 * 覆盖 G2.6：出发扣源库存、运输中不可重复使用、抵达写目标库存、不可重复结算、
 * 源库存不足与目标库存容量不足等分支。
 */
class CargoTransferTest {

    private IndustryRegistry registry;
    private LocalInventory source;
    private LocalInventory target;

    @BeforeEach
    void setUp() {
        registry = new IndustryRegistry();
        source = registry.createInventory(1L);
        target = registry.createInventory(2L);
    }

    @Test
    void departDeductsSourceAndCreatesInTransitRecord() {
        source.deposit(SubstanceId.IRON, 10.0, 1);

        CargoTransfer transfer = registry.startTransfer(source.inventoryId, target.inventoryId,
                Map.of(SubstanceId.IRON, 4.0), 5);

        assertNotNull(transfer);
        assertEquals(CargoTransfer.STATUS_IN_TRANSIT, transfer.status);
        // 出发后源库存扣除
        assertEquals(6.0, source.getAmount(SubstanceId.IRON));
        // 目标库存尚未写入
        assertEquals(0.0, target.getAmount(SubstanceId.IRON));
        // 在途货物既不在源库存也不在目标库存，仅存在于运输记录中（不可重复使用）
        assertEquals(4.0, registry.sumInTransitGoods().get(SubstanceId.IRON));
        assertEquals(0.0, source.getAmount(SubstanceId.IRON) - 6.0);
        assertEquals(0.0, target.getAmount(SubstanceId.IRON));
    }

    @Test
    void departFailsWhenSourceInsufficient() {
        source.deposit(SubstanceId.IRON, 3.0, 1);

        CargoTransfer transfer = registry.startTransfer(source.inventoryId, target.inventoryId,
                Map.of(SubstanceId.IRON, 4.0), 5);

        assertNull(transfer);
        // 失败时源库存不变，无运输记录
        assertEquals(3.0, source.getAmount(SubstanceId.IRON));
        assertTrue(registry.transfersById.isEmpty());
    }

    @Test
    void departRejectsInvalidGoods() {
        source.deposit(SubstanceId.IRON, 10.0, 1);

        // 空货物
        assertNull(registry.startTransfer(source.inventoryId, target.inventoryId, Map.of(), 5));
        // 非法数量
        assertNull(registry.startTransfer(source.inventoryId, target.inventoryId,
                Map.of(SubstanceId.IRON, -1.0), 5));
        // 源库存不存在
        assertNull(registry.startTransfer(999L, target.inventoryId, Map.of(SubstanceId.IRON, 1.0), 5));
    }

    @Test
    void departFailsWhenTargetInventoryMissing() {
        source.deposit(SubstanceId.IRON, 10.0, 1);

        // 目标库存不存在：不得扣减源库存、不得创建在途记录（否则货物将永久在途无法抵达）
        CargoTransfer transfer = registry.startTransfer(source.inventoryId, 9999L,
                Map.of(SubstanceId.IRON, 4.0), 5);

        assertNull(transfer);
        assertEquals(10.0, source.getAmount(SubstanceId.IRON));
        assertTrue(registry.transfersById.isEmpty());
    }

    @Test
    void departFailsWhenTargetIsSourceInventory() {
        source.deposit(SubstanceId.IRON, 10.0, 1);

        // 目标库存等于源库存：自运输无意义，不得扣减或创建记录
        CargoTransfer transfer = registry.startTransfer(source.inventoryId, source.inventoryId,
                Map.of(SubstanceId.IRON, 4.0), 5);

        assertNull(transfer);
        assertEquals(10.0, source.getAmount(SubstanceId.IRON));
        assertTrue(registry.transfersById.isEmpty());
    }

    @Test
    void arrivalWritesTargetAndMarksArrived() {
        source.deposit(SubstanceId.IRON, 10.0, 1);
        CargoTransfer transfer = registry.startTransfer(source.inventoryId, target.inventoryId,
                Map.of(SubstanceId.IRON, 4.0), 5);

        CargoTransfer completed = registry.completeTransfer(transfer.transferId, 10);

        assertNotNull(completed);
        assertEquals(CargoTransfer.STATUS_ARRIVED, completed.status);
        assertEquals(10L, completed.arrivedAtTick);
        assertEquals(4.0, target.getAmount(SubstanceId.IRON));
        assertEquals(6.0, source.getAmount(SubstanceId.IRON));
    }

    @Test
    void transferCannotBeSettledTwice() {
        source.deposit(SubstanceId.IRON, 10.0, 1);
        CargoTransfer transfer = registry.startTransfer(source.inventoryId, target.inventoryId,
                Map.of(SubstanceId.IRON, 4.0), 5);

        assertNotNull(registry.completeTransfer(transfer.transferId, 10));

        // 已抵达的运输记录不可重复结算
        assertNull(registry.completeTransfer(transfer.transferId, 11));
        // 目标库存不会被重复写入
        assertEquals(4.0, target.getAmount(SubstanceId.IRON));
    }

    @Test
    void arrivalFailsWhenTargetFullAndStaysInTransit() {
        source.deposit(SubstanceId.IRON, 10.0, 1);
        // 目标库存容量仅 2 单位，无法容纳 4 单位货物
        LocalInventory tinyTarget = new LocalInventory(3L, 3L, 2.0);
        registry.inventoriesById.put(tinyTarget.inventoryId, tinyTarget);

        CargoTransfer transfer = registry.startTransfer(source.inventoryId, tinyTarget.inventoryId,
                Map.of(SubstanceId.IRON, 4.0), 5);

        CargoTransfer completed = registry.completeTransfer(transfer.transferId, 10);

        assertNull(completed);
        // 保持运输中，货物不丢失，可于后续容量释放后重试
        assertEquals(CargoTransfer.STATUS_IN_TRANSIT, transfer.status);
        assertEquals(0.0, tinyTarget.getAmount(SubstanceId.IRON));
        assertEquals(4.0, registry.sumInTransitGoods().get(SubstanceId.IRON));
    }

    @Test
    void registryIdAssignmentIsDeterministic() {
        // 使用独立注册表，避免受 setUp 已创建的库存影响
        IndustryRegistry freshRegistry = new IndustryRegistry();
        LocalInventory a = freshRegistry.createInventory(1L);
        LocalInventory b = freshRegistry.createInventory(2L);
        ProcessingFacility facility = freshRegistry.createFacility(RecipeRepository.FACILITY_TYPE_ELECTROLYZER,
                a.inventoryId, 1L, RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID);

        assertEquals(1L, a.inventoryId);
        assertEquals(2L, b.inventoryId);
        assertEquals(1L, facility.facilityId);
        assertEquals(3L, freshRegistry.getNextInventoryId());
        assertEquals(2L, freshRegistry.getNextFacilityId());
    }
}
