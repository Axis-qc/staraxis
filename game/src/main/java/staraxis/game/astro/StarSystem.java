package staraxis.game.astro;

import staraxis.game.world.Vec2d;
import staraxis.game.world.hex.SectorCoord;

import java.util.ArrayList;
import java.util.List;

/**
 * StarSystem
 *
 * Represents a single star system, containing one or more stars and a set of
 * planets.
 */
public class StarSystem {
    /**
     * Unique ID for this star system.
     */
    public long id;

    /**
     * The sector this system belongs to.
     */
    public SectorCoord sectorCoord;

    /**
     * The center of the system in world coordinates (GU).
     * For single-star systems, this is the star's position.
     * For multi-star systems, this is the barycenter.
     */
    public Vec2d centerWorldGU;

    /**
     * List of stars in this system. Supports multi-star systems.
     */
    public final List<StarBody> stars = new ArrayList<>();

    /**
     * List of planets orbiting the system's barycenter or primary star.
     */
    public final List<PlanetBody> planets = new ArrayList<>();
}
