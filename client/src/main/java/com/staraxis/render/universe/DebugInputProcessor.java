package com.staraxis.render.universe;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;

/**
 * 监听 F3 键切换坐标轴显示。
 */
public final class DebugInputProcessor extends InputAdapter {

    private final CoordinateAxisOverlay overlay;

    public DebugInputProcessor(CoordinateAxisOverlay overlay) {
        this.overlay = overlay;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.F3) {
            overlay.toggle();
            return true;
        }
        return false;
    }
}
