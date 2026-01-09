package com.staraxis.game.client.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.staraxis.game.client.ui.manager.UIManager;
import com.staraxis.game.client.ui.view.CameraController;
import com.staraxis.game.client.ui.view.HexGridRenderer;
import com.staraxis.game.client.ui.view.HexPicker;
import com.staraxis.game.client.ui.view.WorldOverlayRenderer;
import com.staraxis.game.client.ui.view.debug.DebugOverlayController;
import com.staraxis.game.client.ui.view.debug.DebugSystem;
import com.staraxis.game.client.ui.view.debug.DebugToggleInputProcessor;
import com.staraxis.game.client.ui.view.debug.WorldGridRenderer;
import com.staraxis.game.client.ui.view.stellar.StellarMarkerRenderer;
import com.staraxis.game.client.world.UniverseModel;
import com.staraxis.game.core.coordinate.CoordinateService;
import com.staraxis.game.core.coordinate.CameraWorld;
import com.staraxis.game.core.coordinate.WorldCoordinate;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.net.worldgen.snapshot.StarSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.StarSystemSnapshot;
import com.staraxis.game.client.ui.view.debug.WorldAxisRenderer;

import io.staraxis.Main;

/**
 * 新版宇宙星图界面（UniverseScreen）：
 * - 使用 UniverseModel 作为运行时模型
 * - 当前阶段通过适配器转换为 WorldMap，以复用现有渲染器
 *
 * 014：接入基础坐标系/比例尺调试：F3 显示/隐藏
 * - 世界空间：XY 网格（100px 目标间距 + 1.2x 余量 + 对齐世界原点）
 * - UI：坐标、zoom、比例尺（1px = N unit）
 */
public class UniverseScreen extends ScreenAdapter {

    private final Main game;
    private final Stage uiStage;
    private final OrthographicCamera camera;
    private final UIManager uiManager;

    private final UniverseModel universe;

    private final CameraWorld camWorld = new CameraWorld(0.0, 0.0);

    private final HexGridRenderer gridRenderer;
    private final HexPicker hexPicker;
    private final CameraController cameraController;
    private final WorldOverlayRenderer overlayRenderer;
    private final StellarMarkerRenderer stellarMarkerRenderer;

    private Label debugLabel;

    // 鼠标静止检测
    private int lastMouseX = -1;
    private int lastMouseY = -1;
    private float mouseIdleSec = 0f;
    private static final float MOUSE_IDLE_THRESHOLD_SEC = 2.0f;
    private HexCoord hovered;

    // --- 014: F3 调试渲染（client 表现层状态） ---
    private final CoordinateService coordinateService = new CoordinateService();
    private final DebugSystem debugSystem = new DebugSystem(coordinateService);
    private final WorldGridRenderer debugWorldGridRenderer = new WorldGridRenderer();
    private final WorldAxisRenderer debugWorldAxisRenderer = new WorldAxisRenderer();
    private final DebugOverlayController debugOverlayController;

    public UniverseScreen(Main game, UniverseModel universe) {
        this.game = game;
        this.universe = universe;
        this.uiManager = game.getUiManager();
        this.uiStage = new Stage(new ScreenViewport());

        this.camera = new OrthographicCamera();
        this.camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        this.camera.position.set(0, 0, 20);
        this.camera.lookAt(0, 0, 0);
        this.camera.near = 0.1f;
        this.camera.far = 100f;
        this.camera.update();

        this.gridRenderer = new HexGridRenderer(camWorld);
        // 设置字体用于显示坐标
        if (game.getSkin() != null && game.getSkin().getFont("default") != null) {
            this.gridRenderer.setFont(game.getSkin().getFont("default"));
        }
        this.hexPicker = new HexPicker(gridRenderer, camWorld);
        this.cameraController = new CameraController(camera, camWorld);
        this.overlayRenderer = new WorldOverlayRenderer();
        this.stellarMarkerRenderer = new StellarMarkerRenderer(camWorld);

        // 顶左调试信息（原有 hover 信息）
        Table table = new Table();
        table.top().left();
        table.setFillParent(true);
        debugLabel = new Label("", game.getSkin());
        table.add(debugLabel).pad(10).left();
        uiStage.addActor(table);

        // 014：F3 DebugOverlay（Scene2D Table + Label）
        this.debugOverlayController = new DebugOverlayController(game.getSkin());
        uiStage.addActor(this.debugOverlayController.getActor());
        this.debugOverlayController.setVisible(false);
    }

    @Override
    public void show() {
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(uiStage);
        multiplexer.addProcessor(cameraController);
        // 014: F3 调试开关（仅 UniverseScreen 启用）
        multiplexer.addProcessor(new DebugToggleInputProcessor(debugSystem));
        Gdx.input.setInputProcessor(multiplexer);
        uiManager.setCurrentStage(uiStage);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.05f, 0.05f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        cameraController.setIntercepted(uiStage.getKeyboardFocus() != null);
        cameraController.update(delta);
        camera.update();

        hovered = hexPicker.screenToHex(Gdx.input.getX(), Gdx.input.getY(), camera);

        // 014: 将相机位置（当前世界坐标系近似）映射为 WorldCoordinate
        // 临时策略：grid 固定为 0，offset 直接取 camera.position（单位假定与当前世界渲染坐标一致）
        debugSystem.setCameraWorld(new WorldCoordinate(
                0, 0, 0,
                camera.position.x,
                camera.position.y,
                camera.position.z));

        // 原有 hover 文本
        String hoverText = "";
        if (hovered != null && universe.getSectors().containsKey(hovered)) {
            var sector = universe.getSector(hovered);
            String typeId = sector.getSectorType();
            int stars = 0;
            int planets = 0;
            StarSystemSnapshot sys = sector.getStarSystem();
            if (sys != null && sys.getStars() != null) {
                stars = sys.getStars().size();
                for (StarSnapshot s : sys.getStars()) {
                    if (s.getPlanets() != null) {
                        planets += s.getPlanets().size();
                    }
                }
            }
            hoverText = "hover=" + hovered + " type=" + typeId + " stars=" + stars + " planets=" + planets;
        }
        debugLabel.setText(hoverText);

        // 世界渲染
        gridRenderer.setProjectionMatrix(camera.combined);
        gridRenderer.render(universe, hovered, camera.zoom, camera);

        stellarMarkerRenderer.setProjectionMatrix(camera.combined);
        stellarMarkerRenderer.render(universe, camera.zoom, camera);

        overlayRenderer.setProjectionMatrix(camera.combined);
        overlayRenderer.render(universe, camera);

        // UI层：鼠标附近星区中心坐标
        gridRenderer.renderSectorCenterCoordinatesUi(universe, camera);

        // 鼠标静止检测 & 世界坐标提示
        int currX = Gdx.input.getX();
        int currY = Gdx.input.getY();
        if (currX != lastMouseX || currY != lastMouseY) {
            mouseIdleSec = 0f;
            lastMouseX = currX;
            lastMouseY = currY;
        } else {
            mouseIdleSec += delta;
            if (mouseIdleSec >= MOUSE_IDLE_THRESHOLD_SEC) {
                gridRenderer.renderMouseWorldCoordUi(camera);
            }
        }

        // 014: F3 世界空间调试渲染（不依赖 UIManager）
        if (debugSystem.isEnabled()) {
            var debugState = debugSystem.snapshot(camera.zoom);

            debugWorldGridRenderer.setVisible(true);
            debugWorldGridRenderer.render(camera, debugState.kmPerPixel(), universe);

            // 坐标轴：2D 视角仅绘制 X/Y
            debugWorldAxisRenderer.setVisible(true);
            debugWorldAxisRenderer.render(camera);
        } else {
            debugWorldGridRenderer.setVisible(false);
            debugWorldAxisRenderer.setVisible(false);
        }

        // UI 渲染（Stage.draw 在这里发生）
        uiManager.render(delta, false);

        // 014: F3 文本调试（作为 uiStage actor，不需要手动画 batch）
        if (debugSystem.isEnabled()) {
            var debugState = debugSystem.snapshot(camera.zoom);
            debugOverlayController.setVisible(true);
            debugOverlayController.update(
                    debugState.cameraWorld(),
                    debugState.zoom(),
                    debugState.kmPerPixel(),
                    debugState.scaleText());
        } else {
            debugOverlayController.setVisible(false);
        }
    }

    @Override
    public void resize(int width, int height) {
        uiStage.getViewport().update(width, height, true);
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
        gridRenderer.resizeUiViewport(width, height);
    }

    @Override
    public void dispose() {
        uiStage.dispose();
        gridRenderer.dispose();
        overlayRenderer.dispose();
        stellarMarkerRenderer.dispose();
        debugWorldGridRenderer.dispose();
        debugWorldAxisRenderer.dispose();
    }
}
