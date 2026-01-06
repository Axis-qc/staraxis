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
import com.staraxis.game.client.ui.MainMenuScreen;
import com.staraxis.game.client.ui.manager.UIManager;
import com.staraxis.game.client.net.WorldGenApiClient;
import com.staraxis.game.core.i18n.LocalizationService;
import com.staraxis.game.shared.net.worldgen.ErrorEnvelope;
import com.staraxis.game.shared.net.worldgen.StartNewGameEffectiveConfig;
import com.staraxis.game.shared.net.worldgen.StartNewGameRequest;
import com.staraxis.game.shared.net.worldgen.StartNewGameResponse;
import com.staraxis.game.shared.net.worldgen.snapshot.WorldSnapshot;
import com.staraxis.game.shared.world.WorldGenDefinitions;
import com.staraxis.game.shared.world.WorldSnapshotConverter;

import io.staraxis.Main;

/**
 * 新游戏配置屏幕 (New Game Config Screen). 允许玩家配置地图大小、宜居比例、种子等参数。
 */
public class NewGameConfigScreen extends ScreenAdapter {

    private final Main game;
    private final Stage stage;
    private final LocalizationService i18n;
    private final UIManager uiManager;
    private final Skin skin;

    private SelectBox<String> mapSizeSelect;
    private Slider habitableSlider;
    private Label habitableValueLabel;
    private Slider starDensitySlider; // starDensitySlider（恒星密度滑条）
    private Label starDensityValueLabel; // starDensityValueLabel（恒星密度显示）
    private Slider planetComplexitySlider; // planetComplexitySlider（行星复杂度滑条）
    private Label planetComplexityValueLabel; // planetComplexityValueLabel（行星复杂度显示）
    private Slider nebulaRatioSlider; // nebulaRatioSlider（星云占比滑条）
    private Label nebulaRatioValueLabel; // nebulaRatioValueLabel（星云占比显示）
    private TextField seedField;
    private TextButton btnStart;
    private TextButton btnBack;

    private Label loadingLabel;

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

        // 标题
        Label titleLabel = new Label(i18n.get("new_game_config_title", "New Game Setup"), skin);
        titleLabel.setFontScale(1.5f);
        root.add(titleLabel).padBottom(30).colspan(2).row();

        // 1. 地图大小 (T033)
        root.add(new Label(i18n.get("config_map_size", "Map Size:"), skin)).left().padRight(10);
        mapSizeSelect = new SelectBox<>(skin);
        Map<String, Integer> presets = WorldGenDefinitions.getMapPresets();
        String[] presetIds = presets.keySet().toArray(new String[0]);
        mapSizeSelect.setItems(presetIds);
        mapSizeSelect.setSelected("medium");
        root.add(mapSizeSelect).width(200).padBottom(10).row();

        // 2. 宜居比例 (T034)
        root.add(new Label(i18n.get("config_habitable_ratio", "Habitable Ratio:"), skin)).left().padRight(10);
        Table sliderTable = new Table();
        habitableSlider = new Slider(0f, 1f, 0.05f, false, skin);
        habitableSlider.setValue(0.3f);
        habitableValueLabel = new Label("30%", skin);
        habitableSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                habitableValueLabel.setText((int) (habitableSlider.getValue() * 100) + "%");
            }
        });
        sliderTable.add(habitableSlider).width(150);
        sliderTable.add(habitableValueLabel).padLeft(10);
        root.add(sliderTable).padBottom(10).row();

        // 2.1 恒星密度 (T015)
        root.add(new Label(i18n.get("config_star_density", "Star Density:"), skin)).left().padRight(10);
        Table starDensityTable = new Table();
        starDensitySlider = new Slider(0f, 1f, 0.05f, false, skin);
        starDensitySlider.setValue(0.6f);
        starDensityValueLabel = new Label("60%", skin);
        starDensitySlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                starDensityValueLabel.setText((int) (starDensitySlider.getValue() * 100) + "%");
            }
        });
        starDensityTable.add(starDensitySlider).width(150);
        starDensityTable.add(starDensityValueLabel).padLeft(10);
        root.add(starDensityTable).padBottom(10).row();

        // 2.2 行星复杂度 (T016)
        root.add(new Label(i18n.get("config_planet_complexity", "Planet Complexity:"), skin)).left().padRight(10);
        Table planetComplexityTable = new Table();
        planetComplexitySlider = new Slider(0f, 1f, 0.05f, false, skin);
        planetComplexitySlider.setValue(0.5f);
        planetComplexityValueLabel = new Label("50%", skin);
        planetComplexitySlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                planetComplexityValueLabel.setText((int) (planetComplexitySlider.getValue() * 100) + "%");
            }
        });
        planetComplexityTable.add(planetComplexitySlider).width(150);
        planetComplexityTable.add(planetComplexityValueLabel).padLeft(10);
        root.add(planetComplexityTable).padBottom(10).row();

        // 2.3 星云占比 (T017)
        root.add(new Label(i18n.get("config_nebula_ratio", "Nebula Ratio:"), skin)).left().padRight(10);
        Table nebulaRatioTable = new Table();
        nebulaRatioSlider = new Slider(0f, 1f, 0.05f, false, skin);
        nebulaRatioSlider.setValue(0.2f);
        nebulaRatioValueLabel = new Label("20%", skin);
        nebulaRatioSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                nebulaRatioValueLabel.setText((int) (nebulaRatioSlider.getValue() * 100) + "%");
            }
        });
        nebulaRatioTable.add(nebulaRatioSlider).width(150);
        nebulaRatioTable.add(nebulaRatioValueLabel).padLeft(10);
        root.add(nebulaRatioTable).padBottom(10).row();

        // 3. 种子 (T035)
        root.add(new Label(i18n.get("config_seed", "Seed:"), skin)).left().padRight(10);
        seedField = new TextField("", skin);
        seedField.setMessageText(i18n.get("config_seed_placeholder", "Random if empty"));
        root.add(seedField).width(200).padBottom(10).row();

        // 4. AI 数量 (T036 - 禁用)
        root.add(new Label(i18n.get("config_ai_count", "AI Players:"), skin)).left().padRight(10);
        SelectBox<Integer> aiSelect = new SelectBox<>(skin);
        aiSelect.setItems(1, 2, 3, 4);
        aiSelect.setDisabled(true);
        root.add(aiSelect).width(200).padBottom(10);
        root.add(new Label(i18n.get("config_dev_placeholder", "(In Dev)"), skin)).padLeft(5).row();

        // 5. 按钮 (T039, T040)
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

        // 6. 加载状态占位 (T047)
        loadingLabel = new Label("", skin);
        loadingLabel.setColor(com.badlogic.gdx.graphics.Color.YELLOW);
        root.add(loadingLabel).colspan(2).padTop(10);
    }

    private void startGame() {
        // 显示加载状态并禁用按钮
        btnStart.setDisabled(true);
        btnBack.setDisabled(true);
        loadingLabel.setText(i18n.get("config_generating", "Generating World..."));

        final String mapSize = mapSizeSelect.getSelected();
        final float habitable = habitableSlider.getValue();
        final float starDensity = starDensitySlider.getValue();
        final float planetComplexity = planetComplexitySlider.getValue();
        final float nebulaRatio = nebulaRatioSlider.getValue();
        final String seedText = seedField.getText();

        // 在后台线程执行请求 (T047)
        new Thread(() -> {
            try {
                long startMs = System.currentTimeMillis();
                StartNewGameRequest request = new StartNewGameRequest();
                request.setMapSizePresetId(mapSize);
                request.setHabitableRatio(habitable);
                request.setStarDensity(starDensity);
                request.setPlanetComplexity(planetComplexity);
                request.setNebulaRatio(nebulaRatio);
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
                            + ", habitableRatio=" + effectiveConfig.getHabitableRatio()
                            + ", starDensity=" + effectiveConfig.getStarDensity()
                            + ", planetComplexity=" + effectiveConfig.getPlanetComplexity()
                            + ", nebulaRatio=" + effectiveConfig.getNebulaRatio());
                }

                WorldSnapshot snapshot = response.getWorld();
                if (snapshot == null) {
                    Gdx.app.postRunnable(() -> {
                        btnStart.setDisabled(false);
                        btnBack.setDisabled(false);
                        loadingLabel.setText(i18n.get("worldgen.internal_error", "Error"));
                    });
                    return;
                }

                WorldSnapshotConverter converter = new WorldSnapshotConverter();
                final com.staraxis.game.shared.world.WorldMap worldMap = converter.toWorldMap(snapshot);

                long durationMs = System.currentTimeMillis() - startMs;
                Gdx.app.log("WorldGen", "clientDurationMs=" + durationMs);

                // 生成完成后切回 GL 线程更新 UI/Screen
                Gdx.app.postRunnable(() -> {
                    game.setScreen(new WorldScreen(game, worldMap));
                });
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
        // UI 舞台渲染由 UIManager 驱动
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
