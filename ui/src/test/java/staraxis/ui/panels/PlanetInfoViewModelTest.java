package staraxis.ui.panels;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PlanetInfoViewModelTest（行星信息视图模型单元测试）。
 *
 * 覆盖：
 * - 四分页固定顺序与标签
 * - rowsFor 分页路由
 * - 空页判定（isEmptyPage）
 * - 工业/物流空态文案约定（"暂无…"，不含"尚未接入"）
 * - 明确空态行的构造（分页空态可测）
 */
class PlanetInfoViewModelTest {

    // ===== 分页固定顺序与标签 =====

    @Test
    void pagesHaveFixedOrder() {
        assertEquals(List.of(
                        PlanetInfoViewModel.PlanetPage.OVERVIEW,
                        PlanetInfoViewModel.PlanetPage.COLONY,
                        PlanetInfoViewModel.PlanetPage.INDUSTRY,
                        PlanetInfoViewModel.PlanetPage.LOGISTICS),
                PlanetInfoViewModel.pages());
    }

    @Test
    void pageLabelsMatchPages() {
        assertEquals("概览", PlanetInfoViewModel.pageLabel(PlanetInfoViewModel.PlanetPage.OVERVIEW));
        assertEquals("殖民地", PlanetInfoViewModel.pageLabel(PlanetInfoViewModel.PlanetPage.COLONY));
        assertEquals("工业", PlanetInfoViewModel.pageLabel(PlanetInfoViewModel.PlanetPage.INDUSTRY));
        assertEquals("物流", PlanetInfoViewModel.pageLabel(PlanetInfoViewModel.PlanetPage.LOGISTICS));
    }

    // ===== rowsFor 分页路由 =====

    @Test
    void rowsForRoutesToEachPageList() {
        PlanetInfoViewModel vm = new PlanetInfoViewModel();
        EntityInfoViewModel.FieldEntry a = new EntityInfoViewModel.FieldEntry("类型", "TERRESTRIAL");
        EntityInfoViewModel.FieldEntry b = new EntityInfoViewModel.FieldEntry("[首都] 新伊甸", "前哨殖民地");
        EntityInfoViewModel.FieldEntry c = new EntityInfoViewModel.FieldEntry("", "暂无工业数据（快照未提供）");
        vm.overviewFields.add(a);
        vm.colonyFields.add(b);
        vm.industryFields.add(c);

        assertSame(a, vm.rowsFor(PlanetInfoViewModel.PlanetPage.OVERVIEW).get(0));
        assertSame(b, vm.rowsFor(PlanetInfoViewModel.PlanetPage.COLONY).get(0));
        assertSame(c, vm.rowsFor(PlanetInfoViewModel.PlanetPage.INDUSTRY).get(0));
        assertEquals(0, vm.rowsFor(PlanetInfoViewModel.PlanetPage.LOGISTICS).size());
    }

    // ===== 空页判定 =====

    @Test
    void emptyViewModelHasAllPagesEmpty() {
        PlanetInfoViewModel vm = new PlanetInfoViewModel();
        for (PlanetInfoViewModel.PlanetPage page : PlanetInfoViewModel.pages()) {
            assertTrue(vm.isEmptyPage(page), page + " 应为空页");
        }
    }

    @Test
    void pageWithRowsIsNotEmpty() {
        PlanetInfoViewModel vm = new PlanetInfoViewModel();
        vm.overviewFields.add(new EntityInfoViewModel.FieldEntry("半径", "6000 GU"));
        assertFalse(vm.isEmptyPage(PlanetInfoViewModel.PlanetPage.OVERVIEW));
        assertTrue(vm.isEmptyPage(PlanetInfoViewModel.PlanetPage.COLONY));
    }

    // ===== 空态文案约定（无数据时显示"暂无…"，不显示"尚未接入"） =====

    @Test
    void emptyPageTextsFollowNoDataConvention() {
        // 工业/物流无数据时用"暂无…"空态，且不得残留"尚未接入"喵
        assertTrue(PlanetInfoViewModel.EMPTY_INDUSTRY_TEXT.startsWith("暂无"));
        assertTrue(PlanetInfoViewModel.EMPTY_LOGISTICS_TEXT.startsWith("暂无"));
        assertFalse(PlanetInfoViewModel.EMPTY_INDUSTRY_TEXT.contains("尚未接入"));
        assertFalse(PlanetInfoViewModel.EMPTY_LOGISTICS_TEXT.contains("尚未接入"));
    }

    // ===== 明确空态行（工业/物流快照未提供字段时） =====

    @Test
    void explicitEmptyStateRowsCarryClearText() {
        PlanetInfoViewModel vm = new PlanetInfoViewModel();
        // 模拟 Assembler 在快照无工业/物流字段时的行为喵
        vm.industryFields.add(new EntityInfoViewModel.FieldEntry("", PlanetInfoViewModel.EMPTY_INDUSTRY_TEXT));
        vm.logisticsFields.add(new EntityInfoViewModel.FieldEntry("", PlanetInfoViewModel.EMPTY_LOGISTICS_TEXT));

        assertEquals(1, vm.industryFields.size());
        assertEquals(1, vm.logisticsFields.size());
        assertEquals(PlanetInfoViewModel.EMPTY_INDUSTRY_TEXT,
                vm.rowsFor(PlanetInfoViewModel.PlanetPage.INDUSTRY).get(0).value());
        assertEquals(PlanetInfoViewModel.EMPTY_LOGISTICS_TEXT,
                vm.rowsFor(PlanetInfoViewModel.PlanetPage.LOGISTICS).get(0).value());
        assertFalse(vm.isEmptyPage(PlanetInfoViewModel.PlanetPage.INDUSTRY));
        assertFalse(vm.isEmptyPage(PlanetInfoViewModel.PlanetPage.LOGISTICS));
    }

    // ===== 缺失行星空态 =====

    @Test
    void missingVmHoldsStableEmptyState() {
        PlanetInfoViewModel vm = new PlanetInfoViewModel();
        vm.planetEntityId = 9999L;
        vm.missing = true;
        vm.title = "行星 #9999";
        vm.overviewFields.add(new EntityInfoViewModel.FieldEntry("", PlanetInfoViewModel.MISSING_PLANET_TEXT));
        vm.colonyFields.add(new EntityInfoViewModel.FieldEntry("", PlanetInfoViewModel.EMPTY_CITY_TEXT));
        vm.industryFields.add(new EntityInfoViewModel.FieldEntry("", PlanetInfoViewModel.EMPTY_INDUSTRY_TEXT));
        vm.logisticsFields.add(new EntityInfoViewModel.FieldEntry("", PlanetInfoViewModel.EMPTY_LOGISTICS_TEXT));

        assertTrue(vm.missing);
        for (PlanetInfoViewModel.PlanetPage page : PlanetInfoViewModel.pages()) {
            assertFalse(vm.isEmptyPage(page), page + " 空态 VM 应始终有明确空态行");
        }
        assertEquals(PlanetInfoViewModel.MISSING_PLANET_TEXT,
                vm.rowsFor(PlanetInfoViewModel.PlanetPage.OVERVIEW).get(0).value());
    }
}
