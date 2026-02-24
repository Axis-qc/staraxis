package staraxis.game.state.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import staraxis.game.entity.EntityType;
import staraxis.game.world.Vec2d;
import staraxis.game.world.hex.SectorCoord;

/**
 * EntitySnapshot（实体快照）
 *
 * 游戏世界中所有实体的统一、扁平化快照结构。
 * 用于将后端权威状态高效、清晰地传递给前端。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class EntitySnapshot {

    /** 实体唯一ID（entityId）。 */
    public final long entityId;

    /** 实体类型。 */
    public final EntityType entityType;

    /** 所属恒星系ID（systemId）。 */
    public final long systemId;

    /** 父实体ID（parentEntityId）。 */
    public final long parentEntityId;

    /** 所在星区坐标（sectorCoord）。 */
    public final SectorCoord sectorCoord;

    /** 世界坐标（posWorldGU）。 */
    public final Vec2d posWorldGU;

    /** 所属国家/文明 ID（ownerNationId）。无归属时为 null。 */
    public final String ownerNationId;

    /**
     * 是否为公开可见数据（isPublic）。
     *
     * 说明：
     * - true：公开基础数据（例如恒星/行星等基础天文数据）。
     * - false：私有/情报数据（例如舰船、城市、太空建筑等）。
     */
    public final boolean isPublic;

    /**
     * 特定于类型的详细信息。
     * 使用 Jackson 的多态序列化，根据 entityType 自动选择正确的子类。
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "entityType")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = StarDetails.class, name = "STAR"),
            @JsonSubTypes.Type(value = PlanetDetails.class, name = "PLANET"),
            @JsonSubTypes.Type(value = SystemBarycenterDetails.class, name = "SYSTEM_BARYCENTER"),
            @JsonSubTypes.Type(value = ShipDetails.class, name = "SHIP")
    })
    public final Object details;

    public EntitySnapshot(long entityId, EntityType entityType, long systemId, long parentEntityId,
            SectorCoord sectorCoord, Vec2d posWorldGU, String ownerNationId, boolean isPublic, Object details) {
        this.entityId = entityId;
        this.entityType = entityType;
        this.systemId = systemId;
        this.parentEntityId = parentEntityId;
        this.sectorCoord = sectorCoord;
        this.posWorldGU = posWorldGU;
        this.ownerNationId = ownerNationId;
        this.isPublic = isPublic;
        this.details = details;
    }

    // --- Details Payloads ---

    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class StarDetails {
        public final String starTypeId;
        public final double radiusGU;
        public final double massSolar;
        public final int temperatureK;
        public final String description;
        public final String surfaceTexturePath;

        public StarDetails(String starTypeId, double radiusGU, double massSolar, int temperatureK,
                String description, String surfaceTexturePath) {
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

        public SurfaceRegionSnapshot(long regionId, String regionType, String name, double surfacePercentage,
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

        /** 是否为所属国家的首都星球喵。 */
        public final boolean isCapital;

        public final long orbitCenterEntityId;
        public final double semiMajorAxisGU;
        public final double eccentricity;
        public final double inclinationDeg;
        public final double periapsisArgDeg;
        public final double orbitalPeriodDays;
        public final double meanAnomalyDegAtEpoch;

        public PlanetDetails(String planetTypeId, double radiusGU, double rotationPeriodHours,
                String surfaceTexturePath, boolean isCapital,
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
    public static class SystemBarycenterDetails {
        public final boolean empty = true;
    }
}
