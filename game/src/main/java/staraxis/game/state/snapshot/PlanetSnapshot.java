package staraxis.game.state.snapshot;

/**
 * PlanetSnapshot
 *
 * 行星的只读快照。
 */
public class PlanetSnapshot {
    public final long id;
    public final String typeId;
    public final double radiusGU;
    public final OrbitSnapshot orbit;
    public final double rotationPeriodHours;

    public PlanetSnapshot(long id, String typeId, double radiusGU, OrbitSnapshot orbit, double rotationPeriodHours) {
        this.id = id;
        this.typeId = typeId;
        this.radiusGU = radiusGU;
        this.orbit = orbit;
        this.rotationPeriodHours = rotationPeriodHours;
    }
}
