package staraxis.webnet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * ModMetadata
 *
 * 作用：
 * - Mod 元信息的数据模型（对应 gamedata/mods/<modId>/mod.json）。
 * - 由 WebNetServer 在 /api/mods 接口中读取并返回给前端。
 *
 * 字段说明：
 * - name: Mod 显示名称
 * - description: Mod 描述
 * - version: Mod 版本
 * - compatibleGameVersion: 兼容的游戏版本
 * - author: 作者
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModMetadata {

    public String name = "(未命名)";
    public String description = "(无描述)";
    public String version = "(未知版本)";
    public String compatibleGameVersion = "(未知)";
    public String author = "(未知作者)";
}
