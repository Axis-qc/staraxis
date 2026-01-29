package staraxis.game.state;

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

    public WorldState(SimulationTime time, WorldMap worldMap) {
        this.time = time;
        this.worldMap = worldMap;
    }
}
