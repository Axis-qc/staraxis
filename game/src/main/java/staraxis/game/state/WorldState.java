package staraxis.game.state;

import staraxis.game.astro.AstroData;
import staraxis.game.sim.SimulationTime;
import staraxis.game.world.WorldMap;

/**
 * WorldState
 *
 * 游戏运行时的唯一权威世界状态容器（只允许模拟层读写）。
 */
public class WorldState {

    public final SimulationTime time;

    public final WorldMap worldMap;

    /**
     * 权威星体数据（恒星系、恒星、行星等）：仅允许模拟层读写。
     */
    public final AstroData astro;

    public WorldState(SimulationTime time, WorldMap worldMap, AstroData astro) {
        this.time = time;
        this.worldMap = worldMap;
        this.astro = astro;
    }
}
