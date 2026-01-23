package staraxis.ui.settings;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mod 管理器：负责扫描并按顺序组织 Mod 列表。
 *
 * 设计要点：
 * - UI 层服务，封装 Mod 目录扫描、元数据加载、顺序持久化等细节。
 * - 遵循“根目录 gamedata/mods”口径，并使用 ../gamedata 适配 lwjgl3:run 的 workingDir=assets。
 * - 顺序来源：gamedata/mods/mod-order.json。
 * - 未出现在 order 的 Mod：按目录名排序追加在末尾。
 */
public class ModManager {

    private static final String MODS_ROOT_PATH = "../gamedata/mods/";

    private final ModOrderRepository orderRepository;
    private final ModMetadataRepository metadataRepository;

    public ModManager(ModOrderRepository orderRepository, ModMetadataRepository metadataRepository) {
        this.orderRepository = orderRepository;
        this.metadataRepository = metadataRepository;
    }

    public List<String> listAllModIdsUnordered() {
        FileHandle modsDir = Gdx.files.local(MODS_ROOT_PATH);
        List<String> ids = new ArrayList<>();
        if (!modsDir.exists() || !modsDir.isDirectory()) {
            return ids;
        }
        for (FileHandle modDir : modsDir.list()) {
            if (modDir.isDirectory()) {
                ids.add(modDir.name());
            }
        }
        return ids;
    }

    public List<String> listModIdsOrdered() {
        List<String> allIds = listAllModIdsUnordered();
        allIds.sort(String::compareTo);

        ModOrder order = orderRepository.load();
        List<String> result = new ArrayList<>();

        // 先放 order 里存在且当前目录存在的
        if (order.order != null) {
            for (String id : order.order) {
                if (id == null || id.isBlank()) {
                    continue;
                }
                if (allIds.contains(id) && !result.contains(id)) {
                    result.add(id);
                }
            }
        }

        // 再追加剩余的（按目录名排序）
        for (String id : allIds) {
            if (!result.contains(id)) {
                result.add(id);
            }
        }

        return result;
    }

    public List<ModMetadata> loadModsOrdered() {
        List<String> ids = listModIdsOrdered();
        List<ModMetadata> result = new ArrayList<>();
        for (String id : ids) {
            result.add(metadataRepository.loadOrDefault(id));
        }
        return result;
    }

    public void saveOrder(List<String> orderedModIds) {
        orderRepository.save(orderedModIds);
    }

    public Map<String, ModMetadata> loadModsById() {
        List<String> ids = listAllModIdsUnordered();
        Map<String, ModMetadata> map = new HashMap<>();
        for (String id : ids) {
            map.put(id, metadataRepository.loadOrDefault(id));
        }
        return map;
    }
}
