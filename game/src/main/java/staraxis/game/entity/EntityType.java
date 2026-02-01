package staraxis.game.entity;

/**
 * EntityType
 *
 * Defines the type of an entity in the game world, used for logic dispatching
 * and queries.
 */
public enum EntityType {
    /** A star, which can be an orbit center. */
    STAR,

    /** A planet, which orbits a center entity. */
    PLANET,

    /**
     * A logical entity representing the barycenter of a multi-star system, used as
     * an orbit center.
     */
    SYSTEM_BARYCENTER,

    /** A player or AI-controlled ship (for future use). */
    SHIP,

    /** A space station (for future use). */
    STATION
}
