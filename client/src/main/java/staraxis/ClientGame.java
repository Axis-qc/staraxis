package staraxis;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import staraxis.game.GameRuntime;
import staraxis.game.SimpleGameRuntime;
import staraxis.logging.GdxToSlf4jLogger;
import staraxis.ui.FontProvider;
import staraxis.ui.Gui;
import staraxis.ui.UiSkinLoader;
import staraxis.ui.console.DevConsole;
import staraxis.ui.i18n.I18nService;
import staraxis.ui.screens.MainMenuScreen;
import staraxis.ui.screens.SettingsScreen;
import staraxis.ui.settings.GameSettings;
import staraxis.ui.settings.SettingsRepository;
import staraxis.ui.widgets.DevelopingDialog;

public class ClientGame implements ApplicationListener {

    private Stage stage;
    private Gui gui;
    private GameRuntime gameRuntime;

    private SpriteBatch backgroundBatch;
    private Texture backgroundTexture;

    private DevConsole devConsole;
    private InputMultiplexer inputMultiplexer;

    private float tickAccumulatorSeconds;
    private static final float TICK_SECONDS = 1f / 25f;

    @Override
    public void create() {
        if (Gdx.app != null) {
            Gdx.app.setApplicationLogger(new GdxToSlf4jLogger());
        }

        stage = new Stage(new ScreenViewport());
        inputMultiplexer = new InputMultiplexer();
        inputMultiplexer.addProcessor(stage);
        Gdx.input.setInputProcessor(inputMultiplexer);

        backgroundBatch = new SpriteBatch();
        backgroundTexture = new Texture(Gdx.files.internal("world/world_bg.png"));

        I18nService i18nService = new I18nService();
        i18nService.load("zh");

        Skin skin = UiSkinLoader.loadDefault("ui/uiskin/uiskin.json");

        BitmapFont defaultFont = FontProvider.createDefaultFont();
        BitmapFont ttfFont = FontProvider.tryCreateFontFromTtfOrNull("fonts/chinese/AlibabaPuHuiTi-3-65-Medium.ttf",
                28);
        BitmapFont finalFont = (ttfFont != null) ? ttfFont : defaultFont;

        skin.add("default-font", finalFont, BitmapFont.class);

        TextButton.TextButtonStyle textButtonStyle = skin.get(TextButton.TextButtonStyle.class);
        textButtonStyle.font = finalFont;

        Label.LabelStyle labelStyle = skin.get(Label.LabelStyle.class);
        labelStyle.font = finalFont;

        gui = new Gui(stage, this::applyUiScale, this::applyFontScale);
        gui.register(I18nService.class, i18nService);
        gui.register(Skin.class, skin);
        gui.initJsonUi();

        // Settings
        SettingsRepository settingsRepository = new SettingsRepository();
        gui.register(SettingsRepository.class, settingsRepository);
        applyRuntimeSettings(settingsRepository.load());

        // Screens
        MainMenuScreen mainMenuScreen = new MainMenuScreen(gui);
        gui.register(MainMenuScreen.class, mainMenuScreen);
        SettingsScreen settingsScreen = new SettingsScreen(gui);
        gui.register(SettingsScreen.class, settingsScreen);

        // Dialogs
        DevelopingDialog developingDialog = new DevelopingDialog(skin, i18nService);
        gui.register(DevelopingDialog.class, developingDialog);

        // Console
        devConsole = new DevConsole(gui);
        gui.register(DevConsole.class, devConsole);
        inputMultiplexer.addProcessor(devConsole);

        gameRuntime = new SimpleGameRuntime();
        gameRuntime.start();

        gui.showMainMenu();
    }

    public void applyRuntimeSettings(GameSettings settings) {
        Gdx.graphics.setVSync(settings.vsync);
        applyUiScale(settings.uiScale);
        applyFontScale(settings.fontScale);
        Gdx.app.log("ClientGame",
                "Applied runtime settings (VSync: " + settings.vsync + ", UIScale: " + settings.uiScale + ")");
    }

    /**
     * 应用全局 UI 缩放。
     * 通过调整 Viewport 的 unitsPerPixel 实现。
     */
    public void applyUiScale(float scale) {
        if (stage.getViewport() instanceof ScreenViewport) {
            ((ScreenViewport) stage.getViewport()).setUnitsPerPixel(1.0f / scale);
            Gdx.app.log("ClientGame", "UI scale applied: " + scale);
        }
    }

    /**
     * 应用全局字体缩放。
     * 遍历 Skin 中所有 BitmapFont 并设置 scale。
     */
    public void applyFontScale(float scale) {
        Skin skin = gui.get(Skin.class);
        if (skin != null) {
            for (ObjectMap.Entry<String, BitmapFont> entry : skin.getAll(BitmapFont.class)) {
                entry.value.getData().setScale(scale);
            }
            stage.getViewport().apply(true);
            stage.act(0f);
            Gdx.app.log("ClientGame", "Font scale applied: " + scale);
        }
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
        if (devConsole != null) {
            devConsole.onResize();
        }
    }

    @Override
    public void render() {
        float frameDt = Gdx.graphics.getDeltaTime();

        tickAccumulatorSeconds += frameDt;
        while (tickAccumulatorSeconds >= TICK_SECONDS) {
            if (gameRuntime != null) {
                gameRuntime.update(TICK_SECONDS);
            }
            tickAccumulatorSeconds -= TICK_SECONDS;
        }

        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        if (backgroundBatch != null && backgroundTexture != null) {
            backgroundBatch.begin();
            backgroundBatch.draw(backgroundTexture, 0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            backgroundBatch.end();
        }

        stage.act(frameDt);
        stage.draw();
    }

    @Override
    public void pause() {
    }

    @Override
    public void resume() {
    }

    @Override
    public void dispose() {
        if (gameRuntime != null) {
            gameRuntime.stop();
        }
        if (gui != null) {
            Skin skin = gui.get(Skin.class);
            if (skin != null) {
                skin.dispose();
            }
        }
        if (stage != null) {
            stage.dispose();
        }
        if (backgroundBatch != null) {
            backgroundBatch.dispose();
            backgroundBatch = null;
        }
        if (backgroundTexture != null) {
            backgroundTexture.dispose();
            backgroundTexture = null;
        }
    }
}
