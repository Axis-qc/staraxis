package staraxis.game.state.snapshot;

/**
 * OrbitSnapshot
 *
 * 轨道参数的只读快照。
 */
public class OrbitSnapshot {
    /** 轨道中心实体ID（orbitCenterEntityId）。 */
    public final long orbitCenterEntityId;

    public final double semiMajorAxisGU;
    public final double eccentricity;
    public final double orbitalPeriodDays;
    public final double meanAnomalyDegAtEpoch;

    public OrbitSnapshot(long orbitCenterEntityId, double semiMajorAxisGU, double eccentricity,
            double orbitalPeriodDays,
            double meanAnomalyDegAtEpoch) {
        this.orbitCenterEntityId = orbitCenterEntityId;
        this.semiMajorAxisGU = semiMajorAxisGU;
        this.eccentricity = eccentricity;
        this.orbitalPeriodDays = orbitalPeriodDays;
        this.meanAnomalyDegAtEpoch = meanAnomalyDegAtEpoch;
    }
}
