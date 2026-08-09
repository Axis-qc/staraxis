package staraxis.ui.panels;

import com.badlogic.gdx.graphics.Color;
import staraxis.game.astro.Habitability;
import staraxis.game.astro.PlanetBody;
import staraxis.game.entity.EntityType;
import staraxis.game.ship.ShipDesign;
import staraxis.game.state.DailySettlementState;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.state.snapshot.EntitySnapshot.PlanetDetails;
import staraxis.game.state.snapshot.EntitySnapshot.ShipDetails;
import staraxis.game.state.snapshot.EntitySnapshot.StarDetails;

/**
 * EntityInfoAssembler（实体信息组装器）。
 *
 * 从快照数据中读取指定实体的信息，组装为 {@link EntityInfoViewModel}，
 * 供 {@link EntitySummaryPanel}（左下摘要区）和 {@link EntityInfoPanel}（详细窗口）消费。
 *
 * 职责边界：只做数据读取和组装，不存储状态、不修改快照。
 */
public final class EntityInfoAssembler {

    /** 「移动」指令 id（摘要面板指令网格上抛）喵 */
    public static final String ACTION_MOVE = "move";

    /** 「殖民」指令 id（摘要面板指令网格上抛）喵 */
    public static final String ACTION_COLONIZE = "colonize";

    /** 「移动」指令显示文案喵 */
    private static final String LABEL_MOVE = "移动";

    /** 「殖民」指令显示文案喵 */
    private static final String LABEL_COLONIZE = "殖民";

    private EntityInfoAssembler() {
    }

    /**
     * 从快照中组装实体信息视图模型（无选中舰船，殖民按钮按不可用处理）。
     *
     * @param entityId 实体 ID
     * @param rtState  实时快照（含舰船动态数据）
     * @param ds       低频基线快照（含恒星/行星基线数据）
     * @return 组装好的视图模型，实体未找到时返回 null
     */
    public static EntityInfoViewModel assemble(long entityId,
                                                RealTimeWorldState rtState,
                                                DailySettlementState ds) {
        return assemble(entityId, rtState, ds, -1);
    }

    /**
     * 从快照中组装实体信息视图模型。
     *
     * @param entityId      实体 ID
     * @param rtState       实时快照（含舰船动态数据）
     * @param ds            低频基线快照（含恒星/行星基线数据）
     * @param selectedShipId 当前选中的舰船实体 ID（无选中舰船时为 -1），
     *                       用于判断行星「殖民」按钮是否因殖民舰选中而可用
     * @return 组装好的视图模型，实体未找到时返回 null
     */
    public static EntityInfoViewModel assemble(long entityId,
                                                RealTimeWorldState rtState,
                                                DailySettlementState ds,
                                                long selectedShipId) {
        if (entityId < 0) return null;

        EntitySnapshot snap = findSnapshot(entityId, rtState, ds);
        if (snap == null) return null;

        if (snap.details instanceof StarDetails sd) {
            return assembleStar(snap, sd);
        }
        if (snap.details instanceof PlanetDetails pd) {
            return assemblePlanet(snap, pd, rtState, ds, selectedShipId);
        }
        if (snap.details instanceof ShipDetails shipDet) {
            return assembleShip(snap, shipDet);
        }

        // 未知实体类型，返回最小信息
        return new EntityInfoViewModel()
                .title("实体 #" + entityId);
    }

    private static EntityInfoViewModel assembleStar(EntitySnapshot snap, StarDetails sd) {
        String type = sd.starTypeId != null ? sd.starTypeId : "?";
        return new EntityInfoViewModel()
                .title("恒星 #" + snap.entityId)
                .typeLabel("光谱 " + type, new Color(1f, 0.85f, 0.4f, 1f))
                .summary("类型", type)
                .summary("温度", sd.temperatureK + " K")
                .summary("半径", String.format("%.0f GU", sd.radiusGU))
                .detail("类型", type)
                .detail("温度", sd.temperatureK + " K")
                .detail("半径", String.format("%.0f GU", sd.radiusGU));
    }

    private static EntityInfoViewModel assemblePlanet(EntitySnapshot snap, PlanetDetails pd,
                                                       RealTimeWorldState rtState,
                                                       DailySettlementState ds,
                                                       long selectedShipId) {
        String typeName = pd.planetTypeId != null ? pd.planetTypeId : "?";
        String entityKind;
        if (snap.entityType == EntityType.PLANET) {
            entityKind = "行星";
        } else if (snap.entityType == EntityType.MOON) {
            entityKind = "卫星";
        } else if (snap.entityType == EntityType.ASTEROID) {
            entityKind = "小行星";
        } else {
            entityKind = "天体";
        }

        // 可殖民性标签：按等级着色（绿=宜居，灰=不宜居）喵
        Habitability hab = pd.habitability != null ? pd.habitability : Habitability.INHOSPITABLE;
        Color habColor = switch (hab) {
            case HABITABLE, PARADISE -> new Color(0.4f, 0.9f, 0.5f, 1f);
            case TOUGH -> new Color(0.9f, 0.8f, 0.4f, 1f);
            case HOSTILE -> new Color(0.9f, 0.6f, 0.3f, 1f);
            default -> new Color(0.6f, 0.6f, 0.6f, 1f);
        };
        String habLabel = switch (hab) {
            case PARADISE -> "天堂";
            case HABITABLE -> "宜居";
            case TOUGH -> "艰难";
            case HOSTILE -> "恶劣";
            default -> "不宜居";
        };

        EntityInfoViewModel vm = new EntityInfoViewModel()
                .title(entityKind + " #" + snap.entityId)
                .typeLabel(typeName, new Color(0.5f, 0.8f, 1f, 1f))
                .summary("类型", typeName)
                .summary("可殖民", habLabel)
                .summary("半径", String.format("%.0f GU", pd.radiusGU))
                .summary("轨道", String.format("%.0f GU", pd.semiMajorAxisGU))
                .detail("类型", typeName)
                .detail("可殖民性", habLabel, habColor)
                .detail("半径", String.format("%.0f GU", pd.radiusGU))
                .detail("轨道半径", String.format("%.0f GU", pd.semiMajorAxisGU))
                .detail("轨道周期", pd.orbitalPeriodDays > 0
                        ? String.format("%.1f 天", pd.orbitalPeriodDays)
                        : "?")
                .detail("自转周期", pd.rotationPeriodHours > 0
                        ? String.format("%.1f 小时", pd.rotationPeriodHours)
                        : "?");

        // 地表信息预披露：大陆数量 + 已识别资源种类数（不披露具体储量，需探索）喵
        if (pd.continentCount > 0) {
            vm.summary("大陆", pd.continentCount + " 块");
            vm.detail("大陆数量", pd.continentCount + " 块");
        }
        if (pd.discoveredResourceTypeCount > 0) {
            vm.summary("资源", pd.discoveredResourceTypeCount + " 种已识别");
            vm.detail("已识别资源种类", pd.discoveredResourceTypeCount + " 种（储量需探索）");
        }

        // TODO Phase 2.3: gravity 和 temperatureK 字段待 PlanetDetails 扩展后接入
        if (pd.isCapital) {
            vm.summary("首都", "是");
            vm.detail("首都", "是");
        }

        // 殖民指令：宜居 + 无主 + 无城市的行星显示；选中殖民舰时才可用，否则置灰喵
        if (isColonizablePlanet(snap, pd, ds)) {
            vm.action(ACTION_COLONIZE, LABEL_COLONIZE,
                    isColonyShipSelected(rtState, ds, selectedShipId));
        }

        return vm;
    }

    private static EntityInfoViewModel assembleShip(EntitySnapshot snap, ShipDetails sd) {
        String flags = sd.customFlags != null && !sd.customFlags.isEmpty()
                ? " " + String.join(",", sd.customFlags)
                : "";
        String status = sd.isMoving ? "移动中" : "停泊";
        // 移动指令：仅舰船显示；有归属国家（ownerNationId）时可用喵
        boolean canMove = snap.entityType == EntityType.SHIP && snap.ownerNationId != null;
        return new EntityInfoViewModel()
                .title("舰船 #" + snap.entityId)
                .typeLabel(status, sd.isMoving ? new Color(0.4f, 0.8f, 1f, 1f) : new Color(0.6f, 0.6f, 0.6f, 1f))
                .summary("状态", status + flags)
                .summary("位置", String.format("(%.0f, %.0f, %.0f)",
                        snap.posWorldGU.x(), snap.posWorldGU.y(), snap.posWorldGU.z()))
                .detail("实体ID", String.valueOf(snap.entityId))
                .detail("归属", snap.ownerNationId != null ? snap.ownerNationId : "无")
                .detail("状态", status)
                .detail("位置", String.format("(%.0f, %.0f, %.0f)",
                        snap.posWorldGU.x(), snap.posWorldGU.y(), snap.posWorldGU.z()))
                .action(ACTION_MOVE, LABEL_MOVE, canMove);
    }

    /**
     * 行星是否满足殖民的展示条件（宜居类型 + 无主 + 无城市）喵。
     * 仅控制按钮是否显示，是否可点击由殖民舰选中决定。
     */
    private static boolean isColonizablePlanet(EntitySnapshot snap, PlanetDetails pd,
                                                DailySettlementState ds) {
        if (pd.planetTypeId == null) return false;
        if (!PlanetBody.HABITABLE_PLANET_TYPE_IDS.contains(pd.planetTypeId)) return false;
        if (snap.ownerNationId != null) return false;
        // 行星已有城市（地表快照 cities 非空）时不可殖民喵
        if (ds != null && ds.planetSurfacesByPlanetId != null) {
            var surface = ds.planetSurfacesByPlanetId.get(snap.entityId);
            if (surface != null && surface.cities != null && !surface.cities.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 当前选中的舰船是否为殖民舰（customFlags 含 {@link ShipDesign#FLAG_COLONY}）喵。
     * 未传选中舰船 ID、选中的不是舰船或非殖民舰时返回 false（殖民按钮置灰）。
     */
    private static boolean isColonyShipSelected(RealTimeWorldState rtState,
                                                DailySettlementState ds,
                                                long selectedShipId) {
        if (selectedShipId < 0) return false;
        EntitySnapshot shipSnap = findSnapshot(selectedShipId, rtState, ds);
        if (shipSnap == null || shipSnap.entityType != EntityType.SHIP) return false;
        if (!(shipSnap.details instanceof ShipDetails sd)) return false;
        return sd.customFlags != null && sd.customFlags.contains(ShipDesign.FLAG_COLONY);
    }

    /**
     * 从实时快照和基线快照中查找指定实体。
     * 优先查实时快照（舰船），回退到基线快照（恒星/行星）。
     */
    private static EntitySnapshot findSnapshot(long entityId,
                                                RealTimeWorldState rtState,
                                                DailySettlementState ds) {
        // 1. 实时快照（舰船等动态实体）
        if (rtState != null) {
            for (var entry : rtState.getEntitySnapshotsBySystemView().entrySet()) {
                for (EntitySnapshot snap : entry.getValue()) {
                    if (snap.entityId == entityId) return snap;
                }
            }
        }
        // 2. 基线快照（恒星/行星等静态实体）
        if (ds != null && ds.publicEntityBaselinesBySectorKey != null) {
            for (var entry : ds.publicEntityBaselinesBySectorKey.entrySet()) {
                for (EntitySnapshot snap : entry.getValue()) {
                    if (snap.entityId == entityId) return snap;
                }
            }
        }
        return null;
    }
}