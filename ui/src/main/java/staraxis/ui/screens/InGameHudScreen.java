package staraxis.ui.screens;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.Disposable;
import staraxis.game.StarAxisGameRuntime;
import staraxis.game.state.DailySettlementState;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.ui.Gui;
import staraxis.ui.effects.EffectRegistry;
import staraxis.ui.json.ComponentNode;
import staraxis.ui.json.UiFactory;
import staraxis.ui.json.UiParser;
import staraxis.ui.theme.UiTheme;
import staraxis.ui.widgets.TooltipPanel;
import staraxis.ui.widgets.VectorLabel;

public class InGameHudScreen implements Disposable {

    private static final String UI_PATH = "ui/gameui/ingame-hud/ingame_hud.json";

    private final Gui gui;
    private final Stage stage;
    private Actor root;

    /** 主题引用（来自 EffectRegistry）。 */
    private UiTheme theme;

    /** 形状渲染器引用（来自 Gui）。 */
    private ShapeRenderer sr;

    /** 跟随鼠标的恒星信息浮动面板（暗色半透明窗口 + 文字）。 */
    private TooltipPanel tooltipPanel;

    public InGameHudScreen(Gui gui) {
        this.gui = gui;
        this.stage = gui.getStage();
    }

    public void show() {
        dispose();

        // 缓存 ShapeRenderer 和 UiTheme 引用
        if (sr == null) {
            sr = gui.get(ShapeRenderer.class);
        }
        if (theme == null) {
            theme = UiTheme.from(gui.tryGet(EffectRegistry.class));
        }

        UiParser parser = gui.get(UiParser.class);
        UiFactory factory = gui.get(UiFactory.class);
        if (parser == null || factory == null) return;

        ComponentNode node = parser.parseInternal(UI_PATH);
        if (node == null) return;

        root = factory.create(node);
        if (root != null) {
            stage.addActor(root);
        }

        refreshHud();
    }

    public void refreshHud() {
        StarAxisGameRuntime rt = gui.getRuntime();
        if (rt == null) return;

        RealTimeWorldState state = rt.getRealTimeWorldStateReadonly();
        if (state == null) return;

        if (root instanceof Group rootGroup) {
            Actor timeActor = rootGroup.findActor("time_label");
            if (timeActor instanceof VectorLabel vl) {
                vl.setText(String.format("Y%d M%d D%d %02d:%02d",
                        state.year, state.month, state.day, state.hour, state.minute));
            }
        }
    }

    /**
     * 更新视图标签（左上角），显示当前视图类型与缩放层级。
     *
     * @param text 视图标签文字，例如 "星系视图" 或 "恒星系视图 · 缩放 x4.0"
     */
    public void updateViewInfo(String text) {
        if (!(root instanceof Group)) return;
        Actor actor = ((Group) root).findActor("view_label");
        if (actor instanceof VectorLabel vl) {
            vl.setText(text != null ? text : "");
        }
    }

    /**
     * 设置悬停信息文字（左下角），Galaxy View 和 System View 共用。
     *
     * @param text 悬停信息文字，空或 null 时清空
     */
    public void setHoverInfoText(String text) {
        if (!(root instanceof Group)) return;
        Actor actor = ((Group) root).findActor("star_info_label");
        if (actor instanceof VectorLabel label) {
            label.setText(text != null ? text : "");
        }
    }

    /**
     * 在屏幕指定位置显示恒星信息浮动提示。
     *
     * @param systemTooltip 多行信息文本（由 buildSystemTooltipText 构建）
     * @param screenX       OpenGL 屏幕 X（左下原点）
     * @param screenY       OpenGL 屏幕 Y（左下原点）
     */
    public void showStarTooltip(String systemTooltip, float screenX, float screenY) {
        if (systemTooltip == null || systemTooltip.isEmpty()) {
            hideStarTooltip();
            return;
        }

        // 懒创建浮动面板（需要 SR 引用）
        if (tooltipPanel == null) {
            if (sr == null) return;
            tooltipPanel = new TooltipPanel(sr,
                    staraxis.ui.FontProvider.createVectorFont(), theme);
            tooltipPanel.setTouchable(Touchable.disabled);
            if (root instanceof Group) {
                ((Group) root).addActor(tooltipPanel);
            }
        }

        tooltipPanel.setContent(systemTooltip);

        // Scene2D 使用左上原点，OpenGL 使用左下原点，需要翻转 Y
        float stageH = stage.getHeight();
        float x = screenX + 20f; // 向右偏移避免遮挡恒星
        float y = stageH - screenY + 10f; // 向上偏移

        // 边界裁剪
        float pw = tooltipPanel.getWidth();
        if (x + pw > stage.getWidth()) {
            x = screenX - pw - 20f; // 翻转到左侧
        }
        if (y < 0) {
            y = 10f;
        }
        tooltipPanel.setPosition(x, y);
        tooltipPanel.setVisible(true);
    }

    /**
     * Galaxy View 选择母星系时，鼠标悬停恒星显示该星系的信息面板。
     * 从 DailySettlementState 快照构建信息文本并跟随屏幕坐标。
     *
     * @param starEntityId 恒星实体ID
     * @param ds           DailySettlementState 快照
     * @param screenX      OpenGL 屏幕 X（左下原点）
     * @param screenY      OpenGL 屏幕 Y（左下原点）
     */
    public void showSystemTooltip(long starEntityId, DailySettlementState ds,
                                   float screenX, float screenY) {
        if (starEntityId < 0 || ds == null) {
            hideStarTooltip();
            return;
        }
        showStarTooltip(buildSystemTooltipText(starEntityId, ds), screenX, screenY);
    }

    // ===== private helpers =====

    /** 构建恒星系的完整信息文本。 */
    private static String buildSystemTooltipText(long starEntityId, DailySettlementState ds) {
        if (ds.publicEntityBaselinesBySectorKey == null) return "";

        // 遍历所有 sector 找到该恒星的 StarDetails
        EntitySnapshot.StarDetails starDetail = null;
        long systemId = 0;
        for (var entry : ds.publicEntityBaselinesBySectorKey.entrySet()) {
            for (var snap : entry.getValue()) {
                if (snap != null && snap.entityId == starEntityId
                        && snap.details instanceof EntitySnapshot.StarDetails sd) {
                    starDetail = sd;
                    systemId = snap.systemId;
                    break;
                }
            }
            if (starDetail != null) break;
        }
        if (starDetail == null) return "";

        // 统计该星系的行星数量与宜居行星数量
        int planetCount = 0;
        int habitableCount = 0;
        if (systemId > 0) {
            var baselines = ds.publicEntityBaselinesBySectorKey.get(String.valueOf(systemId));
            if (baselines != null) {
                for (var snap : baselines) {
                    if (snap != null && snap.details instanceof EntitySnapshot.PlanetDetails pd) {
                        if (snap.entityType == staraxis.game.entity.EntityType.PLANET) {
                            planetCount++;
                            if (pd.planetTypeId != null
                                    && staraxis.game.astro.PlanetBody.HABITABLE_PLANET_TYPE_IDS
                                            .contains(pd.planetTypeId)) {
                                habitableCount++;
                            }
                        }
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("恒星系 #%d\n", systemId));
        sb.append(String.format("光谱 %s  %dK  半径 %.1fsol",
                starDetail.starTypeId != null ? starDetail.starTypeId : "?",
                starDetail.temperatureK,
                starDetail.radiusGU / 40.0)); // 40 GU ≈ 1 Solar radius
        if (planetCount > 0) {
            sb.append(String.format("\n行星 x%d", planetCount));
            if (habitableCount > 0) {
                sb.append(String.format("  (宜居 x%d)", habitableCount));
            }
        }
        return sb.toString();
    }

    /** 隐藏恒星浮动面板。 */
    public void hideStarTooltip() {
        if (tooltipPanel != null) {
            tooltipPanel.setVisible(false);
        }
    }

    /**
     * Galaxy View 悬停恒星信息更新（保留旧版兼容）。
     */
    public void updateStarInfo(long hoveredStarId, RealTimeWorldState state) {
        if (hoveredStarId < 0 || state == null) {
            setHoverInfoText("");
            return;
        }

        for (EntitySnapshot snap : state.getEntitySnapshotsView()) {
            if (snap != null && snap.entityId == hoveredStarId && snap.posWorldGU != null) {
                var pos = snap.posWorldGU;
                setHoverInfoText(String.format("(%.0f, %.0f, %.0f)", pos.x(), pos.y(), pos.z()));
                return;
            }
        }
        setHoverInfoText("");
    }

    @Override
    public void dispose() {
        if (root != null) {
            root.remove();
            root = null;
        }
    }
}
