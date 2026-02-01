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
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    /**
     * 特定于类型的详细信息。
     * 使用 Jackson 的多态序列化，根据 entityType 自动选择正确的子类。
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXTERNAL_PROPERTY, property = "entityType")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = StarDetails.class, name = "STAR"),
            @JsonSubTypes.Type(value = PlanetDetails.class, name = "PLANET"),
            @JsonSubTypes.Type(value = SystemBarycenterDetails.class, name = "SYSTEM_BARYCENTER")
    })
    public final Object details;

    public EntitySnapshot(long entityId, EntityType entityType, long systemId, long parentEntityId,
            SectorCoord sectorCoord, Vec2d posWorldGU, Object details) {
        this.entityId = entityId;
        this.entityType = entityType;
        this.systemId = systemId;
        this.parentEntityId = parentEntityId;
        this.sectorCoord = sectorCoord;
        this.posWorldGU = posWorldGU;
        this.details = details;
    }

    // --- Details Payloads ---

    public static class StarDetails {
        public final String starTypeId;
        public final double radiusGU;
        public final double massSolar;
        public final int temperatureK;

        public StarDetails(String starTypeId, double radiusGU, double massSolar, int temperatureK) {
            this.starTypeId = starTypeId;
            this.radiusGU = radiusGU;
            this.massSolar = massSolar;
            this.temperatureK = temperatureK;
        }
    }

    public static class PlanetDetails {
        public final String planetTypeId;
        public final double radiusGU;
        public final double rotationPeriodHours;
        public final OrbitSnapshot orbit;

        public PlanetDetails(String planetTypeId, double radiusGU, double rotationPeriodHours, OrbitSnapshot orbit) {
            this.planetTypeId = planetTypeId;
            this.radiusGU = radiusGU;
            this.rotationPeriodHours = rotationPeriodHours;
            this.orbit = orbit;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SystemBarycenterDetails {
        public final boolean empty = true;
    }
}
