package staraxis.game.state.snapshot;

import staraxis.game.world.Vec2d;
import staraxis.game.world.hex.SectorCoord;

import java.util.List;

/**
 * SystemSnapshot
 *
 * 恒星系的只读快照。
 */
public class SystemSnapshot {
    public final long id;
    public final SectorCoord sectorCoord;
    public final Vec2d centerWorldGU;
    public final List<StarSnapshot> stars;
    public final List<PlanetSnapshot> planets;

    public SystemSnapshot(long id, SectorCoord sectorCoord, Vec2d centerWorldGU, List<StarSnapshot> stars,
            List<PlanetSnapshot> planets) {
        this.id = id;
        this.sectorCoord = sectorCoord;
        this.centerWorldGU = centerWorldGU;
        this.stars = stars;
        this.planets = planets;
    }
}
