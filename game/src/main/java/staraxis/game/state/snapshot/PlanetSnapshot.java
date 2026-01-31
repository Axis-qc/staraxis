package staraxis.game.state.snapshot;

/**
 * PlanetSnapshot
 *
 * 行星的只读快照。
 */
public class PlanetSnapshot {
    public final long id;
    public final String typeId;
    public final double radiusKm;
    public final OrbitSnapshot orbit;
    public final double rotationPeriodHours;

    public PlanetSnapshot(long id, String typeId, double radiusKm, OrbitSnapshot orbit, double rotationPeriodHours) {
        this.id = id;
        this.typeId = typeId;
        this.radiusKm = radiusKm;
        this.orbit = orbit;
        this.rotationPeriodHours = rotationPeriodHours;
    }
}
