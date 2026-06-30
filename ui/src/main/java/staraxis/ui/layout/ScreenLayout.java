package staraxis.ui.layout;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import staraxis.ui.FontProvider;
import staraxis.ui.effects.EffectRegistry;
import staraxis.ui.effects.MenuEntryEffect;
import staraxis.ui.effects.VectorButtonEffect;
import staraxis.ui.theme.UiTheme;
import staraxis.ui.widgets.MenuEntry;
import staraxis.ui.widgets.StarfieldBackground;
import staraxis.ui.widgets.VectorButton;
import staraxis.ui.widgets.VectorLabel;

/**
 * 桌面端 UI 布局工厂 —— 提供统一的页面结构组件。
 *
 * 设计要点：
 * - 为所有 Screen 提供一致的组件创建方式，确保视觉统一。
 * - 组件颜色统一从 {@link UiTheme} 读取，禁止硬编码。
 * - 提供标准间距常量（padding、gap、entry 尺寸等）。
 *
 * 使用方式：
 * <pre>{@code
 *   ScreenLayout L = new ScreenLayout(gui, theme, sr, font);
 *   Stage stage = gui.getStage();
 *   L.addBackground(stage);
 *   stage.addActor(L.createTitle("StarAxis", 64, stage.getHeight() - 140));
 *   stage.addActor(L.createSubtitle("A 4X Space Strategy Game", 64, stage.getHeight() - 175));
 *   stage.addActor(L.createMenuEntry("新游戏", null, () -> { ... }, 64, y, 400, 44));
 *   stage.addActor(L.createVersionLabel(stage));
 * }</pre>
 */
public final class ScreenLayout {

    /** 标题区域字号 */
    public static final float TITLE_SIZE = 50f;
    /** 副标题区域字号 */
    public static final float SUBTITLE_SIZE = 30f;
    /** 菜单项高度 */
    public static final float ENTRY_HEIGHT = 44f;
    /** 菜单项宽度 */
    public static final float ENTRY_WIDTH = 400f;
    /** 菜单项间距 */
    public static final float ENTRY_GAP = 8f;
    /** 左边距 */
    public static final float PADDING_LEFT = 64f;
    /** 底栏高度 */
    public static final float FOOTER_Y = 28f;
    /** 右边距 */
    public static final float PADDING_RIGHT = 32f;

    private final UiTheme theme;
    private final ShapeRenderer sr;
    private final BitmapFont font;
    private final EffectRegistry effectRegistry;

    /** 星空背景实例（addBackground 后可用） */
    private StarfieldBackground starfield;

    public ScreenLayout(UiTheme theme, ShapeRenderer sr, BitmapFont font, EffectRegistry effectRegistry) {
        this.theme = theme;
        this.sr = sr;
        this.font = font;
        this.effectRegistry = effectRegistry;
    }

    /**
     * 获取当前使用的主题实例。
     */
    public UiTheme getTheme() {
        return theme;
    }

    // ===== 背景 =====

    /**
     * 添加星空背景到 Stage 底层。
     * 应在添加其他 UI 元素前调用。
     *
     * @param stage 目标 Stage
     * @return StarfieldBackground 实例（可用于后续 resize）
     */
    public StarfieldBackground addBackground(Stage stage) {
        this.starfield = new StarfieldBackground(sr, null);
        this.starfield.init((int) stage.getWidth(), (int) stage.getHeight());
        return this.starfield;
    }

    /**
     * 获取已创建的星空背景实例。
     */
    public StarfieldBackground getStarfield() {
        return starfield;
    }

    // ===== 标题组件 =====

    /**
     * 创建页面主标题（对应 Web game-title）。
     */
    public VectorLabel createTitle(String text, float x, float y) {
        VectorLabel label = new VectorLabel(font, text, theme.title);
        label.setSize(ENTRY_WIDTH, TITLE_SIZE);
        label.setPosition(x, y);
        return label;
    }

    /**
     * 创建页面副标题（对应 Web game-subtitle）。
     */
    public VectorLabel createSubtitle(String text, float x, float y) {
        VectorLabel label = new VectorLabel(font, text, theme.subtitle);
        label.setSize(ENTRY_WIDTH, SUBTITLE_SIZE);
        label.setPosition(x, y);
        return label;
    }

    /**
     * 创建页面标题（自定义颜色，用于非主菜单页面如"加载存档"）。
     */
    public VectorLabel createPageTitle(String text, float x, float y) {
        VectorLabel label = new VectorLabel(font, text, theme.title);
        label.setSize(ENTRY_WIDTH, TITLE_SIZE);
        label.setPosition(x, y);
        return label;
    }

    // ===== 菜单项 =====

    /**
     * 创建标准菜单项（使用主题的 menu_item_primary effect）。
     */
    public MenuEntry createMenuEntry(String text, String tagText, Runnable action,
                                     float x, float y, float w, float h) {
        MenuEntryEffect effect = null;
        if (effectRegistry != null) {
            effect = effectRegistry.get("menu_item_primary", MenuEntryEffect.class);
        }
        MenuEntry entry;
        if (effect != null) {
            entry = new MenuEntry(sr, font, effect, text, tagText, action);
        } else {
            entry = new MenuEntry(sr, font, text, tagText, action);
        }
        entry.setSize(w, h);
        entry.setPosition(x, y);
        return entry;
    }

    /**
     * 创建次级菜单项（使用主题的 menu_item_secondary effect）。
     */
    public MenuEntry createSecondaryMenuEntry(String text, String tagText, Runnable action,
                                              float x, float y, float w, float h) {
        MenuEntryEffect effect = null;
        if (effectRegistry != null) {
            effect = effectRegistry.get("menu_item_secondary", MenuEntryEffect.class);
        }
        MenuEntry entry;
        if (effect != null) {
            entry = new MenuEntry(sr, font, effect, text, tagText, action);
        } else {
            entry = new MenuEntry(sr, font, text, tagText, action);
        }
        entry.setSize(w, h);
        entry.setPosition(x, y);
        return entry;
    }

    // ===== 按钮 =====

    /**
     * 创建主题按钮（使用主题的 primary_button effect）。
     */
    public VectorButton createPrimaryButton(String text, Runnable action,
                                            float x, float y, float w, float h) {
        VectorButtonEffect effect = null;
        if (effectRegistry != null) {
            effect = effectRegistry.get("primary_button", VectorButtonEffect.class);
        }
        VectorButton btn;
        if (effect != null) {
            btn = new VectorButton(sr, font, effect, text, action);
        } else {
            btn = new VectorButton(sr, font, text, action);
        }
        btn.setSize(w, h);
        btn.setPosition(x, y);
        return btn;
    }

    /**
     * 创建返回按钮（使用主题色，放在页面左下角）。
     */
    public VectorButton createBackButton(String text, Runnable action) {
        return createPrimaryButton(text, action, PADDING_LEFT, FOOTER_Y + 16, 160, 40);
    }

    // ===== 底部元素 =====

    /**
     * 创建版本号标签（右下角），自动计算位置。
     */
    public VectorLabel createVersionLabel(Stage stage, String versionText) {
        VectorLabel label = new VectorLabel(font, versionText, theme.versionDim);
        float w = FontProvider.VECTOR_FONT_SIZE; // 近似宽度
        label.setSize(w, 20);
        label.setPosition(stage.getWidth() - w - PADDING_RIGHT, FOOTER_Y);
        return label;
    }

    // ===== 工具方法 =====

    /**
     * 计算菜单项在列表中的 Y 坐标（从顶部向下排列）。
     *
     * @param baseY    第一个菜单项的 Y 坐标（通常为 stage.getHeight() - headerHeight）
     * @param index    菜单项索引（从 0 开始）
     * @return 该菜单项的 Y 坐标
     */
    public static float menuY(float baseY, int index) {
        return baseY - index * (ENTRY_HEIGHT + ENTRY_GAP);
    }

    /**
     * 计算内容区域顶部 Y 坐标（标题+副标题之后的空白区域起始位置）。
     * 通常标题在 stage.getHeight() - 140，副标题在 stage.getHeight() - 175，
     * 内容区域从 stage.getHeight() - 240 开始。
     */
    public static float contentTop(Stage stage) {
        return stage.getHeight() - 240f;
    }
}
