package staraxis.ui.panels;

import com.badlogic.gdx.graphics.Color;

/**
 * EntityPanelDemoData（实体面板演示假数据）喵。
 *
 * TODO Phase 2 Step B：接入真实数据（EntityInfoAssembler 从快照组装）后本类删除。
 * 仅用于 UiPreviewApp 预览 EntitySummaryPanel / EntityInfoPanel 的布局与交互。
 */
public final class EntityPanelDemoData {

    private EntityPanelDemoData() {
    }

    // 演示用颜色（与 UiTheme 语义色对齐，独立常量避免预览代码依赖主题加载）喵
    private static final Color HABITABLE_GREEN = new Color(0.067f, 0.725f, 0.504f, 1f);
    private static final Color EXPLORER_BLUE = new Color(0.29f, 0.79f, 1f, 1f);

    /** 恒星示例喵 */
    public static EntityInfoViewModel star() {
        EntityInfoViewModel vm = new EntityInfoViewModel();
        vm.title("恒星系 #42 · 晨曦").typeLabel("", null);
        vm.summary("光谱", "G2V");
        vm.summary("温度", "5778 K");
        vm.summary("半径", "1.00 sol");
        vm.summary("行星", "x6 (宜居 x1)");
        vm.detail("光谱类型", "G2V");
        vm.detail("表面温度", "5778 K");
        vm.detail("半径", "1.00 sol");
        vm.detail("质量", "1.00 sol");
        vm.detail("行星数量", "6");
        vm.detail("宜居行星", "1");
        vm.action("focus", "聚焦", true);
        vm.action("chart", "星系图", true);
        return vm;
    }

    /** 行星示例（含地表区域与宜居标签）喵 */
    public static EntityInfoViewModel planet() {
        EntityInfoViewModel vm = new EntityInfoViewModel();
        vm.title("行星 · 新伊甸").typeLabel("宜居", HABITABLE_GREEN);
        vm.summary("类型", "TERRESTRIAL");
        vm.summary("半径", "6,371 GU");
        vm.summary("轨道周期", "365 日");
        vm.summary("地表区域", "5 块 (大陆 x2)");
        vm.detail("类型", "TERRESTRIAL");
        vm.detail("半径", "6,371 GU");
        vm.detail("轨道半长轴", "152,000 GU");
        vm.detail("偏心率", "0.017");
        vm.detail("轨道倾角", "0.03°");
        vm.detail("轨道周期", "365 日");
        vm.detail("自转周期", "24.0 小时");
        vm.detail("---- 地表区域 ----", "");
        vm.detail("希望大陆", "占 32% (可开发 78%)");
        vm.detail("晨曦洋", "占 55%");
        vm.detail("风蚀荒漠", "占 8% (可开发 45%)");
        vm.action("colonize", "殖民", false);
        vm.action("survey", "调查", true);
        vm.action("beacon", "设航标", true);
        return vm;
    }

    /** 卫星示例喵 */
    public static EntityInfoViewModel moon() {
        EntityInfoViewModel vm = new EntityInfoViewModel();
        vm.title("卫星 · 新月").typeLabel("", null);
        vm.summary("归属", "行星 · 新伊甸");
        vm.summary("类型", "ROCKY_BARREN");
        vm.summary("半径", "1,737 GU");
        vm.detail("归属行星", "行星 · 新伊甸");
        vm.detail("类型", "ROCKY_BARREN");
        vm.detail("半径", "1,737 GU");
        vm.detail("轨道半长轴", "18,400 GU");
        vm.detail("轨道周期", "27.3 日");
        vm.action("survey", "调查", true);
        return vm;
    }

    /** 舰船示例喵 */
    public static EntityInfoViewModel ship() {
        EntityInfoViewModel vm = new EntityInfoViewModel();
        vm.title("探索船 · 致远号").typeLabel("探索", EXPLORER_BLUE);
        vm.summary("状态", "巡航中");
        vm.summary("航速", "120 GU/日");
        vm.summary("航向", "042°");
        vm.summary("所属", "晨曦联合");
        vm.detail("舰级", "default_explorer_ship");
        vm.detail("状态", "巡航中");
        vm.detail("航速", "120 GU/日");
        vm.detail("航向", "042°");
        vm.detail("所属国家", "晨曦联合");
        vm.detail("护盾", "100 / 100");
        vm.detail("结构", "240 / 240");
        vm.action("move", "移动", true);
        vm.action("patrol", "巡逻", true);
        vm.action("dock", "停泊", false);
        return vm;
    }
}
