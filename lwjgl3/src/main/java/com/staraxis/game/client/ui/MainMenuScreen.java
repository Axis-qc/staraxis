package com.staraxis.game.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.staraxis.game.client.ui.components.AnimatedButton;
import com.staraxis.game.client.ui.components.ParallaxBackground;
import com.staraxis.game.client.ui.components.Toast;
import com.staraxis.game.core.i18n.LanguageChangeListener;
import com.staraxis.game.core.i18n.LocalizationService;

import io.staraxis.Main;

/**
 * 主菜单页面 (Main Menu Screen)
 *
 * 包含游戏核心入口：新游戏、加载游戏、多人游戏、设置、退出
 */
public class MainMenuScreen extends ScreenAdapter implements LanguageChangeListener {

    private final Main game;
    private final Stage stage;
    private final LocalizationService i18n;
    private ParallaxBackground parallaxBackground;

    private AnimatedButton btnNewGame;
    private AnimatedButton btnLoadGame;
    private AnimatedButton btnMultiplayer;
    private AnimatedButton btnSettings;
    private AnimatedButton btnExit;

    public MainMenuScreen(Main game) {
        this.game = game;
        this.i18n = game.getLocalizationService();
        this.stage = new Stage(new ScreenViewport());
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        i18n.addListener(this);

        // 注册到 UIManager
        game.getUiManager().setCurrentStage(stage);

        // 初始化视差背景
        parallaxBackground = new ParallaxBackground(stage.getViewport());
        // 实际项目中应从 AssetManager 获取 Texture，这里暂时创建简单的星空层
        createPlaceholderParallaxLayers();

        Table table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        // 1. 新游戏 (New Game) - 占位
        btnNewGame = new AnimatedButton(i18n.get("main_menu_new_game"), game.getSkin());
        btnNewGame.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Toast.show(stage, i18n.get("common_feature_in_development"), game.getSkin());
            }
        });

        // 2. 加载游戏 (Load Game) - 占位
        btnLoadGame = new AnimatedButton(i18n.get("main_menu_load_game"), game.getSkin());
        btnLoadGame.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Toast.show(stage, i18n.get("common_feature_in_development"), game.getSkin());
            }
        });

        // 3. 多人游戏 (Multiplayer) - 占位
        btnMultiplayer = new AnimatedButton(i18n.get("main_menu_multiplayer"), game.getSkin());
        btnMultiplayer.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Toast.show(stage, i18n.get("common_feature_in_development"), game.getSkin());
            }
        });

        // 4. 设置 (Settings)
        btnSettings = new AnimatedButton(i18n.get("main_menu_settings"), game.getSkin());
        btnSettings.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new SettingsScreen(game));
            }
        });

        // 5. 退出 (Exit)
        btnExit = new AnimatedButton(i18n.get("main_menu_exit"), game.getSkin());
        btnExit.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Gdx.app.exit();
            }
        });

        // 布局按钮
        float buttonWidth = 200;
        float buttonHeight = 50;
        float padding = 10;

        table.add(btnNewGame).width(buttonWidth).height(buttonHeight).padBottom(padding).row();
        table.add(btnLoadGame).width(buttonWidth).height(buttonHeight).padBottom(padding).row();
        table.add(btnMultiplayer).width(buttonWidth).height(buttonHeight).padBottom(padding).row();
        table.add(btnSettings).width(buttonWidth).height(buttonHeight).padBottom(padding).row();
        table.add(btnExit).width(buttonWidth).height(buttonHeight);
    }

    @Override
    public void onLanguageChanged() {
        btnNewGame.setText(i18n.get("main_menu_new_game"));
        btnLoadGame.setText(i18n.get("main_menu_load_game"));
        btnMultiplayer.setText(i18n.get("main_menu_multiplayer"));
        btnSettings.setText(i18n.get("main_menu_settings"));
        btnExit.setText(i18n.get("main_menu_exit"));
    }

    @Override
    public void render(float delta) {
        // 渲染逻辑已委派给 UIManager
    }

    private void createPlaceholderParallaxLayers() {
        // 创建简单的星空纹理作为占位符
        // Layer 1: 远景（慢速）
        Texture texFar = new Texture(Gdx.files.internal("libgdx.png")); // 暂时复用 libgdx.png
        parallaxBackground.addLayer(new TextureRegion(texFar), 5f, 2f);

        // Layer 2: 近景（快速）
        // 实际开发中应该有专门的星点或云雾贴图
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        i18n.removeListener(this);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
