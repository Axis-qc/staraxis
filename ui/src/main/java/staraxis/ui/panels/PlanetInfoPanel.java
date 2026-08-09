package staraxis.ui.panels;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Group;
import staraxis.ui.effects.VectorButtonEffect;
import staraxis.ui.theme.UiTheme;
import staraxis.ui.widgets.VectorButton;
import staraxis.ui.widgets.VectorImage;
import staraxis.ui.widgets.VectorLabel;
import staraxis.ui.widgets.VectorScrollPane;
import staraxis.ui.widgets.VectorWindow;

import java.util.HashMap;
import java.util.List;

/**
 * PlanetInfoPanel（行星信息窗口 / 行星 UI）喵。
 *
 * 左键点击行星时打开的非模态单例独立窗口，展示该行星从 game 快照读取的详情。
 * 四分页结构：Overview（概览）/ Colony（殖民地）/ Industry（工业）/ Logistics（物流）。
 * 数据源为 {@link PlanetInfoViewModel}，由 {@link PlanetInfoAssembler} 从快照组装。
 * UI 只读快照，不直接读 WorldState。
 *
 * 切换行星（{@link #rebuild}）或切换分页（{@link #setPage}）时整体重建内容区，
 * 不残留上一颗行星的数据。内容行超出视图高度时由 {@link VectorScrollPane} 滚动展示，
 * 不静默截断；无数据时显示"暂无…"明确空态。
 */
public class PlanetInfoPanel extends VectorWindow {

    private final UiTheme theme;
    /** 形状渲染器引用（VectorWindow 的 sr 为私有，构造时留存一份）喵 */
    private final ShapeRenderer sr;
    /** 内容字段用字体（VectorWindow 的 font 为私有，构造时留存一份）喵 */
    private final BitmapFont font;

    /** 当前展示的视图模型喵 */
    private PlanetInfoViewModel vm;
    /** 当前分页喵 */
    private PlanetInfoViewModel.PlanetPage currentPage = PlanetInfoViewModel.PlanetPage.OVERVIEW;
    /** 普通 Tab 按钮效果喵 */
    private final VectorButtonEffect tabEffect;
    /** 选中 Tab 按钮效果（主题色高亮）喵 */
    private final VectorButtonEffect tabActiveEffect;

    public PlanetInfoPanel(ShapeRenderer sr, BitmapFont font, PlanetInfoViewModel vm, UiTheme theme) {
        super(sr, font, vm != null && vm.title != null ? vm.title : "");
        this.sr = sr;
        this.font = font;
        this.theme = theme;
        this.tabEffect = buildTabEffect(false);
        this.tabActiveEffect = buildTabEffect(true);
        rebuild(vm);
    }

    /**
     * 按视图模型重建窗口内容（切换行星时调用，完整替换标题/概览/城市/状态/所有列表）。
     * 快照缺失/行星不存在时 vm.missing 置位，稳定显示空态，不残留上一颗行星数据喵。
     */
    public void rebuild(PlanetInfoViewModel vm) {
        this.vm = vm != null ? vm : new PlanetInfoViewModel();
        setTitle(this.vm.title);
        if (this.vm.missing) {
            currentPage = PlanetInfoViewModel.PlanetPage.OVERVIEW;
        }
        applyLayout();
    }

    /** 切换分页并重建内容区喵 */
    public void setPage(PlanetInfoViewModel.PlanetPage page) {
        if (page == null) return;
        currentPage = page;
        applyLayout();
    }

    /** 当前展示的分页（外部/测试读取用）喵 */
    public PlanetInfoViewModel.PlanetPage getCurrentPage() {
        return currentPage;
    }

    /** 当前展示的视图模型（外部/测试读取用）喵 */
    public PlanetInfoViewModel getViewModel() {
        return vm;
    }

    /** 整体重建：Tab 栏 + 当前页内容（固定窗口，内容行在视图区内部滚动）。 */
    private void applyLayout() {
        getContentGroup().clearChildren();

        List<EntityInfoViewModel.FieldEntry> rows = vm.rowsFor(currentPage);
        if (rows.isEmpty()) {
            rows = List.of(new EntityInfoViewModel.FieldEntry("", "暂无数据"));
        }

        // 概览页在字段区上方显示类型标签喵
        boolean showTag = currentPage == PlanetInfoViewModel.PlanetPage.OVERVIEW
                && vm.typeLabel != null && !vm.typeLabel.isEmpty();

        // 1. 测量文本宽度，保留给布局接口与测试；窗口宽度本身不再由文本驱动。
        float maxFieldW = 0f;
        for (EntityInfoViewModel.FieldEntry f : rows) {
            maxFieldW = Math.max(maxFieldW, measureText(formatField(f)));
        }
        float tagW = showTag ? measureText("[" + vm.typeLabel + "]") : 0f;

        // 2. 纯布局计算：固定内容宽高 / 滚动视口 / Tab 栏几何（无 GL 依赖，可单测）。
        int sectionCount = countSectionRows(rows);
        PlanetInfoLayout.Metrics m = PlanetInfoLayout.compute(
                rows.size(), sectionCount, showTag, maxFieldW, tagW);

        // 3. 放置 Tab 栏（内容区顶部，与滚动视口恒有间隙，滚动区不会吞掉 Tab 点击）喵
        List<PlanetInfoViewModel.PlanetPage> pages = PlanetInfoViewModel.pages();
        for (int i = 0; i < pages.size(); i++) {
            PlanetInfoViewModel.PlanetPage page = pages.get(i);
            boolean active = page == currentPage;
            VectorButton tab = new VectorButton(sr, font, active ? tabActiveEffect : tabEffect,
                    PlanetInfoViewModel.pageLabel(page), () -> setPage(page));
            tab.setSize(PlanetInfoLayout.TAB_WIDTH, PlanetInfoLayout.TAB_HEIGHT);
            tab.setPosition(PlanetInfoLayout.CONTENT_PAD + i * (PlanetInfoLayout.TAB_WIDTH + PlanetInfoLayout.TAB_SPACING),
                    m.tabBarY());
            getContentGroup().addActor(tab);
        }

        // 4. 构建内容组。空态使用固定卡片，普通字段按分组标题和 key/value 层次渲染。
        Group content = new Group();
        float contentHeight = Math.max(m.rowsHeight(), m.viewportHeight());
        content.setSize(m.innerWidth(), contentHeight);
        float y = 0f;
        if (isEmptyState(rows)) {
            addEmptyStateCard(content, rows.get(0).value(), m.innerWidth(), contentHeight);
        } else {
            for (EntityInfoViewModel.FieldEntry f : rows) {
                if (f.section()) {
                    addSectionRow(content, f, y, m.innerWidth());
                    y += PlanetInfoLayout.LINE_HEIGHT + PlanetInfoLayout.SECTION_GAP;
                } else {
                    addFieldRow(content, f, y, m.innerWidth());
                    y += PlanetInfoLayout.LINE_HEIGHT;
                }
            }
        }
        if (showTag) {
            VectorLabel tag = new VectorLabel(font, "[" + vm.typeLabel + "]",
                    vm.typeColor != null ? vm.typeColor : theme.text);
            tag.setPosition(0, contentHeight - PlanetInfoLayout.LINE_HEIGHT);
            content.addActor(tag);
        }

        VectorScrollPane scroll = new VectorScrollPane(sr, content);
        scroll.setPosition(m.scrollPaneX(), m.scrollPaneY());
        scroll.setSize(m.scrollPaneWidth(), m.scrollPaneHeight());
        scroll.setScrollingDisabled(true, false);
        getContentGroup().addActor(scroll);

        setContentSize(m.maxContentWidth(), m.contentHeight());
    }

    /** 统计当前页分组标题数量，供固定布局计算内容滚动范围。 */
    private static int countSectionRows(List<EntityInfoViewModel.FieldEntry> rows) {
        int count = 0;
        for (EntityInfoViewModel.FieldEntry row : rows) {
            if (row.section()) count++;
        }
        return count;
    }

    /** 空态页只有一行 key 为空的占位字段，使用独立卡片而非普通字段行。 */
    private static boolean isEmptyState(List<EntityInfoViewModel.FieldEntry> rows) {
        if (rows.size() != 1) return false;
        String key = rows.get(0).key();
        return key == null || key.isEmpty();
    }

    /** 添加分组标题：主题色文字 + 横向分隔线，形成明确的内容层级。 */
    private void addSectionRow(Group content, EntityInfoViewModel.FieldEntry field,
                               float y, float width) {
        Group row = new Group();
        row.setSize(width, PlanetInfoLayout.LINE_HEIGHT + PlanetInfoLayout.SECTION_GAP);

        VectorImage divider = new VectorImage(sr);
        divider.setTint(new Color(theme.primary.r, theme.primary.g, theme.primary.b, 0.45f));
        divider.setPosition(0, 1f);
        divider.setSize(width, 1f);
        row.addActor(divider);

        VectorLabel title = new VectorLabel(font, field.key(), theme.primaryLight);
        title.setPosition(0, PlanetInfoLayout.SECTION_GAP * 0.5f);
        row.addActor(title);

        row.setPosition(0, y);
        content.addActor(row);
    }

    /** 添加普通字段：key 使用弱化色，value 使用正文色或状态色。 */
    private void addFieldRow(Group content, EntityInfoViewModel.FieldEntry field,
                             float y, float width) {
        Group row = new Group();
        row.setSize(width, PlanetInfoLayout.LINE_HEIGHT);

        String key = field.key() != null ? field.key() : "";
        String value = field.value() != null ? field.value() : "";
        if (key.isEmpty()) {
            VectorLabel valueLabel = new VectorLabel(font, value, theme.textMuted);
            valueLabel.setPosition(0, 0);
            row.addActor(valueLabel);
        } else {
            VectorLabel keyLabel = new VectorLabel(font, key, theme.textMuted);
            keyLabel.setPosition(0, 0);
            row.addActor(keyLabel);

            Color valueColor = field.color() != null ? field.color() : theme.text;
            VectorLabel valueLabel = new VectorLabel(font, value, valueColor);
            valueLabel.setPosition(keyLabel.getWidth() + PlanetInfoLayout.FIELD_VALUE_GAP, 0);
            row.addActor(valueLabel);
        }

        row.setPosition(0, y);
        content.addActor(row);
    }

    /** 添加固定尺寸空态卡片，确保没有数据时页面仍保持完整布局。 */
    private void addEmptyStateCard(Group content, String message,
                                   float width, float contentHeight) {
        float cardHeight = Math.min(PlanetInfoLayout.EMPTY_CARD_HEIGHT, contentHeight);
        Group card = new Group();
        card.setSize(width, cardHeight);

        VectorImage background = new VectorImage(sr);
        background.setTint(new Color(theme.panelBg.r, theme.panelBg.g, theme.panelBg.b, 0.85f));
        background.setSize(width, cardHeight);
        card.addActor(background);

        VectorImage accent = new VectorImage(sr);
        accent.setTint(new Color(theme.primary.r, theme.primary.g, theme.primary.b, 0.8f));
        accent.setSize(3f, cardHeight);
        card.addActor(accent);

        VectorLabel label = new VectorLabel(font, message != null ? message : "暂无数据", theme.textMuted);
        label.setPosition(18f, Math.max(0f, (cardHeight - label.getHeight()) / 2f));
        card.addActor(label);

        card.setPosition(0, Math.max(0f, (contentHeight - cardHeight) / 2f));
        content.addActor(card);
    }

    /** 字段渲染文本：key 为空串（空态行）时仅显示 value 喵 */
    private static String formatField(EntityInfoViewModel.FieldEntry f) {
        return (f.key() == null || f.key().isEmpty()) ? f.value() : f.key() + ": " + f.value();
    }

    /** 用内容字体测量文本渲染宽度（与 VectorLabel 相同缩放口径）喵 */
    private float measureText(String text) {
        if (text == null || text.isEmpty()) return 0f;
        return new VectorLabel(font, text, theme.text).getWidth();
    }

    /** 构建 Tab 按钮效果（选中态使用主题色高亮，未选中态暗底）喵 */
    private VectorButtonEffect buildTabEffect(boolean active) {
        VectorButtonEffect e = VectorButtonEffect.fromMap("planet_tab", new HashMap<>());
        e.background.color = active
                ? new Color(theme.primary.r, theme.primary.g, theme.primary.b, 0.35f)
                : new Color(theme.background.r, theme.background.g, theme.background.b, 0.85f);
        e.background.hoverColor = new Color(theme.primary.r, theme.primary.g, theme.primary.b, 0.45f);
        e.background.pressedColor = new Color(theme.primary.r, theme.primary.g, theme.primary.b, 0.6f);
        e.border.width = 1f;
        e.border.color = new Color(theme.panelBorder.r, theme.panelBorder.g, theme.panelBorder.b, 0.9f);
        e.text.color = active ? new Color(Color.WHITE) : new Color(theme.text);
        e.text.hoverColor = new Color(Color.WHITE);
        e.text.size = 15f;
        e.accent.enabled = active;
        e.accent.color = new Color(theme.primary.r, theme.primary.g, theme.primary.b, 1f);
        e.accent.width = 3f;
        return e;
    }
}
