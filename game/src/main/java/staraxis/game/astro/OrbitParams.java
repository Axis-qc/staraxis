package staraxis.game.astro;

/**
 * OrbitParams
 *
 * Holds the orbital parameters of a celestial body.
 * All distances are in astronomical units (AU) and angles in degrees.
 */
public class OrbitParams {
    /**
     * Semi-major axis in astronomical units (AU).
     */
    public double semiMajorAxisAU;

    /**
     * Eccentricity of the orbit (0=circle, <1=ellipse).
     */
    public double eccentricity;

    /**
     * Inclination of the orbit in degrees.
     */
    public double inclinationDeg;

    /**
     * Orbital period in game days.
     */
    public double orbitalPeriodDays;

    /**
     * Mean anomaly at epoch (t=0) in degrees.
     * Used to determine the body's position in its orbit at a given time.
     */
    public double meanAnomalyDegAtEpoch;
}
