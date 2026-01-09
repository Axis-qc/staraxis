package com.staraxis.game.client.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
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
import com.staraxis.game.core.coordinate.CameraWorld;
import com.staraxis.game.client.ui.view.WorldOverlayRenderer;
import com.staraxis.game.client.ui.view.stellar.StellarMarkerRenderer;
import com.staraxis.game.core.world.stellar.orbit.OrbitPathService;
import com.staraxis.game.client.world.UniverseModel;
import com.staraxis.game.client.world.UniverseModelToWorldMapAdapter;
import com.staraxis.game.client.ui.view.debug.DebugSystem;
import com.staraxis.game.client.ui.view.debug.WorldGridRenderer;
import com.staraxis.universegen.GalaxyGeneratorFacade;
import com.staraxis.universegen.config.UniverseGenConfig;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.net.worldgen.snapshot.WorldGenStatsSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.StarSnapshot;
import com.staraxis.game.shared.net.worldgen.snapshot.StarSystemSnapshot;

import io.staraxis.lwjgl3.debug.OrbitDebugRenderer;
import io.staraxis.lwjgl3.debug.OrbitDebugRenderer.OrbitPathRenderItem;

import java.util.ArrayList;
import java.util.List;

import io.staraxis.Main;

/**
 * 世界渲染屏幕 (World Screen). 负责展示六边形网格地图并处理主要的摄像机交互。
 */
public class WorldScreen extends ScreenAdapter {

    private final Main game;
    private final Stage uiStage;
    private final OrthographicCamera worldCamera;
    private final UIManager uiManager;
    private final CameraWorld camWorld = new CameraWorld(0.0, 0.0);

    private final HexGridRenderer gridRenderer;
    private final HexPicker hexPicker;
    private final CameraController cameraController;
    private final WorldOverlayRenderer overlayRenderer;
    private final StellarMarkerRenderer stellarMarkerRenderer;
    private final OrbitDebugRenderer orbitDebugRenderer;
    private final OrbitPathService orbitPathService;
    private final UniverseModel universeModel;
    private HexCoord hoveredCoord;
    private Label debugLabel;
    private Label fpsLabel;
    private boolean orbitDebugEnabled;
    private float orbitDebugScale;

    public WorldScreen(Main game, UniverseModel universeModel) {
        this.game = game;
        this.uiManager = game.getUiManager();
        this.uiStage = new Stage(new ScreenViewport());
        this.universeModel = universeModel;

        // 初始化世界摄像机 (Orthographic)
        this.worldCamera = new OrthographicCamera();
        this.worldCamera.setToOrtho(false, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        this.worldCamera.position.set(0, 0, 20); // 移动到 Z=20
        this.worldCamera.lookAt(0, 0, 0); // 看向原点
        this.worldCamera.near = 0.1f;
        this.worldCamera.far = 100f;
        this.worldCamera.update();

        // 初始化渲染器与拾取器 (T019, T020)
        this.gridRenderer = new HexGridRenderer(camWorld);
        // 设置字体用于显示坐标
        if (game.getSkin() != null && game.getSkin().getFont("default") != null) {
            this.gridRenderer.setFont(game.getSkin().getFont("default"));
        }
        this.hexPicker = new HexPicker(gridRenderer, camWorld);

        // 初始化摄像机控制器 (T041)
        this.cameraController = new CameraController(worldCamera, camWorld);

        // 初始化覆盖层渲染器 (T052)
        this.overlayRenderer = new WorldOverlayRenderer();

        // 初始化恒星标记渲染器 (US3)
        this.stellarMarkerRenderer = new StellarMarkerRenderer(camWorld);

        this.orbitDebugRenderer = new OrbitDebugRenderer();
        this.orbitPathService = new OrbitPathService();
        this.orbitDebugEnabled = Boolean.parseBoolean(System.getProperty("staraxis.orbitDebugEnabled", "false"));
        this.orbitDebugScale = parseFloatOrDefault(System.getProperty("staraxis.orbitDebugScale", "40.0"), 40.0f);

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
        this(game, createDefaultUniverse());
    }

    private static UniverseModel createDefaultUniverse() {
        UniverseGenConfig config = new UniverseGenConfig();
        config.setGalaxyRadiusR(8); // Default to a reasonable size
        config.setSeed(System.currentTimeMillis());
        config.setHexRadiusLy(1.0f);

        GalaxyGeneratorFacade generator = new GalaxyGeneratorFacade();
        com.staraxis.game.shared.net.worldgen.snapshot.UniverseSnapshot snapshot = generator.generate(config);

        UniverseModelToWorldMapAdapter adapter = new UniverseModelToWorldMapAdapter();
        return adapter.adapt(snapshot);
    }

    @Override
    public void show() {
        // 使用 InputMultiplexer 同时处理 UI 舞台和摄像机控制 (T044)
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(new InputAdapter() {
            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.F6) {
                    orbitDebugEnabled = !orbitDebugEnabled;
                    return true;
                }
                return false;
            }
        });
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
            if (universeModel.getSectors().containsKey(hoveredCoord)) {
                com.staraxis.game.client.world.SectorModel sector = universeModel.getSector(hoveredCoord);
                String typeId = sector.getSectorType();
                typeText = " | typeId=" + typeId;
                StarSystemSnapshot sys = sector.getStarSystem();
                if (sys != null) {
                    int stars = sys.getStars() != null ? sys.getStars().size() : 0;
                    int planets = 0;
                    if (sys.getStars() != null) {
                        for (StarSnapshot s : sys.getStars()) {
                            if (s.getPlanets() != null) {
                                planets += s.getPlanets().size();
                            }
                        }
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

        WorldGenStatsSnapshot stats = universeModel.getStats();
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
        gridRenderer.render(universeModel, hoveredCoord, worldCamera.zoom, worldCamera);

        // 3. 恒星标记渲染 (US3)
        stellarMarkerRenderer.setProjectionMatrix(worldCamera.combined);
        stellarMarkerRenderer.render(universeModel, worldCamera.zoom, worldCamera);

        // 目前 orbit 调试仍依赖旧的 WorldMap/StarSystem 模型，这里暂不绘制轨道，
        // 等后续接入完整的快照→领域模型转换后再恢复。

        // 4. 覆盖层渲染 (T052, T053)
        overlayRenderer.setProjectionMatrix(worldCamera.combined);
        overlayRenderer.render(universeModel, worldCamera);

        // UI层：鼠标附近星区中心坐标
        gridRenderer.renderSectorCenterCoordinatesUi(universeModel, worldCamera);

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
        orbitDebugRenderer.dispose();
    }

    public OrthographicCamera getWorldCamera() {
        return worldCamera;
    }

    private static float parseFloatOrDefault(String raw, float defaultValue) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(raw);
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }
}
