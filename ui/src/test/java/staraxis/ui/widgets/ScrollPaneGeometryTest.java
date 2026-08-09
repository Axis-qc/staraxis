package staraxis.ui.widgets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ScrollPaneGeometryTest（滚动面板几何纯计算单元测试，不依赖 GL）喵。
 *
 * 覆盖：
 * - 偏移约定：scrollOffset=0 停在内容顶部（首次打开即顶部）
 * - 滚轮下滚可到达内容底部，且不会越过上下边界
 * - 拖拽比例映射：轨道顶部=内容顶部、轨道底部=内容底部
 * - 视图缩放后滚动量自动钳制，内容位置不越界
 * - 空态/短内容：无滚动范围、滑块为 0、不产生除零/NaN 越界
 * - 滑块始终落在轨道内
 */
class ScrollPaneGeometryTest {

    private static final float EPS = 1e-4f;

    // ===== 内容高度 / 可滚动判定 =====

    @Test
    void contentHeightFloorsAtViewHeight() {
        assertEquals(1000f, ScrollPaneGeometry.contentHeight(1000f, 420f), EPS);
        assertEquals(420f, ScrollPaneGeometry.contentHeight(20f, 420f), EPS);
        assertEquals(20f, ScrollPaneGeometry.contentHeight(20f, 20f), EPS);
    }

    @Test
    void scrollableOnlyWhenContentTallerThanView() {
        assertTrue(ScrollPaneGeometry.isScrollable(1000f, 420f));
        assertFalse(ScrollPaneGeometry.isScrollable(20f, 20f));
        assertFalse(ScrollPaneGeometry.isScrollable(20f, 420f));
    }

    @Test
    void maxScrollIsContentMinusView() {
        assertEquals(580f, ScrollPaneGeometry.maxScrollY(1000f, 420f), EPS);
        assertEquals(0f, ScrollPaneGeometry.maxScrollY(20f, 20f), EPS);
        assertEquals(0f, ScrollPaneGeometry.maxScrollY(20f, 420f), EPS);
    }

    // ===== 首次打开从内容顶部开始（偏移约定 0 = 顶部） =====

    @Test
    void initialOffsetIsTop() {
        // 新面板初始偏移为 0：内容底部对齐视图底部，展示内容开头喵
        assertEquals(0f, ScrollPaneGeometry.clampScrollY(0f, 1000f, 420f), EPS);
        assertEquals(0f, ScrollPaneGeometry.contentY(0f), EPS);
    }

    @Test
    void topOfContentCoveredAtInitialOffset() {
        // 偏移 0 时内容覆盖视图顶部：内容顶 >= 视图顶喵
        assertEquals(1000f, ScrollPaneGeometry.contentY(0f) + 1000f, EPS);
    }

    // ===== 滚轮浏览到底部 / 边界钳制 =====

    @Test
    void wheelDownReachesBottomAndStops() {
        float offset = 0f;
        // 向下滚 20 格（每格 30px = 600px > 最大滚动量 580px），最终停在底部不越界喵
        offset = ScrollPaneGeometry.scrollOffsetForWheel(offset, -1f, 30f, 1000f, 420f);
        offset = ScrollPaneGeometry.scrollOffsetForWheel(offset, -1f, 30f, 1000f, 420f);
        assertEquals(60f, offset, EPS);
        for (int i = 0; i < 20; i++) {
            offset = ScrollPaneGeometry.scrollOffsetForWheel(offset, -1f, 30f, 1000f, 420f);
        }
        assertEquals(580f, offset, EPS);
        // 继续下滚不会越界喵
        offset = ScrollPaneGeometry.scrollOffsetForWheel(offset, -5f, 30f, 1000f, 420f);
        assertEquals(580f, offset, EPS);
    }

    @Test
    void wheelUpNeverGoesNegative() {
        float offset = ScrollPaneGeometry.scrollOffsetForWheel(0f, 1f, 30f, 1000f, 420f);
        assertEquals(0f, offset, EPS);
        offset = ScrollPaneGeometry.scrollOffsetForWheel(offset, 10f, 30f, 1000f, 420f);
        assertEquals(0f, offset, EPS);
    }

    @Test
    void wheelUpFromBottomReturnsToTop() {
        float offset = ScrollPaneGeometry.scrollOffsetForWheel(580f, 20f, 30f, 1000f, 420f);
        assertEquals(0f, offset, EPS);
    }

    // ===== 拖拽映射 =====

    @Test
    void dragRatioMapsTrackToScrollRange() {
        // 轨道顶部（ratio=1）→ 内容顶部；轨道底部（ratio=0）→ 内容底部喵
        assertEquals(0f, ScrollPaneGeometry.scrollOffsetFromTrackRatio(1f, 1000f, 420f), EPS);
        assertEquals(580f, ScrollPaneGeometry.scrollOffsetFromTrackRatio(0f, 1000f, 420f), EPS);
        assertEquals(290f, ScrollPaneGeometry.scrollOffsetFromTrackRatio(0.5f, 1000f, 420f), EPS);
    }

    @Test
    void dragRatioOutOfRangeIsClamped() {
        assertEquals(0f, ScrollPaneGeometry.scrollOffsetFromTrackRatio(2f, 1000f, 420f), EPS);
        assertEquals(580f, ScrollPaneGeometry.scrollOffsetFromTrackRatio(-1f, 1000f, 420f), EPS);
    }

    // ===== 视图缩放后钳制（窗口缩放不越界） =====

    @Test
    void resizeClampsOffsetToNewMax() {
        // 视图从 420 放大到 500，原偏移 700 超出新最大量 500，必须钳回喵
        assertEquals(500f, ScrollPaneGeometry.clampScrollY(700f, 1000f, 500f), EPS);
        assertEquals(-500f, ScrollPaneGeometry.contentY(500f), EPS);
    }

    @Test
    void contentAlwaysCoversViewportAfterClamp() {
        // 任意合法偏移下，内容均完整覆盖视图范围（上不露顶、下不露底）喵
        for (float offset = 0f; offset <= 580f; offset += 10f) {
            float clamped = ScrollPaneGeometry.clampScrollY(offset, 1000f, 420f);
            assertTrue(clamped >= 0f, "偏移不得为负: " + clamped);
            assertTrue(clamped <= 580f, "偏移不得超过最大滚动量: " + clamped);
            float cy = ScrollPaneGeometry.contentY(clamped);
            assertTrue(cy <= 0f, "内容不得露出视图顶部: " + cy);
            assertTrue(cy + 1000f >= 420f, "内容不得露出视图底部: " + (cy + 1000f));
        }
    }

    // ===== 空态 / 短内容：无除零、无越界 =====

    @Test
    void emptyContentProducesNoScroll() {
        assertFalse(ScrollPaneGeometry.isScrollable(20f, 20f));
        assertEquals(0f, ScrollPaneGeometry.maxScrollY(20f, 20f), EPS);
        assertEquals(0f, ScrollPaneGeometry.scrollBarThumbHeight(20f, 20f, 30f), EPS);
        // 原实现此处 contentH-viewH=0 会产生除零；纯计算必须安全返回 0 喵
        assertEquals(0f, ScrollPaneGeometry.scrollBarThumbY(5f, 20f, 20f, 30f), EPS);
    }

    @Test
    void shortContentScrollBarHidden() {
        assertEquals(0f, ScrollPaneGeometry.scrollBarThumbHeight(46f, 420f, 30f), EPS);
        assertEquals(0f, ScrollPaneGeometry.maxScrollY(46f, 420f), EPS);
    }

    // ===== 滑块几何始终落在轨道内 =====

    @Test
    void thumbStaysInsideTrackAcrossFullScroll() {
        float thumbH = ScrollPaneGeometry.scrollBarThumbHeight(1000f, 420f, 30f);
        float track = 420f - thumbH;
        for (float offset = 0f; offset <= 580f; offset += 10f) {
            float thumbY = ScrollPaneGeometry.scrollBarThumbY(offset, 1000f, 420f, thumbH);
            assertTrue(thumbY >= 0f, "滑块不得越出轨道底部: " + thumbY);
            assertTrue(thumbY <= track, "滑块不得越出轨道顶部: " + thumbY);
        }
    }

    @Test
    void thumbAtTopMeansContentTop() {
        float thumbH = ScrollPaneGeometry.scrollBarThumbHeight(1000f, 420f, 30f);
        float track = 420f - thumbH;
        assertEquals(track, ScrollPaneGeometry.scrollBarThumbY(0f, 1000f, 420f, thumbH), EPS);
        assertEquals(0f, ScrollPaneGeometry.scrollBarThumbY(580f, 1000f, 420f, thumbH), EPS);
    }
}
