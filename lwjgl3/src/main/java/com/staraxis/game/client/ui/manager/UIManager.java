package com.staraxis.game.client.ui.manager;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Disposable;
import com.staraxis.game.client.ui.view.GameViewport;
import com.staraxis.game.core.api.EventBus;

/**
 * UI 管理器 (UI Manager) 负责顶层渲染调度、页面路由以及 UI Model 的生命周期管理。
 */
public class UIManager implements Disposable {

    private final EventBus eventBus;
    private final SpriteBatch batch;
    private final GameViewport gameViewport;
    private Stage currentStage;

    public UIManager(EventBus eventBus, SpriteBatch batch) {
        this.eventBus = eventBus;
        this.batch = batch;
        this.gameViewport = new GameViewport();
    }

    /**
     * 设置当前的 UI 舞台。
     */
    public void setCurrentStage(Stage stage) {
        this.currentStage = stage;
    }

    /**
     * 清除当前舞台（通常在 Screen 隐藏时调用）。
     */
    public void clearCurrentStage() {
        this.currentStage = null;
    }

    /**
     * 渲染 UI 层级。
     *
     * @param delta 时间增量
     * @param clearScreen 是否执行清屏操作
     */
    public void render(float delta, boolean clearScreen) {
        if (clearScreen) {
            // 清屏避免多帧残影叠加
            Gdx.gl.glClearColor(0, 0, 0, 1f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        }

        // 1. 渲染游戏世界 (Game World)
        // 注意：如果 clearScreen 为 false，通常意味着 Screen 已经处理了世界渲染
        if (currentStage != null && clearScreen) {
            gameViewport.render(batch, currentStage.getCamera(), delta);
        }

        // 2. 渲染 UI 覆盖层 (UI Overlay)
        if (currentStage != null) {
            currentStage.act(delta);
            currentStage.draw();
        }
    }

    /**
     * 兼容旧接口，默认清屏。
     */
    public void render(float delta) {
        render(delta, true);
    }

    @Override
    public void dispose() {
        // 释放 UI 资源
    }

    public EventBus getEventBus() {
        return eventBus;
    }
}
