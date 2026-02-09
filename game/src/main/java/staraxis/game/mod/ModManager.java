package staraxis.game.mod;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ModManager (Game版)
 *
 * 作用：
 * - 负责在 game 模块内提供“Mod 列表/加载顺序/启用状态”的口径整合喵。
 * - 避免对 webnet 模块的依赖，保持架构纯净喵。
 */
public class ModManager {

    private static final String MODS_ROOT = "gamedata/mods";

    private final ModOrderRepository orderRepository;

    public ModManager(ModOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * 返回“按顺序 + 已启用”的 modId 列表喵。
     * 口径与 webnet.ModManager 保持严格一致喵。
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
}
