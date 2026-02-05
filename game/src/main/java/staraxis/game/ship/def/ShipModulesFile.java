/*
 * ShipModulesFile
 *
 * 文件作用：
 * - 舰船模块文件的顶层容器模型，用于 JSON 反序列化。
 * - 包含 schemaVersion 与模块列表，便于未来文件格式演进。
 *
 * 提供的接口 API：
 * - 公共字段：schemaVersion（文件格式版本）、modules（模块定义列表）。
 *
 * 使用方式：
 * - 由 ShipAssetRepository 读取 assets/ship/modules.json 或 Mod 覆盖文件。
 * - 解析后提取 modules 字段，合并到全局模块注册表。
 *
 * 注意事项：
 * - 本类为只读数据模型，禁止在运行时修改。
 * - schemaVersion 用于未来兼容性检查，当前版本为 "1.0"。
 * - 所有模块定义必须通过 JSON 配置，禁止硬编码。
 */

package staraxis.game.ship.def;

import java.util.List;

/**
 * 舰船模块文件的顶层容器模型，用于 JSON 反序列化。
 * 包含 schemaVersion 与模块列表，便于未来文件格式演进。
 */
public class ShipModulesFile {
    /** 文件格式版本，用于未来兼容性检查。 */
    public String schemaVersion;

    /** 舰船模块定义列表。 */
    public List<ShipModuleDef> modules;
}