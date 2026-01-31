package staraxis.game.astro;

/**
 * StarBody
 *
 * Represents a single star within a star system.
 */
public class StarBody {
    /**
     * Unique ID for this celestial body.
     */
    public long id;

    /**
     * The type ID of the star (e.g., "G_MAIN_SEQUENCE"), linking to StarTypeDef.
     */
    public String typeId;

    /**
     * Radius of the star in kilometers.
     */
    public double radiusKm;

    /**
     * Mass of the star in solar masses.
     */
    public double massSolar;

    /**
     * Surface temperature in Kelvin.
     */
    public int temperatureK;
}
