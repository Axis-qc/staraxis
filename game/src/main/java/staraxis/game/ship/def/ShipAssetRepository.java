/*
 * ShipAssetRepository
 *
 * 文件作用：
 * - 负责从本体与 Mod 中加载“舰船拼装系统”的权威数据定义（当前阶段：ShipModuleDef）。
 * - 提供只读查询接口，供模拟层（game）在 tick 内构建 ShipDesign / ShipBody 等使用。
 *
 * 数据来源与覆盖规则：
 * - 本体数据根目录：assets/ship/
 * - Mod 数据根目录：gamedata/mods/{modId}/ship/
 * - Mod 加载顺序：gamedata/mods/mod-order.json（order 优先 + 扫描目录补全，disabled 过滤）。
 * - 覆盖口径：同一 moduleId 的模块定义，后加载的覆盖先加载的（最后写入胜出）。
 *
 * 提供的接口 API：
 * - loadAll()：加载/重载所有舰船相关定义（阻塞 IO，仅允许在启动或后台线程调用）。
 * - getModules()：返回只读模块列表。
 * - getModuleById(String moduleId)：按 moduleId 获取模块定义。
 *
 * 使用方式：
 * - StarAxisGameRuntime 启动/创建世界前调用一次 loadAll()。
 * - 运行中如需热重载（开发态），由上层控制在安全时机调用 loadAll()。
 *
 * 注意事项：
 * - game 模块禁止依赖 webnet，因此在此处实现最小 Mod 顺序解析与目录扫描逻辑。
 * - 所有文件读取属于阻塞 IO：禁止在模拟 tick 内调用 loadAll()。
 * - 解析失败时保持容错：尽量返回空列表/不覆盖，避免崩溃。
 */

package staraxis.game.ship.def;

import com.fasterxml.jackson.databind.ObjectMapper;

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
 * 负责从本体与 Mod 中加载“舰船拼装系统”的权威数据定义。
 */
public class ShipAssetRepository {

    private static final String BASE_SHIP_ROOT = "assets/ship";
    private static final String BASE_SHIP_MODULES_ROOT = "assets/ship/modules";
    private static final String MODS_ROOT = "gamedata/mods";
    private static final String MOD_ORDER_PATH = "gamedata/mods/mod-order.json";

    private final ObjectMapper objectMapper;

    private final Map<String, ShipModuleDef> modulesById = new ConcurrentHashMap<>();
    private volatile List<ShipModuleDef> modules = List.of();

    public ShipAssetRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 加载所有舰船相关定义（阻塞 IO）。
     * 支持分类 JSON：assets/ship/modules/{category}.json（如 ENGINE.json、WEAPON.json 等）。
     */
    public void loadAll() {
        Map<String, ShipModuleDef> out = new ConcurrentHashMap<>();

        // 1) 先加载本体分类 JSON
        loadModulesFromDir(out, new File(BASE_SHIP_MODULES_ROOT));

        // 2) 再按 Mod 顺序覆盖（支持分类 JSON）
        for (String modId : listModIdsOrderedAndEnabled()) {
            File modModulesDir = new File(new File(new File(MODS_ROOT, modId), "ship"), "modules");
            loadModulesFromDir(out, modModulesDir);
        }

        // 3) 发布只读视图（按 moduleId 排序，保证确定性展示）
        ArrayList<ShipModuleDef> list = new ArrayList<>(out.values());
        list.sort(Comparator.comparing(a -> a.moduleId != null ? a.moduleId : ""));

        modulesById.clear();
        modulesById.putAll(out);
        modules = Collections.unmodifiableList(list);
    }

    public List<ShipModuleDef> getModules() {
        return modules;
    }

    public ShipModuleDef getModuleById(String moduleId) {
        if (moduleId == null) {
            return null;
        }
        return modulesById.get(moduleId);
    }

    /**
     * 从指定目录加载所有分类 JSON 文件（*.json），合并到 out。
     */
    private void loadModulesFromDir(Map<String, ShipModuleDef> out, File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return;
        }

        File[] jsonFiles = dir.listFiles(File::isFile);
        if (jsonFiles == null) {
            return;
        }

        for (File f : jsonFiles) {
            if (!f.getName().toLowerCase().endsWith(".json")) {
                continue;
            }

            // 支持两种格式：
            // 1) 直接数组：[{...},{...}]
            // 2) 顶层容器：{"schemaVersion":"1.0","modules":[{...}]}
            List<ShipModuleDef> list = readModules(f);
            if (list == null || list.isEmpty()) {
                continue;
            }

            for (ShipModuleDef def : list) {
                if (def == null || def.moduleId == null || def.moduleId.isBlank()) {
                    continue;
                }
                out.put(def.moduleId.trim(), def);
            }
        }
    }

    private void mergeModulesFromFile(Map<String, ShipModuleDef> out, File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return;
        }

        // 支持两种格式：
        // 1) 直接数组：[{...},{...}]
        // 2) 顶层容器：{"schemaVersion":"1.0","modules":[{...}]}
        List<ShipModuleDef> list = readModules(file);
        if (list == null || list.isEmpty()) {
            return;
        }

        for (ShipModuleDef def : list) {
            if (def == null || def.moduleId == null || def.moduleId.isBlank()) {
                continue;
            }
            out.put(def.moduleId.trim(), def);
        }
    }

    private List<ShipModuleDef> readModules(File file) {
        try {
            // 先尝试容器格式
            ShipModulesFile f = objectMapper.readValue(file, ShipModulesFile.class);
            if (f != null && f.modules != null) {
                return f.modules;
            }
        } catch (Exception ignored) {
            // ignored
        }

        try {
            // 再尝试数组格式
            ShipModuleDef[] arr = objectMapper.readValue(file, ShipModuleDef[].class);
            if (arr == null || arr.length == 0) {
                return List.of();
            }
            ArrayList<ShipModuleDef> out = new ArrayList<>(arr.length);
            Collections.addAll(out, arr);
            return out;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * game 模块内部的最小 Mod 顺序解析：
     * - 优先使用 mod-order.json 的 order
     * - 扫描 mods 目录补全
     * - disabled 过滤
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
