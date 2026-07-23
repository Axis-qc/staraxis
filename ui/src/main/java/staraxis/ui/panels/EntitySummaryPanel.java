package staraxis.ui.panels;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import staraxis.ui.theme.UiTheme;
import staraxis.ui.widgets.VectorButton;
import staraxis.ui.widgets.VectorLabel;

/**
 * EntitySummaryPanel（左下实体摘要面板）喵。
 *
 * 星际争霸指令卡式设计：面板结构与尺寸固定，不同实体只变更槽位内容——
 * - 标题槽：实体名称 + 类型标签 + 固定位置的「详细」按钮
 * - 信息槽：固定 {@link #INFO_ROWS} 行，无数据的行留空
 * - 指令网格：固定 {@link #GRID_ROWS} x {@link #GRID_COLS} 槽位，指令按序填充，
 *   空槽画暗色空槽边框，体现固定结构
 *
 * 所有实体共用本通用布局；特殊实体的特殊布局未来通过 ViewModel 布局标识扩展。
 * 数据来源只认 {@link EntityInfoViewModel}，由外部（Assembler/演示数据）喂入。
 */
public class EntitySummaryPanel extends Group {

    /** 面板宽度（px）喵 */
    private static final float PANEL_WIDTH = 400f;
    /** 内边距（px）喵 */
    private static final float PAD = 10f;
    /** 标题行高（px）喵 */
    private static final float TITLE_HEIGHT = 24f;
    /** 信息行高（px）喵 */
    private static final float INFO_LINE_HEIGHT = 19f;
    /** 信息槽固定行数喵 */
    private static final int INFO_ROWS = 4;
    /** 信息区与指令网格的间距（px）喵 */
    private static final float SECTION_GAP = 8f;
    /** 指令网格列数喵 */
    private static final int GRID_COLS = 5;
    /** 指令网格行数喵 */
    private static final int GRID_ROWS = 2;
    /** 指令槽宽（px）喵 */
    private static final float SLOT_WIDTH = 72f;
    /** 指令槽高（px）喵 */
    private static final float SLOT_HEIGHT = 32f;
    /** 指令槽间距（px）喵 */
    private static final float SLOT_GAP = 5f;
    /** 详细按钮宽（px），固定于标题行右侧喵 */
    private static final float DETAILS_BUTTON_WIDTH = 70f;
    /** 边框线宽（px）喵 */
    private static final float BORDER_WIDTH = 1.5f;
    /** 禁用按钮的透明度喵 */
    private static final float DISABLED_ALPHA = 0.4f;
    /** 空槽边框透明度（相对主题边框色）喵 */
    private static final float EMPTY_SLOT_ALPHA = 0.35f;

    /** 面板固定高度：PAD + 标题 + 信息区 + 间距 + 网格 + PAD 喵 */
    private static final float PANEL_HEIGHT = PAD + TITLE_HEIGHT
            + INFO_ROWS * INFO_LINE_HEIGHT + SECTION_GAP
            + GRID_ROWS * SLOT_HEIGHT + (GRID_ROWS - 1) * SLOT_GAP + PAD;

    /** 「详细」按钮的固定 action id，点击时通过 {@link ActionListener} 上抛喵 */
    public static final String ACTION_OPEN_DETAILS = "open_details";

    /** 指令按钮点击回调喵 */
    public interface ActionListener {
        /** @param actionId ActionEntry.id，或 {@link #ACTION_OPEN_DETAILS} 喵 */
        void onAction(String actionId);
    }

    private final ShapeRenderer sr;
    private final BitmapFont font;
    private final UiTheme theme;
    private ActionListener actionListener;

    /** 标题文字效果（大一号字号，与正文区分层次）喵 */
    private final staraxis.ui.effects.VectorLabelEffect titleEffect;
    /** 指令槽按钮效果（暗底 + 主题色细边框 + 小字号）喵 */
    private final staraxis.ui.effects.VectorButtonEffect slotButtonEffect;

    private final VectorLabel titleLabel;
    private final VectorLabel typeTagLabel;
    private final VectorLabel[] infoLabels = new VectorLabel[INFO_ROWS];
    /** 指令网格按钮（槽位数固定，按序填充，空槽对应位置为 null）喵 */
    private final VectorButton[] gridButtons = new VectorButton[GRID_ROWS * GRID_COLS];
    private final VectorButton detailsButton;

    public EntitySummaryPanel(ShapeRenderer sr, BitmapFont font, UiTheme theme) {
        this.sr = sr;
        this.font = font;
        this.theme = theme;
        setTouchable(Touchable.enabled);

        // 标题效果：20px，颜色取主题标题色（拷贝防共享污染）喵
        titleEffect = staraxis.ui.effects.VectorLabelEffect.fromMap("summary_title", new java.util.HashMap<>());
        titleEffect.text.size = 20f;
        titleEffect.text.color = new Color(theme.title.r, theme.title.g, theme.title.b, 1f);

        // 指令槽按钮效果：暗底 + 主题色细边框 + 15px 文字，全部拷贝防共享污染喵
        slotButtonEffect = staraxis.ui.effects.VectorButtonEffect.fromMap("summary_slot", new java.util.HashMap<>());
        slotButtonEffect.background.color = new Color(theme.background.r, theme.background.g, theme.background.b, 0.85f);
        slotButtonEffect.background.hoverColor = new Color(theme.primary.r, theme.primary.g, theme.primary.b, 0.25f);
        slotButtonEffect.background.pressedColor = new Color(theme.primary.r, theme.primary.g, theme.primary.b, 0.45f);
        slotButtonEffect.border.width = 1f;
        slotButtonEffect.border.color = new Color(theme.panelBorder.r, theme.panelBorder.g, theme.panelBorder.b, 0.8f);
        slotButtonEffect.text.color = new Color(theme.text.r, theme.text.g, theme.text.b, 1f);
        slotButtonEffect.text.hoverColor = new Color(Color.WHITE);
        slotButtonEffect.text.size = 15f;
        slotButtonEffect.accent.color = new Color(theme.primary.r, theme.primary.g, theme.primary.b, 1f);

        titleLabel = new VectorLabel(font, titleEffect, "");
        typeTagLabel = new VectorLabel(font, "", theme.text);
        addActor(titleLabel);
        addActor(typeTagLabel);

        for (int i = 0; i < INFO_ROWS; i++) {
            infoLabels[i] = new VectorLabel(font, "", theme.text);
            addActor(infoLabels[i]);
        }

        detailsButton = new VectorButton(sr, font, slotButtonEffect, "详细 >>", () -> {
            if (actionListener != null) actionListener.onAction(ACTION_OPEN_DETAILS);
        });
        addActor(detailsButton);

        setSize(PANEL_WIDTH, PANEL_HEIGHT);
        layoutStaticSlots();
        setVisible(false);
    }

    public void setActionListener(ActionListener listener) {
        this.actionListener = listener;
    }

    /** 固定槽位布局（标题/信息/详细按钮），构造时执行一次喵 */
    private void layoutStaticSlots() {
        // 标题行（顶部）：名称在左，详细按钮固定最右，类型标签在详细按钮左侧喵
        float titleY = PANEL_HEIGHT - PAD - TITLE_HEIGHT;
        titleLabel.setPosition(PAD, titleY);
        detailsButton.setPosition(PANEL_WIDTH - PAD - DETAILS_BUTTON_WIDTH, titleY);
        detailsButton.setSize(DETAILS_BUTTON_WIDTH, TITLE_HEIGHT);

        // 信息槽：标题行之下，自上而下固定 INFO_ROWS 行喵
        for (int i = 0; i < INFO_ROWS; i++) {
            float y = titleY - (i + 1) * INFO_LINE_HEIGHT;
            infoLabels[i].setPosition(PAD, y);
        }
    }

    /** 计算网格槽位的局部坐标（槽位 0 在左上，按行优先排列）喵 */
    private float slotX(int slot) {
        return PAD + (slot % GRID_COLS) * (SLOT_WIDTH + SLOT_GAP);
    }

    private float slotY(int slot) {
        // 网格位于面板底部，槽位 0 在第一行（上方）喵
        return PAD + (GRID_ROWS - 1 - slot / GRID_COLS) * (SLOT_HEIGHT + SLOT_GAP);
    }

    /** 显示指定实体的内容（结构不变，只刷新槽位内容）喵 */
    public void showEntity(EntityInfoViewModel vm) {
        titleLabel.setText(vm.title);
        typeTagLabel.setText(vm.typeLabel);
        if (vm.typeColor != null) {
            typeTagLabel.setTextColor(vm.typeColor);
        }
        // 类型标签右对齐到详细按钮左侧喵
        float titleY = PANEL_HEIGHT - PAD - TITLE_HEIGHT;
        typeTagLabel.setPosition(
                PANEL_WIDTH - PAD - DETAILS_BUTTON_WIDTH - SLOT_GAP - typeTagLabel.getWidth(), titleY);

        // 信息槽填充（无数据的行清空）喵
        for (int i = 0; i < INFO_ROWS; i++) {
            if (i < vm.summaryFields.size()) {
                EntityInfoViewModel.FieldEntry f = vm.summaryFields.get(i);
                infoLabels[i].setText(f.key() + ": " + f.value());
            } else {
                infoLabels[i].setText("");
            }
        }

        // 指令网格填充：清除旧按钮，按序填入槽位，超出槽位数量的指令截断喵
        for (int i = 0; i < gridButtons.length; i++) {
            if (gridButtons[i] != null) {
                gridButtons[i].remove();
                gridButtons[i] = null;
            }
        }
        // TODO 指令分页：指令超过网格槽位数时目前截断，后续参考 SC 做翻页
        int count = Math.min(vm.actions.size(), gridButtons.length);
        for (int i = 0; i < count; i++) {
            EntityInfoViewModel.ActionEntry a = vm.actions.get(i);
            VectorButton b = new VectorButton(sr, font, slotButtonEffect, a.label(), () -> {
                if (a.enabled() && actionListener != null) {
                    actionListener.onAction(a.id());
                }
            });
            b.setPosition(slotX(i), slotY(i));
            b.setSize(SLOT_WIDTH, SLOT_HEIGHT);
            if (!a.enabled()) {
                // VectorButton 无禁用态，用透明度表达置灰喵
                b.setColor(1f, 1f, 1f, DISABLED_ALPHA);
            }
            addActor(b);
            gridButtons[i] = b;
        }

        setVisible(true);
    }

    /** 清空选中，隐藏面板喵 */
    public void clearEntity() {
        setVisible(false);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        float x = getX();
        float y = getY();
        float w = getWidth();
        float h = getHeight();
        float a = getColor().a * parentAlpha;

        batch.end();

        sr.setProjectionMatrix(batch.getProjectionMatrix());
        sr.setTransformMatrix(batch.getTransformMatrix());

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // 背景 — 跟随 theme.panelBg 喵
        Color bg = theme.panelBg;
        sr.begin(ShapeRenderer.ShapeType.Filled);
        sr.setColor(bg.r, bg.g, bg.b, bg.a * a);
        sr.rect(x, y, w, h);
        sr.end();

        Gdx.gl.glLineWidth(BORDER_WIDTH);
        sr.begin(ShapeRenderer.ShapeType.Line);

        // 外边框 — 跟随 theme.panelBorder 喵
        Color border = theme.panelBorder;
        sr.setColor(border.r, border.g, border.b, border.a * a);
        sr.rect(x, y, w, h);

        // 空指令槽边框（暗色，体现固定网格结构）喵
        sr.setColor(border.r, border.g, border.b, border.a * a * EMPTY_SLOT_ALPHA);
        for (int i = 0; i < gridButtons.length; i++) {
            if (gridButtons[i] == null) {
                sr.rect(x + slotX(i), y + slotY(i), SLOT_WIDTH, SLOT_HEIGHT);
            }
        }
        sr.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();

        super.draw(batch, parentAlpha);
    }
}
