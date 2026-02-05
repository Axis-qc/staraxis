/*
 * ShipDesignRepository
 *
 * 文件作用：
 * - 玩家舰船蓝图（ShipDesign）的持久化存储与读取仓库。
 * - 保存到 gamedata/designs/ship/{designId}.json，支持 Mod 预设蓝图（只读）覆盖/合并。
 *
 * 数据来源与覆盖规则：
 * - 玩家蓝图根目录：gamedata/designs/ship/（读写，玩家创建/修改/删除）。
 * - Mod 预设蓝图：gamedata/mods/{modId}/designs/ship/（只读，按 mod-order.json 顺序覆盖）。
 * - 覆盖口径：同一 designId 的蓝图，后加载覆盖先加载（最后写入胜出）。
 *
 * 提供的接口 API：
 * - loadAll()：加载所有蓝图（玩家 + Mod 预设），返回只读映射。
 * - save(ShipDesign design)：保存/覆盖玩家蓝图（写入 gamedata/designs/ship/{designId}.json）。
 * - delete(String designId)：删除玩家蓝图（仅限玩家目录，不允许删除 Mod 预设）。
 * - getDesignById(String designId)：按 designId 查询蓝图。
 *
 * 使用方式：
 * - 舰船设计器 UI：调用 loadAll() 获取可用蓝图；调用 save() 保存新蓝图；调用 delete() 删除玩家蓝图。
 * - 生产系统：通过 getDesignById() 获取 ShipDesign，再结合 ShipAssetRepository 计算成本与属性。
 *
 * 注意事项：
 * - 所有文件读写属于阻塞 IO，禁止在模拟 tick 内调用。
 * - game 模块不依赖 webnet，因此在此处实现最小 Mod 顺序解析（复用 ShipAssetRepository 的 ModOrder）。
 * - 保存前应校验 designId 与 moduleIds 合法性（非空、可在 ShipAssetRepository 解析等）。
 */

package staraxis.game.ship.design;

import com.fasterxml.jackson.databind.ObjectMapper;

import staraxis.game.ship.ShipDesign;
import staraxis.game.ship.def.ShipAssetRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家舰船蓝图（ShipDesign）的持久化存储与读取仓库。
 */
public class ShipDesignRepository {

    private static final String PLAYER_DESIGNS_ROOT = "gamedata/designs/ship";
    private static final String MODS_ROOT = "gamedata/mods";
    private static final String MOD_ORDER_PATH = "gamedata/mods/mod-order.json";

    private final ObjectMapper objectMapper;
    private final ShipAssetRepository shipAssetRepository;

    private final Map<String, ShipDesign> designsById = new ConcurrentHashMap<>();
    private volatile List<ShipDesign> designs = List.of();

    public ShipDesignRepository(ObjectMapper objectMapper, ShipAssetRepository shipAssetRepository) {
        this.objectMapper = objectMapper;
        this.shipAssetRepository = shipAssetRepository;
    }

    /**
     * 加载所有蓝图（玩家 + Mod 预设），阻塞 IO。
     */
    public void loadAll() {
        Map<String, ShipDesign> out = new ConcurrentHashMap<>();

        // 1) 先加载 Mod 预设（按顺序覆盖）
        for (String modId : listModIdsOrderedAndEnabled()) {
            File dir = new File(new File(new File(MODS_ROOT, modId), "designs"), "ship");
            if (!dir.exists() || !dir.isDirectory()) {
                continue;
            }
            File[] files = dir.listFiles(File::isFile);
            if (files == null) {
                continue;
            }
            for (File f : files) {
                if (!f.getName().toLowerCase().endsWith(".json")) {
                    continue;
                }
                ShipDesign d = readDesign(f);
                if (d != null && d.designId != null && !d.designId.isBlank()) {
                    out.put(d.designId.trim(), d);
                }
            }
        }

        // 2) 再加载玩家蓝图（覆盖 Mod 预设）
        File playerDir = new File(PLAYER_DESIGNS_ROOT);
        if (playerDir.exists() && playerDir.isDirectory()) {
            File[] files = playerDir.listFiles(File::isFile);
            if (files != null) {
                for (File f : files) {
                    if (!f.getName().toLowerCase().endsWith(".json")) {
                        continue;
                    }
                    ShipDesign d = readDesign(f);
                    if (d != null && d.designId != null && !d.designId.isBlank()) {
                        out.put(d.designId.trim(), d);
                    }
                }
            }
        }

        // 3) 发布只读视图（按 designId 排序，保证确定性展示）
        ArrayList<ShipDesign> list = new ArrayList<>(out.values());
        list.sort(Comparator.comparing(a -> a.designId != null ? a.designId : ""));

        designsById.clear();
        designsById.putAll(out);
        designs = Collections.unmodifiableList(list);
    }

    /**
     * 保存/覆盖玩家蓝图（阻塞 IO）。
     */
    public boolean save(ShipDesign design) {
        if (design == null || design.designId == null || design.designId.isBlank()) {
            return false;
        }

        // 校验模块合法性
        if (design.moduleIds != null) {
            for (String moduleId : design.moduleIds) {
                if (moduleId == null || moduleId.isBlank()) {
                    continue;
                }
                if (shipAssetRepository.getModuleById(moduleId) == null) {
                    // 模块不存在，保存失败（可改为跳过或警告，口径后续统一）
                    return false;
                }
            }
        }

        File dir = new File(PLAYER_DESIGNS_ROOT);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File f = new File(dir, design.designId.trim() + ".json");
        try {
            objectMapper.writeValue(f, design);
            // 更新内存缓存
            designsById.put(design.designId.trim(), design);
            // 重建只读列表
            ArrayList<ShipDesign> list = new ArrayList<>(designsById.values());
            list.sort(Comparator.comparing(a -> a.designId != null ? a.designId : ""));
            designs = Collections.unmodifiableList(list);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 删除玩家蓝图（仅限玩家目录）。
     */
    public boolean delete(String designId) {
        if (designId == null || designId.isBlank()) {
            return false;
        }
        File f = new File(new File(PLAYER_DESIGNS_ROOT), designId.trim() + ".json");
        boolean deleted = f.exists() && f.isFile() && f.delete();
        if (deleted) {
            designsById.remove(designId.trim());
            // 重建只读列表
            ArrayList<ShipDesign> list = new ArrayList<>(designsById.values());
            list.sort(Comparator.comparing(a -> a.designId != null ? a.designId : ""));
            designs = Collections.unmodifiableList(list);
        }
        return deleted;
    }

    public List<ShipDesign> getDesigns() {
        return designs;
    }

    public ShipDesign getDesignById(String designId) {
        if (designId == null) {
            return null;
        }
        return designsById.get(designId);
    }

    private ShipDesign readDesign(File file) {
        try {
            return objectMapper.readValue(file, ShipDesign.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 复用 ShipAssetRepository 的 Mod 顺序解析逻辑（避免重复）。
     */
    private List<String> listModIdsOrderedAndEnabled() {
        ModOrder conf = readModOrder();
        Set<String> disabled = conf != null && conf.disabled != null ? conf.disabled : Set.of();

        Set<String> ordered = new LinkedHashSet<>();
        if (conf != null && conf.order != null) {
            for (String modId : conf.order) {
                if (modId != null && !modId.isBlank()) {
                    ordered.add(modId.trim());
                }
            }
        }

        File modsDir = new File(MODS_ROOT);
        if (modsDir.exists() && modsDir.isDirectory()) {
            File[] modDirs = modsDir.listFiles(File::isDirectory);
            if (modDirs != null) {
                java.util.Arrays.sort(modDirs, Comparator.comparing(File::getName));
                for (File modDir : modDirs) {
                    String modId = modDir.getName();
                    if (modId != null && !modId.isBlank()) {
                        ordered.add(modId);
                    }
                }
            }
        }

        LinkedList<String> out = new LinkedList<>();
        for (String modId : ordered) {
            if (modId == null || modId.isBlank()) {
                continue;
            }
            if (disabled.contains(modId)) {
                continue;
            }
            out.add(modId);
        }
        return out;
    }

    private ModOrder readModOrder() {
        File f = new File(MOD_ORDER_PATH);
        if (!f.exists() || !f.isFile()) {
            return new ModOrder();
        }
        try {
            return objectMapper.readValue(f, ModOrder.class);
        } catch (Exception ignored) {
            return new ModOrder();
        }
    }

    /**
     * ModOrder
     *
     * 最小配置模型，对齐 webnet 的 mod-order.json 字段口径。
     */
    public static class ModOrder {
        public List<String> order;
        public Set<String> disabled;
    }
}
