package staraxis;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.astro.StarSystem;
import staraxis.game.util.ProgressCallback;
import staraxis.game.world.WorldGenConfig;
import staraxis.logging.GdxToSlf4jLogger;
import staraxis.render.SkyboxRenderer;
import staraxis.render.ViewManager;
import staraxis.render.WorldCamera;
import staraxis.render.util.MenuBackgroundLoader;
import staraxis.render.galaxy.GalaxyViewRenderer;
import staraxis.render.picking.RayPicker;
import staraxis.render.system.SystemViewRenderer;
import staraxis.ui.FontProvider;
import staraxis.ui.Gui;
import staraxis.ui.effects.EffectRegistry;
import staraxis.ui.i18n.I18nService;
import staraxis.ui.json.GameDataProvider;
import staraxis.ui.json.UiFactory;
import staraxis.ui.settings.SettingsRepository;
import staraxis.ui.screens.InGameHudScreen;
import staraxis.ui.screens.SettingsScreen;
import staraxis.ui.screens.WorldSettingsScreen;
import staraxis.ui.widgets.DevelopingDialog;
import staraxis.ui.widgets.PauseMenu;
import staraxis.ui.widgets.StarfieldBackground;

/**
 * ClientGame.
 *
 * 原生客户端入口。默认使用 3D 宇宙渲染管线读取游戏快照。
 * Galaxy View 显示所有恒星（STAR），System View 显示选中星系的恒星+行星轨道。
 */
public class ClientGame implements ApplicationListener {

    /** 平台侧注入的初始化回调，在 GL 上下文就绪后执行（如 GPU 枚举） */
    public static Runnable onReady;

    private Stage stage;
    private Gui gui;
    private StarAxisGameRuntime runtime;
    private StarfieldBackground starfield;

    // 3D 宇宙渲染管线（galaxy 和 system 各持独立镜头，参数互不干扰）
    private WorldCamera galaxyCamera;
    private WorldCamera systemCamera;
    private ViewManager viewManager;
    private GalaxyViewRenderer galaxyViewRenderer;
    private SystemViewRenderer systemViewRenderer;
    private RayPicker rayPicker;

    // 天空盒渲染器
    private SkyboxRenderer skyboxRenderer;

    // 当前选中的恒星系（System View）
    private StarSystem currentSystem;

    // UI 调试叠加层（F3 切换），显示坐标原点/鼠标坐标/悬停元素边框
    private UiDebug uiDebug;

    // 暂停菜单
    private PauseMenu pauseMenu;

    // ── 加载状态 ──────────────────────────────────────────────
    public enum GameState { MENU, LOADING, PLAYING }
    private GameState gameState = GameState.MENU;
    private Thread genThread;
    private volatile float genProgress;
    private volatile String genPhase;
    private volatile StarAxisGameRuntime genResult;
    private volatile boolean genFailed;

    @Override
    public void create() {
        // 平台侧回调（GPU 枚举等，GL 上下文已就绪）
        if (onReady != null) {
            onReady.run();
            onReady = null; // 只执行一次
        }

        if (Gdx.app != null) {
            Gdx.app.setApplicationLogger(new GdxToSlf4jLogger());
        }

        stage = new Stage(new ScreenViewport());

        // 滚轮事件处理器——将滚轮滚动转发给 WorldCamera
        InputProcessor scrollProcessor = new InputProcessor() {
            @Override
            public boolean keyDown(int keycode) {
                return false;
            }

            @Override
            public boolean keyUp(int keycode) {
                return false;
            }

            @Override
            public boolean keyTyped(char character) {
                return false;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                return false;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                return false;
            }

            @Override
            public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
                return false;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                return false;
            }

            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                return false;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                // 根据当前视图转发滚轮到对应镜头
                if (viewManager != null && viewManager.isInSystemView()) {
                    if (systemCamera != null) systemCamera.onScroll(amountY);
                } else {
                    if (galaxyCamera != null) galaxyCamera.onScroll(amountY);
                }
                return false;
            }
        };

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(scrollProcessor);
        multiplexer.addProcessor(stage);
        Gdx.input.setInputProcessor(multiplexer);

        I18nService i18nService = new I18nService();
        i18nService.load("zh");

        gui = new Gui(stage, s -> {
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

        starfield = new StarfieldBackground(sr, MenuBackgroundLoader.loadBackgroundImage());
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

        // 暂停菜单
        pauseMenu = new PauseMenu(gui, sr, vectorFont);
        stage.addActor(pauseMenu);

        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        initSpaceRendering();

        // UI 调试面板初始化（F3 打开，JSON 声明式 UI）
        uiDebug = new UiDebug(stage, gui.get(ShapeRenderer.class), vectorFont,
                gui.get(staraxis.ui.json.UiParser.class),
                gui.get(staraxis.ui.json.UiFactory.class));
        uiDebug.setCamera(galaxyCamera); // 默认显示 galaxy 镜头信息

        // 设置异步世界生成回调
        gui.setOnStartNewGame(cfg -> startAsyncGen(cfg));

        gui.showMainMenu();
    }

    private void initSpaceRendering() {
        // galaxy 镜头：near=10 保证 far/near=10万:1 深度精度，far=1e6 保证拾取精度
        galaxyCamera = new WorldCamera(10f, 1e6f, 480000f);
        galaxyCamera.setMaxOrbitDist(50000);
        // system 镜头：far=3e6 容纳最远 ~116 万 GU 的缩放后轨道
        systemCamera = new WorldCamera(10f, 3e6f, 1000000f);
        systemCamera.setMaxOrbitDist(2000000);
        systemCamera.setZoom(5.0);
        systemCamera.target.set(0, 0, 0);
        viewManager = new ViewManager();
        rayPicker = new RayPicker();
        galaxyViewRenderer = new GalaxyViewRenderer();
        systemViewRenderer = new SystemViewRenderer();
        skyboxRenderer = new SkyboxRenderer();
    }

    @Override
    public void resize(int width, int height) {
        if (stage != null) {
            stage.getViewport().update(width, height, true);
        }
        if (galaxyCamera != null) {
            galaxyCamera.resize(width, height);
        }
        if (systemCamera != null) {
            systemCamera.resize(width, height);
        }
        if (starfield != null) {
            starfield.resize(width, height);
        }
    }

    /**
     * 开始异步世界生成。由 WorldSettingsScreen 触发，在后台线程执行 newGame()
     * 主线程每帧读取 genProgress/genPhase 更新进度条喵。
     */
    public void startAsyncGen(WorldGenConfig cfg) {
        gameState = GameState.LOADING;
        genProgress = 0f;
        genPhase = "";
        genResult = null;
        genFailed = false;
        gui.showLoadingScreen();

        genThread = new Thread(() -> {
            try {
                StarAxisGameRuntime rt = StarAxisGameRuntime.newGame(cfg, new ProgressCallback() {
                    @Override
                    public void onProgress(float progress, String phase) {
                        genProgress = progress;
                        if (phase != null) genPhase = phase;
                    }
                });
                genResult = rt;
            } catch (Exception e) {
                genFailed = true;
                genPhase = "生成失败: " + e.getMessage();
                Gdx.app.error("ClientGame", "World generation failed", e);
            }
        }, "WorldGen");
        genThread.setDaemon(true);
        genThread.start();
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();

        switch (gameState) {
            case LOADING:
                renderLoading(dt);
                break;
            case PLAYING:
                renderPlaying(dt);
                break;
            case MENU:
            default:
                renderMenu(dt);
                break;
        }

        stage.act(dt);
        //  UI 始终渲染在最上层，不受深度测试影响
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        stage.draw();

        // UI 调试叠加层（F3 开关）
        if (uiDebug != null) {
            uiDebug.update();
            uiDebug.render();
        }
    }

    private void renderMenu(float dt) {
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (starfield != null) {
            starfield.act(dt);
            starfield.render();
        }
    }

    private void renderLoading(float dt) {
        // 绘制星空背景
        Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (starfield != null) {
            starfield.act(dt);
            starfield.render();
        }

        // 更新进度条
        gui.updateLoadingProgress(genProgress, genPhase);

        // 检查生成线程是否完成
        if (genResult != null) {
            finishLoading();
        } else if (genFailed) {
            // 生成失败，退回主菜单
            Gdx.app.error("ClientGame", "World generation failed: " + genPhase);
            gameState = GameState.MENU;
            gui.hideLoadingScreen();
            gui.showMainMenu();
        }
    }

    private void finishLoading() {
        StarAxisGameRuntime rt = genResult;
        genResult = null;
        genThread = null;

        runtime = rt;
        gui.registerRuntime(rt);
        gameState = GameState.PLAYING;
        gui.hideLoadingScreen();
        gui.dispatchAction("START_GAME");
        // 将暂停菜单重新添加到舞台（switchScreen 的 stage.clear() 会清除它）
        if (pauseMenu.getStage() == null) {
            stage.addActor(pauseMenu);
        }
    }

    private void renderPlaying(float dt) {
        if (runtime == null || gui.getRuntime() == null) {
            if (runtime != null) {
                runtime.stop();
                runtime = null;
            }
            gameState = GameState.MENU;
            return;
        }

        // ESC 切换暂停菜单
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            pauseMenu.toggle();
        }

        boolean paused = pauseMenu.isMenuVisible();
        if (!paused) {
            runtime.update(dt);
            runtime.publishRealtimeSnapshotIfNeeded();
            renderSpaceScene(dt);
        }

    }

    private void renderSpaceScene(float dt) {
        // 根据当前视图更新对应镜头
        if (viewManager.isInSystemView()) {
            systemCamera.update(dt);
        } else {
            galaxyCamera.update(dt);
        }

        Gdx.gl.glClearColor(0.005f, 0.005f, 0.02f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // M 键从 System View 返回 Galaxy View
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.M)) {
            if (viewManager.isInSystemView()) {
                viewManager.switchToGalaxy();
                currentSystem = null;
                systemViewRenderer.resetTime();
                galaxyCamera.resetView();
                uiDebug.switchActiveCamera(galaxyCamera);
            }
        }

        if (viewManager.isInGalaxyView()) {
            renderGalaxyView();
        } else if (viewManager.isInSystemView()) {
            renderSystemView(dt);
        }
    }

    private void renderGalaxyView() {
        skyboxRenderer.render(galaxyCamera);

        var state = runtime.getRealTimeWorldStateReadonly();

        rayPicker.updateHovered(galaxyCamera, state,
                Gdx.input.getX(), Gdx.input.getY(), galaxyViewRenderer);

        // 更新悬停恒星坐标显示
        InGameHudScreen hud = gui.get(InGameHudScreen.class);
        if (hud != null) {
            hud.updateStarInfo(rayPicker.getHoveredStarId(), state);
        }

        if (Gdx.input.isButtonJustPressed(com.badlogic.gdx.Input.Buttons.LEFT)) {
            long selectedStarId = rayPicker.getHoveredStarId();
            if (selectedStarId >= 0) {
                StarSystem system = findSystemByStarId(selectedStarId);
                if (system != null) {
                    currentSystem = system;
                    viewManager.switchToSystem(selectedStarId);
                    systemViewRenderer.resetTime();
                    systemCamera.resetView();
                    uiDebug.switchActiveCamera(systemCamera);
                }
            }
        }

        galaxyViewRenderer.render(state, galaxyCamera, rayPicker.getHoveredStarId());
    }

    private void renderSystemView(float dt) {
        if (currentSystem == null)
            return;

        skyboxRenderer.render(systemCamera);

        systemViewRenderer.advanceTime(dt);
        systemViewRenderer.render(currentSystem, systemCamera);
    }

    private StarSystem findSystemByStarId(long starId) {
        var ws = runtime.getWorldStateForSimOnly();
        if (ws == null || ws.astro == null)
            return null;

        for (StarSystem sys : ws.astro.getSystemsView()) {
            if (sys == null)
                continue;
            for (var star : sys.stars) {
                if (star != null && star.entityId == starId) {
                    return sys;
                }
            }
        }
        return null;
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
        if (uiDebug != null) {
            uiDebug.dispose();
            uiDebug = null;
        }
        if (galaxyViewRenderer != null) {
            galaxyViewRenderer.dispose();
            galaxyViewRenderer = null;
        }
        if (systemViewRenderer != null) {
            systemViewRenderer.dispose();
            systemViewRenderer = null;
        }
        if (skyboxRenderer != null) {
            skyboxRenderer.dispose();
            skyboxRenderer = null;
        }
        if (runtime != null) {
            runtime.stop();
            runtime = null;
        }
        FontProvider.disposeAllIncremental();
    }
}
