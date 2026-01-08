package com.staraxis.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.staraxis.render.universe.UniverseRenderer;
import com.staraxis.universegen.GalaxyGeneratorFacade;
import com.staraxis.universegen.config.UniverseGenConfig;
import com.staraxis.universegen.model.Galaxy;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * 简易加载界面：后台生成 Galaxy，显示进度条 (0/1)。
 */
public class GalaxyLoadScreen extends ScreenAdapter {

    private final ShapeRenderer shapeRenderer = new ShapeRenderer();
    private final UniverseRenderer renderer;

    private float progress = 0f;
    private CompletableFuture<Galaxy> future;

    public GalaxyLoadScreen(UniverseRenderer renderer, UniverseGenConfig cfg) {
        this.renderer = renderer;
        future = CompletableFuture.supplyAsync(() -> {
            try {
                GalaxyGeneratorFacade facade = new GalaxyGeneratorFacade();
                return facade.generate(cfg); // 内存生成，不保存磁盘
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((galaxy, ex) -> {
            if (ex == null) {
                renderer.setGalaxy(galaxy);
                progress = 1f;
            } else {
                Gdx.app.error("LoadScreen", "生成失败", ex);
            }
        });
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0, 0, 0, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.DARK_GRAY);
        shapeRenderer.rect(100, 100, 400, 30);
        shapeRenderer.setColor(Color.GREEN);
        shapeRenderer.rect(100, 100, 400 * progress, 30);
        shapeRenderer.end();
        // 进度更新
        if (!future.isDone()) {
            progress = Math.min(progress + delta * 0.1f, 0.9f); // 假进度
        }
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
    }
}
