package staraxis.webnet.mod;

/**
 * ModManager
 *
 * 作用：
 * - 负责“Mod 列表/加载顺序/启用状态”的口径整合。
 * - 当前阶段主要用于：
 *   - i18n 合并时确定 Mods 加载顺序
 *   -（未来）资源覆盖、UI Registry 合并等也将依赖同一套顺序
 *
 * 数据来源：
 * - 权威顺序文件：gamedata/mods/mod-order.json（由 ModOrderRepository 读取）
 * - Mods 根目录：gamedata/mods/
 *
 * 当前实现口径：
 * - 顺序：优先使用 mod-order.json 的 order
 * - 发现：扫描 mods 目录下实际存在的 modId，补充到末尾（按目录名排序）
 * - 启用/禁用：disabled 中的 modId 视为禁用，会从最终“启用列表”中剔除
 *
 * 注意事项：
 * - 文件系统扫描属于阻塞 IO：如果在 Undertow 请求线程中调用，应使用 exchange.dispatch(...) 切换到 worker 线程。
 */

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ModManager {

    private static final String MODS_ROOT = "gamedata/mods";

    private final ModOrderRepository orderRepository;

    public ModManager(ModOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * 返回“按顺序 + 已启用”的 modId 列表。
     *
     * 口径：
     * - 先读取 mod-order.json 的 order 作为优先顺序
     * - 再把 mods 目录下存在但不在 order 里的 mod 追加到末尾（按目录名排序），避免“漏掉就无法加载”
     * - 最后过滤掉 disabled 中的 modId
     */
    public List<String> listModIdsOrderedAndEnabled() {
        Set<String> ordered = new LinkedHashSet<>();

        ModOrder conf = orderRepository != null ? orderRepository.load() : new ModOrder();
        Set<String> disabled = conf != null && conf.disabled != null ? conf.disabled : Set.of();

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
                java.util.Arrays.sort(modDirs, java.util.Comparator.comparing(File::getName));
                for (File modDir : modDirs) {
                    String modId = modDir.getName();
                    if (modId != null && !modId.isBlank()) {
                        ordered.add(modId);
                    }
                }
            }
        }

        var out = new ArrayList<String>();
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

    /**
     * 返回“已发现的所有 modId”（不区分是否禁用），用于 Mod 管理 UI。
     */
    public List<String> listAllModIdsDiscovered() {
        var out = new ArrayList<String>();
        File modsDir = new File(MODS_ROOT);
        if (!modsDir.exists() || !modsDir.isDirectory()) {
            return out;
        }
        File[] modDirs = modsDir.listFiles(File::isDirectory);
        if (modDirs == null) {
            return out;
        }
        java.util.Arrays.sort(modDirs, java.util.Comparator.comparing(File::getName));
        for (File modDir : modDirs) {
            String modId = modDir.getName();
            if (modId != null && !modId.isBlank()) {
                out.add(modId);
            }
        }
        return out;
    }
}
