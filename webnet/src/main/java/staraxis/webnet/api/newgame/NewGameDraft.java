package staraxis.webnet.api.newgame;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NewGameDraft
 *
 * 新游戏草稿（New Game Draft）：用于保存“新游戏流程”的中间状态。
 *
 * 落盘口径：
 * - gamedata/saves/<username>_newgame.json
 *
 * 所有权口径：
 * - 请求必须携带 username + playerId
 * - 服务端必须读取 gamedata/accounts/<username>.json 校验 playerId 一致
 */
public class NewGameDraft {

    /**
     * schemaVersion：用于未来结构升级与兼容。
     */
    public int schemaVersion = 1;

    /**
     * username：账号名（文件名隔离）。
     */
    public String username;

    /**
     * playerId：权威玩家 ID（所有权校验依据）。
     */
    public String playerId;

    /**
     * updatedAtUnixMs：最近一次写入时间。
     */
    public long updatedAtUnixMs;

    /**
     * nationId：玩家选择的国家 id。
     */
    public String nationId;

    /**
     * worldGenConfig：世界生成配置（数据驱动：使用 map 以便演进）。
     */
    public Map<String, Object> worldGenConfig = new LinkedHashMap<>();

    public NewGameDraft() {
    }
}
