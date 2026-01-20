package staraxis.ui.screens;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.utils.Disposable;
import staraxis.ui.Gui;
import staraxis.ui.json.ComponentNode;
import staraxis.ui.json.UiFactory;
import staraxis.ui.json.UiParser;
import staraxis.ui.settings.GameSettings;
import staraxis.ui.settings.SettingsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private String activeTab = "general";

    private GameSettings currentSettings;

    private static final Integer[] FPS_PRESETS = { 0, 30, 60, 120, 144, 240 };
    private List<String> resolutionOptions = new ArrayList<>();
    private String selectedFpsText;

    private static final float SCALE_STEP = 0.1f;
    private static final float MIN_SCALE = 0.5f;
    private static final float MAX_SCALE = 2.0f;

    public SettingsScreen(Gui gui) {
        this.gui = gui;
        this.stage = gui.getStage();
    }

    public void show() {
        dispose();

        SettingsRepository repository = gui.get(SettingsRepository.class);
        if (repository == null) {
            Gdx.app.error("SettingsScreen", "SettingsRepository not found.");
            return;
        }
        this.currentSettings = repository.load();
        this.selectedFpsText = String.valueOf(currentSettings.fpsLimit);

        buildResolutionOptions();

        UiParser parser = gui.get(UiParser.class);
        UiFactory factory = gui.get(UiFactory.class);
        ComponentNode node = parser.parseInternal(UI_PATH);
        if (node == null) {
            Gdx.app.error("SettingsScreen", "Failed to parse " + UI_PATH);
            return;
        }

        root = factory.create(node);
        stage.addActor(root);

        bindDataToView();
        renderResolutionRepeat();
        renderFpsRepeat();
        openTab(activeTab);
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

        refreshUiScaleLabel();
        refreshFontScaleLabel();

        Actor resPopup = g.findActor("resolution_popup");
        if (resPopup != null)
            resPopup.setVisible(false);
        Actor fpsPopup = g.findActor("fps_popup");
        if (fpsPopup != null)
            fpsPopup.setVisible(false);
    }

    private void alignPopupToButton(Actor popup, Actor button) {
        if (popup == null || button == null || popup.getParent() == null)
            return;

        stage.act(0f);

        float popupW = popup.getWidth();
        float popupH = popup.getHeight();

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

        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();

        if (targetXStage + popupW > screenW)
            targetXStage = screenW - popupW;
        if (targetXStage < 0)
            targetXStage = 0;

        if (targetYStage < 0)
            targetYStage = 0;
        if (targetYStage + popupH > screenH)
            targetYStage = screenH - popupH;

        float x = targetXStage - parentStage.x;
        float y = targetYStage - parentStage.y;

        popup.setPosition(x, y);
        popup.toFront();
        stage.act(0f);
    }

    public void openTab(String tabId) {
        if (tabId == null || tabId.isBlank())
            tabId = "general";
        activeTab = tabId;

        if (!(root instanceof Group))
            return;
        Group g = (Group) root;

        Actor settingsScroll = g.findActor("settings_scroll");
        if (settingsScroll != null)
            settingsScroll.setVisible("general".equals(tabId));

        Actor graphicsScroll = g.findActor("graphics_scroll");
        if (graphicsScroll != null)
            graphicsScroll.setVisible("graphics".equals(tabId));

        Actor inputScroll = g.findActor("input_scroll");
        if (inputScroll != null)
            inputScroll.setVisible("input".equals(tabId));

        Actor otherScroll = g.findActor("other_scroll");
        if (otherScroll != null)
            otherScroll.setVisible("other".equals(tabId));

        Actor modExampleScroll = g.findActor("mod_example_scroll");
        if (modExampleScroll != null)
            modExampleScroll.setVisible("mod_example".equals(tabId));

        Actor resPopup = g.findActor("resolution_popup");
        if (resPopup != null)
            resPopup.setVisible(false);
        Actor fpsPopup = g.findActor("fps_popup");
        if (fpsPopup != null)
            fpsPopup.setVisible(false);

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
            if (newVisible)
                alignPopupToButton(resPopup, resBtn);
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
            if (newVisible)
                alignPopupToButton(fpsPopup, fpsBtn);
        }
    }

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
        for (Integer v : FPS_PRESETS)
            fpsItems.add(String.valueOf(v));
        factory.renderRepeatItems((Group) repeatActor, fpsItems, selectedFpsText, "SELECT_FPS_LIMIT");
    }

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

    public void toggleVsync() {
        currentSettings.vsync = !currentSettings.vsync;
        Gdx.graphics.setVSync(currentSettings.vsync);
        refreshVsyncButton();
    }

    public void setMasterVolume(float v) {
        currentSettings.masterVolume = v;
        refreshMasterVolumeSlider();
    }

    public void increaseUiScale() {
        changeUiScale(SCALE_STEP);
    }

    public void decreaseUiScale() {
        changeUiScale(-SCALE_STEP);
    }

    public void increaseFontScale() {
        changeFontScale(SCALE_STEP);
    }

    public void decreaseFontScale() {
        changeFontScale(-SCALE_STEP);
    }

    private void changeUiScale(float delta) {
        float newScale = currentSettings.uiScale + delta;
        newScale = new BigDecimal(newScale).setScale(1, RoundingMode.HALF_UP).floatValue();
        if (newScale >= MIN_SCALE && newScale <= MAX_SCALE) {
            currentSettings.uiScale = newScale;
            gui.applyUiScale(newScale);
            refreshUiScaleLabel();
        }
    }

    private void changeFontScale(float delta) {
        float newScale = currentSettings.fontScale + delta;
        newScale = new BigDecimal(newScale).setScale(1, RoundingMode.HALF_UP).floatValue();
        if (newScale >= MIN_SCALE && newScale <= MAX_SCALE) {
            currentSettings.fontScale = newScale;
            gui.applyFontScale(newScale);
            refreshFontScaleLabel();
        }
    }

    public void saveSettings() {
        SettingsRepository r = gui.get(SettingsRepository.class);
        if (r != null)
            r.save(currentSettings);
    }

    private void refreshUiScaleLabel() {
        if (root instanceof Group) {
            Label label = ((Group) root).findActor("ui_scale_label");
            if (label != null)
                label.setText(String.format("%.1fx", currentSettings.uiScale));
        }
    }

    private void refreshFontScaleLabel() {
        if (root instanceof Group) {
            Label label = ((Group) root).findActor("font_scale_label");
            if (label != null)
                label.setText(String.format("%.1fx", currentSettings.fontScale));
        }
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
