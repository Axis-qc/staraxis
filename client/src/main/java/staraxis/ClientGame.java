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
import staraxis.game.astro.StarSystem;
import staraxis.logging.GdxToSlf4jLogger;
import staraxis.render.ViewManager;
import staraxis.render.WorldCamera;
import staraxis.render.galaxy.GalaxyViewRenderer;
import staraxis.render.picking.RayPicker;
import staraxis.render.system.SystemViewRenderer;
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
 * ClientGame.
 *
 * 原生客户端入口。默认使用 3D 宇宙渲染管线读取游戏快照。
 * Galaxy View 显示所有恒星（STAR），System View 显示选中星系的恒星+行星轨道。
 */
public class ClientGame implements ApplicationListener {

    private Stage stage;
    private Gui gui;
    private StarAxisGameRuntime runtime;
    private StarfieldBackground starfield;

    // 3D 宇宙渲染管线
    private WorldCamera spaceCamera;
    private ViewManager viewManager;
    private GalaxyViewRenderer galaxyViewRenderer;
    private SystemViewRenderer systemViewRenderer;
    private RayPicker rayPicker;

    // 当前选中的恒星系（System View）
    private StarSystem currentSystem;

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

        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        initSpaceRendering();

        gui.showMainMenu();
    }

    private void initSpaceRendering() {
        spaceCamera = new WorldCamera();
        viewManager = new ViewManager();
        rayPicker = new RayPicker();
        galaxyViewRenderer = new GalaxyViewRenderer();
        systemViewRenderer = new SystemViewRenderer();
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
        if (spaceCamera != null) {
            spaceCamera.resize(width, height);
        }
        if (galaxyViewRenderer != null) {
            galaxyViewRenderer.resize(width, height);
        }
        if (systemViewRenderer != null) {
            systemViewRenderer.resize(width, height);
        }
        if (starfield != null) {
            starfield.resize(width, height);
        }
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();

        runtime = gui.getRuntime();
        if (runtime != null) {
            runtime.update(dt);
            runtime.publishRealtimeSnapshotIfNeeded();

            renderSpaceScene(dt);
        } else {
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            if (starfield != null) {
                starfield.act(dt);
                starfield.render();
            }
        }

        stage.act(dt);
        stage.draw();
    }

    private void renderSpaceScene(float dt) {
        spaceCamera.update(dt);

        Gdx.gl.glClearColor(0.005f, 0.005f, 0.02f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            if (viewManager.isInSystemView()) {
                viewManager.switchToGalaxy();
                currentSystem = null;
                systemViewRenderer.resetTime();
                spaceCamera.resetView();
            }
        }

        if (viewManager.isInGalaxyView()) {
            renderGalaxyView();
        } else if (viewManager.isInSystemView()) {
            renderSystemView(dt);
        }
    }

    private void renderGalaxyView() {
        var state = runtime.getRealTimeWorldStateReadonly();

        rayPicker.updateHovered(spaceCamera, state,
            Gdx.input.getX(), Gdx.input.getY(), galaxyViewRenderer);

        if (Gdx.input.isButtonJustPressed(com.badlogic.gdx.Input.Buttons.LEFT)) {
            long selectedStarId = rayPicker.getHoveredStarId();
            if (selectedStarId >= 0) {
                StarSystem system = findSystemByStarId(selectedStarId);
                if (system != null) {
                    currentSystem = system;
                    viewManager.switchToSystem(selectedStarId);
                    systemViewRenderer.resetTime();
                    spaceCamera.setZoom(5.0);
                    spaceCamera.target.set(0, 0, 0);
                }
            }
        }

        galaxyViewRenderer.render(state, spaceCamera, rayPicker.getHoveredStarId());
    }

    private void renderSystemView(float dt) {
        if (currentSystem == null) return;

        systemViewRenderer.advanceTime(dt);
        systemViewRenderer.render(currentSystem, spaceCamera);
    }

    private StarSystem findSystemByStarId(long starId) {
        var ws = runtime.getWorldStateForSimOnly();
        if (ws == null || ws.astro == null) return null;

        for (StarSystem sys : ws.astro.getSystemsView()) {
            if (sys == null) continue;
            for (var star : sys.stars) {
                if (star != null && star.entityId == starId) {
                    return sys;
                }
            }
        }
        return null;
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
        if (galaxyViewRenderer != null) {
            galaxyViewRenderer.dispose();
            galaxyViewRenderer = null;
        }
        if (systemViewRenderer != null) {
            systemViewRenderer.dispose();
            systemViewRenderer = null;
        }
        if (runtime != null) {
            runtime.stop();
            runtime = null;
        }
        FontProvider.disposeAllIncremental();
    }
}
