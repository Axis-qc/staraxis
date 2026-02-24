package staraxis.game.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import staraxis.game.intel.def.IntelConfigDef;
import staraxis.game.intel.def.IntelConfigRepository;

/**
 * GlobalConfigRegistry（全局配置注册中心）
 *
 * 作用：提供统一的配置入口，但配置文件仍按系统拆分（数据驱动 + 可演进）喵。
 *
 * 设计原则：
 * - 统一入口：外部只依赖本类获取各系统配置喵。
 * - 分系统文件：各系统仍使用各自 Repository 进行 base + mods 覆盖加载喵。
 * - 可扩展：后续可注册 planet/astro/ship 等更多系统配置喵。
 */
public class GlobalConfigRegistry {

    private final ObjectMapper objectMapper;

    // --- Intel ---
    private final IntelConfigRepository intelRepo;

    public GlobalConfigRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.intelRepo = new IntelConfigRepository(objectMapper);
    }

    /**
     * 加载所有配置（base + mods）喵。
     */
    public void loadAll() {
        intelRepo.loadAll();
    }

    /**
     * 获取情报系统配置喵。
     */
    public IntelConfigDef intel() {
        return intelRepo.getConfig();
    }

    /**
     * 获取 ObjectMapper（供未来其他仓库复用）喵。
     */
    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }
}
