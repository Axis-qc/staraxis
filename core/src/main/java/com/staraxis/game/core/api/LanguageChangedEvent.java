package com.staraxis.game.core.api;

/**
 * 语言变更事件 (Language Changed Event)
 */
public class LanguageChangedEvent {

    public final String localeCode;

    public LanguageChangedEvent(String localeCode) {
        this.localeCode = localeCode;
    }
}
