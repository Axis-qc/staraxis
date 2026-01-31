package staraxis.game.state.snapshot;

/**
 * StarSnapshot
 *
 * 恒星的只读快照。
 */
public class StarSnapshot {
    public final long id;
    public final String typeId;
    public final double radiusKm;
    public final double massSolar;
    public final int temperatureK;

    public StarSnapshot(long id, String typeId, double radiusKm, double massSolar, int temperatureK) {
        this.id = id;
        this.typeId = typeId;
        this.radiusKm = radiusKm;
        this.massSolar = massSolar;
        this.temperatureK = temperatureK;
    }
}
