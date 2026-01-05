package com.staraxis.game.client.config;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Preferences;

/**
 * 配置管理器 (Settings Manager)
 *
 * 负责游戏设置（分辨率、全屏、帧率等）的持久化存储与应用
 */
public class SettingsManager {

    private static final String PREFS_NAME = "staraxis_settings";
    private final Preferences prefs;

    public SettingsManager() {
        this.prefs = Gdx.app.getPreferences(PREFS_NAME);
    }

    /**
     * 获取设置的分辨率宽度
     */
    public int getWidth() {
        return prefs.getInteger("width", 1280);
    }

    /**
     * 获取设置的分辨率高度
     */
    public int getHeight() {
        return prefs.getInteger("height", 720);
    }

    /**
     * 获取是否全屏
     */
    public boolean isFullscreen() {
        return prefs.getBoolean("fullscreen", false);
    }

    /**
     * 获取目标帧率 (0 为无限制)
     */
    public int getTargetFPS() {
        return prefs.getInteger("targetFPS", 60);
    }

    /**
     * 保存并应用设置
     */
    public void saveSettings(int width, int height, boolean fullscreen, int targetFPS) {
        prefs.putInteger("width", width);
        prefs.putInteger("height", height);
        prefs.putBoolean("fullscreen", fullscreen);
        prefs.putInteger("targetFPS", targetFPS);
        prefs.flush();

        applySettings();
    }

    /**
     * 将当前设置应用到图形引擎
     */
    public void applySettings() {
        int width = getWidth();
        int height = getHeight();
        boolean fullscreen = isFullscreen();
        int fps = getTargetFPS();

        if (fullscreen) {
            DisplayMode targetMode = null;
            for (DisplayMode mode : Gdx.graphics.getDisplayModes()) {
                if (mode.width == width && mode.height == height) {
                    targetMode = mode;
                    break;
                }
            }
            if (targetMode != null) {
                Gdx.graphics.setFullscreenMode(targetMode);
            }
        } else {
            Gdx.graphics.setWindowedMode(width, height);
        }

        Gdx.graphics.setForegroundFPS(fps > 0 ? fps : 0);
    }
}
