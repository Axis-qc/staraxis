package com.staraxis.game.client.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.staraxis.game.client.ui.components.AnimatedButton;
import com.staraxis.game.core.i18n.LanguageChangeListener;
import com.staraxis.game.core.i18n.LocalizationService;
import com.staraxis.game.client.config.SettingsManager;
import io.staraxis.Main;

import java.util.Comparator;
import java.util.TreeSet;

/**
 * 设置页面 (Settings Screen)
 *
 * 允许玩家调整分辨率、全屏模式和帧率限制
 */
public class SettingsScreen extends ScreenAdapter implements LanguageChangeListener {

    private final Main game;
    private final Stage stage;
    private final SettingsManager settings;
    private final LocalizationService i18n;

    private Label lblRes;
    private Label lblFps;
    private Label lblLang;
    private CheckBox cbFullscreen;
    private AnimatedButton btnApply;
    private AnimatedButton btnBack;
    private SelectBox<String> selLang;

    public SettingsScreen(Main game) {
        this.game = game;
        this.settings = game.getSettingsManager();
        this.i18n = game.getLocalizationService();
        this.stage = new Stage(new ScreenViewport());
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        i18n.addListener(this);

        // 注册到 UIManager
        game.getUiManager().setCurrentStage(stage);

        Table table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        // 1. 分辨率选择 (Resolution SelectBox)
        lblRes = new Label(i18n.get("settings_resolution", "Resolution") + ":", game.getSkin());
        final SelectBox<String> selRes = new SelectBox<>(game.getSkin());

        // 动态获取分辨率并去重排序
        TreeSet<String> resSet = new TreeSet<>(new Comparator<String>() {
            @Override
            public int compare(String o1, String o2) {
                String[] p1 = o1.split("x");
                String[] p2 = o2.split("x");
                int w1 = Integer.parseInt(p1[0]);
                int w2 = Integer.parseInt(p2[0]);
                if (w1 != w2) {
                    return Integer.compare(w1, w2);
                }
                return Integer.compare(Integer.parseInt(p1[1]), Integer.parseInt(p2[1]));
            }
        });

        for (DisplayMode mode : Gdx.graphics.getDisplayModes()) {
            resSet.add(mode.width + "x" + mode.height);
        }

        Array<String> resArray = new Array<>();
        for (String s : resSet) {
            resArray.add(s);
        }
        selRes.setItems(resArray);
        selRes.setSelected(settings.getWidth() + "x" + settings.getHeight());

        // 2. 全屏开关 (Fullscreen CheckBox)
        cbFullscreen = new CheckBox(" " + i18n.get("settings_fullscreen"), game.getSkin());
        cbFullscreen.setChecked(settings.isFullscreen());

        // 3. 帧率限制 (FPS SelectBox)
        lblFps = new Label(i18n.get("settings_fps") + ":", game.getSkin());
        final SelectBox<Integer> selFps = new SelectBox<>(game.getSkin());
        selFps.setItems(30, 60, 144, 0); // 0 为无限制
        selFps.setSelected(settings.getTargetFPS());

        // 4. 语言选择 (Language SelectBox)
        lblLang = new Label(i18n.get("settings_language") + ":", game.getSkin());
        selLang = new SelectBox<>(game.getSkin());

        // 映射显示名称与 Locale 代码
        final Array<String> languages = new Array<>();
        languages.add("简体中文");
        languages.add("English");
        selLang.setItems(languages);

        // 设置当前选中项
        String currentLang = Gdx.app.getPreferences("staraxis-settings").getString("language", "zh_CN");
        selLang.setSelected(currentLang.equals("zh_CN") ? "简体中文" : "English");

        // 5. 按钮控制
        btnApply = new AnimatedButton(i18n.get("settings_apply"), game.getSkin());
        btnApply.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                String[] resParts = selRes.getSelected().split("x");
                int w = Integer.parseInt(resParts[0]);
                int h = Integer.parseInt(resParts[1]);
                boolean fs = cbFullscreen.isChecked();
                int fps = selFps.getSelected();

                // 保存基础设置
                settings.saveSettings(w, h, fs, fps);

                // 保存并应用语言设置
                String selectedLang = selLang.getSelected().equals("简体中文") ? "zh_CN" : "en_US";
                i18n.setLanguage(selectedLang);
            }
        });

        btnBack = new AnimatedButton(i18n.get("settings_back"), game.getSkin());
        btnBack.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                game.setScreen(new MainMenuScreen(game));
            }
        });

        // 布局
        table.add(lblRes).pad(10);
        table.add(selRes).width(150).row();

        table.add(cbFullscreen).colspan(2).pad(10).row();

        table.add(lblFps).pad(10);
        table.add(selFps).width(150).row();

        table.add(lblLang).pad(10);
        table.add(selLang).width(150).row();

        table.add(btnApply).width(100).height(40).pad(20);
        table.add(btnBack).width(100).height(40).pad(20);
    }

    @Override
    public void onLanguageChanged() {
        lblRes.setText(i18n.get("settings_resolution", "Resolution") + ":");
        lblFps.setText(i18n.get("settings_fps", "FPS Limit") + ":");
        lblLang.setText(i18n.get("settings_language", "Language") + ":");
        cbFullscreen.setText(" " + i18n.get("settings_fullscreen", "Fullscreen"));
        btnApply.setText(i18n.get("settings_apply", "Apply"));
        btnBack.setText(i18n.get("settings_back", "Back"));
    }

    @Override
    public void render(float delta) {
        // 渲染逻辑已委派给 UIManager
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
        i18n.removeListener(this);
    }

    @Override
    public void dispose() {
        stage.dispose();
    }
}
