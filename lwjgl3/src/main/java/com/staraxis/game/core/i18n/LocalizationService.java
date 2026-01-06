package com.staraxis.game.core.i18n;

import java.util.Locale;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.I18NBundle;
import com.staraxis.game.core.api.EventBus;
import com.staraxis.game.core.api.LanguageChangedEvent;

/**
 * 本地化服务类 (Localization Service) 负责管理游戏的语言包加载、字体生成以及语言切换通知。
 */
public class LocalizationService {

    private static final String PREFS_NAME = "staraxis-settings";
    private static final String PREF_LANGUAGE = "language";
    private static final String DEFAULT_LOCALE = "zh_CN";

    private I18NBundle bundle;
    private BitmapFont font;
    private FreeTypeFontGenerator fontGenerator;
    private final Array<LanguageChangeListener> listeners = new Array<>();
    private final Preferences prefs;
    private EventBus eventBus;

    public LocalizationService() {
        this.prefs = Gdx.app.getPreferences(PREFS_NAME);
    }

    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * 初始化服务，从配置中读取语言并加载资源。
     */
    public void init() {
        String localeCode = prefs.getString(PREF_LANGUAGE, DEFAULT_LOCALE);
        loadBundle(localeCode);
        generateFont();
    }

    /**
     * 加载对应语言的资源包。
     */
    private void loadBundle(String localeCode) {
        FileHandle baseFileHandle = Gdx.files.internal("i18n/messages");
        Locale locale = localeCode.contains("_")
                ? new Locale(localeCode.split("_")[0], localeCode.split("_")[1])
                : new Locale(localeCode);

        try {
            bundle = I18NBundle.createBundle(baseFileHandle, locale);
        } catch (Exception e) {
            Gdx.app.error("LocalizationService", "Failed to load bundle for locale: " + localeCode, e);
            // 回退到默认语言
            bundle = I18NBundle.createBundle(baseFileHandle, new Locale("zh", "CN"));
        }
    }

    /**
     * 使用 FreeType 生成支持中文的字体。
     */
    private void generateFont() {
        if (font != null) {
            font.dispose();
        }
        if (fontGenerator != null) {
            fontGenerator.dispose();
        }

        FileHandle fontFile = Gdx.files.internal("fonts/AlibabaPuHuiTi-3-65-Medium.ttf");
        fontGenerator = new FreeTypeFontGenerator(fontFile);
        FreeTypeFontGenerator.FreeTypeFontParameter parameter = new FreeTypeFontGenerator.FreeTypeFontParameter();

        // 设置字体参数
        parameter.size = 24;
        parameter.color = Color.WHITE;
        parameter.incremental = true; // 关键：增量渲染中文字符，节省内存

        // 注意：对于增量字体，generator 不能在 render 期间 dispose
        font = fontGenerator.generateFont(parameter);
    }

    /**
     * 根据键获取翻译文本。
     */
    public String get(String key) {
        if (bundle == null) {
            return key;
        }
        try {
            return bundle.get(key);
        } catch (Exception e) {
            // 缺失翻译时显示键名
            return key;
        }
    }

    /**
     * 根据键获取翻译文本，支持默认值。
     */
    public String get(String key, String defaultValue) {
        if (bundle == null) {
            return defaultValue;
        }
        try {
            return bundle.get(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * 切换语言并通知所有监听器。
     */
    public void setLanguage(String localeCode) {
        prefs.putString(PREF_LANGUAGE, localeCode);
        prefs.flush();

        loadBundle(localeCode);
        // 如果需要不同语言使用不同字体参数，可在此重新生成字体

        notifyListeners();

        if (eventBus != null) {
            eventBus.post(new LanguageChangedEvent(localeCode));
        }
    }

    /**
     * 注册监听器。
     */
    public void addListener(LanguageChangeListener listener) {
        if (!listeners.contains(listener, true)) {
            listeners.add(listener);
        }
    }

    /**
     * 移除监听器。
     */
    public void removeListener(LanguageChangeListener listener) {
        listeners.removeValue(listener, true);
    }

    private void notifyListeners() {
        for (LanguageChangeListener listener : listeners) {
            listener.onLanguageChanged();
        }
    }

    public BitmapFont getFont() {
        return font;
    }

    public void dispose() {
        if (font != null) {
            font.dispose();
        }
        if (fontGenerator != null) {
            fontGenerator.dispose();
        }
    }
}
