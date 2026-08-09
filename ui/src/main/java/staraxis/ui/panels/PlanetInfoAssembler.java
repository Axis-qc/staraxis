package staraxis.ui.panels;

import com.badlogic.gdx.graphics.Color;
import staraxis.game.astro.Habitability;
import staraxis.game.entity.EntityType;
import staraxis.game.industry.CargoTransfer;
import staraxis.game.industry.ProcessingFacility;
import staraxis.game.industry.ProductionSettlementService;
import staraxis.game.industry.RecipeProcessor;
import staraxis.game.industry.RecipeRepository;
import staraxis.game.industry.ResourceExtractionFacility;
import staraxis.game.industry.SubstanceId;
import staraxis.game.state.DailySettlementState;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.state.snapshot.EntitySnapshot.PlanetDetails;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * PlanetInfoAssembler（行星信息组装器）喵。
 *
 * 组合行星基础快照（{@link PlanetDetails}）与日结算快照中的地表区域/城市/工业/物流数据
 * （{@link DailySettlementState#planetSurfacesByPlanetId}），组装为
 * {@link PlanetInfoViewModel} 供 {@link PlanetInfoPanel} 四分页消费。
 *
 * 工业/物流数据（G2.7/G2.8）：
 * - Industry 页：本地库存（容量/已用/物质数量/预留数量）、采集设施、加工设施
 *   （配方/进度/状态/失败原因）、最近结算产出（{@link DailySettlementState.SettlementReportDailySnapshot}）。
 * - Logistics 页：与该行星库存相关的在途运输（transferId/源目标库存/货物/状态）。
 * 所有展示值均格式化为字符串（子类快照仅在本类内读取，不暴露为可变 UI 状态）。
 *
 * 职责边界：只做数据读取和组装，不存储状态、不修改快照、不读 WorldState。
 * 快照缺失/行星不存在时返回带 missing 标记的空态 VM（永不返回 null），保证稳定空态。
 */
public final class PlanetInfoAssembler {

    private PlanetInfoAssembler() {
    }

    /** 可殖民性标签颜色：绿色系（宜居）喵 */
    private static final Color HAB_GREEN = new Color(0.4f, 0.9f, 0.5f, 1f);
    /** 可殖民性标签颜色：黄色系（艰难）喵 */
    private static final Color HAB_YELLOW = new Color(0.9f, 0.8f, 0.4f, 1f);
    /** 可殖民性标签颜色：橙红系（恶劣）喵 */
    private static final Color HAB_ORANGE = new Color(0.9f, 0.6f, 0.3f, 1f);
    /** 可殖民性标签颜色：灰色系（不宜居）喵 */
    private static final Color HAB_GRAY = new Color(0.6f, 0.6f, 0.6f, 1f);
    /** 类型标签颜色：天蓝色喵 */
    private static final Color TYPE_BLUE = new Color(0.5f, 0.8f, 1f, 1f);
    /** 首都城市行颜色（与 UiTheme.warning #F59E0B 对齐）喵 */
    private static final Color CAPITAL_COLOR = new Color(0.96f, 0.62f, 0.04f, 1f);
    /** 工业/物流状态颜色：运行/成功（绿色系）喵 */
    private static final Color STATUS_RUNNING = new Color(0.4f, 0.9f, 0.5f, 1f);
    /** 工业/物流状态颜色：阻塞/失败（橙色系，与 UiTheme.warning #F59E0B 对齐）喵 */
    private static final Color STATUS_WARNING = new Color(0.96f, 0.62f, 0.04f, 1f);
    /** 工业/物流状态颜色：运输中/信息（蓝色系）喵 */
    private static final Color STATUS_INFO = new Color(0.5f, 0.8f, 1f, 1f);

    /**
     * 从快照中组装行星信息视图模型。
     *
     * @param planetEntityId 行星实体 ID
     * @param rtState        实时快照（行星动态数据，当前仅用于查找实体）
     * @param ds             低频基线快照（行星基线 + 地表区域 + 城市 + 工业/物流）
     * @return 组装好的视图模型；行星不存在/快照缺失时返回带 missing 标记的空态 VM，永不返回 null
     */
    public static PlanetInfoViewModel assemble(long planetEntityId,
                                               RealTimeWorldState rtState,
                                               DailySettlementState ds) {
        PlanetInfoViewModel vm = new PlanetInfoViewModel();
        vm.planetEntityId = planetEntityId;

        EntitySnapshot snap = findPlanetSnapshot(planetEntityId, rtState, ds);
        PlanetDetails pd = (snap != null && snap.details instanceof PlanetDetails d) ? d : null;
        if (snap == null || pd == null) {
            // 行星不存在或快照缺失：稳定空态，各页均为明确空态行喵
            vm.missing = true;
            vm.title = "行星 #" + planetEntityId;
            vm.overviewFields.add(emptyRow(PlanetInfoViewModel.MISSING_PLANET_TEXT));
            vm.colonyFields.add(emptyRow(PlanetInfoViewModel.EMPTY_CITY_TEXT));
            vm.industryFields.add(emptyRow(PlanetInfoViewModel.EMPTY_INDUSTRY_TEXT));
            vm.logisticsFields.add(emptyRow(PlanetInfoViewModel.EMPTY_LOGISTICS_TEXT));
            return vm;
        }

        assembleBase(vm, snap, pd);
        assembleSurface(vm, planetEntityId, ds);
        return vm;
    }

    /** 组装行星基础概览（类型/可殖民性/轨道等）喵 */
    private static void assembleBase(PlanetInfoViewModel vm, EntitySnapshot snap, PlanetDetails pd) {
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

        Habitability hab = pd.habitability != null ? pd.habitability : Habitability.INHOSPITABLE;
        Color habColor = switch (hab) {
            case HABITABLE, PARADISE -> HAB_GREEN;
            case TOUGH -> HAB_YELLOW;
            case HOSTILE -> HAB_ORANGE;
            default -> HAB_GRAY;
        };
        String habLabel = switch (hab) {
            case PARADISE -> "天堂";
            case HABITABLE -> "宜居";
            case TOUGH -> "艰难";
            case HOSTILE -> "恶劣";
            default -> "不宜居";
        };

        vm.title = entityKind + " #" + snap.entityId;
        vm.typeLabel = typeName;
        vm.typeColor = TYPE_BLUE;

        vm.overviewFields.add(new EntityInfoViewModel.FieldEntry("类型", typeName));
        vm.overviewFields.add(new EntityInfoViewModel.FieldEntry("可殖民性", habLabel, habColor));
        vm.overviewFields.add(new EntityInfoViewModel.FieldEntry("半径", String.format("%.0f GU", pd.radiusGU)));
        vm.overviewFields.add(new EntityInfoViewModel.FieldEntry("轨道半径", String.format("%.0f GU", pd.semiMajorAxisGU)));
        vm.overviewFields.add(new EntityInfoViewModel.FieldEntry("轨道周期", pd.orbitalPeriodDays > 0
                ? String.format("%.1f 天", pd.orbitalPeriodDays) : "?"));
        vm.overviewFields.add(new EntityInfoViewModel.FieldEntry("自转周期", pd.rotationPeriodHours > 0
                ? String.format("%.1f 小时", pd.rotationPeriodHours) : "?"));

        if (pd.continentCount > 0) {
            vm.overviewFields.add(new EntityInfoViewModel.FieldEntry("大陆数量", pd.continentCount + " 块"));
        }
        if (pd.discoveredResourceTypeCount > 0) {
            vm.overviewFields.add(new EntityInfoViewModel.FieldEntry("已识别资源种类",
                    pd.discoveredResourceTypeCount + " 种（储量需探索）"));
        }
        if (pd.isCapital) {
            vm.overviewFields.add(new EntityInfoViewModel.FieldEntry("首都", "是"));
        }
    }

    /** 组装地表区域、城市与工业/物流数据（均来自该行星的日结算快照条目）喵 */
    private static void assembleSurface(PlanetInfoViewModel vm, long planetEntityId, DailySettlementState ds) {
        DailySettlementState.PlanetSurfaceDailySnapshot surface = null;
        if (ds != null && ds.planetSurfacesByPlanetId != null) {
            surface = ds.planetSurfacesByPlanetId.get(planetEntityId);
        }

        // 地表区域 → 概览页喵
        if (surface != null && surface.surfaceRegions != null && !surface.surfaceRegions.isEmpty()) {
            vm.overviewFields.add(sectionRow("地表区域"));
            for (DailySettlementState.SurfaceRegionDailySnapshot r : surface.surfaceRegions) {
                vm.overviewFields.add(new EntityInfoViewModel.FieldEntry(regionLabel(r), regionValue(r)));
            }
        } else {
            vm.overviewFields.add(emptyRow(PlanetInfoViewModel.EMPTY_REGION_TEXT));
        }

        // 城市/殖民地 → 殖民地页喵
        if (surface != null && surface.cities != null && !surface.cities.isEmpty()) {
            vm.colonyFields.add(sectionRow("行星城市"));
            for (DailySettlementState.CityDailySnapshot c : surface.cities) {
                String key = (c.isPlanetaryCapital ? "[首都] " : "") + c.name;
                String value = stageLabel(c.cityStage) + " · 规模" + c.cityScale + " · 人口 " + c.population;
                vm.colonyFields.add(new EntityInfoViewModel.FieldEntry(key, value,
                        c.isPlanetaryCapital ? CAPITAL_COLOR : null));
            }
        } else {
            vm.colonyFields.add(emptyRow(PlanetInfoViewModel.EMPTY_CITY_TEXT));
        }

        // 工业页：本地库存 / 采集与加工设施 / 最近结算产出喵
        assembleIndustry(vm, surface);
        // 物流页：与该行星库存相关的在途运输喵
        assembleLogistics(vm, surface);
    }

    /** 组装工业页字段行（本地库存 / 采集设施 / 加工设施 / 最近结算产出）喵 */
    private static void assembleIndustry(PlanetInfoViewModel vm,
            DailySettlementState.PlanetSurfaceDailySnapshot surface) {
        // 行星存在但快照中无该行星条目：稳定空态，不伪造数据喵
        if (surface == null) {
            vm.industryFields.add(emptyRow(PlanetInfoViewModel.EMPTY_INDUSTRY_TEXT));
            return;
        }

        // 本地库存：容量 / 已用容量 / 物质数量 / 预留数量喵
        if (surface.inventories != null && !surface.inventories.isEmpty()) {
            for (DailySettlementState.InventoryDailySnapshot inv : surface.inventories) {
                vm.industryFields.add(sectionRow("本地库存 #" + inv.inventoryId));
                vm.industryFields.add(new EntityInfoViewModel.FieldEntry("库存容量",
                        fmtAmount(inv.usedCapacity) + " / " + fmtAmount(inv.capacity) + " 单位"));
                assembleInventorySubstances(vm.industryFields, inv.substances, inv.reservedAmounts);
            }
        }

        // 采集设施喵
        if (surface.extractionFacilities != null && !surface.extractionFacilities.isEmpty()) {
            vm.industryFields.add(sectionRow("采集设施"));
            for (DailySettlementState.ExtractionFacilityDailySnapshot f : surface.extractionFacilities) {
                assembleExtractionFacility(vm, f);
            }
        }

        // 加工设施：配方 / 进度 / 状态 / 失败原因喵
        if (surface.processingFacilities != null && !surface.processingFacilities.isEmpty()) {
            vm.industryFields.add(sectionRow("加工设施"));
            for (DailySettlementState.ProcessingFacilityDailySnapshot f : surface.processingFacilities) {
                assembleProcessingFacility(vm, f);
            }
        }

        // 最近结算产出喵
        if (surface.lastSettlementReport != null) {
            assembleSettlementReport(vm, surface.lastSettlementReport);
        }

        if (vm.industryFields.isEmpty()) {
            vm.industryFields.add(emptyRow(PlanetInfoViewModel.EMPTY_INDUSTRY_TEXT));
        }
    }

    /** 组装物流页字段行（与该行星库存相关的在途运输）喵 */
    private static void assembleLogistics(PlanetInfoViewModel vm,
            DailySettlementState.PlanetSurfaceDailySnapshot surface) {
        if (surface == null || surface.inTransitTransfers == null || surface.inTransitTransfers.isEmpty()) {
            vm.logisticsFields.add(emptyRow(PlanetInfoViewModel.EMPTY_LOGISTICS_TEXT));
            return;
        }
        for (DailySettlementState.TransferDailySnapshot t : surface.inTransitTransfers) {
            vm.logisticsFields.add(sectionRow("运输 #" + t.transferId));
            vm.logisticsFields.add(new EntityInfoViewModel.FieldEntry(
                    "状态", statusLabel(t.status), transferColor(t.status)));
            vm.logisticsFields.add(new EntityInfoViewModel.FieldEntry("源库存", "#" + t.sourceInventoryId));
            vm.logisticsFields.add(new EntityInfoViewModel.FieldEntry("目标库存", "#" + t.targetInventoryId));
            vm.logisticsFields.add(new EntityInfoViewModel.FieldEntry("货物", amountsText(t.goods)));
        }
    }

    /** 单个库存的物质数量/预留数量行（substanceId 并集，保持快照迭代顺序）喵 */
    private static void assembleInventorySubstances(List<EntityInfoViewModel.FieldEntry> rows,
            Map<String, Double> substances, Map<String, Double> reservedAmounts) {
        if ((substances == null || substances.isEmpty()) && (reservedAmounts == null || reservedAmounts.isEmpty())) {
            rows.add(new EntityInfoViewModel.FieldEntry("物质数量", "无"));
            return;
        }
        // 并集去重：优先 substances 迭代顺序，reserved 中的新物质追加在后喵
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (substances != null) {
            ids.addAll(substances.keySet());
        }
        if (reservedAmounts != null) {
            ids.addAll(reservedAmounts.keySet());
        }
        for (String id : ids) {
            double amount = substances != null ? substances.getOrDefault(id, 0.0) : 0.0;
            double reserved = reservedAmounts != null ? reservedAmounts.getOrDefault(id, 0.0) : 0.0;
            rows.add(new EntityInfoViewModel.FieldEntry(substanceLabel(id) + " 数量", fmtAmount(amount)));
            if (reserved > 0.0) {
                rows.add(new EntityInfoViewModel.FieldEntry(substanceLabel(id) + " 预留", fmtAmount(reserved)));
            }
        }
    }

    /** 单个采集设施字段行喵 */
    private static void assembleExtractionFacility(PlanetInfoViewModel vm,
            DailySettlementState.ExtractionFacilityDailySnapshot f) {
        Color color = facilityColor(f.status, f.lastFailureReason);
        vm.industryFields.add(new EntityInfoViewModel.FieldEntry(
                "采集设施 #" + f.facilityId, facilityTypeLabel(f.facilityType), color));
        vm.industryFields.add(new EntityInfoViewModel.FieldEntry("采集产物",
                substanceLabel(f.resourceId) + " " + fmtAmount(f.amountPerDay) + "/日"));
        vm.industryFields.add(new EntityInfoViewModel.FieldEntry("状态",
                statusText(f.status, f.lastFailureReason), color));
    }

    /** 单个加工设施字段行（配方 / 进度 / 状态 / 失败原因）喵 */
    private static void assembleProcessingFacility(PlanetInfoViewModel vm,
            DailySettlementState.ProcessingFacilityDailySnapshot f) {
        Color color = facilityColor(f.status, f.lastFailureReason);
        vm.industryFields.add(new EntityInfoViewModel.FieldEntry(
                "加工设施 #" + f.facilityId, facilityTypeLabel(f.facilityType), color));
        vm.industryFields.add(new EntityInfoViewModel.FieldEntry("配方",
                f.activeRecipeId != null ? recipeLabel(f.activeRecipeId) : "未配置"));
        vm.industryFields.add(new EntityInfoViewModel.FieldEntry("加工进度",
                fmtAmount(f.progressDays) + " 日"));
        vm.industryFields.add(new EntityInfoViewModel.FieldEntry("状态",
                statusText(f.status, f.lastFailureReason), color));
    }

    /** 最近一次结算结果 → 工业页喵 */
    private static void assembleSettlementReport(PlanetInfoViewModel vm,
            DailySettlementState.SettlementReportDailySnapshot report) {
        vm.industryFields.add(sectionRow("最近结算 · tick " + report.tick));
        if (report.extractions.isEmpty() && report.facilities.isEmpty() && report.transfers.isEmpty()) {
            vm.industryFields.add(emptyRow("本次无产出"));
            return;
        }
        for (DailySettlementState.ExtractionResultDailySnapshot r : report.extractions) {
            String key = "采集 #" + r.facilityId;
            if (r.success) {
                vm.industryFields.add(new EntityInfoViewModel.FieldEntry(key,
                        "产出 " + amountsText(r.extracted), STATUS_RUNNING));
            } else {
                vm.industryFields.add(new EntityInfoViewModel.FieldEntry(key,
                        "失败 · " + failureReasonLabel(r.failureReason), STATUS_WARNING));
            }
        }
        for (DailySettlementState.FacilityResultDailySnapshot r : report.facilities) {
            String key = "加工 #" + r.facilityId;
            if (r.success) {
                String value = "完成 " + r.batchCount + " 批";
                if (!r.produced.isEmpty()) {
                    value += " · 产出 " + amountsText(r.produced);
                }
                if (!r.consumed.isEmpty()) {
                    value += " · 消耗 " + amountsText(r.consumed);
                }
                vm.industryFields.add(new EntityInfoViewModel.FieldEntry(key, value, STATUS_RUNNING));
            } else {
                vm.industryFields.add(new EntityInfoViewModel.FieldEntry(key,
                        "失败 · " + failureReasonLabel(r.failureReason), STATUS_WARNING));
            }
        }
        for (DailySettlementState.TransferResultDailySnapshot r : report.transfers) {
            String key = "运输 #" + r.transferId;
            boolean arrived = CargoTransfer.STATUS_ARRIVED.equals(r.resultType);
            vm.industryFields.add(new EntityInfoViewModel.FieldEntry(key,
                    arrived ? "已抵达 " + amountsText(r.goods) : "运输中 · 目标库存已满",
                    arrived ? STATUS_RUNNING : STATUS_INFO));
        }
    }

    /** 创建空态行：key 为空串，面板渲染时显示为独立空态卡片。 */
    private static EntityInfoViewModel.FieldEntry emptyRow(String text) {
        return new EntityInfoViewModel.FieldEntry("", text);
    }

    /** 创建分组标题行，避免用普通文本符号模拟视觉分隔。 */
    private static EntityInfoViewModel.FieldEntry sectionRow(String title) {
        return EntityInfoViewModel.FieldEntry.section(title);
    }

    /** 地表区域行 key（区域名称）喵 */
    private static String regionLabel(DailySettlementState.SurfaceRegionDailySnapshot r) {
        return r.name != null && !r.name.isEmpty() ? r.name : r.regionType;
    }

    /** 地表区域行 value（占比 + 可开发比例）喵 */
    private static String regionValue(DailySettlementState.SurfaceRegionDailySnapshot r) {
        String base = String.format("占 %.0f%%", r.surfacePercentage * 100.0);
        if (r.developableSpaceRatio > 0) {
            base += String.format(" (可开发 %.0f%%)", r.developableSpaceRatio * 100.0);
        }
        return base;
    }

    /** 城市阶段中文标签（与 game City.getDisplayStage 保持一致）喵 */
    private static String stageLabel(String cityStage) {
        return switch (cityStage == null ? "" : cityStage) {
            case "OUTPOST" -> "前哨殖民地";
            case "SETTLEMENT" -> "定居点";
            case "TOWN" -> "城镇";
            case "CITY" -> "城市";
            case "MEGALOPOLIS" -> "巨型都市";
            default -> cityStage != null && !cityStage.isEmpty() ? cityStage : "?";
        };
    }

    // ===== 工业/物流展示辅助（G2.7 快照 → 字符串，不暴露快照对象） =====

    /** 物质中文标签（与 game SubstanceId 常量一致）喵 */
    private static String substanceLabel(String id) {
        if (id == null) {
            return "?";
        }
        return switch (id) {
            case SubstanceId.WATER -> "水";
            case SubstanceId.MINERAL_ORE -> "金属矿";
            case SubstanceId.SILICATE_ORE -> "硅酸盐矿";
            case SubstanceId.CARBON_ORE -> "碳质矿";
            case SubstanceId.HYDROGEN -> "氢气";
            case SubstanceId.OXYGEN -> "氧气";
            case SubstanceId.CARBON -> "碳";
            case SubstanceId.IRON -> "铁";
            case SubstanceId.SILICON -> "硅";
            case SubstanceId.ALUMINUM -> "铝";
            case SubstanceId.COPPER -> "铜";
            case SubstanceId.ENERGY -> "能源";
            default -> id;
        };
    }

    /** 设施类型中文标签喵 */
    private static String facilityTypeLabel(String type) {
        if (type == null) {
            return "?";
        }
        return switch (type) {
            case ResourceExtractionFacility.TYPE_WATER_EXTRACTION -> "水采集器";
            case RecipeRepository.FACILITY_TYPE_ELECTROLYZER -> "电解槽";
            default -> type;
        };
    }

    /** 配方中文标签喵 */
    private static String recipeLabel(String recipeId) {
        if (recipeId == null) {
            return "?";
        }
        return switch (recipeId) {
            case RecipeRepository.DEFAULT_ELECTROLYSIS_RECIPE_ID -> "水电解";
            default -> recipeId;
        };
    }

    /** 状态中文标签（采集/加工/运输共用，BLOCKED 在两类设施中同值）喵 */
    private static String statusLabel(String status) {
        return switch (status == null ? "" : status) {
            case ResourceExtractionFacility.STATUS_ACTIVE -> "运行中";
            case ResourceExtractionFacility.STATUS_BLOCKED -> "阻塞";
            case ProcessingFacility.STATUS_IDLE -> "闲置";
            case ProcessingFacility.STATUS_PROCESSING -> "加工中";
            case CargoTransfer.STATUS_IN_TRANSIT -> "运输中";
            case CargoTransfer.STATUS_ARRIVED -> "已抵达";
            case CargoTransfer.STATUS_CANCELLED -> "已取消";
            default -> status != null && !status.isBlank() ? status : "未知";
        };
    }

    /** 状态文本：正常时仅状态，失败时追加失败原因喵 */
    private static String statusText(String status, String failureReason) {
        String base = statusLabel(status);
        if (failureReason != null && !failureReason.isBlank()) {
            base += " · " + failureReasonLabel(failureReason);
        }
        return base;
    }

    /** 失败原因中文标签（与 game ProductionSettlementService / RecipeProcessor 常量一致）喵 */
    private static String failureReasonLabel(String reason) {
        return switch (reason == null ? "" : reason) {
            case ProductionSettlementService.FAILURE_REASON_NO_RECIPE -> "未配置配方";
            case ProductionSettlementService.FAILURE_REASON_NO_INVENTORY -> "缺少库存";
            case ProductionSettlementService.FAILURE_REASON_INVALID_EXTRACTION_AMOUNT -> "采集产出配置非法";
            case RecipeProcessor.FAILURE_REASON_INPUT_INSUFFICIENT -> "输入物不足";
            case RecipeProcessor.FAILURE_REASON_ENERGY_INSUFFICIENT -> "能源不足";
            case RecipeProcessor.FAILURE_REASON_OUTPUT_CAPACITY_INSUFFICIENT -> "存储空间不足";
            default -> reason != null && !reason.isBlank() ? reason : "未知";
        };
    }

    /** 设施状态着色：阻塞/失败橙色，正常运行绿色，其余正文色（null）喵 */
    private static Color facilityColor(String status, String failureReason) {
        if (status != null && (status.equals(ResourceExtractionFacility.STATUS_BLOCKED)
                || status.equals(ProcessingFacility.STATUS_BLOCKED))
                || failureReason != null && !failureReason.isBlank()) {
            return STATUS_WARNING;
        }
        if (status != null && (status.equals(ResourceExtractionFacility.STATUS_ACTIVE)
                || status.equals(ProcessingFacility.STATUS_PROCESSING))) {
            return STATUS_RUNNING;
        }
        return null;
    }

    /** 运输状态着色：已抵达绿色，已取消橙色，运输中蓝色喵 */
    private static Color transferColor(String status) {
        if (CargoTransfer.STATUS_ARRIVED.equals(status)) {
            return STATUS_RUNNING;
        }
        if (CargoTransfer.STATUS_CANCELLED.equals(status)) {
            return STATUS_WARNING;
        }
        return STATUS_INFO;
    }

    /** 物质数量文本：物质 数量（多物质逗号连接，保持快照迭代顺序）喵 */
    private static String amountsText(Map<String, Double> amounts) {
        if (amounts == null || amounts.isEmpty()) {
            return "无";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Double> e : amounts.entrySet()) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(substanceLabel(e.getKey())).append(" ").append(fmtAmount(e.getValue()));
        }
        return sb.toString();
    }

    /** 数量紧凑文本：整数显示整数，小数保留一位喵 */
    private static String fmtAmount(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e9) {
            return String.valueOf((long) value);
        }
        return String.format("%.1f", value);
    }

    /**
     * 从实时快照和基线快照中查找指定行星实体。
     * 优先查实时快照，回退到基线快照（与 EntityInfoAssembler 口径一致）喵。
     */
    private static EntitySnapshot findPlanetSnapshot(long entityId,
                                                     RealTimeWorldState rtState,
                                                     DailySettlementState ds) {
        if (rtState != null) {
            for (List<EntitySnapshot> snaps : rtState.getEntitySnapshotsBySystemView().values()) {
                for (EntitySnapshot snap : snaps) {
                    if (snap.entityId == entityId) return snap;
                }
            }
        }
        if (ds != null && ds.publicEntityBaselinesBySectorKey != null) {
            for (List<EntitySnapshot> snaps : ds.publicEntityBaselinesBySectorKey.values()) {
                for (EntitySnapshot snap : snaps) {
                    if (snap.entityId == entityId) return snap;
                }
            }
        }
        return null;
    }
}
