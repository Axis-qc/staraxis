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
import com.staraxis.game.client.world.UniverseModelToWorldMapAdapter;
import com.staraxis.game.core.coordinate.CoordinateService;
import com.staraxis.game.core.coordinate.WorldCoordinate;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.WorldMap;
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
    private final UniverseModelToWorldMapAdapter adapter;

    private final HexGridRenderer gridRenderer;
    private final HexPicker hexPicker;
    private final CameraController cameraController;
    private final WorldOverlayRenderer overlayRenderer;
    private final StellarMarkerRenderer stellarMarkerRenderer;

    private Label debugLabel;
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
        this.adapter = new UniverseModelToWorldMapAdapter();

        this.camera = new OrthographicCamera();
        this.camera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        this.camera.position.set(0, 0, 20);
        this.camera.lookAt(0, 0, 0);
        this.camera.near = 0.1f;
        this.camera.far = 100f;
        this.camera.update();

        this.gridRenderer = new HexGridRenderer();
        this.hexPicker = new HexPicker(gridRenderer);
        this.cameraController = new CameraController(camera);
        this.overlayRenderer = new WorldOverlayRenderer();
        this.stellarMarkerRenderer = new StellarMarkerRenderer(gridRenderer);

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

        WorldMap worldMap = adapter.toWorldMap(universe);

        // 原有 hover 文本
        String hoverText = "";
        if (hovered != null && worldMap.getTiles().containsKey(hovered)) {
            String typeId = worldMap.getTile(hovered).getTypeId();
            int stars = worldMap.getTile(hovered).getStarSystem() != null
                    ? worldMap.getTile(hovered).getStarSystem().getStars().size()
                    : 0;
            int planets = 0;
            if (worldMap.getTile(hovered).getStarSystem() != null) {
                planets = worldMap.getTile(hovered).getStarSystem().getStars().stream().mapToInt(s -> s.getPlanets().size()).sum();
            }
            hoverText = "hover=" + hovered + " type=" + typeId + " stars=" + stars + " planets=" + planets;
        }
        debugLabel.setText(hoverText);

        // 世界渲染
        gridRenderer.setProjectionMatrix(camera.combined);
        gridRenderer.render(worldMap, hovered, camera.zoom, camera);

        stellarMarkerRenderer.setProjectionMatrix(camera.combined);
        stellarMarkerRenderer.render(worldMap, camera.zoom, camera);

        overlayRenderer.setProjectionMatrix(camera.combined);
        overlayRenderer.render(worldMap, camera);

        // 014: F3 世界空间调试渲染（不依赖 UIManager）
        if (debugSystem.isEnabled()) {
            var debugState = debugSystem.snapshot(camera.zoom);

            debugWorldGridRenderer.setVisible(true);
            debugWorldGridRenderer.render(camera, debugState.kmPerPixel());

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
