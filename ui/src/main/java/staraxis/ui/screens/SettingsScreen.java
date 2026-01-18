package staraxis.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Disposable;
import staraxis.ui.Gui;
import staraxis.ui.json.ComponentNode;
import staraxis.ui.json.UiFactory;
import staraxis.ui.json.UiParser;
import staraxis.ui.settings.GameSettings;
import staraxis.ui.settings.SettingsRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 设置界面 Screen。
 */
public class SettingsScreen implements Disposable {

    private static final String UI_PATH = "ui/gameui/settings/settings.json";

    private final Gui gui;
    private final Stage stage;
    private Actor root;

    private GameSettings currentSettings;

    /**
     * FPS 预设列表。
     * 0 表示无限制。
     */
    private static final Integer[] FPS_PRESETS = { 0, 30, 60, 120, 144, 240 };

    /** 系统支持的分辨率列表（运行时采集并用于 repeat 填充）。 */
    private List<String> resolutionOptions = new ArrayList<>();

    /** 当前选中的 FPS（用于 repeat 高亮判断）。 */
    private String selectedFpsText;

    public SettingsScreen(Gui gui) {
        this.gui = gui;
        this.stage = gui.getStage();
    }

    public void show() {
        dispose();

        // 1) 读取设置（权威数据来源仍是 settings.json）
        SettingsRepository repository = gui.get(SettingsRepository.class);
        if (repository == null) {
            Gdx.app.error("SettingsScreen", "SettingsRepository not found.");
            return;
        }
        this.currentSettings = repository.load();
        this.selectedFpsText = String.valueOf(currentSettings.fpsLimit);

        // 2) 收集系统支持的分辨率列表
        buildResolutionOptions();

        // 3) 用 JSON 声明式构建设置界面
        UiParser parser = gui.get(UiParser.class);
        UiFactory factory = gui.get(UiFactory.class);
        ComponentNode node = parser.parseInternal(UI_PATH);
        if (node == null) {
            Gdx.app.error("SettingsScreen", "Failed to parse " + UI_PATH);
            return;
        }

        root = factory.create(node);
        stage.addActor(root);

        // 4) 将设置值绑定到界面（按钮文本、slider 值等）
        bindDataToView();

        // 5) 初始化 repeat 列表内容（初始为隐藏，但内容先渲染好）
        renderResolutionRepeat();
        renderFpsRepeat();

        // 6) 触发一次布局，避免首次点击浮窗时尺寸仍为 0
        stage.act(0f);
    }

    private void buildResolutionOptions() {
        Set<String> unique = new TreeSet<>((a, b) -> {
            String[] pa = a.split("x");
            String[] pb = b.split("x");
            int ha = Integer.parseInt(pa[1]);
            int hb = Integer.parseInt(pb[1]);
            if (ha != hb)
                return Integer.compare(ha, hb);
            int wa = Integer.parseInt(pa[0]);
            int wb = Integer.parseInt(pb[0]);
            return Integer.compare(wa, wb);
        });

        try {
            for (DisplayMode m : Gdx.graphics.getDisplayModes()) {
                if (m.width >= 800 && m.height >= 600) {
                    unique.add(m.width + "x" + m.height);
                }
            }
        } catch (Exception ignored) {
        }

        if (unique.isEmpty()) {
            Collections.addAll(unique, "1280x720", "1600x900", "1920x1080");
        }

        resolutionOptions = new ArrayList<>(unique);
    }

    private void bindDataToView() {
        if (!(root instanceof Group))
            return;
        Group g = (Group) root;

        TextButton resBtn = g.findActor("resolution_button");
        if (resBtn != null)
            resBtn.setText(currentSettings.resolution);

        TextButton fpsBtn = g.findActor("fps_limit_button");
        if (fpsBtn != null)
            updateFpsLimitButtonText(fpsBtn);

        TextButton vsyncBtn = g.findActor("vsync_button");
        if (vsyncBtn != null)
            updateVsyncButtonText(vsyncBtn);

        Slider vol = g.findActor("master_volume_slider");
        if (vol != null)
            vol.setValue(currentSettings.masterVolume);

        Actor resPopup = g.findActor("resolution_popup");
        if (resPopup != null)
            resPopup.setVisible(false);
        Actor fpsPopup = g.findActor("fps_popup");
        if (fpsPopup != null)
            fpsPopup.setVisible(false);
    }

    /* ------------------------ 浮窗：展开/收起 + 动态对齐 ------------------------ */

    /**
     * 将浮窗对齐到按钮右侧，并确保位置在屏幕范围内。
     *
     * 注意：首次显示时 popup 可能还没完成 layout，width/height 可能为 0。
     * 这里通过 stage.act(0) 强制完成一次布局，再读取尺寸。
     */
    private void alignPopupToButton(Actor popup, Actor button) {
        if (popup == null || button == null)
            return;
        if (popup.getParent() == null)
            return;

        // 强制完成一次布局，确保 popup 的尺寸可用
        stage.act(0f);

        float popupW = popup.getWidth();
        float popupH = popup.getHeight();

        // 兜底：若仍为 0，给一个合理的默认值，避免跑屏幕外
        if (popupW <= 0)
            popupW = 240f;
        if (popupH <= 0)
            popupH = 240f;

        Vector2 buttonStage = new Vector2(0, 0);
        button.localToStageCoordinates(buttonStage);

        Vector2 parentStage = new Vector2(0, 0);
        popup.getParent().localToStageCoordinates(parentStage);

        float targetXStage = buttonStage.x + button.getWidth();
        float targetYStage = buttonStage.y + button.getHeight() - popupH;

        // 屏幕边界限制：避免超出右侧/下侧
        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();

        if (targetXStage + popupW > screenW) {
            targetXStage = screenW - popupW;
        }
        if (targetXStage < 0)
            targetXStage = 0;

        if (targetYStage < 0)
            targetYStage = 0;
        if (targetYStage + popupH > screenH) {
            targetYStage = screenH - popupH;
        }

        float x = targetXStage - parentStage.x;
        float y = targetYStage - parentStage.y;

        popup.setPosition(x, y);
        popup.toFront();

        // 再触发布局一次，让 ScrollPane/Repeat 根据新位置与尺寸刷新
        stage.act(0f);
    }

    public void toggleResolutionList() {
        if (!(root instanceof Group))
            return;
        Group g = (Group) root;

        Actor resPopup = g.findActor("resolution_popup");
        Actor fpsPopup = g.findActor("fps_popup");
        Actor resBtn = g.findActor("resolution_button");

        if (fpsPopup != null)
            fpsPopup.setVisible(false);

        if (resPopup != null) {
            boolean newVisible = !resPopup.isVisible();
            resPopup.setVisible(newVisible);
            if (newVisible) {
                alignPopupToButton(resPopup, resBtn);
            }
        }
    }

    public void toggleFpsList() {
        if (!(root instanceof Group))
            return;
        Group g = (Group) root;

        Actor resPopup = g.findActor("resolution_popup");
        Actor fpsPopup = g.findActor("fps_popup");
        Actor fpsBtn = g.findActor("fps_limit_button");

        if (resPopup != null)
            resPopup.setVisible(false);

        if (fpsPopup != null) {
            boolean newVisible = !fpsPopup.isVisible();
            fpsPopup.setVisible(newVisible);
            if (newVisible) {
                alignPopupToButton(fpsPopup, fpsBtn);
            }
        }
    }

    /* ------------------------ repeat 渲染 ------------------------ */

    private void renderResolutionRepeat() {
        if (!(root instanceof Group))
            return;
        Group g = (Group) root;

        Actor repeatActor = g.findActor("resolution_repeat");
        if (!(repeatActor instanceof Group))
            return;

        UiFactory factory = gui.get(UiFactory.class);
        if (factory == null)
            return;

        factory.renderRepeatItems((Group) repeatActor, resolutionOptions, currentSettings.resolution,
                "SELECT_RESOLUTION");
    }

    private void renderFpsRepeat() {
        if (!(root instanceof Group))
            return;
        Group g = (Group) root;

        Actor repeatActor = g.findActor("fps_repeat");
        if (!(repeatActor instanceof Group))
            return;

        UiFactory factory = gui.get(UiFactory.class);
        if (factory == null)
            return;

        List<String> fpsItems = new ArrayList<>();
        for (Integer v : FPS_PRESETS) {
            fpsItems.add(String.valueOf(v));
        }

        factory.renderRepeatItems((Group) repeatActor, fpsItems, selectedFpsText, "SELECT_FPS_LIMIT");
    }

    /* ------------------------ 选项选择（由 action 驱动） ------------------------ */

    public void selectResolution(String resolution) {
        if (resolution == null || resolution.isBlank())
            return;

        currentSettings.resolution = resolution;

        try {
            String[] p = resolution.split("x");
            int w = Integer.parseInt(p[0]);
            int h = Integer.parseInt(p[1]);
            Gdx.graphics.setWindowedMode(w, h);
        } catch (Exception ignored) {
        }

        refreshResolutionButton();
        renderResolutionRepeat();
        hidePopups();
    }

    public void selectFpsLimit(String fpsText) {
        if (fpsText == null || fpsText.isBlank())
            return;

        try {
            int v = Integer.parseInt(fpsText);
            currentSettings.fpsLimit = v;
            selectedFpsText = fpsText;
            try {
                Gdx.graphics.setForegroundFPS(v);
            } catch (Exception ignored) {
            }
            refreshFpsLimitButton();
            renderFpsRepeat();
            hidePopups();
        } catch (Exception ignored) {
        }
    }

    private void hidePopups() {
        if (!(root instanceof Group))
            return;
        Group g = (Group) root;
        Actor resPopup = g.findActor("resolution_popup");
        if (resPopup != null)
            resPopup.setVisible(false);
        Actor fpsPopup = g.findActor("fps_popup");
        if (fpsPopup != null)
            fpsPopup.setVisible(false);
    }

    /* ------------------------ 其它设置项 ------------------------ */

    public void toggleVsync() {
        currentSettings.vsync = !currentSettings.vsync;
        Gdx.graphics.setVSync(currentSettings.vsync);
        refreshVsyncButton();
    }

    public void setMasterVolume(float v) {
        currentSettings.masterVolume = v;
        refreshMasterVolumeSlider();
    }

    public void saveSettings() {
        SettingsRepository r = gui.get(SettingsRepository.class);
        if (r != null)
            r.save(currentSettings);
    }

    private void refreshResolutionButton() {
        if (root instanceof Group) {
            TextButton b = ((Group) root).findActor("resolution_button");
            if (b != null)
                b.setText(currentSettings.resolution);
        }
    }

    private void refreshMasterVolumeSlider() {
        if (root instanceof Group) {
            Slider s = ((Group) root).findActor("master_volume_slider");
            if (s != null)
                s.setValue(currentSettings.masterVolume);
        }
    }

    private void refreshVsyncButton() {
        if (root instanceof Group) {
            TextButton b = ((Group) root).findActor("vsync_button");
            if (b != null)
                updateVsyncButtonText(b);
        }
    }

    private void refreshFpsLimitButton() {
        if (root instanceof Group) {
            TextButton b = ((Group) root).findActor("fps_limit_button");
            if (b != null)
                updateFpsLimitButtonText(b);
        }
    }

    private void updateVsyncButtonText(TextButton button) {
        button.setText(gui.i18n(currentSettings.vsync ? "common.on" : "common.off"));
    }

    private void updateFpsLimitButtonText(TextButton button) {
        button.setText(currentSettings.fpsLimit == 0 ? gui.i18n("settings.unlimited")
                : String.valueOf(currentSettings.fpsLimit));
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.remove();
            root = null;
        }
    }
}
