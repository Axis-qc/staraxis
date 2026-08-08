package staraxis.render.model;

import java.util.Map;

/**
 * ShipModelRegistryFile（模型渲染配置表 JSON 顶层容器 POJO）。
 *
 * 对应 assets/ship/model/model_registry.json 的完整结构：
 * - models：modelKey -> ShipModelDef（模型定义映射表）
 *
 * 职责：纯数据模型，Jackson 反序列化用。
 */
public class ShipModelRegistryFile {

    /** modelKey -> 模型定义（含 glTF 路径与缩放系数）。 */
    public Map<String, ShipModelDef> models;

    public ShipModelRegistryFile() {
    }
}
