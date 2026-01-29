package staraxis.game.nation.design;

import java.util.List;
import java.util.Optional;

/**
 * NationDesignRepository
 *
 * 玩家自定义国家的存取接口（Repository Interface）。
 *
 * 约束：
 * - `game` 模块不做任何文件 IO（保持纯模拟/数据层）。
 * - 具体落盘路径与 JSON 序列化由 `webnet` 模块实现。
 *
 * 数据来源口径（由 webnet 保证）：
 * - 预设国家：gamedata/nations/presets/<nationId>.json
 * - 玩家国家：gamedata/nations/players/<username>/<nationId>.json
 */
public interface NationDesignRepository {

    /**
     * 列出某个玩家的全部自定义国家 id（nationId）。
     *
     * @param username 玩家账号名
     * @return nationId 列表
     */
    List<String> listNationIdsByUsername(String username);

    /**
     * 加载某个玩家的自定义国家设计。
     *
     * @param username 玩家账号名
     * @param nationId 国家 id
     * @return 若存在则返回设计对象
     */
    Optional<PlayerNationDesign> load(String username, String nationId);

    /**
     * 保存某个玩家的自定义国家设计。
     *
     * 说明：
     * - 所有权校验必须由实现方（webnet）完成：校验 username 对应 accounts 文件中的 playerId。
     * - 保存时应覆盖写入对应文件。
     *
     * @param design 玩家自定义国家设计
     */
    void save(PlayerNationDesign design);
}
