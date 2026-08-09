package staraxis.game.industry;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * IndustryRegistry（工业系统注册表）
 *
 * 权威维护本地库存、加工设施与货物运输记录的集合与 ID 分配（G2 元素化生产）。
 * 挂载于 WorldState，仅允许模拟层读写。
 *
 * 职责：
 * - 分配 inventoryId / facilityId / transferId（全局唯一，确定性递增）。
 * - 注册与查询 LocalInventory / ProcessingFacility / CargoTransfer。
 * - 提供 CargoTransfer 出发与抵达的原子性操作（校验通过后整体生效）。
 */
public class IndustryRegistry {

    /** 库存 ID 生成器起始值（避免与实体 ID 命名空间混淆）。 */
    private static final long ID_START = 1L;

    private long nextInventoryId = ID_START;
    private long nextFacilityId = ID_START;
    private long nextTransferId = ID_START;

    /**
     * 本地库存表（inventoryId -> LocalInventory）。
     * 使用 LinkedHashMap 保证迭代顺序 = 创建（ID 分配）顺序，结算报告输出顺序确定。
     */
    public final Map<Long, LocalInventory> inventoriesById = new LinkedHashMap<>();

    /**
     * 加工设施表（facilityId -> ProcessingFacility）。
     * 使用 LinkedHashMap 保证迭代顺序 = 创建（ID 分配）顺序，结算报告输出顺序确定。
     */
    public final Map<Long, ProcessingFacility> facilitiesById = new LinkedHashMap<>();

    /**
     * 采集设施表（facilityId -> ResourceExtractionFacility）。
     * 与加工设施共享同一设施 ID 命名空间（nextFacilityId），保证 ID 全局唯一；
     * 使用 LinkedHashMap 保证迭代顺序 = 创建（ID 分配）顺序，结算报告输出顺序确定。
     */
    public final Map<Long, ResourceExtractionFacility> extractionFacilitiesById = new LinkedHashMap<>();

    /**
     * 运输记录表（transferId -> CargoTransfer）。
     * 使用 LinkedHashMap 保证迭代顺序 = 创建（ID 分配）顺序，结算报告输出顺序确定。
     */
    public final Map<Long, CargoTransfer> transfersById = new LinkedHashMap<>();

    /**
     * 创建并注册一个本地库存（默认容量）。
     *
     * @param ownerEntityId 所属实体 ID
     * @return 已注册的本地库存
     */
    public LocalInventory createInventory(long ownerEntityId) {
        return createInventory(ownerEntityId, LocalInventory.DEFAULT_CAPACITY);
    }

    /**
     * 创建并注册一个本地库存（指定容量）。
     *
     * @param ownerEntityId 所属实体 ID
     * @param capacity      容量上限
     * @return 已注册的本地库存
     */
    public LocalInventory createInventory(long ownerEntityId, double capacity) {
        LocalInventory inventory = new LocalInventory(nextInventoryId++, ownerEntityId, capacity);
        inventoriesById.put(inventory.inventoryId, inventory);
        return inventory;
    }

    /**
     * 创建并注册一个加工设施。
     *
     * @param facilityType     设施类型
     * @param inventoryId      所属本地库存 ID
     * @param locationEntityId 所在实体 ID
     * @param activeRecipeId   激活配方 ID
     * @return 已注册的加工设施
     */
    public ProcessingFacility createFacility(String facilityType, long inventoryId,
            long locationEntityId, String activeRecipeId) {
        ProcessingFacility facility = new ProcessingFacility(nextFacilityId++, facilityType,
                inventoryId, locationEntityId, activeRecipeId);
        facilitiesById.put(facility.facilityId, facility);
        return facility;
    }

    /**
     * 发起货物运输（出发阶段）。
     *
     * 原子语义：
     * - 校验源库存存在、目标库存存在且与源库存不同、货物合法、源库存对每种货物均可取出。
     * - 全部校验通过后，从源库存逐种扣除货物，并创建 IN_TRANSIT 运输记录。
     * - 任一校验失败则整体不生效，返回 null。
     *
     * @param sourceInventoryId 源库存 ID
     * @param targetInventoryId 目标库存 ID
     * @param goods             货物（substanceId -> 数量）
     * @param tick              当前模拟 tick
     * @return 创建成功的运输记录；参数/库存/货物不足时返回 null
     */
    public CargoTransfer startTransfer(long sourceInventoryId, long targetInventoryId,
            Map<String, Double> goods, long tick) {
        if (goods == null || goods.isEmpty()) {
            return null;
        }
        LocalInventory source = inventoriesById.get(sourceInventoryId);
        if (source == null) {
            return null;
        }
        // 目标库存必须存在且不等于源库存，否则货物将永久在途无法抵达
        LocalInventory target = inventoriesById.get(targetInventoryId);
        if (target == null || target == source) {
            return null;
        }

        // 预校验：所有货物数量为正且源库存可取出
        for (Map.Entry<String, Double> entry : goods.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()
                    || entry.getValue() == null || entry.getValue() <= 0.0) {
                return null;
            }
            if (!source.canWithdraw(entry.getKey(), entry.getValue())) {
                return null;
            }
        }

        // 原子扣减源库存
        for (Map.Entry<String, Double> entry : goods.entrySet()) {
            source.withdraw(entry.getKey(), entry.getValue(), tick);
        }

        CargoTransfer transfer = new CargoTransfer(nextTransferId++, sourceInventoryId,
                targetInventoryId, goods, tick);
        transfersById.put(transfer.transferId, transfer);
        return transfer;
    }

    /**
     * 完成货物运输（抵达阶段）。
     *
     * 原子语义：
     * - 校验记录存在且状态为 IN_TRANSIT（不可重复结算）。
     * - 校验目标库存存在且容量可容纳全部货物。
     * - 全部校验通过后，将货物逐种写入目标库存，状态置为 ARRIVED。
     * - 任一校验失败则保持 IN_TRANSIT，返回 null。
     *
     * @param transferId 运输记录 ID
     * @param tick       当前模拟 tick
     * @return 结算成功的运输记录（状态已置 ARRIVED）；状态不合法或目标库存无法容纳时返回 null
     */
    public CargoTransfer completeTransfer(long transferId, long tick) {
        CargoTransfer transfer = transfersById.get(transferId);
        if (transfer == null || !CargoTransfer.STATUS_IN_TRANSIT.equals(transfer.status)) {
            return null;
        }
        LocalInventory target = inventoriesById.get(transfer.targetInventoryId);
        if (target == null) {
            return null;
        }

        // 预校验：目标库存可容纳全部货物
        for (Map.Entry<String, Double> entry : transfer.goods.entrySet()) {
            if (!target.canDeposit(entry.getKey(), entry.getValue())) {
                return null;
            }
        }

        // 原子写入目标库存
        for (Map.Entry<String, Double> entry : transfer.goods.entrySet()) {
            target.deposit(entry.getKey(), entry.getValue(), tick);
        }
        transfer.status = CargoTransfer.STATUS_ARRIVED;
        transfer.arrivedAtTick = tick;
        return transfer;
    }

    /**
     * 按 ID 查询本地库存。
     *
     * @param inventoryId 库存 ID
     * @return 本地库存，不存在返回 null
     */
    public LocalInventory getInventory(long inventoryId) {
        return inventoriesById.get(inventoryId);
    }

    /**
     * 按 ID 查询加工设施。
     *
     * @param facilityId 设施 ID
     * @return 加工设施，不存在返回 null
     */
    public ProcessingFacility getFacility(long facilityId) {
        return facilitiesById.get(facilityId);
    }

    /**
     * 创建并注册一个采集设施（与加工设施共享设施 ID 命名空间）。
     *
     * @param facilityType     设施类型（如 WATER_EXTRACTOR 水采集设施）
     * @param inventoryId      所属本地库存 ID
     * @param locationEntityId 所在实体 ID
     * @param resourceId       采集的资源物质 ID
     * @param amountPerDay     每日固定产出量
     * @return 已注册的采集设施
     */
    public ResourceExtractionFacility createExtractionFacility(String facilityType, long inventoryId,
            long locationEntityId, String resourceId, double amountPerDay) {
        ResourceExtractionFacility facility = new ResourceExtractionFacility(nextFacilityId++, facilityType,
                inventoryId, locationEntityId, resourceId, amountPerDay);
        extractionFacilitiesById.put(facility.facilityId, facility);
        return facility;
    }

    /**
     * 按 ID 查询采集设施。
     *
     * @param facilityId 设施 ID
     * @return 采集设施，不存在返回 null
     */
    public ResourceExtractionFacility getExtractionFacility(long facilityId) {
        return extractionFacilitiesById.get(facilityId);
    }

    /**
     * 按所属实体 ID 查询本地库存（每个行星/空间站/深空资源站拥有独立库存）。
     *
     * 用于殖民等场景的幂等检查：所属实体已存在库存时不再重复创建。
     * 正常业务下每个所属实体至多一个库存，返回首个匹配；不匹配时返回 null。
     *
     * @param ownerEntityId 所属实体 ID
     * @return 匹配的本地库存；不存在返回 null
     */
    public LocalInventory getInventoryByOwner(long ownerEntityId) {
        for (LocalInventory inventory : inventoriesById.values()) {
            if (inventory.ownerEntityId == ownerEntityId) {
                return inventory;
            }
        }
        return null;
    }

    /**
     * 按 ID 查询运输记录。
     *
     * @param transferId 运输记录 ID
     * @return 运输记录，不存在返回 null
     */
    public CargoTransfer getTransfer(long transferId) {
        return transfersById.get(transferId);
    }

    /**
     * 获取所有运输中的记录（IN_TRANSIT）。
     *
     * @return 运输中的记录列表
     */
    public Collection<CargoTransfer> getInTransitTransfers() {
        return transfersById.values().stream()
                .filter(t -> CargoTransfer.STATUS_IN_TRANSIT.equals(t.status))
                .collect(Collectors.toList());
    }

    /**
     * 获取下一个库存 ID（不递增，仅供存档序列化）。
     *
     * @return 下一个库存 ID
     */
    public synchronized long getNextInventoryId() {
        return nextInventoryId;
    }

    /**
     * 设置下一个库存 ID（仅存档反序列化时调用）。
     *
     * @param value 要设置的值（小于当前值时忽略）
     */
    public synchronized void setNextInventoryId(long value) {
        if (value > nextInventoryId) {
            nextInventoryId = value;
        }
    }

    /**
     * 获取下一个设施 ID（不递增，仅供存档序列化）。
     *
     * @return 下一个设施 ID
     */
    public synchronized long getNextFacilityId() {
        return nextFacilityId;
    }

    /**
     * 设置下一个设施 ID（仅存档反序列化时调用）。
     *
     * @param value 要设置的值（小于当前值时忽略）
     */
    public synchronized void setNextFacilityId(long value) {
        if (value > nextFacilityId) {
            nextFacilityId = value;
        }
    }

    /**
     * 获取下一个运输记录 ID（不递增，仅供存档序列化）。
     *
     * @return 下一个运输记录 ID
     */
    public synchronized long getNextTransferId() {
        return nextTransferId;
    }

    /**
     * 设置下一个运输记录 ID（仅存档反序列化时调用）。
     *
     * @param value 要设置的值（小于当前值时忽略）
     */
    public synchronized void setNextTransferId(long value) {
        if (value > nextTransferId) {
            nextTransferId = value;
        }
    }

    /**
     * 判断注册表是否为空（无库存/设施/运输）。
     *
     * @return 全空时返回 true
     */
    public boolean isEmpty() {
        return inventoriesById.isEmpty() && facilitiesById.isEmpty()
                && extractionFacilitiesById.isEmpty() && transfersById.isEmpty();
    }

    /**
     * 统计在途货物总量（按物质汇总所有运输中记录）。
     *
     * @return substanceId -> 运输中总量
     */
    public Map<String, Double> sumInTransitGoods() {
        Map<String, Double> sum = new LinkedHashMap<>();
        for (CargoTransfer transfer : getInTransitTransfers()) {
            for (Map.Entry<String, Double> entry : transfer.goods.entrySet()) {
                sum.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }
        return sum;
    }
}
