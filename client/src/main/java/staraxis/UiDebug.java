package staraxis;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

import staraxis.render.WorldCamera;
import staraxis.ui.FontProvider;
import staraxis.ui.json.UiFactory;
import staraxis.ui.json.UiParser;
import staraxis.ui.widgets.VectorButton;
import staraxis.ui.widgets.VectorLabel;

/**
 * UiDebug — 调试工具，面板布局由 JSON 声明（UiParser + UiFactory）。
 *
 * F3 打开/关闭调试面板（assets/ui/gameui/debug-panel/debug_panel.json）。
 * 面板提供功能开关 + 镜头实时数据 + Actor 边框叠加层。
 * 代码层仅负责交互绑定与数据刷新。
 */
public class UiDebug {

    private final Stage stage;
    private final ShapeRenderer shapes;
    private final BitmapFont font;
    private final SpriteBatch overlayBatch;
    private final UiParser parser;
    private final UiFactory factory;

    private WorldCamera camera;

    // ---- 面板 ----
    private Actor panelRoot;
    private boolean panelOpen;

    // 开关状态
    private boolean showOrigin = true;
    private boolean showBounds = true;
    private boolean showMouse = true;
    private boolean showCameraOverlay = true;

    // 按钮/标签引用（从 JSON Actor 树查找）
    private VectorButton btnOrigin, btnBounds, btnMouse, btnCamera;
    private VectorLabel labelTarget, labelOrbit, labelZoom, labelYawPitch, labelFps;

    // ---- 叠加层 ----
    private Actor hoveredActor;
    private float mouseStageX, mouseStageY;
    private final Rectangle hoveredStageBounds = new Rectangle();
    private final Vector2 tmpVec = new Vector2();

    private boolean dragging;
    private float dragStartX, dragStartY;

    private static final float CROSS_SIZE = 10f;
    private static final float FONT_SCALE = 16f / FontProvider.VECTOR_FONT_GEN_SIZE;
    private static final Color C_ORIGIN = Color.RED, C_CORNER = Color.GREEN;
    private static final Color C_CENTER = Color.CYAN, C_HOVER = Color.YELLOW;

    private static final String PANEL_JSON = "ui/gameui/debug-panel/debug_panel.json";

    public UiDebug(Stage stage, ShapeRenderer shapes, BitmapFont font, UiParser parser, UiFactory factory) {
        this.stage = stage;
        this.shapes = shapes;
        this.font = font;
        this.parser = parser;
        this.factory = factory;
        this.overlayBatch = new SpriteBatch();
    }

    public void setCamera(WorldCamera camera) {
        this.camera = camera;
    }

    // ==================== 每帧调用 ====================

    public void update() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.F3)) {
            togglePanel();
        }

        if (panelOpen) {
            ensurePanelOnStage();
            refreshData();
        }

        // Actor 边框命中测试
        if (panelOpen && showBounds) {
            int sx = Gdx.input.getX();
            int sy = Gdx.input.getY();
            int sh = Gdx.graphics.getHeight();
            mouseStageX = sx;
            mouseStageY = sh - sy;
            Actor hit = stage.hit(mouseStageX, mouseStageY, true);
            if (hit != null && isInsidePanel(hit)) {
                hoveredActor = null;
            } else {
                hoveredActor = hit;
                if (hoveredActor != null) {
                    computeStageBounds(hoveredActor, hoveredStageBounds);
                }
            }
        } else {
            hoveredActor = null;
        }
    }

    public void render() {
        if (!panelOpen)
            return;
        if (!anyOverlayEnabled())
            return;

        int sw = Gdx.graphics.getWidth();
        int sh = Gdx.graphics.getHeight();

        Gdx.gl.glEnable(GL20.GL_BLEND);

        // ---- 形状层（ShapeRenderer） ----
        shapes.setProjectionMatrix(stage.getCamera().combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        // begin() 不重置 transformMatrix，覆盖 DebugPanel 遗留的面板位移矩阵
        shapes.setTransformMatrix(new com.badlogic.gdx.math.Matrix4());

        if (showOrigin) {
            drawOriginMarker();
            drawCornerMarker(sw, sh);
            drawCenterMarker(sw, sh);
        }
        if (showBounds) {
            drawHoveredBounds();
        }

        shapes.end();

        // ---- 文字层（SpriteBatch） ----
        if (showOrigin || showMouse || showBounds || showCameraOverlay) {
            overlayBatch.setProjectionMatrix(stage.getCamera().combined);
            overlayBatch.begin();

            float oldScale = beginFont();

            if (showOrigin)
                drawFixedLabels(sw, sh);
            if (showMouse)
                drawMouseInfo(sh);
            if (showBounds)
                drawActorInfo(sh);
            if (showCameraOverlay)
                drawCameraOverlay(sh);

            endFont(oldScale);

            overlayBatch.end();
        }

        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private boolean anyOverlayEnabled() {
        return showOrigin || showBounds || showMouse || showCameraOverlay;
    }

    // ==================== 面板管理 ====================

    private void togglePanel() {
        panelOpen = !panelOpen;
        if (panelOpen) {
            if (panelRoot == null)
                loadPanelJson();
            stage.addActor(panelRoot);
        } else {
            if (panelRoot != null)
                panelRoot.remove();
            hoveredActor = null;
        }
    }

    private void ensurePanelOnStage() {
        if (panelRoot != null && panelRoot.getStage() == null)
            stage.addActor(panelRoot);
    }

    private boolean isInsidePanel(Actor a) {
        while (a != null) { if (a == panelRoot) return true; a = a.getParent(); }
        return false;
    }

    /** 从 JSON 加载面板，查找按钮/标签引用，绑定交互 */
    private void loadPanelJson() {
        var node = parser.parseInternal(PANEL_JSON);
        if (node == null) return;
        panelRoot = factory.create(node);
        if (panelRoot == null) return;

        btnOrigin = findBtn("btn_origin"); btnBounds = findBtn("btn_bounds");
        btnMouse  = findBtn("btn_mouse");  btnCamera = findBtn("btn_camera");
        labelTarget   = findLbl("label_target");   labelOrbit    = findLbl("label_orbit");
        labelZoom     = findLbl("label_zoom");     labelYawPitch = findLbl("label_yawpitch");
        labelFps      = findLbl("label_fps");

        bindToggle(btnOrigin, "显示原点",  v -> showOrigin        = v);
        bindToggle(btnBounds, "Actor 边框", v -> showBounds        = v);
        bindToggle(btnMouse,  "鼠标信息",  v -> showMouse         = v);
        bindToggle(btnCamera, "镜头数据",  v -> showCameraOverlay  = v);

        panelRoot.addListener(new InputListener() {
            public boolean touchDown(InputEvent e, float x, float y, int p, int b) {
                Vector2 sp = panelRoot.localToStageCoordinates(tmpVec.set(x, y));
                dragStartX = sp.x - panelRoot.getX(); dragStartY = sp.y - panelRoot.getY();
                dragging = true; return true;
            }
            public void touchDragged(InputEvent e, float x, float y, int p) {
                if (!dragging) return;
                Vector2 sp = panelRoot.localToStageCoordinates(tmpVec.set(x, y));
                panelRoot.setPosition(sp.x - dragStartX, sp.y - dragStartY);
                clampPanelToStage();
            }
            public void touchUp(InputEvent e, float x, float y, int p, int b) { dragging = false; }
        });
        clampPanelToStage();
    }

    private VectorButton findBtn(String name) { return findActor(panelRoot, name, VectorButton.class); }
    private VectorLabel findLbl(String name) { return findActor(panelRoot, name, VectorLabel.class); }

    @SuppressWarnings("unchecked")
    private static <T> T findActor(Actor root, String name, Class<T> type) {
        if (name.equals(root.getName())) return type.isInstance(root) ? (T) root : null;
        if (root instanceof Group)
            for (Actor c : ((Group) root).getChildren()) { T r = findActor(c, name, type); if (r != null) return r; }
        return null;
    }

    private void bindToggle(VectorButton btn, String label, java.util.function.Consumer<Boolean> onToggle) {
        if (btn == null) return;
        btn.addListener(new ClickListener() {
            public void clicked(InputEvent e, float x, float y) {
                boolean[] s = { getToggleState(label) };
                s[0] = !s[0]; onToggle.accept(s[0]);
                btn.setText((s[0] ? "[X] " : "[  ] ") + label);
            }
        });
    }

    private boolean getToggleState(String label) {
        switch (label) {
            case "显示原点": return showOrigin;
            case "Actor 边框": return showBounds;
            case "鼠标信息": return showMouse;
            default: return showCameraOverlay;
        }
    }

    private void clampPanelToStage() {
        float sw = Gdx.graphics.getWidth(), sh = Gdx.graphics.getHeight();
        float x = com.badlogic.gdx.math.MathUtils.clamp(panelRoot.getX(), 0, sw - panelRoot.getWidth());
        float y = com.badlogic.gdx.math.MathUtils.clamp(panelRoot.getY(), 0, sh - panelRoot.getHeight());
        panelRoot.setPosition(x, y);
    }

    private void refreshData() {
        if (labelFps != null)
            labelFps.setText("FPS: " + Gdx.graphics.getFramesPerSecond());
        if (camera == null) { if (labelTarget != null) labelTarget.setText("(no camera)"); return; }
        if (labelTarget != null)
            labelTarget.setText(String.format("t:(%.0f,%.0f,%.0f)", camera.target.x, camera.target.y, camera.target.z));
        if (labelOrbit != null)
            labelOrbit.setText(String.format("orbit: %.0f  max: %.0f", camera.getOrbitDistance(), camera.getMaxOrbitDist()));
        if (labelZoom != null)
            labelZoom.setText(String.format("zoom: %.2f [%.1f..%.1f]", camera.zoomLevel, WorldCamera.MIN_ZOOM, WorldCamera.MAX_ZOOM));
        if (labelYawPitch != null)
            labelYawPitch.setText(String.format("yaw: %.1f  pitch: %.1f", camera.yaw, camera.pitch));
    }

    // ==================== 形状绘制 ====================

    private float beginFont() { float o = font.getData().scaleX; font.getData().setScale(FONT_SCALE); return o; }
    private void endFont(float o) { font.getData().setScale(o); }

    private void drawOriginMarker() {
        shapes.setColor(C_ORIGIN);
        shapes.line(-CROSS_SIZE, 0, CROSS_SIZE, 0);
        shapes.line(0, -CROSS_SIZE, 0, CROSS_SIZE);
        shapes.circle(0, 0, 4f);
    }

    private void drawCornerMarker(int sw, int sh) {
        shapes.setColor(C_CORNER);
        shapes.line(sw - CROSS_SIZE, sh, sw + CROSS_SIZE, sh);
        shapes.line(sw, sh - CROSS_SIZE, sw, sh + CROSS_SIZE);
    }

    private void drawCenterMarker(int sw, int sh) {
        float cx = sw / 2f;
        float cy = sh / 2f;
        shapes.setColor(C_CENTER);
        shapes.line(cx - CROSS_SIZE, cy, cx + CROSS_SIZE, cy);
        shapes.line(cx, cy - CROSS_SIZE, cx, cy + CROSS_SIZE);
    }

    private void drawHoveredBounds() {
        if (hoveredActor == null)
            return;
        shapes.setColor(C_HOVER);
        shapes.rect(
                hoveredStageBounds.x, hoveredStageBounds.y,
                hoveredStageBounds.width, hoveredStageBounds.height);
    }

    // ==================== 文字叠加层 ====================

    private void drawFixedLabels(int sw, int sh) {
        font.setColor(C_ORIGIN);
        font.draw(overlayBatch, "origin (0,0)", 14, 14);
        font.setColor(C_CORNER);
        font.draw(overlayBatch, "(" + sw + ", " + sh + ")", sw - 130, sh - 6);
        font.setColor(C_CENTER);
        int cx = sw / 2, cy = sh / 2;
        font.draw(overlayBatch, "center (" + cx + ", " + cy + ")", cx + 12, cy + 10);
    }

    private void drawMouseInfo(int sh) {
        font.setColor(Color.WHITE);
        font.draw(overlayBatch, String.format("Mouse: screen(%d, %d)  stage(%.0f, %.0f)",
                Gdx.input.getX(), Gdx.input.getY(), mouseStageX, mouseStageY), 4, sh - 6);
    }

    private void drawActorInfo(int sh) {
        if (hoveredActor == null)
            return;
        float lineY = sh - 6 - font.getLineHeight() - 2;

        font.setColor(C_HOVER);
        String name = hoveredActor.getName() != null ? hoveredActor.getName() : "(unnamed)";
        font.draw(overlayBatch, String.format("Actor: %s [%s]  stage(%.0f, %.0f)  size(%.0f x %.0f)",
                name, hoveredActor.getClass().getSimpleName(),
                hoveredStageBounds.x, hoveredStageBounds.y,
                hoveredStageBounds.width, hoveredStageBounds.height), 4, lineY);
        lineY -= font.getLineHeight() + 2;

        Actor parent = hoveredActor.getParent();
        int depth = 1;
        while (parent != null && depth < 5) {
            String pname = parent.getName() != null ? parent.getName() : "(unnamed)";
            computeStageBounds(parent, tmpVec);
            font.setColor(C_HOVER.r, C_HOVER.g, C_HOVER.b, 0.6f);
            font.draw(overlayBatch,
                    String.format("  parent[%d]: %s [%s]  stage(%.0f, %.0f)  size(%.0f, %.0f)",
                            depth, pname, parent.getClass().getSimpleName(),
                            tmpVec.x, tmpVec.y, parent.getWidth(), parent.getHeight()),
                    4, lineY);
            lineY -= font.getLineHeight() + 2;
            parent = parent.getParent();
            depth++;
        }
    }

    private void drawCameraOverlay(int sh) {
        if (camera == null) {
            font.setColor(Color.GRAY);
            font.draw(overlayBatch, "Camera: (no camera)", 4, sh - 6);
            return;
        }
        font.setColor(Color.WHITE);
        float lineY = sh - 6;
        font.draw(overlayBatch, String.format("Camera target: (%.1f, %.1f, %.1f)",
                camera.target.x, camera.target.y, camera.target.z), 4, lineY);
        lineY -= font.getLineHeight() + 2;
        font.draw(overlayBatch,
                String.format("  orbit: %.1f  zoom: %.2f  yaw: %.1f  pitch: %.1f",
                        camera.getOrbitDistance(), camera.zoomLevel, camera.yaw, camera.pitch),
                4, lineY);
    }

    // ==================== 工具 ====================

    private void computeStageBounds(Actor actor, Rectangle out) {
        // localToStageCoordinates 原地修改 Vector2 并返回自身，
        // 必须先把 x,y 拷贝出来再调用第二次，否则会被覆盖
        actor.localToStageCoordinates(tmpVec.set(0, 0));
        float x = tmpVec.x, y = tmpVec.y;
        actor.localToStageCoordinates(
                tmpVec.set(actor.getWidth(), actor.getHeight()));
        out.set(x, y, tmpVec.x - x, tmpVec.y - y);
    }

    private void computeStageBounds(Actor actor, Vector2 out) {
        actor.localToStageCoordinates(out.set(0, 0));
    }

    public boolean isPanelOpen() {
        return panelOpen;
    }

    public void dispose() {
        overlayBatch.dispose();
        if (panelRoot != null) { panelRoot.remove(); panelRoot = null; }
    }
}
