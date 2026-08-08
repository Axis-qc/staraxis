package staraxis.render.model;

/**
 * ShipModelDef（模型渲染配置表条目 POJO）。
 *
 * 对应 assets/ship/model/model_registry.json 中 models 节的一项：
 * - path：glTF 文件相对路径（相对 assets 根目录，供 Gdx.files.internal 加载）
 * - scale：模型渲染缩放系数（1.0 = 原始大小，不缩放）
 *
 * 职责：纯数据模型，Jackson 反序列化 model_registry.json 用。
 */
public class ShipModelDef {

    /** glTF 文件相对路径（相对 assets 根目录）。 */
    public String path;

    /** 模型渲染缩放系数（1.0 = 原始大小）。 */
    public float scale;

    public ShipModelDef() {
    }
}
