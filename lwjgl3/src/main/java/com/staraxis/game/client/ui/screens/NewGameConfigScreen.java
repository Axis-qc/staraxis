package com.staraxis.game.client.ui.screens;

import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.staraxis.game.client.net.WorldGenApiClient;
import com.staraxis.game.client.ui.MainMenuScreen;
import com.staraxis.game.client.ui.manager.UIManager;
import com.staraxis.game.client.world.UniverseModel;
import com.staraxis.game.client.world.UniverseSnapshotConverter;
import com.staraxis.game.core.i18n.LocalizationService;
import com.staraxis.game.shared.net.worldgen.ErrorEnvelope;
import com.staraxis.game.shared.net.worldgen.StartNewGameEffectiveConfig;
import com.staraxis.game.shared.net.worldgen.StartNewGameRequest;
import com.staraxis.game.shared.net.worldgen.StartNewGameResponse;
import com.staraxis.game.shared.net.worldgen.snapshot.UniverseSnapshot;
import com.staraxis.game.shared.world.WorldGenDefinitions;

import io.staraxis.Main;

/**
 * 新游戏配置屏幕（NewGameConfigScreen）。
 *
 * 013：使用三滑条比例（galaxy/nebula/deep_space），总和上限为 1。
 */
public class NewGameConfigScreen extends ScreenAdapter {

    private final Main game;
    private final Stage stage;
    private final LocalizationService i18n;
    private final UIManager uiManager;
    private final Skin skin;

    private SelectBox<String> mapSizeSelect;

    private Slider galaxyRatioSlider;
    private Label galaxyRatioValueLabel;

    private Slider nebulaRatioSlider;
    private Label nebulaRatioValueLabel;

    private Slider deepSpaceRatioSlider;
    private Label deepSpaceRatioValueLabel;

    private Slider planetComplexitySlider;
    private Label planetComplexityValueLabel;

    private TextField seedField;
    private TextButton btnStart;
    private TextButton btnBack;

    private Label loadingLabel;

    // 防止联动递归触发
    private boolean ratioUpdating;

    public NewGameConfigScreen(Main game) {
        this.game = game;
        this.i18n = game.getLocalizationService();
        this.uiManager = game.getUiManager();
        this.skin = game.getSkin();
        this.stage = new Stage(new ScreenViewport());
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        uiManager.setCurrentStage(stage);

        Table root = new Table();
        root.setFillParent(true);
        root.center();
        stage.addActor(root);

        Label titleLabel = new Label(i18n.get("new_game_config_title", "New Game Setup"), skin);
        titleLabel.setFontScale(1.5f);
        root.add(titleLabel).padBottom(30).colspan(2).row();

        // 1) 地图大小
        root.add(new Label(i18n.get("config_map_size", "Map Size:"), skin)).left().padRight(10);
        mapSizeSelect = new SelectBox<>(skin);
        Map<String, Integer> presets = WorldGenDefinitions.getMapPresets();
        String[] presetIds = presets.keySet().toArray(new String[0]);
        mapSizeSelect.setItems(presetIds);
        mapSizeSelect.setSelected("medium");
        root.add(mapSizeSelect).width(200).padBottom(10).row();

        // 2) 星区比例：galaxy
        root.add(new Label(i18n.get("config_galaxy_ratio", "Galaxy Ratio:"), skin)).left().padRight(10);
        Table galaxyTable = new Table();
        galaxyRatioSlider = new Slider(0f, 1f, 0.05f, false, skin);
        galaxyRatioValueLabel = new Label("60%", skin);
        galaxyTable.add(galaxyRatioSlider).width(150);
        galaxyTable.add(galaxyRatioValueLabel).padLeft(10);
        root.add(galaxyTable).padBottom(10).row();

        // 3) 星区比例：nebula
        root.add(new Label(i18n.get("config_nebula_ratio", "Nebula Ratio:"), skin)).left().padRight(10);
        Table nebulaTable = new Table();
        nebulaRatioSlider = new Slider(0f, 1f, 0.05f, false, skin);
        nebulaRatioValueLabel = new Label("20%", skin);
        nebulaTable.add(nebulaRatioSlider).width(150);
        nebulaTable.add(nebulaRatioValueLabel).padLeft(10);
        root.add(nebulaTable).padBottom(10).row();

        // 4) 星区比例：deep_space（只读派生）
        root.add(new Label(i18n.get("config_deep_space_ratio", "Deep Space Ratio:"), skin)).left().padRight(10);
        Table deepSpaceTable = new Table();
        deepSpaceRatioSlider = new Slider(0f, 1f, 0.05f, false, skin);
        deepSpaceRatioSlider.setDisabled(true);
        deepSpaceRatioValueLabel = new Label("20%", skin);
        deepSpaceTable.add(deepSpaceRatioSlider).width(150);
        deepSpaceTable.add(deepSpaceRatioValueLabel).padLeft(10);
        root.add(deepSpaceTable).padBottom(10).row();

        // 5) 行星复杂度（保留）
        root.add(new Label(i18n.get("config_planet_complexity", "Planet Complexity:"), skin)).left().padRight(10);
        Table planetTable = new Table();
        planetComplexitySlider = new Slider(0f, 1f, 0.05f, false, skin);
        planetComplexityValueLabel = new Label("50%", skin);
        planetComplexitySlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                planetComplexityValueLabel.setText((int) (planetComplexitySlider.getValue() * 100) + "%");
            }
        });
        planetTable.add(planetComplexitySlider).width(150);
        planetTable.add(planetComplexityValueLabel).padLeft(10);
        root.add(planetTable).padBottom(10).row();

        // 6) 种子
        root.add(new Label(i18n.get("config_seed", "Seed:"), skin)).left().padRight(10);
        seedField = new TextField("", skin);
        seedField.setMessageText(i18n.get("config_seed_placeholder", "Random if empty"));
        root.add(seedField).width(200).padBottom(10).row();

        // 7) AI 数量（占位/禁用）
        root.add(new Label(i18n.get("config_ai_count", "AI Players:"), skin)).left().padRight(10);
        SelectBox<Integer> aiSelect = new SelectBox<>(skin);
        aiSelect.setItems(1, 2, 3, 4);
        aiSelect.setDisabled(true);
        root.add(aiSelect).width(200).padBottom(10);
        root.add(new Label(i18n.get("config_dev_placeholder", "(In Dev)"), skin)).padLeft(5).row();

        // 默认值
        galaxyRatioSlider.setValue(0.6f);
        nebulaRatioSlider.setValue(0.2f);
        planetComplexitySlider.setValue(0.5f);
        recalcDeepSpaceAndLabels();

        // 联动：只允许调 galaxy / nebula，deep_space 派生
        galaxyRatioSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (ratioUpdating) {
                    return;
                }
                ratioUpdating = true;
                clampTwoSlidersToOne(galaxyRatioSlider, nebulaRatioSlider);
                recalcDeepSpaceAndLabels();
                ratioUpdating = false;
            }
        });

        nebulaRatioSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (ratioUpdating) {
                    return;
                }
                ratioUpdating = true;
                clampTwoSlidersToOne(nebulaRatioSlider, galaxyRatioSlider);
                recalcDeepSpaceAndLabels();
                ratioUpdating = false;
            }
        });

        // 8) 按钮
        Table btnTable = new Table();
        btnStart = new TextButton(i18n.get("config_start", "Start Game"), skin);
        btnStart.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                startGame();
            }
        });

        btnBack = new TextButton(i18n.get("config_back", "Back"), skin);
        btnBack.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MainMenuScreen(game));
            }
        });

        btnTable.add(btnStart).width(120).height(40).padRight(20);
        btnTable.add(btnBack).width(120).height(40);
        root.add(btnTable).colspan(2).padTop(30).row();

        // 9) 加载状态
        loadingLabel = new Label("", skin);
        loadingLabel.setColor(com.badlogic.gdx.graphics.Color.YELLOW);
        root.add(loadingLabel).colspan(2).padTop(10);
    }

    /**
     * 约束：primary + secondary <= 1。
     * 若 primary 调大导致超限，则自动压缩 secondary。
     */
    private void clampTwoSlidersToOne(Slider primary, Slider secondary) {
        float p = primary.getValue();
        float s = secondary.getValue();
        float sum = p + s;
        if (sum > 1.0f) {
            secondary.setValue(Math.max(0.0f, 1.0f - p));
        }
    }

    private void recalcDeepSpaceAndLabels() {
        float g = galaxyRatioSlider.getValue();
        float n = nebulaRatioSlider.getValue();
        float d = Math.max(0.0f, 1.0f - g - n);

        galaxyRatioValueLabel.setText((int) (g * 100) + "%");
        nebulaRatioValueLabel.setText((int) (n * 100) + "%");

        deepSpaceRatioSlider.setValue(d);
        deepSpaceRatioValueLabel.setText((int) (d * 100) + "%");
    }

    private void startGame() {
        btnStart.setDisabled(true);
        btnBack.setDisabled(true);
        loadingLabel.setText(i18n.get("config_generating", "Generating World..."));

        final String mapSize = mapSizeSelect.getSelected();
        final float galaxyRatio = galaxyRatioSlider.getValue();
        final float nebulaRatio = nebulaRatioSlider.getValue();
        final float deepSpaceRatio = Math.max(0.0f, 1.0f - galaxyRatio - nebulaRatio);
        final float planetComplexity = planetComplexitySlider.getValue();
        final String seedText = seedField.getText();

        new Thread(() -> {
            try {
                long startMs = System.currentTimeMillis();

                StartNewGameRequest request = new StartNewGameRequest();
                request.setMapSizePresetId(mapSize);
                request.setGalaxyRatio(galaxyRatio);
                request.setNebulaRatio(nebulaRatio);
                request.setDeepSpaceRatio(deepSpaceRatio);
                request.setPlanetComplexity(planetComplexity);
                request.setSeedText(seedText);

                WorldGenApiClient apiClient = new WorldGenApiClient("http://127.0.0.1:8080");
                StartNewGameResponse response = apiClient.startNewGame(request);

                if (response.getError() != null) {
                    ErrorEnvelope err = response.getError();
                    String msg = i18n.get(err.getMessageKey(), "Error");
                    Gdx.app.postRunnable(() -> {
                        btnStart.setDisabled(false);
                        btnBack.setDisabled(false);
                        loadingLabel.setText(msg);
                    });
                    return;
                }

                StartNewGameEffectiveConfig effectiveConfig = response.getEffectiveConfig();
                if (effectiveConfig != null) {
                    Gdx.app.log("WorldGen", "effectiveConfig: mapSizePresetId=" + effectiveConfig.getMapSizePresetId()
                            + ", seedText=" + effectiveConfig.getSeedText()
                            + ", seedValue=" + effectiveConfig.getSeedValue()
                            + ", galaxyRatio=" + effectiveConfig.getGalaxyRatio()
                            + ", nebulaRatio=" + effectiveConfig.getNebulaRatio()
                            + ", deepSpaceRatio=" + effectiveConfig.getDeepSpaceRatio()
                            + ", planetComplexity=" + effectiveConfig.getPlanetComplexity());
                }

                UniverseSnapshot snapshot = response.getWorld();
                if (snapshot == null) {
                    Gdx.app.postRunnable(() -> {
                        btnStart.setDisabled(false);
                        btnBack.setDisabled(false);
                        loadingLabel.setText(i18n.get("worldgen.internal_error", "Error"));
                    });
                    return;
                }

                UniverseSnapshotConverter converter = new UniverseSnapshotConverter();
                final UniverseModel universe = converter.toUniverseModel(snapshot);

                long durationMs = System.currentTimeMillis() - startMs;
                Gdx.app.log("WorldGen", "clientDurationMs=" + durationMs);

                Gdx.app.postRunnable(() -> game.setScreen(new UniverseScreen(game, universe)));
            } catch (Exception e) {
                Gdx.app.error("NewGameConfig", "Failed to generate world", e);
                Gdx.app.postRunnable(() -> {
                    btnStart.setDisabled(false);
                    btnBack.setDisabled(false);
                    loadingLabel.setText(i18n.get("worldgen.server_unreachable", "Error"));
                });
            }
        }).start();
    }

    @Override
    public void render(float delta) {
        game.getUiManager().render(delta);
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
