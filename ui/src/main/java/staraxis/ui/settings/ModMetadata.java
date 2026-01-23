package staraxis.ui.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Mod 元数据的数据模型（POJO），对应于每个 mod 目录下的 mod.json。
 *
 * 设计要点：
 * - 使用 Jackson 的 @JsonIgnoreProperties(ignoreUnknown = true) 允许 mod.json
 * 包含未来版本的新字段而不导致解析失败。
 * - 提供一个静态工厂方法 createDefault() 用于在 mod.json 缺失或损坏时提供兜底实例。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModMetadata {

    public int schemaVersion = 1;
    public String modId;
    public String name;
    public String version;
    public String compatibleGameVersion;
    public String description;

    /**
     * 创建一个默认的 ModMetadata 实例，用于文件缺失或解析失败时的回退。
     *
     * @param modDirectoryName Mod 的目录名，将作为默认的 modId 和 name。
     * @return 一个包含基础信息的 ModMetadata 实例。
     */
    public static ModMetadata createDefault(String modDirectoryName) {
        ModMetadata defaults = new ModMetadata();
        defaults.modId = modDirectoryName;
        defaults.name = modDirectoryName;
        defaults.version = "0.0.0";
        defaults.compatibleGameVersion = "";
        defaults.description = "";
        return defaults;
    }
}
