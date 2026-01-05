package com.staraxis.game.core.i18n;

/**
 * 语言变更监听器接口 (Language Change Listener Interface) 当游戏语言发生切换时，实现此接口的组件将收到通知。
 */
public interface LanguageChangeListener {

    /**
     * 当语言发生变更时触发。 UI 组件应在此方法中重新获取翻译文本并更新显示。
     */
    void onLanguageChanged();
}
