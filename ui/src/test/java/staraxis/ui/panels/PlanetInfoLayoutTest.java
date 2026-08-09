package staraxis.ui.panels;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PlanetInfoLayoutTest（行星信息窗口布局纯计算单元测试，不依赖 GL）喵。
 *
 * 覆盖：
 * - 长内容：滚动视口封顶最大高度、内容区内部滚动不截断、滚动范围正确
 * - 空态/短内容：窗口保持固定尺寸、无滚动、不越界
 * - Tab 栏与滚动视口恒无几何重叠（滚动区不吞掉 Tab 点击）
 * - 所有维度非负、滚动视口与 Tab 栏均落在内容区边界内
 */
class PlanetInfoLayoutTest {

    private static final float TAB_BAR_WIDTH = 4 * PlanetInfoLayout.TAB_WIDTH
            + 3 * PlanetInfoLayout.TAB_SPACING;
    private static final float FIXED_VIEWPORT_H = PlanetInfoLayout.FIXED_CONTENT_HEIGHT
            - (PlanetInfoLayout.TAB_HEIGHT + PlanetInfoLayout.TAB_GAP
            + PlanetInfoLayout.CONTENT_PAD + PlanetInfoLayout.CONTENT_PAD);
    private static final float EPS = 1e-4f;

    // ===== 长内容：内部滚动，不截断 =====

    @Test
    void longContentScrollsInsideCappedViewport() {
        // 50 行字段：行高 1000px，超过固定视口高度，内容区内部滚动。
        PlanetInfoLayout.Metrics m = PlanetInfoLayout.compute(50, false, 150f, 0f);

        assertEquals(50 * PlanetInfoLayout.LINE_HEIGHT, m.rowsHeight(), EPS);
        assertEquals(FIXED_VIEWPORT_H, m.viewportHeight(), EPS);
        assertTrue(m.isScrollable());
        assertEquals(m.rowsHeight() - m.viewportHeight(), m.scrollRange(), EPS);
        assertTrue(m.scrollRange() > 0f, "长内容应产生正滚动范围");
        // 内容区总高度固定，不随字段数量变化。
        assertEquals(PlanetInfoLayout.FIXED_CONTENT_HEIGHT, m.contentHeight(), EPS);
        assertTrue(m.insideBounds());
    }

    @Test
    void longContentKeepsFullFieldRows() {
        // 滚动只影响视口高度，内容组始终完整包含全部行（不静默截断）喵
        PlanetInfoLayout.Metrics m = PlanetInfoLayout.compute(50, true, 150f, 60f);
        assertEquals(50 * PlanetInfoLayout.LINE_HEIGHT
                + PlanetInfoLayout.LINE_HEIGHT + PlanetInfoLayout.TAG_GAP, m.rowsHeight(), EPS);
        assertTrue(m.isScrollable());
        assertEquals(FIXED_VIEWPORT_H, m.viewportHeight(), EPS);
    }

    // ===== 空态 / 短内容：固定窗口，无滚动，不越界 =====

    @Test
    void emptyStateFitsViewportNoScroll() {
        PlanetInfoLayout.Metrics m = PlanetInfoLayout.compute(1, false, 150f, 0f);

        assertEquals(PlanetInfoLayout.LINE_HEIGHT, m.rowsHeight(), EPS);
        assertEquals(FIXED_VIEWPORT_H, m.viewportHeight(), EPS);
        assertFalse(m.isScrollable());
        assertEquals(0f, m.scrollRange(), EPS);
        assertEquals(PlanetInfoLayout.FIXED_CONTENT_WIDTH, m.maxContentWidth(), EPS);
        assertEquals(PlanetInfoLayout.FIXED_CONTENT_HEIGHT, m.contentHeight(), EPS);
        assertTrue(m.insideBounds());
    }

    @Test
    void emptyStateWithTagFitsViewport() {
        // 空态 + 类型标签：窗口仍使用固定视口高度。
        PlanetInfoLayout.Metrics m = PlanetInfoLayout.compute(1, true, 150f, 60f);

        assertEquals(PlanetInfoLayout.LINE_HEIGHT + PlanetInfoLayout.LINE_HEIGHT
                + PlanetInfoLayout.TAG_GAP, m.rowsHeight(), EPS);
        assertEquals(FIXED_VIEWPORT_H, m.viewportHeight(), EPS);
        assertFalse(m.isScrollable());
        assertEquals(0f, m.scrollRange(), EPS);
        assertEquals(PlanetInfoLayout.FIXED_CONTENT_HEIGHT, m.contentHeight(), EPS);
        assertTrue(m.insideBounds());
    }

    @Test
    void zeroRowCountFallsBackToEmptyState() {
        // rowCount 防呆：至少 1 行占位，不会产生负/零高度越界喵
        PlanetInfoLayout.Metrics m = PlanetInfoLayout.compute(0, false, 150f, 0f);
        assertEquals(FIXED_VIEWPORT_H, m.viewportHeight(), EPS);
        assertFalse(m.isScrollable());
        assertTrue(m.insideBounds());
    }

    @Test
    void shortAndLongPagesShareFixedWindowSize() {
        PlanetInfoLayout.Metrics shortPage = PlanetInfoLayout.compute(1, false, 20f, 0f);
        PlanetInfoLayout.Metrics longPage = PlanetInfoLayout.compute(40, 3, true, 900f, 900f);

        assertEquals(shortPage.maxContentWidth(), longPage.maxContentWidth(), EPS);
        assertEquals(shortPage.contentHeight(), longPage.contentHeight(), EPS);
        assertEquals(shortPage.viewportHeight(), longPage.viewportHeight(), EPS);
        assertEquals(40 * PlanetInfoLayout.LINE_HEIGHT + 3 * PlanetInfoLayout.SECTION_GAP
                + PlanetInfoLayout.LINE_HEIGHT + PlanetInfoLayout.TAG_GAP,
                longPage.rowsHeight(), EPS);
    }

    // ===== Tab 栏与滚动视口：恒无重叠（滚动区不吞掉 Tab 点击） =====

    @Test
    void tabsNeverOverlapScrollPaneForLongContent() {
        for (int rows = 1; rows <= 60; rows++) {
            PlanetInfoLayout.Metrics m = PlanetInfoLayout.compute(rows, false, 150f, 0f);
            assertFalse(m.tabsOverlapScrollPane(), "行数 " + rows + " 时 Tab 栏与滚动区发生重叠");
            assertTrue(m.insideBounds());
        }
    }

    @Test
    void tabsNeverOverlapScrollPaneWithTag() {
        for (int rows = 1; rows <= 60; rows++) {
            PlanetInfoLayout.Metrics m = PlanetInfoLayout.compute(rows, true, 150f, 60f);
            assertFalse(m.tabsOverlapScrollPane(), "行数 " + rows + "（带标签）时 Tab 栏与滚动区发生重叠");
            assertTrue(m.insideBounds());
        }
    }

    @Test
    void tabGapKeepsScrollAreaBelowTabBar() {
        PlanetInfoLayout.Metrics m = PlanetInfoLayout.compute(50, false, 150f, 0f);
        float scrollTop = m.scrollPaneY() + m.scrollPaneHeight();
        assertTrue(scrollTop < m.tabBarY(), "滚动区顶部应低于 Tab 栏底部");
        // 间隙恒为 TAB_GAP + CONTENT_PAD，确保点击区域分离喵
        assertEquals(PlanetInfoLayout.TAB_GAP + PlanetInfoLayout.CONTENT_PAD,
                m.tabBarY() - scrollTop, EPS);
    }

    // ===== 宽度：固定窗口不受字段和标签文本影响 =====

    @Test
    void tabBarWidthDrivesWindowWhenWiderThanFields() {
        PlanetInfoLayout.Metrics m = PlanetInfoLayout.compute(2, false, 10f, 0f);
        assertEquals(PlanetInfoLayout.FIXED_CONTENT_WIDTH, m.maxContentWidth(), EPS);
        assertEquals(m.maxContentWidth() - PlanetInfoLayout.CONTENT_PAD * 2f, m.innerWidth(), EPS);
    }

    @Test
    void fieldTextWidthDrivesWindowWhenWider() {
        float fieldW = 500f;
        PlanetInfoLayout.Metrics m = PlanetInfoLayout.compute(2, false, fieldW, 0f);
        assertEquals(PlanetInfoLayout.FIXED_CONTENT_WIDTH, m.maxContentWidth(), EPS);
    }

    @Test
    void tagTextWidthDrivesWindowWhenWider() {
        float tagW = 600f;
        PlanetInfoLayout.Metrics m = PlanetInfoLayout.compute(2, true, 100f, tagW);
        assertEquals(PlanetInfoLayout.FIXED_CONTENT_WIDTH, m.maxContentWidth(), EPS);
    }

    @Test
    void tagHiddenWhenShowTagFalse() {
        // 非概览页不显示标签：标签宽度不应撑大窗口喵
        PlanetInfoLayout.Metrics m = PlanetInfoLayout.compute(2, false, 100f, 600f);
        assertEquals(PlanetInfoLayout.FIXED_CONTENT_WIDTH, m.maxContentWidth(), EPS);
    }
}
