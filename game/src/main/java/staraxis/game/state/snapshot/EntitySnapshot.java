package staraxis.game.state.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import staraxis.game.entity.EntityType;
import staraxis.game.world.Vec2d;
import staraxis.game.world.hex.SectorCoord;

@JsonInclude(JsonInclude.Include.ALWAYS)
public class EntitySnapshot {

    public final long entityId;
    public final EntityType entityType;
    public final long systemId;
    public final long parentEntityId;
    public final SectorCoord sectorCoord;
    public final Vec2d posWorldGU;
    public final String ownerNationId;
    public final boolean isPublic;

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "entityType")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = StarDetails.class, name = "STAR"),
            @JsonSubTypes.Type(value = PlanetDetails.class, name = "PLANET"),
            @JsonSubTypes.Type(value = SystemBarycenterDetails.class, name = "SYSTEM_BARYCENTER"),
            @JsonSubTypes.Type(value = ShipDetails.class, name = "SHIP")
    })
    public final Object details;

    public final int intelRequiredLevel;

    public EntitySnapshot(
            long entityId,
            EntityType entityType,
            long systemId,
            long parentEntityId,
            SectorCoord sectorCoord,
            Vec2d posWorldGU,
            String ownerNationId,
            boolean isPublic,
            Object details) {
        this(entityId, entityType, systemId, parentEntityId, sectorCoord, posWorldGU, ownerNationId, isPublic,
                details, 0);
    }

    public EntitySnapshot(
            long entityId,
            EntityType entityType,
            long systemId,
            long parentEntityId,
            SectorCoord sectorCoord,
            Vec2d posWorldGU,
            String ownerNationId,
            boolean isPublic,
            Object details,
            int intelRequiredLevel) {
        this.entityId = entityId;
        this.entityType = entityType;
        this.systemId = systemId;
        this.parentEntityId = parentEntityId;
        this.sectorCoord = sectorCoord;
        this.posWorldGU = posWorldGU;
        this.ownerNationId = ownerNationId;
        this.isPublic = isPublic;
        this.details = details;
        this.intelRequiredLevel = Math.max(0, Math.min(10, intelRequiredLevel));
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class StarDetails {
        public final String starTypeId;
        public final double radiusGU;
        public final double massSolar;
        public final int temperatureK;
        public final String description;
        public final String surfaceTexturePath;

        public StarDetails(
                String starTypeId,
                double radiusGU,
                double massSolar,
                int temperatureK,
                String description,
                String surfaceTexturePath) {
            this.starTypeId = starTypeId;
            this.radiusGU = radiusGU;
            this.massSolar = massSolar;
            this.temperatureK = temperatureK;
            this.description = description;
            this.surfaceTexturePath = surfaceTexturePath;
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class SurfaceRegionSnapshot {
        public final long regionId;
        public final String regionType;
        public final String name;
        public final double surfacePercentage;
        public final double developableSpaceRatio;

        public SurfaceRegionSnapshot(
                long regionId,
                String regionType,
                String name,
                double surfacePercentage,
                double developableSpaceRatio) {
            this.regionId = regionId;
            this.regionType = regionType;
            this.name = name;
            this.surfacePercentage = surfacePercentage;
            this.developableSpaceRatio = developableSpaceRatio;
        }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class PlanetDetails {
        public final String planetTypeId;
        public final double radiusGU;
        public final double rotationPeriodHours;
        public final String surfaceTexturePath;
        public final boolean isCapital;
        public final long orbitCenterEntityId;
        public final double semiMajorAxisGU;
        public final double eccentricity;
        public final double inclinationDeg;
        public final double periapsisArgDeg;
        public final double orbitalPeriodDays;
        public final double meanAnomalyDegAtEpoch;

        public PlanetDetails(
                String planetTypeId,
                double radiusGU,
                double rotationPeriodHours,
                String surfaceTexturePath,
                boolean isCapital,
                long orbitCenterEntityId,
                double semiMajorAxisGU,
                double eccentricity,
                double inclinationDeg,
                double periapsisArgDeg,
                double orbitalPeriodDays,
                double meanAnomalyDegAtEpoch) {
            this.planetTypeId = planetTypeId;
            this.radiusGU = radiusGU;
            this.rotationPeriodHours = rotationPeriodHours;
            this.surfaceTexturePath = surfaceTexturePath;
            this.isCapital = isCapital;
            this.orbitCenterEntityId = orbitCenterEntityId;
            this.semiMajorAxisGU = semiMajorAxisGU;
            this.eccentricity = eccentricity;
            this.inclinationDeg = inclinationDeg;
            this.periapsisArgDeg = periapsisArgDeg;
            this.orbitalPeriodDays = orbitalPeriodDays;
            this.meanAnomalyDegAtEpoch = meanAnomalyDegAtEpoch;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class MovementCommandDetails {
        public final String commandType;
        public final Vec2d targetPosition;
        public final Vec2d startPosition;
        public final Vec2d startVelocity;
        public final double startHeadingDeg;
        public final double startGameSeconds;
        public final int startSimulationTick;
        public final double maxSpeed;
        public final double baseAcceleration;
        public final double bowAccelerationBonus;
        public final double turnRate;
        public final double lateralSpeedPenalty;
        public final double reverseSpeedPenalty;

        public MovementCommandDetails(
                String commandType,
                Vec2d targetPosition,
                Vec2d startPosition,
                Vec2d startVelocity,
                double startHeadingDeg,
                double startGameSeconds,
                int startSimulationTick,
                double maxSpeed,
                double baseAcceleration,
                double bowAccelerationBonus,
                double turnRate,
                double lateralSpeedPenalty,
                double reverseSpeedPenalty) {
            this.commandType = commandType;
            this.targetPosition = targetPosition;
            this.startPosition = startPosition;
            this.startVelocity = startVelocity;
            this.startHeadingDeg = startHeadingDeg;
            this.startGameSeconds = startGameSeconds;
            this.startSimulationTick = startSimulationTick;
            this.maxSpeed = maxSpeed;
            this.baseAcceleration = baseAcceleration;
            this.bowAccelerationBonus = bowAccelerationBonus;
            this.turnRate = turnRate;
            this.lateralSpeedPenalty = lateralSpeedPenalty;
            this.reverseSpeedPenalty = reverseSpeedPenalty;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class ShipDetails {
        public final java.util.Set<String> customFlags;
        public final double headingDeg;
        public final boolean isMoving;
        public final Vec2d movementTarget;
        public final Vec2d velocity;
        public final double maxSpeed;
        public final double baseAcceleration;
        public final double bowAccelerationBonus;
        public final double turnRate;
        public final double lateralSpeedPenalty;
        public final double reverseSpeedPenalty;
        public final MovementCommandDetails movementCommand;

        public ShipDetails(
                java.util.Set<String> customFlags,
                double headingDeg,
                boolean isMoving,
                Vec2d movementTarget,
                Vec2d velocity,
                double maxSpeed,
                double baseAcceleration,
                double bowAccelerationBonus,
                double turnRate,
                double lateralSpeedPenalty,
                double reverseSpeedPenalty,
                MovementCommandDetails movementCommand) {
            this.customFlags = customFlags == null ? java.util.Set.of() : java.util.Set.copyOf(customFlags);
            this.headingDeg = headingDeg;
            this.isMoving = isMoving;
            this.movementTarget = movementTarget;
            this.velocity = velocity;
            this.maxSpeed = maxSpeed;
            this.baseAcceleration = baseAcceleration;
            this.bowAccelerationBonus = bowAccelerationBonus;
            this.turnRate = turnRate;
            this.lateralSpeedPenalty = lateralSpeedPenalty;
            this.reverseSpeedPenalty = reverseSpeedPenalty;
            this.movementCommand = movementCommand;
        }

        public ShipDetails(java.util.Set<String> customFlags, double headingDeg) {
            this(customFlags, headingDeg, false, null, null, 20.0, 5.0, 5.0, 45.0, 0.6, 0.3, null);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SystemBarycenterDetails {
        public final boolean empty = true;
    }
}
