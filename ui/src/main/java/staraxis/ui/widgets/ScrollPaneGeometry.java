package staraxis.ui.widgets;

import com.badlogic.gdx.math.MathUtils;

/**
 * ScrollPaneGeometry（滚动面板几何纯计算）喵。
 *
 * 与渲染/GL 解耦的滚动边界计算，是 {@link VectorScrollPane} 几何逻辑的唯一事实来源，
 * 可在无 GL 环境下进行单元测试（布局/滚动边界不依赖渲染层）。
 *
 * 偏移约定：scrollOffset = 0 表示内容停在顶部（显示内容开头）；
 * scrollOffset 越大表示内容越向底部滚动（显示内容结尾），最大为 maxScrollY。
 * 内容坐标采用左下原点：内容底部对齐视图底部时 scrollOffset = 0。
 */
public final class ScrollPaneGeometry {

    private ScrollPaneGeometry() {
    }

    /** 内容高度（下限为视图高度，避免内容小于视图时出现负滚动量/负偏移）喵 */
    public static float contentHeight(float contentHeight, float viewHeight) {
        return Math.max(contentHeight, viewHeight);
    }

    /** 是否可滚动（内容高度大于视图高度）喵 */
    public static boolean isScrollable(float contentHeight, float viewHeight) {
        return contentHeight > viewHeight;
    }

    /** 最大滚动量（scrollOffset 上限），不可滚动时为 0 喵 */
    public static float maxScrollY(float contentHeight, float viewHeight) {
        return Math.max(0f, contentHeight - viewHeight);
    }

    /** 钳制滚动量到合法区间 [0, maxScrollY]，不可滚动时归零喵 */
    public static float clampScrollY(float scrollOffset, float contentHeight, float viewHeight) {
        return MathUtils.clamp(scrollOffset, 0f, maxScrollY(contentHeight, viewHeight));
    }

    /** 内容左下角 y（相对视图底部）：scrollOffset=0 时内容底部对齐视图底部喵 */
    public static float contentY(float scrollOffset) {
        return -scrollOffset;
    }

    /** 滚动条滑块高度：不可滚动时返回 0（不画滚动条），否则按比例计算并保证下限 minHeight 喵 */
    public static float scrollBarThumbHeight(float contentHeight, float viewHeight, float minHeight) {
        if (!isScrollable(contentHeight, viewHeight)) return 0f;
        float ratio = viewHeight / contentHeight;
        return Math.max(viewHeight * ratio, minHeight);
    }

    /** 滚动条滑块左下角 y（相对视图底部轨道）：滑块顶部 = 内容顶部，滑块底部 = 内容底部喵 */
    public static float scrollBarThumbY(float scrollOffset, float contentHeight, float viewHeight,
                                        float thumbHeight) {
        float maxScroll = maxScrollY(contentHeight, viewHeight);
        if (maxScroll <= 0f || thumbHeight >= viewHeight) return 0f;
        float availableTrack = viewHeight - thumbHeight;
        return (1f - clampScrollY(scrollOffset, contentHeight, viewHeight) / maxScroll) * availableTrack;
    }

    /**
     * 拖拽比例转滚动量（ratio=1 指针在轨道顶部 → 内容顶部 offset=0；ratio=0 底部 → 内容底部）喵。
     * ratio 超出 [0,1] 时钳制，防止拖出轨道越界。
     */
    public static float scrollOffsetFromTrackRatio(float ratio, float contentHeight, float viewHeight) {
        return (1f - MathUtils.clamp(ratio, 0f, 1f)) * maxScrollY(contentHeight, viewHeight);
    }

    /** 滚轮滚动量转偏移：amountY 为正（向上滚）→ 回顶部，为负（向下滚）→ 向底部，结果自动钳制喵 */
    public static float scrollOffsetForWheel(float scrollOffset, float amountY, float scrollSpeed,
                                             float contentHeight, float viewHeight) {
        return clampScrollY(scrollOffset - amountY * scrollSpeed, contentHeight, viewHeight);
    }
}
