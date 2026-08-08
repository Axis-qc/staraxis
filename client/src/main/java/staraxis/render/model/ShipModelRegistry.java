package staraxis.render.model;

import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.Logger;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ShipModelRegistry（舰船模型渲染配置注册表）。
 *
 * 启动时解析 assets/ship/model/model_registry.json，构建 modelKey -> ShipModelDef 映射。
 * 渲染层按 modelKey 取模型定义（glTF 路径 + 缩放系数），零文件路径散落。
 *
 * 使用方式：
 * - 渲染器构造时调用 get(modelKey) 取 ShipModelDef
 * - 未找到返回 null，由调用方按"模型资产为硬依赖"语义中断
 *
 * 与 SpriteRegistry 同构：Jackson + POJO + Gdx.files.internal。
 */
public class ShipModelRegistry {

    /** 模型渲染配置表 JSON 路径（相对 assets 根目录）。 */
    private static final String REGISTRY_PATH = "assets/ship/model/model_registry.json";

    private static final Logger LOG = new Logger("ShipModelRegistry", Logger.INFO);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** modelKey -> 模型定义（最终查询目标）。 */
    private final Map<String, ShipModelDef> models = new HashMap<>();

    /** 是否已完成加载。 */
    private boolean loaded;

    /**
     * 一次性解析 model_registry.json 并构建 modelKey 映射。
     * 加载失败仅记录日志（与 SpriteRegistry 一致的容错语义），
     * 缺失的 modelKey 由调用方在 get() 返回 null 后自行中断。
     */
    public ShipModelRegistry() {
        if (loaded) {
            return;
        }

        try {
            ShipModelRegistryFile file = objectMapper.readValue(
                    Gdx.files.internal(REGISTRY_PATH).file(),
                    ShipModelRegistryFile.class);
            if (file == null || file.models == null) {
                LOG.error("model_registry.json 解析为空");
                loaded = true;
                return;
            }
            models.putAll(file.models);
            LOG.info("ShipModelRegistry 加载完成: " + models.size() + " 个模型");
        } catch (Exception e) {
            LOG.error("无法加载 model_registry.json: " + e.getMessage());
        }

        loaded = true;
    }

    /**
     * 按 modelKey 获取模型定义，纯内存 Map 查找（O(1)）。
     *
     * @param modelKey 模型标识 key
     * @return 模型定义，未找到返回 null
     */
    public ShipModelDef get(String modelKey) {
        return models.get(modelKey);
    }
}
