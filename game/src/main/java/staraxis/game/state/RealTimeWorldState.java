package staraxis.game.state;

import staraxis.game.world.Vec2d;
import staraxis.game.world.hex.SectorCoord;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RealTimeWorldState
 *
 * 实时世界状态（只读快照）：用于战斗、移动、即时事件等实时系统；以及需要即时数据的 UI 展示。
 *
 * 更新方式：每个 simulationTick 结束时，模拟层在 inactive 缓冲中全量填充后 swap 发布为 active。
 */
public class RealTimeWorldState {

    public long simulationTick;

    public int gameDatetimeDay;

    public double accGameHoursInDay;

    public int worldRadius;

    /**
     * 星区中心点缓存：key 为 SectorCoord（axial q,r）。
     */
    private final Map<SectorCoord, Vec2d> sectorCentersWorldGU = new LinkedHashMap<>();

    public RealTimeWorldState() {
    }

    /**
     * 全量填充前调用：清空并准备写入。
     */
    public void resetForFill() {
        simulationTick = 0;
        gameDatetimeDay = 0;
        accGameHoursInDay = 0;
        worldRadius = 0;
        sectorCentersWorldGU.clear();
    }

    /**
     * 模拟层填充：写入一个星区中心点。
     */
    public void putSectorCenter(SectorCoord coord, Vec2d centerWorldGU) {
        sectorCentersWorldGU.put(coord, centerWorldGU);
    }

    /**
     * 只读视图：按 id（SectorCoord）索引。
     */
    public Map<SectorCoord, Vec2d> getSectorCentersWorldGUView() {
        return Collections.unmodifiableMap(sectorCentersWorldGU);
    }
}
