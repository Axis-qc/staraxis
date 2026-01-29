package staraxis.game.nation.design;

import staraxis.game.nation.NationDef;

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
     * updatedAtUnixMs：最近一次保存时间（毫秒）。
     */
    public long updatedAtUnixMs;

    /**
     * 默认构造（供 JSON 反序列化/创建占位对象）。
     */
    public PlayerNationDesign() {
    }
}
