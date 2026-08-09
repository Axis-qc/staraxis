package staraxis.game.industry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * LocalInventoryTest（本地库存单元测试）
 *
 * 覆盖 G2.1 库存语义：存取、容量上限、预留与变更日志。
 */
class LocalInventoryTest {

    @Test
    void depositAddsSubstanceAndRecordsChange() {
        LocalInventory inv = new LocalInventory(1L, 100L, 1000.0);

        assertTrue(inv.deposit(SubstanceId.WATER, 10.0, 1));

        assertEquals(10.0, inv.getAmount(SubstanceId.WATER));
        assertEquals(10.0, inv.getUsedCapacity());
        assertEquals(990.0, inv.getFreeCapacity());
        assertEquals(1, inv.changes.size());
        assertEquals(InventoryChange.TYPE_DEPOSIT, inv.changes.get(0).type);
        assertEquals(SubstanceId.WATER, inv.changes.get(0).substanceId);
    }

    @Test
    void depositFailsWhenCapacityExceeded() {
        LocalInventory inv = new LocalInventory(1L, 100L, 100.0);
        inv.deposit(SubstanceId.WATER, 90.0, 1);

        // 超容量存入失败，库存不变
        assertFalse(inv.deposit(SubstanceId.WATER, 20.0, 2));
        assertEquals(90.0, inv.getAmount(SubstanceId.WATER));
        assertEquals(1, inv.changes.size());

        // 恰好填满容量成功
        assertTrue(inv.deposit(SubstanceId.WATER, 10.0, 3));
        assertEquals(100.0, inv.getAmount(SubstanceId.WATER));
    }

    @Test
    void withdrawDeductsAndRecordsChange() {
        LocalInventory inv = new LocalInventory(1L, 100L, 1000.0);
        inv.deposit(SubstanceId.IRON, 50.0, 1);

        assertTrue(inv.withdraw(SubstanceId.IRON, 20.0, 2));
        assertEquals(30.0, inv.getAmount(SubstanceId.IRON));
        assertEquals(InventoryChange.TYPE_WITHDRAW, inv.changes.get(1).type);
    }

    @Test
    void withdrawFailsWhenInsufficient() {
        LocalInventory inv = new LocalInventory(1L, 100L, 1000.0);
        inv.deposit(SubstanceId.IRON, 10.0, 1);

        assertFalse(inv.withdraw(SubstanceId.IRON, 11.0, 2));
        assertEquals(10.0, inv.getAmount(SubstanceId.IRON));
        assertEquals(1, inv.changes.size());
    }

    @Test
    void reservationReducesAvailableAmount() {
        LocalInventory inv = new LocalInventory(1L, 100L, 1000.0);
        inv.deposit(SubstanceId.HYDROGEN, 100.0, 1);

        assertTrue(inv.reserve(SubstanceId.HYDROGEN, 30.0, 2));
        assertEquals(100.0, inv.getAmount(SubstanceId.HYDROGEN));
        assertEquals(30.0, inv.getReservedAmount(SubstanceId.HYDROGEN));
        assertEquals(70.0, inv.getAvailableAmount(SubstanceId.HYDROGEN));

        // 预留后可支配量不足，无法再取出
        assertFalse(inv.withdraw(SubstanceId.HYDROGEN, 80.0, 3));
        // 可支配量范围内可取出
        assertTrue(inv.withdraw(SubstanceId.HYDROGEN, 70.0, 4));
        assertEquals(30.0, inv.getAmount(SubstanceId.HYDROGEN));
    }

    @Test
    void releaseReservationRestoresAvailableAmount() {
        LocalInventory inv = new LocalInventory(1L, 100L, 1000.0);
        inv.deposit(SubstanceId.HYDROGEN, 100.0, 1);
        inv.reserve(SubstanceId.HYDROGEN, 30.0, 2);

        assertTrue(inv.releaseReservation(SubstanceId.HYDROGEN, 30.0, 3));
        assertEquals(0.0, inv.getReservedAmount(SubstanceId.HYDROGEN));
        assertEquals(100.0, inv.getAvailableAmount(SubstanceId.HYDROGEN));
    }

    @Test
    void constructorKeepsDefaultCapacityWhenInvalid() {
        LocalInventory inv = new LocalInventory(1L, 100L, -5.0);
        assertEquals(LocalInventory.DEFAULT_CAPACITY, inv.capacity);
    }

    @Test
    void zeroAmountSubstanceDefaultsToZero() {
        LocalInventory inv = new LocalInventory(1L, 100L, 1000.0);
        assertNotNull(inv);
        assertEquals(0.0, inv.getAmount(SubstanceId.COPPER));
        assertEquals(0.0, inv.getUsedCapacity());
        assertTrue(inv.deposit(SubstanceId.COPPER, 0.0, 1));
        assertEquals(0.0, inv.getAmount(SubstanceId.COPPER));
    }

    @Test
    void changesLogIsBounded() {
        LocalInventory inv = new LocalInventory(1L, 100L, LocalInventory.DEFAULT_CAPACITY);
        int operations = LocalInventory.MAX_CHANGES + 50;
        for (int i = 0; i < operations; i++) {
            inv.deposit(SubstanceId.WATER, 1.0, i + 1L);
        }

        // 变更日志有界保留：最多 MAX_CHANGES 条，超限移除最旧记录
        assertEquals(LocalInventory.MAX_CHANGES, inv.changes.size());
        // 最新记录保留（tick = operations），最旧记录已被移除
        assertEquals((long) operations, inv.changes.get(inv.changes.size() - 1).tick);
        assertEquals((long) operations - LocalInventory.MAX_CHANGES + 1L, inv.changes.get(0).tick);
    }
}
