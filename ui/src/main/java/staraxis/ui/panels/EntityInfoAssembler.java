package staraxis.ui.panels;

import com.badlogic.gdx.graphics.Color;
import staraxis.game.astro.Habitability;
import staraxis.game.entity.EntityType;
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

    private EntityInfoAssembler() {
    }

    /**
     * 从快照中组装实体信息视图模型。
     *
     * @param entityId 实体 ID
     * @param rtState  实时快照（含舰船动态数据）
     * @param ds       低频基线快照（含恒星/行星基线数据）
     * @return 组装好的视图模型，实体未找到时返回 null
     */
    public static EntityInfoViewModel assemble(long entityId,
                                                RealTimeWorldState rtState,
                                                DailySettlementState ds) {
        if (entityId < 0) return null;

        EntitySnapshot snap = findSnapshot(entityId, rtState, ds);
        if (snap == null) return null;

        if (snap.details instanceof StarDetails sd) {
            return assembleStar(snap, sd);
        }
        if (snap.details instanceof PlanetDetails pd) {
            return assemblePlanet(snap, pd);
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

    private static EntityInfoViewModel assemblePlanet(EntitySnapshot snap, PlanetDetails pd) {
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

        return vm;
    }

    private static EntityInfoViewModel assembleShip(EntitySnapshot snap, ShipDetails sd) {
        String flags = sd.customFlags != null && !sd.customFlags.isEmpty()
                ? " " + String.join(",", sd.customFlags)
                : "";
        String status = sd.isMoving ? "移动中" : "停泊";
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
                        snap.posWorldGU.x(), snap.posWorldGU.y(), snap.posWorldGU.z()));
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