package staraxis.game.command;

import java.util.List;
import java.util.Map;

/**
 * LoadWorldCommand（加载世界存档命令）喵。
 *
 * 从存档数据恢复游戏运行时状态。
 * 替代 webnet 层直接读写 WorldState 的 applyTimeState / applyNationState / applyEntitiesState / setNextEntityId 喵。
 *
 * 承载原始 JSON 反序列化后的数据结构，由 LoadWorldHandler 在 game 模块内解析并写入 WorldState 喵。
 */
public class LoadWorldCommand extends Command {

    private final Map<String, Object> worldData;
    private final List<Map<String, Object>> nations;
    private final List<Map<String, Object>> entities;
    private final long nextEntityId;

    /**
     * @param worldData   世界时间轴数据（simulationTick / totalGameSeconds / timeScale / gameSecondsPerRealSecond 等）
     * @param nations     国家数据列表
     * @param entities    动态实体数据列表（SHIP / STATION）
     * @param nextEntityId 下一个实体 ID 生成器值
     */
    public LoadWorldCommand(
            Map<String, Object> worldData,
            List<Map<String, Object>> nations,
            List<Map<String, Object>> entities,
            long nextEntityId) {
        super("loadWorld");
        this.worldData = worldData;
        this.nations = nations;
        this.entities = entities;
        this.nextEntityId = nextEntityId;
    }

    public Map<String, Object> getWorldData() {
        return worldData;
    }

    public List<Map<String, Object>> getNations() {
        return nations;
    }

    public List<Map<String, Object>> getEntities() {
        return entities;
    }

    public long getNextEntityId() {
        return nextEntityId;
    }
}
