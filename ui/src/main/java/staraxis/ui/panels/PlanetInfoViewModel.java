package staraxis.ui.panels;

import com.badlogic.gdx.graphics.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * PlanetInfoViewModel（行星信息视图模型）喵。
 *
 * 行星信息窗口（{@link PlanetInfoPanel}）四分页的数据源：
 * - Overview（概览）：行星基础属性 + 地表区域
 * - Colony（殖民地）：城市/殖民地列表
 * - Industry（工业）：工业快照数据（本地库存 / 采集与加工设施 / 最近结算产出）
 * - Logistics（物流）：物流快照数据（与该行星库存相关的在途运输）
 *
 * 由 {@link PlanetInfoAssembler} 从 game 快照组装，UI 只消费本模型，不直接读 WorldState。
 * 本类为纯数据 + 纯逻辑，可脱离渲染层进行单元测试。
 */
public class PlanetInfoViewModel {

    /** 分页枚举：四个固定页面喵 */
    public enum PlanetPage {
        OVERVIEW,
        COLONY,
        INDUSTRY,
        LOGISTICS
    }

    /** 分页显示标签（固定中文）喵 */
    public static String pageLabel(PlanetPage page) {
        return switch (page) {
            case OVERVIEW -> "概览";
            case COLONY -> "殖民地";
            case INDUSTRY -> "工业";
            case LOGISTICS -> "物流";
        };
    }

    /** 四个分页的固定顺序喵 */
    public static List<PlanetPage> pages() {
        return List.of(PlanetPage.OVERVIEW, PlanetPage.COLONY,
                PlanetPage.INDUSTRY, PlanetPage.LOGISTICS);
    }

    /** 概览页空态文案：行星无地表区域数据喵 */
    public static final String EMPTY_REGION_TEXT = "暂无地表数据";
    /** 殖民地页空态文案：行星尚未殖民喵 */
    public static final String EMPTY_CITY_TEXT = "尚未殖民";
    /** 工业页空态文案：该行星无工业快照数据喵 */
    public static final String EMPTY_INDUSTRY_TEXT = "暂无工业数据";
    /** 物流页空态文案：该行星无在途运输数据喵 */
    public static final String EMPTY_LOGISTICS_TEXT = "暂无物流数据";
    /** 行星不存在/快照缺失的空态文案喵 */
    public static final String MISSING_PLANET_TEXT = "快照中未找到该行星";

    /** 绑定行星实体 ID（窗口数据源标识）喵 */
    public long planetEntityId = -1;
    /** 行星不存在/快照缺失时置 true，用于稳定空态喵 */
    public boolean missing = false;
    /** 窗口标题喵 */
    public String title = "";
    /** 类型标签（如 planetTypeId），空串则不显示喵 */
    public String typeLabel = "";
    /** 类型标签颜色，null 时用主题正文色喵 */
    public Color typeColor;

    /** 概览页字段行喵 */
    public final List<EntityInfoViewModel.FieldEntry> overviewFields = new ArrayList<>();
    /** 殖民地页字段行（城市列表）喵 */
    public final List<EntityInfoViewModel.FieldEntry> colonyFields = new ArrayList<>();
    /** 工业页字段行（本地库存 / 采集与加工设施 / 最近结算产出）喵 */
    public final List<EntityInfoViewModel.FieldEntry> industryFields = new ArrayList<>();
    /** 物流页字段行（与该行星库存相关的在途运输）喵 */
    public final List<EntityInfoViewModel.FieldEntry> logisticsFields = new ArrayList<>();

    /** 获取指定分页的字段行喵 */
    public List<EntityInfoViewModel.FieldEntry> rowsFor(PlanetPage page) {
        return switch (page) {
            case OVERVIEW -> overviewFields;
            case COLONY -> colonyFields;
            case INDUSTRY -> industryFields;
            case LOGISTICS -> logisticsFields;
        };
    }

    /** 指定分页是否为空（无字段行）喵 */
    public boolean isEmptyPage(PlanetPage page) {
        return rowsFor(page).isEmpty();
    }
}
