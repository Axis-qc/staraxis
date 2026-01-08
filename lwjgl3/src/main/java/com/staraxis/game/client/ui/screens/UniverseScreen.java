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
import com.staraxis.game.client.world.UniverseModel;
import com.staraxis.game.client.world.UniverseModelToWorldMapAdapter;
import com.staraxis.game.shared.world.HexCoord;
import com.staraxis.game.shared.world.WorldMap;

import io.staraxis.Main;

/**
 * 新版宇宙星图界面（UniverseScreen）：
 * - 使用 UniverseModel 作为运行时模型
 * - 当前阶段通过适配器转换为 WorldMap，以复用现有渲染器
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

    private Label debugLabel;
    private HexCoord hovered;

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

        Table table = new Table();
        table.top().left();
        table.setFillParent(true);
        debugLabel = new Label("", game.getSkin());
        table.add(debugLabel).pad(10).left();
        uiStage.addActor(table);
    }

    @Override
    public void show() {
        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(uiStage);
        multiplexer.addProcessor(cameraController);
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

        WorldMap worldMap = adapter.toWorldMap(universe);

        String hoverText = "";
        if (hovered != null && worldMap.getTiles().containsKey(hovered)) {
            String typeId = worldMap.getTile(hovered).getTypeId();
            int stars = worldMap.getTile(hovered).getStarSystem() != null ? worldMap.getTile(hovered).getStarSystem().getStars().size() : 0;
            hoverText = "hover=" + hovered + " type=" + typeId + " stars=" + stars;
        }
        debugLabel.setText(hoverText);

        gridRenderer.setProjectionMatrix(camera.combined);
        gridRenderer.render(worldMap, hovered, camera.zoom, camera);

        overlayRenderer.setProjectionMatrix(camera.combined);
        overlayRenderer.render(worldMap, camera);

        game.getUiManager().render(delta, false);
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
    }
}
