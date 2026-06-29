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
import staraxis.game.space.galaxy.GalaxyConfig;
import staraxis.game.space.galaxy.GalaxyData;
import staraxis.game.space.galaxy.GalaxyGenerator;
import staraxis.game.space.galaxy.GalaxyGeneratorFactory;
import staraxis.game.space.system.StarSystemData;
import staraxis.game.space.system.StarSystemGenerator;
import staraxis.logging.GdxToSlf4jLogger;
import staraxis.render.NativeWorldRenderer;
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

    /** 3D 宇宙渲染管线 */
    private WorldCamera spaceCamera;
    private ViewManager viewManager;
    private GalaxyData galaxyData;
    private GalaxyViewRenderer galaxyViewRenderer;
    private SystemViewRenderer systemViewRenderer;
    private StarSystemGenerator starSystemGenerator;
    private StarSystemData currentSystem;
    private RayPicker rayPicker;
    private boolean spaceMode = false;

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

        initSpaceRendering();

        gui.showMainMenu();
    }

    /**
     * 初始化 3D 宇宙渲染管线。
     */
    private void initSpaceRendering() {
        spaceCamera = new WorldCamera();
        viewManager = new ViewManager();
        rayPicker = new RayPicker();
        galaxyViewRenderer = new GalaxyViewRenderer();
        systemViewRenderer = new SystemViewRenderer();
        starSystemGenerator = new StarSystemGenerator();

        // 生成默认螺旋星系
        GalaxyConfig config = GalaxyConfig.defaultSpiral();
        config.starCount = 5000;
        config.worldSeed = 42L;
        GalaxyGenerator generator = GalaxyGeneratorFactory.create(config.galaxyType);
        galaxyData = generator.generate(config);
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
        if (worldRenderer != null) {
            worldRenderer.resize(width, height);
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

        // T 键切换 3D 宇宙模式
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.T)) {
            spaceMode = !spaceMode;
            if (spaceMode) {
                spaceCamera.resetView();
                viewManager.switchToGalaxy();
            }
        }

        // 3D 宇宙模式渲染
        if (spaceMode) {
            renderSpaceScene(dt);
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

    /**
     * 渲染 3D 宇宙场景。
     */
    private void renderSpaceScene(float dt) {
        spaceCamera.update(dt);

        Gdx.gl.glClearColor(0.005f, 0.005f, 0.02f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // ESC 返回星系视图
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

    /**
     * 渲染星系视图。
     */
    private void renderGalaxyView() {
        // 更新悬停恒星
        rayPicker.updateHovered(spaceCamera, galaxyData,
            Gdx.input.getX(), Gdx.input.getY());

        // 点击选中恒星 -> 进入系统视图
        if (Gdx.input.isButtonJustPressed(com.badlogic.gdx.Input.Buttons.LEFT)) {
            long selectedStarId = rayPicker.getHoveredStarId();
            if (selectedStarId >= 0) {
                var star = galaxyData.getStar(selectedStarId);
                if (star != null) {
                    currentSystem = starSystemGenerator.generate(star, galaxyData.worldSeed);
                    viewManager.switchToSystem(selectedStarId);
                    systemViewRenderer.resetTime();
                    spaceCamera.setZoom(5.0);
                    spaceCamera.target.set(0, 0, 0);
                }
            }
        }

        galaxyViewRenderer.render(galaxyData, spaceCamera, rayPicker.getHoveredStarId());
    }

    /**
     * 渲染恒星系视图。
     */
    private void renderSystemView(float dt) {
        if (currentSystem == null) return;

        // 推进模拟时间（1游戏秒 = 1真实秒）
        systemViewRenderer.advanceTime(dt);
        systemViewRenderer.render(currentSystem, spaceCamera);
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
