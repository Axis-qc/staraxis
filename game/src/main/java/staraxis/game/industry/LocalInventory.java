package staraxis.game.industry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LocalInventory（本地库存）
 *
 * 每个行星、空间站、深空资源站拥有一个独立本地库存（G2.1）。
 * 地表与轨道上的采集、加工、消费设施共享同一个库存；设施只读写所属库存，不直接互连。
 *
 * 库存语义：
 * - substances（物质数量）：当前实际持有量。
 * - reservedAmounts（预留数量）：已被承诺但尚未实际消耗/转移的量（如生产排程、装船承诺）。
 * - changes（库存变化）：存取/预留操作日志，供可见性与测试断言。
 *
 * 容量口径：capacity 为总单位上限，等于所有物质数量之和的上限。
 */
public class LocalInventory {

    /** 默认库存容量（单位），未显式指定时兜底。 */
    public static final double DEFAULT_CAPACITY = 100_000.0;

    /** 工业系统通用浮点比较容差（避免累积误差导致误判）。 */
    public static final double EPSILON = 1e-9;

    /** 库存变更日志最大保留条数（防止长期运行无界增长）。 */
    public static final int MAX_CHANGES = 1000;

    /** 库存 ID（全局唯一，由 IndustryRegistry 分配）。 */
    public long inventoryId;

    /** 所属实体 ID（行星/空间站/深空资源站的实体 ID，ownerEntityId）。 */
    public long ownerEntityId;

    /** 库存容量上限（单位）。 */
    public double capacity;

    /** 物质数量表（substanceId -> 数量）。 */
    public final Map<String, Double> substances = new LinkedHashMap<>();

    /** 预留数量表（substanceId -> 已预留数量）。 */
    public final Map<String, Double> reservedAmounts = new LinkedHashMap<>();

    /** 库存变更日志（InventoryChange 列表，按时间顺序追加）。 */
    public final List<InventoryChange> changes = new ArrayList<>();

    /**
     * 默认构造（inventoryId=0，ownerEntityId=0，默认容量）。
     */
    public LocalInventory() {
        this(0L, 0L, DEFAULT_CAPACITY);
    }

    /**
     * 构造本地库存。
     *
     * @param inventoryId   库存 ID
     * @param ownerEntityId 所属实体 ID
     * @param capacity      容量上限（单位）
     */
    public LocalInventory(long inventoryId, long ownerEntityId, double capacity) {
        this.inventoryId = inventoryId;
        this.ownerEntityId = ownerEntityId;
        this.capacity = capacity > 0 ? capacity : DEFAULT_CAPACITY;
    }

    /**
     * 获取物质的当前持有量。
     *
     * @param substanceId 物质 ID
     * @return 持有量（无记录为 0）
     */
    public double getAmount(String substanceId) {
        return substances.getOrDefault(substanceId, 0.0);
    }

    /**
     * 获取物质的已预留数量。
     *
     * @param substanceId 物质 ID
     * @return 预留数量（无记录为 0）
     */
    public double getReservedAmount(String substanceId) {
        return reservedAmounts.getOrDefault(substanceId, 0.0);
    }

    /**
     * 获取物质的可支配数量（持有量 - 预留量）。
     *
     * @param substanceId 物质 ID
     * @return 可支配数量
     */
    public double getAvailableAmount(String substanceId) {
        return getAmount(substanceId) - getReservedAmount(substanceId);
    }

    /**
     * 获取库存已占用容量（所有物质持有量之和）。
     *
     * @return 已占用容量
     */
    public double getUsedCapacity() {
        double used = 0.0;
        for (double amount : substances.values()) {
            used += amount;
        }
        return used;
    }

    /**
     * 获取剩余可用容量。
     *
     * @return capacity - 已占用容量
     */
    public double getFreeCapacity() {
        return capacity - getUsedCapacity();
    }

    /**
     * 判断在现有占用量上再存入 amount 是否不超过容量。
     *
     * @param substanceId 物质 ID
     * @param amount      存入数量
     * @return 容量允许时返回 true
     */
    public boolean hasCapacityFor(String substanceId, double amount) {
        if (amount < 0.0) {
            return false;
        }
        return getUsedCapacity() + amount <= capacity + EPSILON;
    }

    /**
     * 判断能否存入指定数量的物质（容量 + 非负校验）。
     *
     * @param substanceId 物质 ID
     * @param amount      存入数量
     * @return 可存入返回 true
     */
    public boolean canDeposit(String substanceId, double amount) {
        return amount >= 0.0 && hasCapacityFor(substanceId, amount);
    }

    /**
     * 存入物质（成功时记录 DEPOSIT 变更）。
     *
     * @param substanceId 物质 ID
     * @param amount      存入数量（必须非负）
     * @param tick        当前模拟 tick
     * @return 容量不足返回 false，否则返回 true
     */
    public boolean deposit(String substanceId, double amount, long tick) {
        if (!canDeposit(substanceId, amount)) {
            return false;
        }
        double balance = getAmount(substanceId) + amount;
        substances.put(substanceId, balance);
        recordChange(InventoryChange.TYPE_DEPOSIT, substanceId, amount, tick, balance);
        return true;
    }

    /**
     * 判断能否取出指定数量的物质（可支配量充足 + 非负）。
     *
     * @param substanceId 物质 ID
     * @param amount      取出数量
     * @return 可取出发 true
     */
    public boolean canWithdraw(String substanceId, double amount) {
        return amount >= 0.0 && getAvailableAmount(substanceId) >= amount - EPSILON;
    }

    /**
     * 取出物质（成功时记录 WITHDRAW 变更）。
     *
     * @param substanceId 物质 ID
     * @param amount      取出数量（必须非负）
     * @param tick        当前模拟 tick
     * @return 可支配量不足返回 false，否则返回 true
     */
    public boolean withdraw(String substanceId, double amount, long tick) {
        if (!canWithdraw(substanceId, amount)) {
            return false;
        }
        double balance = getAmount(substanceId) - amount;
        substances.put(substanceId, balance);
        recordChange(InventoryChange.TYPE_WITHDRAW, substanceId, amount, tick, balance);
        return true;
    }

    /**
     * 预留物质（承诺占用，成功时记录 RESERVE 变更）。
     *
     * @param substanceId 物质 ID
     * @param amount      预留数量（必须非负）
     * @param tick        当前模拟 tick
     * @return 可支配量不足返回 false，否则返回 true
     */
    public boolean reserve(String substanceId, double amount, long tick) {
        if (amount < 0.0 || getAvailableAmount(substanceId) < amount - EPSILON) {
            return false;
        }
        double reserved = getReservedAmount(substanceId) + amount;
        reservedAmounts.put(substanceId, reserved);
        recordChange(InventoryChange.TYPE_RESERVE, substanceId, amount, tick, reserved);
        return true;
    }

    /**
     * 释放预留物质（成功时记录 RELEASE_RESERVE 变更）。
     *
     * @param substanceId 物质 ID
     * @param amount      释放数量（必须非负，且不超过当前预留量）
     * @param tick        当前模拟 tick
     * @return 预留量不足返回 false，否则返回 true
     */
    public boolean releaseReservation(String substanceId, double amount, long tick) {
        if (amount < 0.0 || getReservedAmount(substanceId) < amount - EPSILON) {
            return false;
        }
        double reserved = getReservedAmount(substanceId) - amount;
        if (reserved <= EPSILON) {
            reservedAmounts.remove(substanceId);
            reserved = 0.0;
        } else {
            reservedAmounts.put(substanceId, reserved);
        }
        recordChange(InventoryChange.TYPE_RELEASE_RESERVE, substanceId, amount, tick, reserved);
        return true;
    }

    /**
     * 记录一条库存变更日志，超过 {@link #MAX_CHANGES} 时移除最旧记录（有界保留）。
     *
     * @param type          变更方向标识
     * @param substanceId   物质 ID
     * @param amount        变更数量
     * @param tick          变更时的模拟 tick
     * @param resultBalance 变更后库存余额
     */
    private void recordChange(String type, String substanceId, double amount, long tick, double resultBalance) {
        changes.add(new InventoryChange(type, substanceId, amount, tick, resultBalance));
        if (changes.size() > MAX_CHANGES) {
            changes.remove(0);
        }
    }
}
