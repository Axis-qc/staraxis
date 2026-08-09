package staraxis.game.save;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import staraxis.game.industry.CargoTransfer;
import staraxis.game.industry.IndustryRegistry;
import staraxis.game.industry.LocalInventory;
import staraxis.game.industry.ProcessingFacility;
import staraxis.game.industry.ResourceExtractionFacility;

/**
 * IndustryStateCodec（工业注册表存档编解码器）喵。
 *
 * 将 WorldState.industryRegistry 序列化为 JSON 友好的 Map 结构，并可从该结构
 * 反向恢复注册表内容（本地库存 / 加工设施 / 采集设施 / 运输记录 / ID 生成器）。
 *
 * 序列化位置（存档 schema）：
 * - WorldSaveService 将本编解码器的输出挂载在 state.world.industry 字段下。
 * - 挂载在 world 下的原因：现有加载链路仅通过 LoadWorldCommand 携带
 *   world / nations / entities / nextEntityId 四段数据，且 webnet 侧不得改动，
 *   world.industry 是唯一无需改动 webnet 即可让数据流入 game 加载链路的载体。
 *
 * 单向兼容：
 * - 写入永远使用本格式（新版本）；读取时 industry 字段缺失（旧存档）则保持空注册表。
 * - 不序列化 LocalInventory.changes（库存变更日志，有界可见性日志），
 *   加载后变更日志为空，不影响模拟正确性。
 */
public final class IndustryStateCodec {

    private IndustryStateCodec() {
    }

    // ── 编码（保存） ─────────────────────────────────────────────

    /**
     * 将工业注册表编码为 JSON 友好的 Map。
     *
     * 使用 LinkedHashMap 保持迭代顺序 = 各表创建（ID 分配）顺序，
     * 保证存档/加载后 Map 迭代顺序一致（结算报告输出顺序确定性）喵。
     *
     * @param registry 工业注册表
     * @return 可序列化 Map（永不返回 null）
     */
    public static Map<String, Object> encode(IndustryRegistry registry) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("nextInventoryId", registry.getNextInventoryId());
        root.put("nextFacilityId", registry.getNextFacilityId());
        root.put("nextTransferId", registry.getNextTransferId());

        // 本地库存
        List<Map<String, Object>> inventories = new ArrayList<>();
        for (LocalInventory inv : registry.inventoriesById.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("inventoryId", inv.inventoryId);
            m.put("ownerEntityId", inv.ownerEntityId);
            m.put("capacity", inv.capacity);
            m.put("substances", copyDoubleMap(inv.substances));
            m.put("reservedAmounts", copyDoubleMap(inv.reservedAmounts));
            inventories.add(m);
        }
        root.put("inventories", inventories);

        // 加工设施
        List<Map<String, Object>> processingFacilities = new ArrayList<>();
        for (ProcessingFacility f : registry.facilitiesById.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("facilityId", f.facilityId);
            m.put("facilityType", f.facilityType);
            m.put("inventoryId", f.inventoryId);
            m.put("locationEntityId", f.locationEntityId);
            m.put("activeRecipeId", f.activeRecipeId);
            m.put("progressDays", f.progressDays);
            m.put("progressRecipeId", f.progressRecipeId);
            m.put("status", f.status);
            m.put("lastFailureReason", f.lastFailureReason);
            processingFacilities.add(m);
        }
        root.put("processingFacilities", processingFacilities);

        // 采集设施
        List<Map<String, Object>> extractionFacilities = new ArrayList<>();
        for (ResourceExtractionFacility f : registry.extractionFacilitiesById.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("facilityId", f.facilityId);
            m.put("facilityType", f.facilityType);
            m.put("inventoryId", f.inventoryId);
            m.put("locationEntityId", f.locationEntityId);
            m.put("resourceId", f.resourceId);
            m.put("amountPerDay", f.amountPerDay);
            m.put("status", f.status);
            m.put("lastFailureReason", f.lastFailureReason);
            extractionFacilities.add(m);
        }
        root.put("extractionFacilities", extractionFacilities);

        // 货物运输记录
        List<Map<String, Object>> transfers = new ArrayList<>();
        for (CargoTransfer t : registry.transfersById.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("transferId", t.transferId);
            m.put("sourceInventoryId", t.sourceInventoryId);
            m.put("targetInventoryId", t.targetInventoryId);
            m.put("goods", copyDoubleMap(t.goods));
            m.put("status", t.status);
            m.put("departedAtTick", t.departedAtTick);
            m.put("arrivedAtTick", t.arrivedAtTick);
            transfers.add(m);
        }
        root.put("transfers", transfers);

        return root;
    }

    // ── 解码（加载） ─────────────────────────────────────────────

    /**
     * 从存档 Map 恢复工业注册表。
     *
     * 恢复内容：inventoriesById / facilitiesById / extractionFacilitiesById /
     * transfersById 及 nextInventoryId / nextFacilityId / nextTransferId。
     * 列表按存档顺序插入 LinkedHashMap，保证 Map 迭代顺序与保存时一致。
     *
     * 单向兼容：data 为 null 或缺失字段（旧存档）时保持注册表现状
     * （正常加载流程中为全新空注册表）。
     *
     * @param data     存档中的 industry 段（world.industry）
     * @param registry 目标工业注册表
     */
    public static void apply(Map<String, Object> data, IndustryRegistry registry) {
        if (data == null || registry == null) {
            return;
        }

        // 1. 本地库存
        Object inventoriesObj = data.get("inventories");
        if (inventoriesObj instanceof List<?> invList) {
            for (Object item : invList) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) raw;
                LocalInventory inv = new LocalInventory(
                        asLong(m.get("inventoryId")),
                        asLong(m.get("ownerEntityId")),
                        asDouble(m.get("capacity"), LocalInventory.DEFAULT_CAPACITY));
                fillDoubleMap(m.get("substances"), inv.substances);
                fillDoubleMap(m.get("reservedAmounts"), inv.reservedAmounts);
                registry.inventoriesById.put(inv.inventoryId, inv);
            }
        }

        // 2. 加工设施
        Object pfObj = data.get("processingFacilities");
        if (pfObj instanceof List<?> pfList) {
            for (Object item : pfList) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) raw;
                ProcessingFacility f = new ProcessingFacility(
                        asLong(m.get("facilityId")),
                        toStringValue(m.get("facilityType")),
                        asLong(m.get("inventoryId")),
                        asLong(m.get("locationEntityId")),
                        toStringValue(m.get("activeRecipeId")));
                f.progressDays = asDouble(m.get("progressDays"), 0.0);
                f.progressRecipeId = toStringValue(m.get("progressRecipeId"));
                String status = toStringValue(m.get("status"));
                f.status = status == null ? ProcessingFacility.STATUS_IDLE : status;
                f.lastFailureReason = m.get("lastFailureReason") == null
                        ? null : String.valueOf(m.get("lastFailureReason"));
                registry.facilitiesById.put(f.facilityId, f);
            }
        }

        // 3. 采集设施
        Object efObj = data.get("extractionFacilities");
        if (efObj instanceof List<?> efList) {
            for (Object item : efList) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) raw;
                ResourceExtractionFacility f = new ResourceExtractionFacility(
                        asLong(m.get("facilityId")),
                        toStringValue(m.get("facilityType")),
                        asLong(m.get("inventoryId")),
                        asLong(m.get("locationEntityId")),
                        toStringValue(m.get("resourceId")),
                        asDouble(m.get("amountPerDay"), 0.0));
                String status = toStringValue(m.get("status"));
                f.status = status == null ? ResourceExtractionFacility.STATUS_ACTIVE : status;
                f.lastFailureReason = m.get("lastFailureReason") == null
                        ? null : String.valueOf(m.get("lastFailureReason"));
                registry.extractionFacilitiesById.put(f.facilityId, f);
            }
        }

        // 4. 货物运输记录
        Object trObj = data.get("transfers");
        if (trObj instanceof List<?> trList) {
            for (Object item : trList) {
                if (!(item instanceof Map<?, ?> raw)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) raw;
                Map<String, Double> goods = new LinkedHashMap<>();
                fillDoubleMap(m.get("goods"), goods);
                CargoTransfer t = new CargoTransfer(
                        asLong(m.get("transferId")),
                        asLong(m.get("sourceInventoryId")),
                        asLong(m.get("targetInventoryId")),
                        goods,
                        asLong(m.get("departedAtTick")));
                String status = toStringValue(m.get("status"));
                t.status = status == null ? CargoTransfer.STATUS_IN_TRANSIT : status;
                t.arrivedAtTick = asLong(m.get("arrivedAtTick"));
                registry.transfersById.put(t.transferId, t);
            }
        }

        // 5. ID 生成器（setNext*Id 内部忽略小于当前值的写入，防止旧数据回退）喵
        if (data.get("nextInventoryId") instanceof Number n) {
            registry.setNextInventoryId(n.longValue());
        }
        if (data.get("nextFacilityId") instanceof Number n) {
            registry.setNextFacilityId(n.longValue());
        }
        if (data.get("nextTransferId") instanceof Number n) {
            registry.setNextTransferId(n.longValue());
        }
    }

    // ── 辅助方法 ─────────────────────────────────────────────────

    /**
     * 深拷贝 substanceId -> 数量 表（保持顺序，写入时不得引用运行时可变对象）喵。
     */
    private static Map<String, Double> copyDoubleMap(Map<String, Double> source) {
        Map<String, Double> copy = new LinkedHashMap<>();
        if (source != null) {
            copy.putAll(source);
        }
        return copy;
    }

    /**
     * 从反序列化后的对象填充 substanceId -> 数量 表（保持来源 Map 顺序）喵。
     */
    private static void fillDoubleMap(Object raw, Map<String, Double> target) {
        if (!(raw instanceof Map<?, ?> m)) {
            return;
        }
        for (Map.Entry<?, ?> e : m.entrySet()) {
            if (e.getKey() != null && e.getValue() instanceof Number num) {
                target.put(String.valueOf(e.getKey()), num.doubleValue());
            }
        }
    }

    /**
     * 安全读取 long 值（null 时返回 0）喵。
     */
    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : 0L;
    }

    /**
     * 安全读取 double 值（null 或非数字时返回默认值）喵。
     */
    private static double asDouble(Object value, double defaultValue) {
        return value instanceof Number n ? n.doubleValue() : defaultValue;
    }

    /**
     * 安全读取字符串值（null 时返回 null）喵。
     */
    private static String toStringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
