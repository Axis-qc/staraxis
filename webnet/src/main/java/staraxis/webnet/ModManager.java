package staraxis.webnet;

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
     * 当前阶段口径：
     * - 读取 gamedata/mods/mod-order.json 的 order 作为优先顺序
     * - 仍然会把 mods 目录下存在但不在 order 里的 mod 追加到末尾（按目录名排序），避免“漏掉就无法加载”
     * - 启用/禁用规则暂未实现：默认全部启用
     */
    public List<String> listModIdsOrderedAndEnabled() {
        Set<String> result = new LinkedHashSet<>();

        ModOrder order = orderRepository != null ? orderRepository.load() : new ModOrder();
        if (order != null && order.order != null) {
            for (String modId : order.order) {
                if (modId != null && !modId.isBlank()) {
                    result.add(modId.trim());
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
                        result.add(modId);
                    }
                }
            }
        }

        return new ArrayList<>(result);
    }
}
