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
import com.staraxis.game.client.ui.view.stellar.StellarMarkerRenderer;
import com.staraxis.game.core.world.DefaultWorldGenerator;
import com.staraxis.game.core.world.WorldGenerator;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.WorldGenConfig;
import com.staraxis.game.shared.world.WorldMap;
import com.staraxis.game.shared.world.stellar.Star;
import com.staraxis.game.shared.world.stellar.StarSystem;
import com.staraxis.game.shared.world.stellar.WorldGenStats;

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
    private final StellarMarkerRenderer stellarMarkerRenderer;
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
        this.worldCamera.position.set(0, 0, 20); // 移动到 Z=20
        this.worldCamera.lookAt(0, 0, 0); // 看向原点
        this.worldCamera.near = 0.1f;
        this.worldCamera.far = 100f;
        this.worldCamera.update();

        // 初始化渲染器与拾取器 (T019, T020)
        this.gridRenderer = new HexGridRenderer();
        this.hexPicker = new HexPicker(gridRenderer);

        // 初始化摄像机控制器 (T041)
        this.cameraController = new CameraController(worldCamera);

        // 初始化覆盖层渲染器 (T052)
        this.overlayRenderer = new WorldOverlayRenderer();

        // 初始化恒星标记渲染器 (US3)
        this.stellarMarkerRenderer = new StellarMarkerRenderer(gridRenderer);

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
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1f); // 稍微亮一点的深空背景色，便于调试
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        // 自动管理输入拦截 (T014, T015)
        // 如果 UI 舞台上有控件获得了键盘焦点（如 TextField），则拦截镜头控制
        cameraController.setIntercepted(uiStage.getKeyboardFocus() != null);

        // 更新逻辑
        cameraController.update(delta);
        worldCamera.update();

        // 处理拾取逻辑 (T021)
        hoveredCoord = hexPicker.screenToHex(Gdx.input.getX(), Gdx.input.getY(), worldCamera);
        String hoverText = "";
        if (hoveredCoord != null) {
            String typeText = "";
            if (worldMap.getTiles().containsKey(hoveredCoord)) {
                String typeId = worldMap.getTile(hoveredCoord).getTypeId();
                typeText = " | typeId=" + typeId;
                StarSystem sys = worldMap.getTile(hoveredCoord).getStarSystem();
                if (sys != null) {
                    int stars = sys.getStars().size();
                    int planets = 0;
                    for (Star s : sys.getStars()) {
                        planets += s.getPlanets().size();
                    }
                    typeText += " | stars=" + stars + ", planets=" + planets;
                }
            }

            hoverText = String.format("%s: %s%s | %s: %.2f",
                    game.getLocalizationService().get("world_hovered_coord", "Hovered"),
                    hoveredCoord.toString(),
                    typeText,
                    game.getLocalizationService().get("world_zoom_level", "Zoom"),
                    worldCamera.zoom);
        }

        WorldGenStats stats = worldMap.getStats();
        String statsText = "";
        if (stats != null) {
            statsText = "\nstats: sectorCounts=" + stats.getSectorCounts()
                    + ", starCount=" + stats.getStarCount()
                    + ", planetCount=" + stats.getPlanetCount();
        }

        debugLabel.setText(hoverText + statsText);
        fpsLabel.setText("FPS: " + Gdx.graphics.getFramesPerSecond());

        // 2. 网格层渲染 (T053)
        gridRenderer.setProjectionMatrix(worldCamera.combined);
        gridRenderer.render(worldMap, hoveredCoord, worldCamera.zoom, worldCamera);

        // 3. 恒星标记渲染 (US3)
        stellarMarkerRenderer.setProjectionMatrix(worldCamera.combined);
        stellarMarkerRenderer.render(worldMap, worldCamera.zoom, worldCamera);

        // 4. 覆盖层渲染 (T052, T053)
        overlayRenderer.setProjectionMatrix(worldCamera.combined);
        overlayRenderer.render(worldMap, worldCamera);

        // 5. UI 层渲染 (T053)
        // 显式调用 UIManager 渲染，且不执行清屏，以保留背景和网格
        game.getUiManager().render(delta, false);
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
        stellarMarkerRenderer.dispose();
    }

    public OrthographicCamera getWorldCamera() {
        return worldCamera;
    }
}
