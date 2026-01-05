package com.staraxis.game.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.staraxis.game.client.ui.components.Toast;

import io.staraxis.Main;

/**
 * 主菜单页面 (Main Menu Screen)
 *
 * 包含游戏核心入口：新游戏、加载游戏、多人游戏、设置、退出
 */
public class MainMenuScreen extends ScreenAdapter {

    private final Main game;
    private final Stage stage;

    public MainMenuScreen(Main game) {
        this.game = game;
        this.stage = new Stage(new ScreenViewport());
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);

        Table table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        // 1. 新游戏 (New Game) - 占位
        TextButton btnNewGame = new TextButton("New Game", game.getSkin());
        btnNewGame.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Toast.show(stage, "Feature under development (US4)", game.getSkin());
            }
        });

        // 2. 加载游戏 (Load Game) - 占位
        TextButton btnLoadGame = new TextButton("Load Game", game.getSkin());
        btnLoadGame.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Toast.show(stage, "Feature under development (US4)", game.getSkin());
            }
        });

        // 3. 多人游戏 (Multiplayer) - 占位
        TextButton btnMultiplayer = new TextButton("Multiplayer", game.getSkin());
        btnMultiplayer.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                Toast.show(stage, "Feature under development (US4)", game.getSkin());
            }
        });

        // 4. 设置 (Settings)
        TextButton btnSettings = new TextButton("Settings", game.getSkin());
        btnSettings.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new SettingsScreen(game));
            }
        });

        // 5. 退出 (Exit)
        TextButton btnExit = new TextButton("Exit", game.getSkin());
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
    public void render(float delta) {
        Gdx.gl.glClearColor(0.1f, 0.1f, 0.15f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(delta);
        stage.draw();
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
