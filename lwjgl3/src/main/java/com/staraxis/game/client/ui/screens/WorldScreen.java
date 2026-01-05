package com.staraxis.game.client.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.staraxis.game.client.ui.manager.UIManager;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.staraxis.game.client.ui.view.CameraController;
import com.staraxis.game.client.ui.view.HexGridRenderer;
import com.staraxis.game.client.ui.view.HexPicker;
import com.staraxis.game.client.ui.view.WorldOverlayRenderer;
import com.staraxis.game.core.world.DefaultWorldGenerator;
import com.staraxis.game.core.world.WorldGenerator;
import com.staraxis.game.shared.world.*;
import io.staraxis.Main;

/**
 * 世界渲染屏幕 (World Screen). 负责展示六边形网格地图并处理主要的摄像机交互。
 */
public class WorldScreen extends ScreenAdapter {

    private final Main game;
    private final Stage uiStage;
    private final OrthographicCamera worldCamera;
    private final UIManager uiManager;
    private final HexGridRenderer gridRenderer;
    private final HexPicker hexPicker;
    private final CameraController cameraController;
    private final WorldOverlayRenderer overlayRenderer;
    private final WorldMap worldMap;
    private HexCoord hoveredCoord;
    private Label debugLabel;
    private Label fpsLabel;

    public WorldScreen(Main game, WorldMap worldMap) {
        this.game = game;
        this.uiManager = game.getUiManager();
        this.uiStage = new Stage(new ScreenViewport());
        this.worldMap = worldMap;

        // 初始化世界摄像机 (Orthographic)
        this.worldCamera = new OrthographicCamera();
        this.worldCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        this.worldCamera.position.set(0, 0, 0);
        this.worldCamera.update();

        // 初始化渲染器与拾取器 (T019, T020)
        this.gridRenderer = new HexGridRenderer();
        this.hexPicker = new HexPicker(gridRenderer);

        // 初始化摄像机控制器 (T041)
        this.cameraController = new CameraController(worldCamera);

        // 初始化覆盖层渲染器 (T052)
        this.overlayRenderer = new WorldOverlayRenderer();

        // 创建调试 UI (T022, T050)
        Table table = new Table();
        table.top().left();
        table.setFillParent(true);
        debugLabel = new Label("Hovered: (0, 0, 0)", game.getSkin());
        fpsLabel = new Label("FPS: 0", game.getSkin());
        table.add(debugLabel).pad(10).left().row();
        table.add(fpsLabel).pad(10).left();
        uiStage.addActor(table);
    }

    /**
     * 调试用构造函数，创建一个默认地图。
     */
    public WorldScreen(Main game) {
        this(game, createDefaultMap());
    }

    private static WorldMap createDefaultMap() {
        WorldGenConfig config = new WorldGenConfig();
        config.setMapSizePresetId("small");
        config.setSeedValue(System.currentTimeMillis());
        config.setHabitableRatio(0.3f);

        WorldGenerator generator = new DefaultWorldGenerator();
        return generator.generate(config);
    }

    @Override
    public void show() {
        // 使用 InputMultiplexer 同时处理 UI 舞台和摄像机控制 (T044)
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(uiStage);
        multiplexer.addProcessor(cameraController);
        Gdx.input.setInputProcessor(multiplexer);

        uiManager.setCurrentStage(uiStage);
    }

    @Override
    public void render(float delta) {
        // 1. 背景层渲染 (T051)
        Gdx.gl.glClearColor(0.02f, 0.02f, 0.05f, 1f); // 深空背景色
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // 自动管理输入拦截 (T014, T015)
        // 如果 UI 舞台上有控件获得了键盘焦点（如 TextField），则拦截镜头控制
        cameraController.setIntercepted(uiStage.getKeyboardFocus() != null);

        // 更新逻辑
        cameraController.update(delta);
        worldCamera.update();

        // 处理拾取逻辑 (T021)
        hoveredCoord = hexPicker.screenToHex(Gdx.input.getX(), Gdx.input.getY(), worldCamera);
        if (hoveredCoord != null) {
            debugLabel.setText(String.format("%s: %s | %s: %.2f",
                    game.getLocalizationService().get("world_hovered_coord", "Hovered"),
                    hoveredCoord.toString(),
                    game.getLocalizationService().get("world_zoom_level", "Zoom"),
                    worldCamera.zoom));
        }
        fpsLabel.setText("FPS: " + Gdx.graphics.getFramesPerSecond());

        // 2. 网格层渲染 (T053)
        gridRenderer.setProjectionMatrix(worldCamera.combined);
        gridRenderer.render(worldMap, hoveredCoord, worldCamera.zoom, worldCamera);

        // 3. 覆盖层渲染 (T052, T053)
        overlayRenderer.setProjectionMatrix(worldCamera.combined);
        overlayRenderer.render(worldMap, worldCamera);

        // 4. UI 层渲染 (T053)
        // 由 UIManager 在此后调用 stage.draw()
    }

    @Override
    public void resize(int width, int height) {
        uiStage.getViewport().update(width, height, true);
        worldCamera.viewportWidth = width;
        worldCamera.viewportHeight = height;
        worldCamera.update();
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        uiManager.clearCurrentStage();
    }

    @Override
    public void dispose() {
        uiStage.dispose();
        gridRenderer.dispose();
        overlayRenderer.dispose();
    }

    public OrthographicCamera getWorldCamera() {
        return worldCamera;
    }
}
