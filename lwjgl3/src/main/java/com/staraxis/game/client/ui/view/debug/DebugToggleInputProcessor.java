package com.staraxis.game.client.ui.view.debug;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

/**
 * F3 调试开关输入：切换 DebugOverlay / WorldGrid / 坐标轴显示。
 */
public final class DebugToggleInputProcessor extends InputAdapter {

    private final DebugSystem debugSystem;

    public DebugToggleInputProcessor(DebugSystem debugSystem) {
        this.debugSystem = debugSystem;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.F3) {
            debugSystem.toggle();
            return true;
        }
        return false;
    }
}
