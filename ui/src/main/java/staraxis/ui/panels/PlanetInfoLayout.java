package staraxis.ui.panels;

/**
 * PlanetInfoLayout（行星信息窗口布局纯计算）喵。
 *
 * 从 {@link PlanetInfoPanel} 中抽出的无 GL 依赖的布局计算，集中维护固定窗口尺寸、
 * 滚动视口、分页 Tab 栏的几何关系，是面板布局逻辑的唯一事实来源，
 * 可在无 GL 环境下进行单元测试。
 *
 * 约定：窗口内容区采用固定宽高，内容行超出视口高度时在内容区内部滚动（不截断）；
 * Tab 栏固定在内容区顶部，与滚动视口之间始终保留 TAB_GAP + CONTENT_PAD 的间隙，
 * 保证滚动区不吞掉 Tab 点击。
 */
public final class PlanetInfoLayout {

    private PlanetInfoLayout() {
    }

    /** 内容区内边距（px）喵 */
    public static final float CONTENT_PAD = 12f;
    /** 字段行高（px）喵 */
    public static final float LINE_HEIGHT = 20f;
    /** 类型标签与字段区的间距（px）喵 */
    public static final float TAG_GAP = 6f;
    /** 行星窗口固定内容宽度（px），不随当前字段文本变化。 */
    public static final float FIXED_CONTENT_WIDTH = 600f;
    /** 行星窗口固定内容高度（px），不随当前分页数据量变化。 */
    public static final float FIXED_CONTENT_HEIGHT = 440f;
    /** 旧布局常量别名：固定宽度由 FIXED_CONTENT_WIDTH 统一定义。 */
    public static final float MIN_CONTENT_WIDTH = FIXED_CONTENT_WIDTH;
    /** 旧布局常量别名：固定高度由 FIXED_CONTENT_HEIGHT 统一定义。 */
    public static final float MAX_CONTENT_HEIGHT = FIXED_CONTENT_HEIGHT;
    /** 分页 Tab 栏与内容区的间距（px）喵 */
    public static final float TAB_GAP = 10f;
    /** 分页 Tab 按钮高度（px）喵 */
    public static final float TAB_HEIGHT = 26f;
    /** 分页 Tab 按钮宽度（px）喵 */
    public static final float TAB_WIDTH = 80f;
    /** 分页 Tab 按钮间距（px）喵 */
    public static final float TAB_SPACING = 6f;
    /** 分组标题额外占用的间距（px）。 */
    public static final float SECTION_GAP = 8f;
    /** 字段 key 与 value 之间的间距（px）。 */
    public static final float FIELD_VALUE_GAP = 8f;
    /** 空态卡片高度（px）。 */
    public static final float EMPTY_CARD_HEIGHT = 72f;

    /** 分页 Tab 数量（固定四个）喵 */
    public static int tabCount() {
        return PlanetInfoViewModel.pages().size();
    }

    /** Tab 栏总宽（N 个按钮 + (N-1) 个间隔）喵 */
    public static float tabBarWidth() {
        return tabCount() * TAB_WIDTH + (tabCount() - 1) * TAB_SPACING;
    }

    /**
     * 窗口内容布局指标喵。
     *
     * @param maxContentWidth   窗口内容区宽度（maxW）
     * @param innerWidth        滚动视口宽度（内容组宽度，maxW - 2*CONTENT_PAD）
     * @param rowsHeight        内容组高度（字段行 + 类型标签，可能超出视口）
     * @param viewportHeight    固定滚动视口高度，长内容在其中内部滚动
     * @param contentHeight     窗口内容区总高度（Tab 栏 + 滚动视口 + 内边距）
     * @param scrollPaneX       滚动视口左下角 x（内容区局部坐标）
     * @param scrollPaneY       滚动视口左下角 y（内容区局部坐标）
     * @param scrollPaneWidth   滚动视口宽度
     * @param scrollPaneHeight  滚动视口高度
     * @param tabBarY           Tab 栏左下角 y（内容区局部坐标，Tab 栏固定在顶部）
     */
    public record Metrics(
            float maxContentWidth,
            float innerWidth,
            float rowsHeight,
            float viewportHeight,
            float contentHeight,
            float scrollPaneX, float scrollPaneY,
            float scrollPaneWidth, float scrollPaneHeight,
            float tabBarY) {

        /** 内容可滚动量（rowsHeight - 视口高度），不可滚动时为 0 喵 */
        public float scrollRange() {
            return Math.max(0f, rowsHeight - viewportHeight);
        }

        /** 是否出现内容滚动（长内容）喵 */
        public boolean isScrollable() {
            return rowsHeight > viewportHeight;
        }

        /** Tab 栏与滚动视口是否几何重叠（重叠会吞掉 Tab 点击，必须为 false）喵 */
        public boolean tabsOverlapScrollPane() {
            float scrollTop = scrollPaneY + scrollPaneHeight;
            return scrollTop > tabBarY;
        }

        /** 所有维度均非负且滚动视口不超出内容区（防止越界/负尺寸）喵 */
        public boolean insideBounds() {
            return maxContentWidth > 0 && innerWidth > 0 && rowsHeight > 0
                    && viewportHeight > 0 && contentHeight > 0
                    && scrollPaneX >= 0 && scrollPaneY >= 0
                    && scrollPaneX + scrollPaneWidth <= maxContentWidth
                    && scrollPaneY + scrollPaneHeight <= contentHeight
                    && tabBarY + TAB_HEIGHT <= contentHeight;
        }
    }

    /**
     * 计算窗口内容布局喵。
     *
     * @param rowCount          当前页字段行数（调用方需已补齐空态占位行，至少为 1）
     * @param showTag           是否在字段区上方显示类型标签
     * @param maxFieldTextWidth 保留的文本测量参数，不再参与窗口宽度计算
     * @param tagTextWidth      保留的标签测量参数，不再参与窗口宽度计算
     * @return 布局指标
     */
    public static Metrics compute(int rowCount, boolean showTag,
                                  float maxFieldTextWidth, float tagTextWidth) {
        return compute(rowCount, 0, showTag, maxFieldTextWidth, tagTextWidth);
    }

    /**
     * 计算带分组标题的固定尺寸窗口布局。
     *
     * @param rowCount          当前页全部字段行数（包含分组标题行）
     * @param sectionCount      分组标题行数量
     * @param showTag           是否在字段区上方显示类型标签
     * @param maxFieldTextWidth 保留的文本测量参数，不再参与窗口宽度计算
     * @param tagTextWidth      保留的标签测量参数，不再参与窗口宽度计算
     * @return 布局指标
     */
    public static Metrics compute(int rowCount, int sectionCount, boolean showTag,
                                  float maxFieldTextWidth, float tagTextWidth) {
        // 至少 1 行：空态由调用方补占位行，此处防呆保证无负高度喵
        int rows = Math.max(1, rowCount);
        int sections = Math.max(0, sectionCount);

        // 宽高固定：短数据使用完整窗口，长数据只增加滚动范围，不改变窗口外框。
        float maxW = FIXED_CONTENT_WIDTH;
        float innerW = maxW - CONTENT_PAD * 2f;
        float rowsHeight = rows * LINE_HEIGHT + sections * SECTION_GAP
                + (showTag ? LINE_HEIGHT + TAG_GAP : 0f);
        float viewportH = FIXED_CONTENT_HEIGHT
                - (TAB_HEIGHT + TAB_GAP + CONTENT_PAD + CONTENT_PAD);
        float contentHeight = FIXED_CONTENT_HEIGHT;

        // 滚动视口位于内容区底部，Tab 栏固定在顶部，两者间恒有 TAB_GAP + CONTENT_PAD 间隙喵
        float scrollX = CONTENT_PAD;
        float scrollY = CONTENT_PAD;
        float tabBarY = contentHeight - TAB_HEIGHT;

        return new Metrics(maxW, innerW, rowsHeight, viewportH, contentHeight,
                scrollX, scrollY, innerW, viewportH, tabBarY);
    }
}
