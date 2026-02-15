package staraxis.game.nation.design;

import staraxis.game.nation.NationDef;
import staraxis.game.nation.SpawnStrategy;

/**
 * PlayerNationDesign
 *
 * 玩家自定义国家设计（Player Nation Design）。
 *
 * 说明：
 * - 该对象用于“玩家设计国家”的保存与读取。
 * - 所有权识别口径：`username`（文件路径隔离） + `playerId`（权威校验）。
 * - 该对象仅描述设计内容（definition），不包含运行时状态。
 */
public class PlayerNationDesign {

    /**
     * schemaVersion：用于未来结构升级与兼容。
     */
    public int schemaVersion = 1;

    /**
     * username：玩家账号名（对应 gamedata/accounts/<username>.json）。
     */
    public String username;

    /**
     * playerId：玩家权威 ID（对应 gamedata/accounts/<username>.json 里的 playerId）。
     */
    public String playerId;

    /**
     * nation：玩家自定义国家的定义（NationDef）。
     */
    public NationDef nation;

    /**
     * spawnStrategy：开局出生策略。
     *
     * 口径说明：
     * - RANDOM_SYSTEM：随机选择一个合适的星系和行星作为初始位置。
     * - PRESET_SYSTEM：使用预设星系（例如根据预设ID从 Astro 预设表中查找）。
     * - MANUAL_SELECTION：由玩家在界面中显式选择星系/星球（后续扩展）。
     *
     * 当前实现：如果未显式设置，建议上层逻辑默认为 RANDOM_SYSTEM。
     */
    public SpawnStrategy spawnStrategy = SpawnStrategy.RANDOM_SYSTEM;

    /**
     * updatedAtUnixMs：最近一次保存时间（毫秒）。
     */
    public long updatedAtUnixMs;

    /**
     * 默认构造（供 JSON 反序列化/创建占位对象）。
     */
    public PlayerNationDesign() {
    }
}
