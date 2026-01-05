package com.staraxis.game.client.ui.model;

import com.badlogic.gdx.utils.Array;

/**
 * 基础 UI 模型类 (Base UI Model) 提供数据绑定和脏检查的基础逻辑。
 */
public abstract class BaseUIModel {

    private boolean dirty = true;
    private final Array<UIModelListener> listeners = new Array<>();

    public interface UIModelListener {

        void onChanged();
    }

    public void addListener(UIModelListener listener) {
        listeners.add(listener);
    }

    public void removeListener(UIModelListener listener) {
        listeners.removeValue(listener, true);
    }

    protected void setDirty(boolean dirty) {
        this.dirty = dirty;
        if (dirty) {
            notifyListeners();
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    protected void notifyListeners() {
        for (UIModelListener listener : listeners) {
            listener.onChanged();
        }
    }

    /**
     * 重置脏标记（通常在渲染后调用）
     */
    public void clearDirty() {
        this.dirty = false;
    }
}
