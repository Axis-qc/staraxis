package staraxis;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import staraxis.render.util.MenuBackgroundLoader;
import staraxis.ui.FontProvider;
import staraxis.ui.Gui;
import staraxis.ui.UiPointerService;
import staraxis.ui.UiWindowManager;
import staraxis.ui.effects.EffectRegistry;
import staraxis.ui.i18n.I18nService;
import staraxis.ui.json.GameDataProvider;
import staraxis.ui.json.UiFactory;
import staraxis.ui.screens.InGameHudScreen;
import staraxis.ui.screens.SettingsScreen;
import staraxis.ui.screens.WorldSettingsScreen;
import staraxis.ui.settings.SettingsRepository;
import staraxis.ui.widgets.DevelopingDialog;
import staraxis.ui.widgets.StarfieldBackground;

/**
 * BaseUiInit.
 *
 * 提取 ClientGame 与 UiPreviewApp 的共享 UI 初始化逻辑。
 * 两边的 create() 中从 Stage 创建到 4 个 Screen 注册的流程完全一致，
 * 调用此类的静态方法执行共享初始化，再各自补充独有逻辑。
 */
public class BaseUiInit {

    public final Stage stage;
    public final Gui gui;
    public final StarfieldBackground starfield;
    public final ShapeRenderer sr;
    public final BitmapFont vectorFont;
    public final I18nService i18nService;

    private BaseUiInit(Stage stage, Gui gui, StarfieldBackground starfield,
                       ShapeRenderer sr, BitmapFont vectorFont, I18nService i18nService) {
        this.stage = stage;
        this.gui = gui;
        this.starfield = starfield;
        this.sr = sr;
        this.vectorFont = vectorFont;
        this.i18nService = i18nService;
    }

    /**
     * 执行共享 UI 初始化，调用方自行设置 InputMultiplexer 和补充独有逻辑。
     *
     * @return 包含所有共享初始化产物的 BaseUiInit 实例
     */
    public static BaseUiInit init() {
        Stage stage = new Stage(new ScreenViewport());

        I18nService i18nService = new I18nService();
        i18nService.load("zh");

        Gui gui = new Gui(stage, s -> {
        }, s -> {
        });
        gui.register(I18nService.class, i18nService);
        gui.register(SettingsRepository.class, new SettingsRepository());

        BitmapFont defaultFont = FontProvider.createDefaultFont();
        BitmapFont ttfFont = FontProvider.createUiFont();
        BitmapFont finalFont = (ttfFont != null) ? ttfFont : defaultFont;
        BitmapFont vectorTtfFont = FontProvider.createVectorFont();
        BitmapFont vectorFont = (vectorTtfFont != null) ? vectorTtfFont : finalFont;

        gui.register(BitmapFont.class, vectorFont);
        gui.initJsonUi();

        ShapeRenderer sr = new ShapeRenderer();
        gui.register(ShapeRenderer.class, sr);

        // 统一 UI 命中守卫：全部 UI 交互区域集中注册，3D 层零 UI 知识（Phase 2.6）喵
        UiPointerService pointerService = new UiPointerService();
        gui.register(UiPointerService.class, pointerService);

        // 全局窗口管理器：管理非模态信息窗口（实体详情、舰队列表等），ESC 栈依赖；
        // 构造时自注册窗口 bounds 到 UiPointerService 喵
        gui.register(UiWindowManager.class, new UiWindowManager(stage, pointerService));

        StarfieldBackground starfield = new StarfieldBackground(sr, MenuBackgroundLoader.loadBackgroundImage());
        starfield.init(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        UiFactory factory = gui.get(UiFactory.class);
        EffectRegistry effectRegistry = gui.get(EffectRegistry.class);
        if (factory != null) {
            factory.setEffectRegistry(effectRegistry);
            factory.setShapeRenderer(sr);
            factory.setBitmapFont(vectorFont);
            factory.setDataProvider(new GameDataProvider());
        }

        WorldSettingsScreen worldSettingsScreen = new WorldSettingsScreen(gui);
        InGameHudScreen inGameHudScreen = new InGameHudScreen(gui);
        SettingsScreen settingsScreen = new SettingsScreen(gui);
        DevelopingDialog developingDialog = new DevelopingDialog(sr, vectorFont, i18nService);

        gui.register(WorldSettingsScreen.class, worldSettingsScreen);
        gui.register(InGameHudScreen.class, inGameHudScreen);
        gui.register(SettingsScreen.class, settingsScreen);
        gui.register(DevelopingDialog.class, developingDialog);

        return new BaseUiInit(stage, gui, starfield, sr, vectorFont, i18nService);
    }
}
