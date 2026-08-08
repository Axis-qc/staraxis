package staraxis;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.command.SetupPlayerHomeCommand;
import staraxis.game.entity.EntityType;
import staraxis.game.log.TickProfiler;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.util.ProgressCallback;
import staraxis.game.world.WorldGenConfig;
import staraxis.logging.GdxToSlf4jLogger;
import staraxis.render.SkyboxRenderer;
import staraxis.render.ViewManager;
import staraxis.render.WorldCamera;
import staraxis.render.util.MenuBackgroundLoader;
import staraxis.render.util.MenuGalaxyBackground;
import staraxis.render.galaxy.GalaxyViewRenderer;
import staraxis.render.picking.RayPicker;
import staraxis.render.system.SystemViewRenderer;
import staraxis.sprite.SpriteRegistry;
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
import staraxis.ui.UiPointerService;
import staraxis.ui.UiWindowManager;
import staraxis.ui.widgets.PauseMenu;
import staraxis.ui.widgets.SelectHomeConfirmDialog;
import staraxis.ui.widgets.StarfieldBackground;
import staraxis.ui.SelectionService;

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
    private MenuGalaxyBackground menuGalaxy;
    private GameSnapshotProvider snapshotProvider;

    // 当前 System View 所在恒星系ID（替代直接引用 StarSystem 实例）
    private long currentSystemId;

    // 3D 宇宙渲染管线（galaxy 和 system 各持独立镜头，参数互不干扰）
    private WorldCamera galaxyCamera;
    private WorldCamera systemCamera;
    private ViewManager viewManager;
    private GalaxyViewRenderer galaxyViewRenderer;
    private SystemViewRenderer systemViewRenderer;
    private RayPicker rayPicker;

    // 天空盒渲染器
    private SkyboxRenderer skyboxRenderer;

    // UI 调试叠加层（F3 切换），显示坐标原点/鼠标坐标/悬停元素边框
    private UiDebug uiDebug;

    // 暂停菜单
    private PauseMenu pauseMenu;

    // 舰船移动交互控制器（选中、移动、聚焦、路径可视化）
    private ShipMoveController shipMoveController;

    // 全局选中服务（选中态唯一来源，Galaxy/System 双视图共用）
    private SelectionService selectionService;

    // 统一 UI 命中守卫服务：全部鼠标输入（左键/右键/中键/滚轮）先查归属，
    // 鼠标在 UI 上只触发 UI 交互，不在 UI 上才触发 3D 场景交互喵
    private UiPointerService pointerService;

    // 纹理注册器（启动时加载所有纹理到 GPU 内存）
    private SpriteRegistry spriteRegistry;

    private int frameCount;

    // ── 加载状态 ──────────────────────────────────────────────
    public enum GameState {
        MENU, LOADING, SELECTING_HOME, PLAYING
    }

    private GameState gameState = GameState.MENU;
    private Thread genThread;
    private volatile float genProgress;
    private volatile String genPhase;
    private volatile StarAxisGameRuntime genResult;
    private volatile boolean genFailed;
    /** 玩家在世界设置中选定的国家ID，开局流程中传递给 game 层。 */
    private String pendingNationId;

    /** 选择母星系确认弹窗。 */
    private SelectHomeConfirmDialog homeConfirmDialog;

    /** 待确认的星系ID（点击恒星后暂存，等待弹窗确认）。 */
    private long pendingConfirmSystemId = -1;

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

        BaseUiInit base = BaseUiInit.init();
        stage = base.stage;
        gui = base.gui;
        starfield = base.starfield;
        pointerService = gui.tryGet(UiPointerService.class);

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
                // 鼠标在 UI 上时滚轮归 UI（未来窗口列表滚动/ScrollPane），
                // 不缩放镜头，返回 false 交给 stage 处理；scrolled 回调无坐标参数，
                // 用 Gdx.input 实时位置经守卫判定喵
                if (pointerService != null && pointerService.isMouseOverUi()) {
                    return false;
                }
                // 根据当前视图转发滚轮到对应镜头
                if (viewManager != null && viewManager.isInSystemView()) {
                    if (systemCamera != null)
                        systemCamera.onScroll(amountY);
                } else {
                    if (galaxyCamera != null)
                        galaxyCamera.onScroll(amountY);
                }
                return false;
            }
        };

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(scrollProcessor);
        multiplexer.addProcessor(stage);
        Gdx.input.setInputProcessor(multiplexer);

        // 暂停菜单
        pauseMenu = new PauseMenu(gui, base.sr, base.vectorFont);
        stage.addActor(pauseMenu);

        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        // 纹理注册器：启动时加载所有精灵纹理到 GPU 内存
        spriteRegistry = new SpriteRegistry();
        spriteRegistry.loadAll();

        initSpaceRendering();

        // 主菜单 3D 银河背景（纯数学生成 8000 星，毫秒级启动）
        menuGalaxy = new MenuGalaxyBackground();

        // UI 调试面板初始化（F3 打开，JSON 声明式 UI）
        uiDebug = new UiDebug(stage, gui.get(ShapeRenderer.class), base.vectorFont,
                gui.get(staraxis.ui.json.UiParser.class),
                gui.get(staraxis.ui.json.UiFactory.class));
        uiDebug.setCamera(galaxyCamera); // 默认显示 galaxy 镜头信息
        // 调试面板注册到统一守卫：F3 打开期间点击面板不触发 3D 选中/移动喵
        uiDebug.setPointerService(pointerService);

        // 设置异步世界生成回调
        gui.setOnStartNewGame(cfg -> startAsyncGen(cfg));

        gui.showMainMenu();

        shipMoveController = new ShipMoveController();
        selectionService = new SelectionService();
        shipMoveController.setSelectionService(selectionService);
        gui.register(SelectionService.class, selectionService);
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
        if (menuGalaxy != null) {
            menuGalaxy.resize(width, height);
        }
    }

    public void startAsyncGen(WorldGenConfig cfg) {
        gameState = GameState.LOADING;
        genProgress = 0f;
        genPhase = "";
        genResult = null;
        genFailed = false;
        // 记录玩家选定的国家ID，开局确认母星系时传递给 game 层
        pendingNationId = (cfg != null && cfg.playerNationDef != null)
                ? cfg.playerNationDef.id
                : null;
        gui.showLoadingScreen();

        genThread = new Thread(() -> {
            try {
                StarAxisGameRuntime rt = StarAxisGameRuntime.newGame(cfg, new ProgressCallback() {
                    @Override
                    public void onProgress(float progress, String phase) {
                        genProgress = progress;
                        if (phase != null)
                            genPhase = phase;
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
            case SELECTING_HOME:
                renderSelectingHome(dt);
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
        // UI 始终渲染在最上层，不受深度测试影响
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        stage.draw();

        // UI 调试叠加层（F3 开关）
        if (uiDebug != null) {
            uiDebug.update();
            uiDebug.render();
        }
    }

    private void renderMenu(float dt) {
        if (menuGalaxy != null) {
            menuGalaxy.update(dt);
            menuGalaxy.render();
        } else if (starfield != null) {
            // 降级：如果 3D 背景不可用，使用 2D 星空
            starfield.act(dt);
            starfield.render();
        }
    }

    private void renderLoading(float dt) {
        // 3D 银河背景持续旋转
        if (menuGalaxy != null) {
            menuGalaxy.update(dt);
            menuGalaxy.render();
        } else if (starfield != null) {
            starfield.act(dt);
            starfield.render();
        } else {
            Gdx.gl.glClearColor(0f, 0f, 0f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
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
        // 重置渲染器 GPU 缓冲区，确保新世界的恒星数据替换旧世界的 GPU 实例数据喵
        galaxyViewRenderer.reset();
        systemViewRenderer.reset();

        StarAxisGameRuntime rt = genResult;
        genResult = null;
        genThread = null;

        runtime = rt;
        snapshotProvider = new LocalSnapshotProvider(rt);
        shipMoveController.setSnapshotProvider(snapshotProvider);
        gui.registerRuntime(rt);

        // 世界生成完成，进入选母星系模式（而非直接 PLAYING）
        gameState = GameState.SELECTING_HOME;
        galaxyCamera.resetView(); // 镜头重置到俯瞰全银河
        gui.hideLoadingScreen();
        gui.dispatchAction("START_GAME");
        // 将暂停菜单重新添加到舞台（switchScreen 的 stage.clear() 会清除它）
        if (pauseMenu.getStage() == null) {
            stage.addActor(pauseMenu);
        }
    }

    /**
     * 选择母星系模式渲染。
     * 允许玩家在 Galaxy View 中自由浏览并点击恒星选择母星系。
     * ESC 触发暂停菜单（与 PLAYING 模式行为一致）。
     */
    private void renderSelectingHome(float dt) {
        if (runtime == null || gui.getRuntime() == null) {
            if (runtime != null) {
                runtime.stop();
                runtime = null;
            }
            snapshotProvider = null;
            galaxyViewRenderer.reset();
            systemViewRenderer.reset();
            gameState = GameState.MENU;
            return;
        }

        // ESC 栈：先关闭最上层信息窗口，无窗口时才切换暂停菜单喵
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            UiWindowManager windowManager = gui.tryGet(UiWindowManager.class);
            if (windowManager == null || !windowManager.closeTopMost()) {
                pauseMenu.toggle();
            }
        }

        boolean paused = pauseMenu.isMenuVisible();

        // 延迟初始化确认弹窗（需要 ShapeRenderer 和字体）——只做一次
        if (homeConfirmDialog == null) {
            homeConfirmDialog = new SelectHomeConfirmDialog(
                    gui.get(com.badlogic.gdx.graphics.glutils.ShapeRenderer.class),
                    gui.get(com.badlogic.gdx.graphics.g2d.BitmapFont.class),
                    pointerService);
        }

        if (!paused) {
            // 中键旋转归属：鼠标在 UI 上时关闭（SELECTING_HOME 仅 galaxy 镜头）喵
            galaxyCamera.setMiddleButtonEnabled(
                    pointerService == null || !pointerService.isMouseOverUi());
            // 更新 galaxy 镜头（选择期间在 Galaxy View 中浏览）
            galaxyCamera.update(dt);

            Gdx.gl.glClearColor(0.005f, 0.005f, 0.02f, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

            skyboxRenderer.render(galaxyCamera);

            var state = snapshotProvider.getRealtimeState();
            var lowFreq = snapshotProvider.getDailyState();

            rayPicker.updateHovered(galaxyCamera, state,
                    Gdx.input.getX(), Gdx.input.getY(), galaxyViewRenderer);

            long hoveredStarId = rayPicker.getHoveredStarId();

            InGameHudScreen hud = gui.get(InGameHudScreen.class);
            if (hud != null) {
                hud.updateViewInfo("选择母星系  x" + String.format("%.1f", galaxyCamera.zoomLevel));

                // 悬停恒星时喂给 tooltip 绑定器：悬停 300ms 显示，鼠标移入 tooltip 3 秒钉住喵
                if (hoveredStarId >= 0) {
                    float[] worldPos = galaxyViewRenderer.getStarPosition(hoveredStarId);
                    if (worldPos != null) {
                        com.badlogic.gdx.math.Vector3 proj = new com.badlogic.gdx.math.Vector3(
                                worldPos[0], worldPos[1], worldPos[2]);
                        galaxyCamera.camera.project(proj);
                        hud.updateStarTooltipHover(hoveredStarId, lowFreq, proj.x, proj.y);
                    } else {
                        hud.updateStarTooltipHover(-1, null, 0, 0);
                    }
                } else {
                    hud.updateStarTooltipHover(-1, null, 0, 0);
                }
            }

            // 左键点击恒星：弹出确认弹窗（弹窗未显示时才响应；鼠标在 UI 上
            // 只触发 UI 交互，不触发 3D；弹窗打开时守卫拦截 3D 为双保险）喵
            if (Gdx.input.isButtonJustPressed(com.badlogic.gdx.Input.Buttons.LEFT)
                    && (pointerService == null || !pointerService.isMouseOverUi())
                    && (homeConfirmDialog == null || !homeConfirmDialog.isVisible())) {
                if (hoveredStarId >= 0) {
                    long sysId = findSystemByStarId(hoveredStarId);
                    if (sysId > 0) {
                        pendingConfirmSystemId = sysId;

                        // 构建星系描述文本
                        String desc = buildStarHoverText(hoveredStarId);
                        boolean recommended = isSystemRecommended(sysId);
                        homeConfirmDialog.show(stage, desc, recommended,
                                () -> { // 确认回调
                                    long sid = pendingConfirmSystemId;
                                    pendingConfirmSystemId = -1;
                                    confirmHomeSystem(sid);
                                },
                                () -> { // 取消回调
                                    pendingConfirmSystemId = -1;
                                });
                    }
                }
            }

            // 渲染 Galaxy View
            galaxyViewRenderer.render(state, lowFreq, galaxyCamera, rayPicker.getHoveredStarId());
        }
    }

    /**
     * 构建恒星的简短描述文本（用于确认弹窗）。
     */
    private String buildStarHoverText(long starEntityId) {
        if (starEntityId < 0)
            return "未知星系";
        var ds = snapshotProvider.getDailyState();
        if (ds == null || ds.publicEntityBaselinesBySectorKey == null)
            return "未知星系";
        for (var entry : ds.publicEntityBaselinesBySectorKey.entrySet()) {
            for (var snap : entry.getValue()) {
                if (snap != null && snap.entityId == starEntityId
                        && snap.details instanceof EntitySnapshot.StarDetails sd) {
                    String type = sd.starTypeId != null ? sd.starTypeId : "?";
                    return String.format("恒星系 #%d  %s  %dK", snap.systemId, type, sd.temperatureK);
                }
            }
        }
        return "恒星系 #" + starEntityId;
    }

    public boolean isSystemRecommended(long systemId) {
        if (systemId <= 0)
            return false;
        var ds = snapshotProvider.getDailyState();
        if (ds == null || ds.publicEntityBaselinesBySectorKey == null)
            return false;
        var baselines = ds.publicEntityBaselinesBySectorKey.get(String.valueOf(systemId));
        if (baselines == null)
            return false;
        for (var snap : baselines) {
            if (snap != null && snap.details instanceof EntitySnapshot.PlanetDetails pd
                    && pd.planetTypeId != null
                    && staraxis.game.astro.PlanetBody.HABITABLE_PLANET_TYPE_IDS.contains(pd.planetTypeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 确认母星系，执行开局设置并切换到 System View。
     *
     * 调用 game 端的 setupPlayerHome 同步执行：
     * 注册国家 → 星系归属 → 在最远行星轨道外侧生成初始舰队。
     * 完成后切换到 System View，镜头定位到舰队位置。
     *
     * @param systemId 玩家选定的星系ID
     */
    private void confirmHomeSystem(long systemId) {
        if (runtime == null || systemId <= 0)
            return;

        String nationId = pendingNationId != null ? pendingNationId : "nation_player";

        // 同步执行开局设置喵
        SetupPlayerHomeCommand result = runtime.setupPlayerHome(nationId, systemId);
        if (!result.isSuccess()) {
            Gdx.app.error("ClientGame", "setupPlayerHome failed: " + result.getErrorMessage());
            return;
        }

        // 切换到 System View
        long starId = findStarIdBySystemId(systemId);
        if (starId > 0) {
            viewManager.switchToSystem(starId);
        }
        currentSystemId = systemId;
        systemViewRenderer.resetTime();
        uiDebug.switchActiveCamera(systemCamera);
        runtime.setActiveSystemId(systemId);

        // 镜头定位到舰队位置（T9）
        var fleetPos = result.getFleetCenterPos();
        if (fleetPos != null) {
            systemCamera.setTargetPosition(
                    (float) fleetPos.x(), (float) fleetPos.y(), (float) fleetPos.z());
            systemCamera.setZoom(3.0); // 较近缩放级别，能看到舰队细节
            // 激活虫洞平面
            systemViewRenderer.showWormhole(fleetPos.x(), fleetPos.y(), fleetPos.z());
        }

        // 进入正常游戏状态
        gameState = GameState.PLAYING;
    }

    private void renderPlaying(float dt) {
        if (runtime == null || gui.getRuntime() == null) {
            if (runtime != null) {
                runtime.stop();
                runtime = null;
            }
            snapshotProvider = null;
            galaxyViewRenderer.reset();
            systemViewRenderer.reset();
            gameState = GameState.MENU;
            return;
        }

        // ESC 栈：先关闭最上层信息窗口，无窗口时才切换暂停菜单喵
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ESCAPE)) {
            UiWindowManager windowManager = gui.tryGet(UiWindowManager.class);
            if (windowManager == null || !windowManager.closeTopMost()) {
                pauseMenu.toggle();
            }
        }

        boolean paused = pauseMenu.isMenuVisible();

        if (!paused) {
            long updateStart = System.nanoTime();
            runtime.update(dt);
            long updateEnd = System.nanoTime();
            runtime.publishRealtimeSnapshotIfNeeded();
            long renderStart = System.nanoTime();
            renderSpaceScene(dt);
            long renderEnd = System.nanoTime();
            // 每 60 帧输出一次渲染耗时
            if (frameCount++ % 60 == 0) {
                TickProfiler.logRender(updateEnd - updateStart, renderEnd - renderStart);
            }
        }

    }

    private void renderSpaceScene(float dt) {
        // 中键旋转归属：鼠标在 UI 上时关闭，PLAYING 双镜头均按守卫设置喵
        boolean mouseOverUi = pointerService != null && pointerService.isMouseOverUi();
        // 根据当前视图更新对应镜头
        if (viewManager.isInSystemView()) {
            systemCamera.setMiddleButtonEnabled(!mouseOverUi);
            systemCamera.update(dt);
        } else {
            galaxyCamera.setMiddleButtonEnabled(!mouseOverUi);
            galaxyCamera.update(dt);
        }
        Gdx.gl.glClearColor(0.005f, 0.005f, 0.02f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // M 键从 System View 返回 Galaxy View
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.M)) {
            if (viewManager.isInSystemView()) {
                viewManager.switchToGalaxy();
                currentSystemId = 0;
                systemViewRenderer.resetTime();
                uiDebug.switchActiveCamera(galaxyCamera);
                runtime.setActiveSystemId(0);
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

        var state = snapshotProvider.getRealtimeState();
        var lowFreq = snapshotProvider.getDailyState();

        rayPicker.updateHovered(galaxyCamera, state,
                Gdx.input.getX(), Gdx.input.getY(), galaxyViewRenderer);

        InGameHudScreen hud = gui.get(InGameHudScreen.class);
        if (hud != null) {
            hud.updateViewInfo(String.format("星系视图  x%.1f", galaxyCamera.zoomLevel));
            hud.updateStarInfo(rayPicker.getHoveredStarId(), state);
        }

        if (Gdx.input.isButtonJustPressed(com.badlogic.gdx.Input.Buttons.LEFT)) {
            // 模态窗口打开时，窗口外点击先关闭窗口，不触发进入星系喵
            UiWindowManager wm = gui.tryGet(UiWindowManager.class);
            if (wm != null && wm.closeModalIfClickedOutside(
                    Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY())) {
                return;
            }
            // 统一守卫：鼠标在 UI 上只触发 UI 交互，不触发 3D（进入星系）喵
            if (pointerService != null && pointerService.isMouseOverUi()) {
                return;
            }
            long selectedStarId = rayPicker.getHoveredStarId();
            if (selectedStarId >= 0) {
                long sysId = findSystemByStarId(selectedStarId);
                if (sysId > 0) {
                    currentSystemId = sysId;
                    viewManager.switchToSystem(selectedStarId);
                    systemViewRenderer.resetTime();
                    uiDebug.switchActiveCamera(systemCamera);
                }
            }
        }

        galaxyViewRenderer.render(state, lowFreq, galaxyCamera, rayPicker.getHoveredStarId());
    }

    private void renderSystemView(float dt) {
        if (currentSystemId <= 0)
            return;

        // F4 切换区块网格调试
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.F4)) {
            systemViewRenderer.setDebugChunksEnabled(!systemViewRenderer.isDebugChunksEnabled());
        }

        skyboxRenderer.render(systemCamera);

        systemViewRenderer.advanceTime(dt);
        // 通知 game 引擎当前活跃星系（优先计算）
        runtime.setActiveSystemId(currentSystemId);

        // 只有 WASDQE 能取消镜头跟踪（提前清标志，跟循环在底部）
        if (systemCamera.isUserControlled()) {
            shipMoveController.cancelCameraFollow();
        }

        // 收集当前星系的完整快照数据
        var state = snapshotProvider.getRealtimeState();
        var ds = snapshotProvider.getDailyState();
        java.util.List<EntitySnapshot> systemSnapshots = java.util.List.of();
        java.util.List<EntitySnapshot> shipSnapshots = java.util.List.of();

        // 恒星/行星等基线数据从每日基线快照取
        if (ds != null && ds.publicEntityBaselinesBySectorKey != null) {
            systemSnapshots = ds.publicEntityBaselinesBySectorKey.get(String.valueOf(currentSystemId));
        }
        if (systemSnapshots == null)
            systemSnapshots = java.util.List.of();

        // 舰船从实时快照取（补充到基线快照中，或单独传）
        if (state != null) {
            var rtSnaps = state.getEntitySnapshotsBySystemView().get(String.valueOf(currentSystemId));
            if (rtSnaps != null) {
                java.util.ArrayList<EntitySnapshot> ships = new java.util.ArrayList<>();
                for (EntitySnapshot snap : rtSnaps) {
                    if (snap != null && snap.details instanceof EntitySnapshot.ShipDetails) {
                        ships.add(snap);
                    }
                }
                shipSnapshots = ships;
            }
        }
        systemViewRenderer.setShips(shipSnapshots);
        systemViewRenderer.setHighlightEntity(selectionService.getSelectedEntityId());
        systemViewRenderer.render(systemSnapshots, systemCamera);

        // System View 悬停拾取 + HUD 更新
        long hoveredId = systemViewRenderer.pick(systemCamera,
                Gdx.input.getX(), Gdx.input.getY());
        InGameHudScreen hud = gui.get(InGameHudScreen.class);
        if (hud != null) {
            hud.updateViewInfo(String.format("恒星系视图  x%.1f", systemCamera.zoomLevel));
            hud.setHoverInfoText(buildSystemHoverText(hoveredId));
        }

        // ── 左键处理：先消费模态窗口的窗口外点击，再经统一守卫决定是否执行 3D 选中逻辑 ──
        if (Gdx.input.isButtonJustPressed(com.badlogic.gdx.Input.Buttons.LEFT)) {
            UiWindowManager wm = gui.tryGet(UiWindowManager.class);
            boolean modalConsumed = wm != null
                    && wm.closeModalIfClickedOutside(
                            Gdx.input.getX(), Gdx.graphics.getHeight() - Gdx.input.getY());
            if (!modalConsumed && (pointerService == null || !pointerService.isMouseOverUi())) {
                shipMoveController.handleLeftClick(hoveredId, runtime,
                        systemViewRenderer, systemCamera);
            }
        }

        // ── 右键 Homeworld 式移动（每帧更新，鼠标在 UI 上时不响应） ──
        if (pointerService == null || !pointerService.isMouseOverUi()) {
            shipMoveController.updateMoveMode(runtime, systemCamera);
        }

        // ── 移动模式：渲染目标点标记 ──
        var shapeRenderer = gui != null ? gui.get(ShapeRenderer.class) : null;
        shipMoveController.renderMovePreview(shapeRenderer, runtime, systemCamera);

        // ── 选中舰船有移动目标时显示路径 ──
        shipMoveController.renderMovePath(shapeRenderer, runtime, systemCamera);

        // ── 镜头跟踪：在所有输入处理后执行 ──
        shipMoveController.updateCameraFollow(systemViewRenderer, runtime, systemCamera);
    }

    /** 构建 System View 悬停天体描述文字（从快照读取）。 */
    private String buildSystemHoverText(long entityId) {
        if (entityId < 0 || currentSystemId <= 0)
            return "";

        // 从快照中搜索该 entityId（优先实时快照，回退每日基线）
        var state = snapshotProvider.getRealtimeState();
        java.util.List<EntitySnapshot> snapshots = null;
        if (state != null) {
            snapshots = state.getEntitySnapshotsBySystemView().get(String.valueOf(currentSystemId));
        }
        if (snapshots == null) {
            var ds = snapshotProvider.getDailyState();
            if (ds != null && ds.publicEntityBaselinesBySectorKey != null) {
                snapshots = ds.publicEntityBaselinesBySectorKey.get(String.valueOf(currentSystemId));
            }
        }
        if (snapshots == null)
            return "";

        for (EntitySnapshot snap : snapshots) {
            if (snap.entityId != entityId)
                continue;

            if (snap.details instanceof EntitySnapshot.StarDetails sd) {
                String type = sd.starTypeId != null ? sd.starTypeId : "?";
                return String.format("恒星  %s  半径%.0fGU  %dK", type, sd.radiusGU, sd.temperatureK);
            }
            if (snap.details instanceof EntitySnapshot.PlanetDetails pd) {
                String prefix;
                if (snap.entityType == EntityType.PLANET)
                    prefix = "行星";
                else if (snap.entityType == EntityType.ASTEROID)
                    prefix = "小行星";
                else if (snap.entityType == EntityType.MOON)
                    prefix = "卫星";
                else
                    prefix = "天体";
                String type = pd.planetTypeId != null ? pd.planetTypeId : "?";
                return String.format("%s  %s  半径%.0fGU  轨道%.0fGU", prefix, type, pd.radiusGU, pd.semiMajorAxisGU);
            }
            if (snap.details instanceof EntitySnapshot.ShipDetails shipDet) {
                String sel = entityId == shipMoveController.getSelectedShipId()
                        ? (shipMoveController.isMoveModeActive()
                                ? String.format(" [选中] 目标Y=%.0f 左键确认", shipMoveController.getMoveTargetY())
                                : " [选中] 右键设目标+拖动调Y 左键确认")
                        : "";
                String flags = shipDet.customFlags != null && !shipDet.customFlags.isEmpty()
                        ? " " + String.join(",", shipDet.customFlags)
                        : "";
                return String.format("舰船 实体%d%s%s", entityId, flags, sel);
            }
        }
        return "";
    }

    /**
     * 根据恒星ID从快照中查找所属恒星系ID。
     * 实时快照仅含舰船，恒星需从每日基线快照查。
     */
    private long findSystemByStarId(long starId) {
        // 1. 优先从每日基线快照查（含 STAR 实体）
        var ds = snapshotProvider.getDailyState();
        if (ds != null && ds.publicEntityBaselinesBySectorKey != null) {
            for (var entry : ds.publicEntityBaselinesBySectorKey.entrySet()) {
                for (var snap : entry.getValue()) {
                    if (snap != null && snap.entityId == starId) {
                        return snap.systemId;
                    }
                }
            }
        }
        // 2. 回退到实时快照查（仅含 SHIP，用于双击舰船入口）
        var state = snapshotProvider.getRealtimeState();
        if (state != null) {
            for (var entry : state.getEntitySnapshotsBySystemView().entrySet()) {
                for (var snap : entry.getValue()) {
                    if (snap != null && snap.entityId == starId) {
                        return snap.systemId;
                    }
                }
            }
        }
        return 0;
    }

    /**
     * 通过星系ID反查恒星实体ID。
     * 用于确认母星系后切换到 System View（System View 需要恒星ID作为入口）。
     */
    private long findStarIdBySystemId(long systemId) {
        if (systemId <= 0)
            return 0;
        var ds = snapshotProvider.getDailyState();
        if (ds != null && ds.publicEntityBaselinesBySectorKey != null) {
            for (var entry : ds.publicEntityBaselinesBySectorKey.entrySet()) {
                for (var snap : entry.getValue()) {
                    if (snap != null && snap.systemId == systemId
                            && snap.entityType == EntityType.STAR) {
                        return snap.entityId;
                    }
                }
            }
        }
        return 0;
    }

    /**
     * TODO Phase 3: 临时桥接——从快照构建最小 StarSystem 对象供尚未改造完的渲染器使用。
     * Phase 3 中 SystemViewRenderer/SystemViewOverlay 改为直接接收快照后此方法可删除。
     */
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
        if (menuGalaxy != null) {
            menuGalaxy.dispose();
            menuGalaxy = null;
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
        if (spriteRegistry != null) {
            spriteRegistry.dispose();
            spriteRegistry = null;
        }
        FontProvider.disposeAllIncremental();
    }
}
