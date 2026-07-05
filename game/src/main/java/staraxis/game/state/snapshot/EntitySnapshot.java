package staraxis.game.state.snapshot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import staraxis.game.entity.EntityType;
import staraxis.game.space.SpacePosition;

/**
 * EntitySnapshot（实体快照）
 *
 * 作用：
 * - 游戏世界实体的一次性快照，通过 RealTimeWorldState / DailySettlementState 发布。
 * - 单机模式下 Java 客户端通过内存共享直接读权威状态（EntitySnapshot 仅用于渲染器读取恒星数据）。
 * - 多人联机模式下作为主→客的完整下行快照契约，远端 Java 客户端通过此 DTO 渲染世界。
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class EntitySnapshot {

    public final long entityId;
    public final EntityType entityType;
    public final long systemId;
    public final long parentEntityId;
    public final SpacePosition posWorldGU;
    public final String ownerNationId;
    /** 所属玩家ID（playerId）。无归属时为 null。 */
    public final String ownerPlayerId;
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
            SpacePosition posWorldGU,
            String ownerNationId,
            String ownerPlayerId,
            boolean isPublic,
            Object details) {
        this(entityId, entityType, systemId, parentEntityId, posWorldGU, ownerNationId, ownerPlayerId, isPublic,
                details, 0);
    }

    public EntitySnapshot(
            long entityId,
            EntityType entityType,
            long systemId,
            long parentEntityId,
            SpacePosition posWorldGU,
            String ownerNationId,
            String ownerPlayerId,
            boolean isPublic,
            Object details,
            int intelRequiredLevel) {
        this.entityId = entityId;
        this.entityType = entityType;
        this.systemId = systemId;
        this.parentEntityId = parentEntityId;
        this.posWorldGU = posWorldGU;
        this.ownerNationId = ownerNationId;
        this.ownerPlayerId = ownerPlayerId;
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

    /**
     * ShipDetails（舰船快照 DTO）
     *
     * 作用：EntitySnapshot.details 的 SHIP 类型载荷，用于向客户端展示舰船的运行时状态。
     *
     * 注意：
     * - 单机模式下 Java 客户端直接读 ShipBody 权威状态（内存共享），不读此 DTO。
     * - 但此 DTO 是未来多人联机中远端客户端（非主机玩家）获知舰船状态的唯一途径。
     *
     * TODO 多人联机：此为下行快照契约的一部分，即使单机无消费者亦不可删除。
     * TODO Phase 5+：maxSpeed/baseAcceleration 等字段已不再由 game 维护，后续客户端应通过
     *   ShipStatsCalculator 在本地计算或由服务端下发计算结果。
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class ShipDetails {
        public final java.util.Set<String> customFlags;
        public final double headingDeg;
        public final boolean isMoving;
        public final SpacePosition movementTarget;
        public final SpacePosition velocity;

        // 以下字段已不再由 game 引擎维护（Phase 5），保留用于旧客户端兼容。
        // 新客户端应忽略这些值。
        @Deprecated
        public final double maxSpeed;
        @Deprecated
        public final double baseAcceleration;
        @Deprecated
        public final double bowAccelerationBonus;
        @Deprecated
        public final double turnRate;
        @Deprecated
        public final double lateralSpeedPenalty;
        @Deprecated
        public final double reverseSpeedPenalty;

        /** 完整构造函数（含旧字段，保持 JSON 兼容）。 */
        public ShipDetails(
                java.util.Set<String> customFlags,
                double headingDeg,
                boolean isMoving,
                SpacePosition movementTarget,
                SpacePosition velocity,
                double maxSpeed,
                double baseAcceleration,
                double bowAccelerationBonus,
                double turnRate,
                double lateralSpeedPenalty,
                double reverseSpeedPenalty) {
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
        }

        /** 简化构造函数（旧字段使用默认值）。 */
        public ShipDetails(
                java.util.Set<String> customFlags,
                double headingDeg,
                boolean isMoving,
                SpacePosition movementTarget,
                SpacePosition velocity) {
            this(customFlags, headingDeg, isMoving, movementTarget, velocity,
                    20.0, 5.0, 5.0, 45.0, 0.6, 0.3);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SystemBarycenterDetails {
        public final boolean empty = true;
    }
}
