package staraxis.game_asset.data;

/**
 * MaterialData（中性材质描述，不依赖任何渲染 API）。
 *
 * 基于 glTF PBR 材质模型，包含基础颜色因子、纹理路径引用和 PBR 参数。
 * 纹理路径为相对路径（相对于 assets 根目录），由渲染后端负责加载为实际纹理对象。
 */
public class MaterialData {

    /** 基础颜色因子（RGBA，默认白色） */
    public float[] baseColorFactor = {1f, 1f, 1f, 1f};

    /** 金属度因子（0 = 非金属，1 = 金属，默认 1） */
    public float metallicFactor = 1f;

    /** 粗糙度因子（0 = 光滑，1 = 粗糙，默认 1） */
    public float roughnessFactor = 1f;

    /** 自发光颜色因子（RGB，默认无自发光） */
    public float[] emissiveFactor = {0f, 0f, 0f};

    /** 基础颜色贴图路径（可空，相对于 assets 根目录） */
    public String baseColorTexturePath;

    /** 法线贴图路径（可空，相对于 assets 根目录） */
    public String normalTexturePath;

    /** 金属粗糙度贴图路径（可空，glTF 的 metallicRoughness 贴图） */
    public String metallicRoughnessTexturePath;

    /** 自发光贴图路径（可空） */
    public String emissiveTexturePath;

    /** 镜面反射贴图路径（可空，KHR_materials_specular 扩展） */
    public String specularTexturePath;
}
