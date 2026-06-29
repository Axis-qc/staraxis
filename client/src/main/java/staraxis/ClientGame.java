package staraxis;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import staraxis.game.StarAxisGameRuntime;
import staraxis.logging.GdxToSlf4jLogger;
import staraxis.render.NativeWorldRenderer;
import staraxis.render.TestSceneRenderer;
import staraxis.ui.FontProvider;
import staraxis.ui.Gui;
import staraxis.ui.UiSkinLoader;
import staraxis.ui.effects.EffectRegistry;
import staraxis.ui.i18n.I18nService;
import staraxis.ui.json.GameDataProvider;
import staraxis.ui.json.UiFactory;
import staraxis.ui.settings.SettingsRepository;
import staraxis.ui.screens.InGameHudScreen;
import staraxis.ui.screens.SettingsScreen;
import staraxis.ui.screens.WorldSettingsScreen;
import staraxis.ui.widgets.DevelopingDialog;
import staraxis.ui.widgets.StarfieldBackground;

/**
 * ClientGame
 *
 * 原生客户端应用入口：负责创建本机 Host 运行时、推进模拟、读取只读快照并交给 OpenGL 渲染层。
 *
 * UI 层使用 Scene2D Stage + Gui 覆盖在世界渲染之上。
 * 启动时显示主菜单，通过菜单流程进入游戏。
 */
public class ClientGame implements ApplicationListener {

    private Stage stage;
    private Gui gui;
    private StarAxisGameRuntime runtime;
    private NativeWorldRenderer worldRenderer;
    private StarfieldBackground starfield;

    /** 3D 宇宙测试场景（按 T 键切换）喵 */
    private TestSceneRenderer testScene;
    private boolean testMode = false;

    @Override
    public void create() {
        if (Gdx.app != null) {
            Gdx.app.setApplicationLogger(new GdxToSlf4jLogger());
        }

        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(new InputMultiplexer(stage));

        I18nService i18nService = new I18nService();
        i18nService.load("zh");

        gui = new Gui(stage, s -> {}, s -> {});
        gui.register(I18nService.class, i18nService);
        gui.register(SettingsRepository.class, new SettingsRepository());

        Skin skin = UiSkinLoader.loadDefault("ui/uiskin/uiskin.json");

        BitmapFont defaultFont = FontProvider.createDefaultFont();
        BitmapFont ttfFont = FontProvider.createUiFont();
        BitmapFont finalFont = (ttfFont != null) ? ttfFont : defaultFont;
        BitmapFont vectorTtfFont = FontProvider.createVectorFont();
        BitmapFont vectorFont = (vectorTtfFont != null) ? vectorTtfFont : finalFont;

        skin.add("default-font", finalFont, BitmapFont.class);

        TextButton.TextButtonStyle textButtonStyle = skin.get(TextButton.TextButtonStyle.class);
        textButtonStyle.font = finalFont;

        Label.LabelStyle labelStyle = skin.get(Label.LabelStyle.class);
        labelStyle.font = finalFont;

        gui.register(Skin.class, skin);
        gui.initJsonUi();

        ShapeRenderer sr = new ShapeRenderer();
        gui.register(ShapeRenderer.class, sr);

        starfield = new StarfieldBackground(sr, loadMainMenuBackgroundImage());
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
        DevelopingDialog developingDialog = new DevelopingDialog(skin, i18nService);

        gui.register(WorldSettingsScreen.class, worldSettingsScreen);
        gui.register(InGameHudScreen.class, inGameHudScreen);
        gui.register(SettingsScreen.class, settingsScreen);
        gui.register(DevelopingDialog.class, developingDialog);

        worldRenderer = new NativeWorldRenderer();
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        gui.showMainMenu();
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
        if (worldRenderer != null) {
            worldRenderer.resize(width, height);
        }
        if (testScene != null) {
            testScene.resize(width, height);
        }
        if (starfield != null) {
            starfield.resize(width, height);
        }
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();

        // T 键切换测试场景喵
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.T)) {
            testMode = !testMode;
            if (testMode && testScene == null) {
                testScene = new TestSceneRenderer();
                testScene.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            }
        }

        // 测试模式：纯 3D 场景，不跑游戏逻辑喵
        if (testMode && testScene != null) {
            testScene.render(dt);
            return;
        }

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        runtime = gui.getRuntime();
        if (runtime != null) {
            runtime.update(dt);
            runtime.publishRealtimeSnapshotIfNeeded();

            if (worldRenderer != null) {
                worldRenderer.render(runtime.getRealTimeWorldStateReadonly());
            }

            InGameHudScreen hud = gui.get(InGameHudScreen.class);
            if (hud != null) {
                hud.refreshHud();
            }
        } else {
            if (starfield != null) {
                starfield.act(dt);
                starfield.render();
            }
        }

        stage.act(dt);
        stage.draw();
    }

    private String loadMainMenuBackgroundImage() {
        JsonValue root = new JsonReader().parse(Gdx.files.internal("ui/gameui/main-menu/main_menu.json"));
        JsonValue props = root.get("properties");
        return props == null ? null : props.getString("backgroundImage", null);
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        if (gui != null) {
            Skin skin = gui.get(Skin.class);
            if (skin != null) {
                skin.dispose();
            }
            ShapeRenderer sr = gui.get(ShapeRenderer.class);
            if (sr != null) {
                sr.dispose();
            }
        }
        if (stage != null) {
            stage.dispose();
        }
        if (starfield != null) {
            starfield.dispose();
            starfield = null;
        }
        if (worldRenderer != null) {
            worldRenderer.dispose();
            worldRenderer = null;
        }
        if (testScene != null) {
            testScene.dispose();
            testScene = null;
        }
        if (runtime != null) {
            runtime.stop();
            runtime = null;
        }
        FontProvider.disposeAllIncremental();
    }
}
