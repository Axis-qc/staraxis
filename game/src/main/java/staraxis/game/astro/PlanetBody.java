package staraxis.game.astro;

/**
 * PlanetBody
 *
 * Represents a single planet within a star system.
 */
public class PlanetBody {
    /**
     * Unique ID for this celestial body.
     */
    public long id;

    /**
     * The type ID of the planet (e.g., "TERRESTRIAL"), linking to PlanetTypeDef.
     */
    public String typeId;

    /**
     * Radius of the planet in kilometers.
     */
    public double radiusKm;

    /**
     * Orbital parameters of the planet.
     */
    public OrbitParams orbit;

    /**
     * Rotation period in game hours.
     */
    public double rotationPeriodHours;
}
