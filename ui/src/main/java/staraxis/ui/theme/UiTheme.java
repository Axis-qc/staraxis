package staraxis.ui.theme;

import com.badlogic.gdx.graphics.Color;
import staraxis.ui.effects.EffectDef;
import staraxis.ui.effects.EffectRegistry;
import staraxis.ui.effects.MenuEntryEffect;
import staraxis.ui.effects.VectorButtonEffect;
import staraxis.ui.effects.VectorLabelEffect;

/**
 * UI 主题门面 —— 桌面端统一颜色管理入口。
 *
 * 设计要点：
 * - 从 {@link EffectRegistry} 读取主题定义，提供语义化的颜色 token。
 * - 所有 UI Screen / Widget 通过本类获取颜色，禁止硬编码颜色常量。
 * - 语义 token 与 Web 端 CSS 变量对应，确保跨端视觉一致。
 * - 若 EffectRegistry 未初始化，回退到内置默认值。
 *
 * 使用方式：
 * 
 * <pre>{@code
 * UiTheme theme = UiTheme.from(gui.get(EffectRegistry.class));
 * Color titleColor = theme.title();
 * Color accentColor = theme.accent();
 * }</pre>
 */
public final class UiTheme {

    /** 主题主色（对应 Web --sa-primary / --glow-color），默认 #4AC9FF */
    public final Color primary;
    /** 主色发光（对应 Web --sa-glow-soft），默认 #4AC9FF 18% */
    public final Color primaryGlow;
    /** 主色高亮（略亮版），默认 #7ADFFF */
    public final Color primaryLight;

    /** 背景色，默认 #0A0F1E */
    public final Color background;
    /** 面板背景（半透明），对应 Web --sa-panel-bg */
    public final Color panelBg;
    /** 面板边框，对应 Web --sa-panel-border */
    public final Color panelBorder;

    /** 正文颜色，对应 Web --sa-text */
    public final Color text;
    /** 弱化文字，对应 Web --sa-text-muted */
    public final Color textMuted;
    /** 高亮文字（hover），对应 Web --sa-text-hover */
    public final Color textHover;

    /** 标题颜色（纯白），对应 Web title_hero */
    public final Color title;
    /** 副标题颜色（主题色），对应 Web subtitle_accent */
    public final Color subtitle;

    /** 成功色 */
    public final Color success;
    /** 警告色 */
    public final Color warning;
    /** 危险色 */
    public final Color danger;

    /** 菜单项 - 主级 */
    public final MenuEntryEffect menuPrimary;
    /** 菜单项 - 次级 */
    public final MenuEntryEffect menuSecondary;
    /** 按钮 - 主按钮 */
    public final VectorButtonEffect buttonPrimary;

    /** 版本号文字颜色 */
    public final Color versionDim;

    // ---- 默认值（EffectRegistry 不可用时使用） ----

    private static final Color DEF_PRIMARY = new Color(0.29f, 0.79f, 1f, 1f); // #4AC9FF
    private static final Color DEF_PRIMARY_GLOW = new Color(0.29f, 0.79f, 1f, 0.18f);
    private static final Color DEF_PRIMARY_LIGHT = new Color(0.48f, 0.87f, 1f, 1f); // #7ADFFF
    private static final Color DEF_BG = new Color(0.039f, 0.059f, 0.118f, 1f); // #0A0F1E
    private static final Color DEF_PANEL_BG = new Color(0.039f, 0.098f, 0.184f, 0.6f);
    private static final Color DEF_PANEL_BORDER = new Color(0.29f, 0.79f, 1f, 0.3f);
    private static final Color DEF_TEXT = new Color(0.82f, 0.84f, 0.90f, 1f); // #D1D5DB
    private static final Color DEF_TEXT_MUTED = new Color(0.82f, 0.84f, 0.90f, 0.72f);
    private static final Color DEF_TEXT_HOVER = Color.WHITE;
    private static final Color DEF_TITLE = Color.WHITE;
    private static final Color DEF_SUBTITLE = DEF_PRIMARY;
    private static final Color DEF_SUCCESS = new Color(0.067f, 0.725f, 0.504f, 1f); // #10B981
    private static final Color DEF_WARNING = new Color(0.96f, 0.62f, 0.04f, 1f); // #F59E0B
    private static final Color DEF_DANGER = new Color(0.937f, 0.267f, 0.267f, 1f); // #EF4444
    private static final Color DEF_VERSION_DIM = new Color(0.545f, 0.58f, 0.62f, 0.53f);

    private UiTheme(EffectRegistry registry) {
        if (registry != null) {
            // 从 EffectRegistry 读取各 effect 定义
            MenuEntryEffect menuPrim = registry.get("menu_item_primary", MenuEntryEffect.class);
            MenuEntryEffect menuSec = registry.get("menu_item_secondary", MenuEntryEffect.class);
            VectorButtonEffect btnPrim = registry.get("primary_button", VectorButtonEffect.class);
            VectorLabelEffect titleHero = registry.get("title_hero", VectorLabelEffect.class);
            VectorLabelEffect subtitleAccent = registry.get("subtitle_accent", VectorLabelEffect.class);
            VectorLabelEffect versionDimEffect = registry.get("version_dim", VectorLabelEffect.class);

            // 从主题 effect 推导语义颜色
            this.primary = safeColor(menuPrim != null ? menuPrim.bullet.hoverColor : null, DEF_PRIMARY);
            this.primaryGlow = new Color(primary.r, primary.g, primary.b, 0.18f);
            this.primaryLight = safeColor(subtitleAccent != null ? subtitleAccent.text.color : null, DEF_PRIMARY_LIGHT);
            this.background = DEF_BG; // 背景暂时保持默认，未来可通过 container effect 覆盖
            this.panelBg = DEF_PANEL_BG;
            this.panelBorder = new Color(primary.r, primary.g, primary.b, 0.3f);

            this.text = safeColor(menuPrim != null ? menuPrim.text.color : null, DEF_TEXT);
            this.textMuted = safeColor(menuSec != null ? menuSec.text.color : null, DEF_TEXT_MUTED);
            this.textHover = safeColor(menuPrim != null ? menuPrim.text.hoverColor : null, DEF_TEXT_HOVER);

            this.title = safeColor(titleHero != null ? titleHero.text.color : null, DEF_TITLE);
            this.subtitle = safeColor(subtitleAccent != null ? subtitleAccent.text.color : null, DEF_SUBTITLE);

            this.versionDim = safeColor(versionDimEffect != null ? versionDimEffect.text.color : null, DEF_VERSION_DIM);

            // 保留原始 EffectDef 引用，供 Widget 直接使用
            this.menuPrimary = menuPrim;
            this.menuSecondary = menuSec;
            this.buttonPrimary = btnPrim;
        } else {
            // 完全回退到默认值
            this.primary = DEF_PRIMARY;
            this.primaryGlow = DEF_PRIMARY_GLOW;
            this.primaryLight = DEF_PRIMARY_LIGHT;
            this.background = DEF_BG;
            this.panelBg = DEF_PANEL_BG;
            this.panelBorder = DEF_PANEL_BORDER;
            this.text = DEF_TEXT;
            this.textMuted = DEF_TEXT_MUTED;
            this.textHover = DEF_TEXT_HOVER;
            this.title = DEF_TITLE;
            this.subtitle = DEF_SUBTITLE;
            this.versionDim = DEF_VERSION_DIM;
            this.menuPrimary = null;
            this.menuSecondary = null;
            this.buttonPrimary = null;
        }

        this.success = DEF_SUCCESS;
        this.warning = DEF_WARNING;
        this.danger = DEF_DANGER;
    }

    /**
     * 从 EffectRegistry 构建主题实例。
     *
     * @param registry EffectRegistry，可为 null（使用默认值）
     * @return UiTheme 实例
     */
    public static UiTheme from(EffectRegistry registry) {
        return new UiTheme(registry);
    }

    /**
     * 创建纯默认主题（EffectRegistry 不可用时的兜底方案）。
     */
    public static UiTheme defaults() {
        return new UiTheme(null);
    }

    private static Color safeColor(Color value, Color fallback) {
        return value != null ? value : fallback;
    }
}
