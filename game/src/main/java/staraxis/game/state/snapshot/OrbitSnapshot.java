package staraxis.game.state.snapshot;

/**
 * OrbitSnapshot
 *
 * 轨道参数的只读快照。
 */
public class OrbitSnapshot {
    public final double semiMajorAxisAU;
    public final double eccentricity;
    public final double orbitalPeriodDays;
    public final double meanAnomalyDegAtEpoch;

    public OrbitSnapshot(double semiMajorAxisAU, double eccentricity, double orbitalPeriodDays,
            double meanAnomalyDegAtEpoch) {
        this.semiMajorAxisAU = semiMajorAxisAU;
        this.eccentricity = eccentricity;
        this.orbitalPeriodDays = orbitalPeriodDays;
        this.meanAnomalyDegAtEpoch = meanAnomalyDegAtEpoch;
    }
}
