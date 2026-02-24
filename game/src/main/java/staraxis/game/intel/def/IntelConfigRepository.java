package staraxis.game.intel.def;

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.mod.ModManager;
import staraxis.game.mod.ModOrderRepository;

import java.io.File;
import java.util.List;

/**
 * IntelConfigRepository（情报配置仓库）
 * 
 * 负责加载并合并情报系统的配置数据喵。
 * 遵循 base + mods（后读覆盖前读）的加载口径喵。
 */
public class IntelConfigRepository {

    private final ObjectMapper objectMapper;
    private IntelConfigDef config;

    public IntelConfigRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.config = new IntelConfigDef(); // 默认配置喵
    }

    /**
     * 加载所有情报配置喵。
     */
    public void loadAll() {
        String baseRelativePath = "assets/intel/intel-config.json";
        String modRelativePath = "intel/intel-config.json";

        // 1. 加载本体配置喵
        loadFromFile(baseRelativePath);

        // 2. 加载所有已启用 Mod 的配置并覆盖喵
        ModManager modMgr = new ModManager(new ModOrderRepository());
        List<String> modIds = modMgr.listModIdsOrderedAndEnabled();

        for (String modId : modIds) {
            String modPath = "gamedata/mods/" + modId + "/" + modRelativePath;
            loadFromFile(modPath);
        }
    }

    private void loadFromFile(String path) {
        try {
            File file = new File(path);
            if (file.exists() && file.isFile()) {
                // 使用 Jackson 的 readerForUpdating 实现增量覆盖更新喵
                config = objectMapper.readerForUpdating(config).readValue(file);
                System.out.println("[IntelConfigRepository] Loaded config from: " + path + " 喵");
            }
        } catch (Exception e) {
            System.err.println("[IntelConfigRepository] Failed to load config from " + path + ": " + e.getMessage() + " 喵");
        }
    }

    /**
     * 获取当前生效的情报配置喵。
     */
    public IntelConfigDef getConfig() {
        return config;
    }
}
