package staraxis;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.astro.StarSystem;
import staraxis.game.space.SpacePosition;
import staraxis.game.ship.ShipBody;
import staraxis.render.WorldCamera;
import staraxis.render.system.SystemViewRenderer;

/**
 * ShipMoveController（舰船移动交互控制器）。
 *
 * 从 ClientGame 抽取，负责：
 * - 舰船选中/取消（单击）
 * - 双击聚焦舰船并跟踪镜头
 * - 右键 Homeworld 式 3D 移动（拖拽设目标 + 调整 Y 轴）
 * - 移动目标可视化（大圆环 + 路径线 + 垂直指示线 + 目标点小圆环）
 * - 已发出的移动路径可视化
 * - 镜头跟踪实体
 */
public class ShipMoveController {

    private long selectedShipId = -1;
    private long cameraFollowTargetId = -1;

    private boolean moveModeActive;
    private double moveTargetX, moveTargetY, moveTargetZ;
    private double moveBaseY;
    private int moveDragStartScreenY;
    private static final double Y_SENSITIVITY = 50.0;

    private long lastClickTimeNs;
    private int lastClickX = -1, lastClickY = -1;
    private static final long DOUBLE_CLICK_INTERVAL_NS = 400_000_000L;
    private static final int DOUBLE_CLICK_PX_THRESHOLD = 15;
    private final Vector3 focusTmp = new Vector3();

    public long getSelectedShipId() {
        return selectedShipId;
    }

    public long getCameraFollowTargetId() {
        return cameraFollowTargetId;
    }

    public boolean isMoveModeActive() {
        return moveModeActive;
    }

    public double getMoveTargetY() {
        return moveTargetY;
    }

    /**
     * 取消镜头跟踪（WASDQE 手动控制镜头时调用）。
     */
    public void cancelCameraFollow() {
        cameraFollowTargetId = -1;
    }

    /**
     * 每帧更新移动模式（右键拖动目标点）。
     */
    public void updateMoveMode(StarAxisGameRuntime runtime, WorldCamera systemCamera) {
        if (selectedShipId < 0 || runtime == null)
            return;

        var ws = runtime.getWorldStateForSimOnly();
        if (ws == null)
            return;

        var entity = ws.entitiesById.get(selectedShipId);
        if (!(entity instanceof ShipBody ship))
            return;

        boolean rightDown = Gdx.input.isButtonPressed(com.badlogic.gdx.Input.Buttons.RIGHT);
        boolean rightJustPressed = Gdx.input.isButtonJustPressed(com.badlogic.gdx.Input.Buttons.RIGHT);

        if (rightJustPressed) {
            moveBaseY = ship.posWorldGU != null ? ship.posWorldGU.y() : 0;
            moveTargetY = moveBaseY;
            moveDragStartScreenY = Gdx.input.getY();
            moveModeActive = true;
        }

        if (moveModeActive) {
            var ray = systemCamera.camera.getPickRay(Gdx.input.getX(), Gdx.input.getY());
            double planeY = moveTargetY;
            double t = (planeY - ray.origin.y) / ray.direction.y;
            moveTargetX = ray.origin.x + ray.direction.x * t;
            moveTargetZ = ray.origin.z + ray.direction.z * t;

            if (rightDown) {
                int dy = Gdx.input.getY() - moveDragStartScreenY;
                moveTargetY = moveBaseY - (double) dy * Y_SENSITIVITY;
            }
        }
    }

    /**
     * 左键按下处理：双击聚焦 / 移动模式确认 / 单击选中。
     */
    public void handleLeftClick(long hoveredId, StarSystem currentSystem,
                                 StarAxisGameRuntime runtime, SystemViewRenderer systemViewRenderer,
                                 WorldCamera systemCamera) {
        long now = System.nanoTime();
        int screenX = Gdx.input.getX();
        int screenY = Gdx.input.getY();

        boolean isDoubleClick = (now - lastClickTimeNs) < DOUBLE_CLICK_INTERVAL_NS
                && Math.abs(screenX - lastClickX) < DOUBLE_CLICK_PX_THRESHOLD
                && Math.abs(screenY - lastClickY) < DOUBLE_CLICK_PX_THRESHOLD;

        lastClickTimeNs = now;
        lastClickX = screenX;
        lastClickY = screenY;

        if (isDoubleClick) {
            if (hoveredId >= 0 && currentSystem != null) {
                cameraFollowTargetId = hoveredId;
                selectedShipId = hoveredId;
                if (systemViewRenderer.getBodyPosition(hoveredId, currentSystem, focusTmp)) {
                    systemCamera.target.set(focusTmp);
                } else {
                    var ws = runtime.getWorldStateForSimOnly();
                    if (ws != null) {
                        var entity = ws.entitiesById.get(hoveredId);
                        if (entity != null && entity.posWorldGU != null) {
                            systemCamera.target.set(
                                (float) entity.posWorldGU.x(),
                                (float) entity.posWorldGU.y(),
                                (float) entity.posWorldGU.z());
                        }
                    }
                }
            }
        } else if (moveModeActive) {
            var ws = runtime.getWorldStateForSimOnly();
            if (ws != null) {
                var entity = ws.entitiesById.get(selectedShipId);
                if (entity instanceof ShipBody ship) {
                    ship.movementTarget = new SpacePosition(moveTargetX, moveTargetY, moveTargetZ);
                    ship.isMoving = true;
                    ship.velWorldGU = SpacePosition.ORIGIN;
                    ws.markRealtimeDirty();
                }
            }
            moveModeActive = false;
        } else {
            var ws = runtime.getWorldStateForSimOnly();
            if (ws != null) {
                var entity = ws.entitiesById.get(hoveredId);
                if (entity instanceof ShipBody) {
                    selectedShipId = hoveredId;
                } else {
                    selectedShipId = -1;
                }
            }
        }
    }

    /**
     * 渲染移动模式下的目标点标记（大圆环 + 路径线 + 垂直指示线 + 目标点小圆环）。
     */
    public void renderMovePreview(ShapeRenderer shapeRenderer, StarAxisGameRuntime runtime,
                                   WorldCamera systemCamera) {
        if (!moveModeActive || shapeRenderer == null)
            return;

        double shipX = 0, shipY = 0, shipZ = 0;
        var w = runtime.getWorldStateForSimOnly();
        if (w != null) {
            var e = w.entitiesById.get(selectedShipId);
            if (e != null && e.posWorldGU != null) {
                shipX = e.posWorldGU.x();
                shipY = e.posWorldGU.y();
                shipZ = e.posWorldGU.z();
            }
        }

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(systemCamera.camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        float sx = (float) shipX, sy = (float) shipY, sz = (float) shipZ;
        float mx = (float) moveTargetX, my = (float) moveTargetY, mz = (float) moveTargetZ;
        float baseY = (float) moveBaseY;

        float dx = mx - sx, dz = mz - sz;
        float circleRadius = (float) Math.sqrt(dx * dx + dz * dz);
        if (circleRadius > 10) {
            shapeRenderer.setColor(0.4f, 0.8f, 1.0f, 0.25f);
            drawCircle(shapeRenderer, sx, baseY, sz, circleRadius, 48);
        }

        shapeRenderer.setColor(0.4f, 0.8f, 1.0f, 0.4f);
        shapeRenderer.line(sx, sy, sz, mx, my, mz);

        shapeRenderer.setColor(0.4f, 0.8f, 1.0f, 0.5f);
        shapeRenderer.line(mx, baseY, mz, mx, my, mz);

        shapeRenderer.setColor(0.4f, 0.8f, 1.0f, 0.7f);
        drawCircle(shapeRenderer, mx, my, mz, 30f, 24);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * 渲染已发出的移动路径（路径线 + 目标点小圈）。
     */
    public void renderMovePath(ShapeRenderer shapeRenderer, StarAxisGameRuntime runtime,
                                WorldCamera systemCamera) {
        if (moveModeActive || selectedShipId < 0 || runtime == null || shapeRenderer == null)
            return;

        var w = runtime.getWorldStateForSimOnly();
        if (w == null)
            return;

        var e = w.entitiesById.get(selectedShipId);
        if (!(e instanceof ShipBody ship) || !ship.isMoving || ship.movementTarget == null)
            return;

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(systemCamera.camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        float sx = (float) e.posWorldGU.x(), sy = (float) e.posWorldGU.y(), sz = (float) e.posWorldGU.z();
        float tx = (float) ship.movementTarget.x(), ty = (float) ship.movementTarget.y(), tz = (float) ship.movementTarget.z();

        shapeRenderer.setColor(0.4f, 0.8f, 1.0f, 0.3f);
        shapeRenderer.line(sx, sy, sz, tx, ty, tz);

        shapeRenderer.setColor(0.4f, 0.8f, 1.0f, 0.5f);
        drawCircle(shapeRenderer, tx, ty, tz, 20f, 20);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    /**
     * 镜头跟踪实体（所有输入处理完后调用）。
     */
    public void updateCameraFollow(SystemViewRenderer systemViewRenderer, StarSystem currentSystem,
                                    StarAxisGameRuntime runtime, WorldCamera systemCamera) {
        if (cameraFollowTargetId < 0)
            return;

        if (systemViewRenderer.getBodyPosition(cameraFollowTargetId, currentSystem, focusTmp)) {
            systemCamera.target.set(focusTmp);
        } else {
            var ws = runtime.getWorldStateForSimOnly();
            if (ws != null) {
                var entity = ws.entitiesById.get(cameraFollowTargetId);
                if (entity != null && entity.posWorldGU != null) {
                    systemCamera.target.set(
                        (float) entity.posWorldGU.x(),
                        (float) entity.posWorldGU.y(),
                        (float) entity.posWorldGU.z());
                }
            }
        }
    }

    private static void drawCircle(ShapeRenderer sr, float cx, float cy, float cz, float radius, int segments) {
        for (int i = 0; i < segments; i++) {
            float a1 = (float) (i * 2.0 * Math.PI / segments);
            float a2 = (float) ((i + 1) * 2.0 * Math.PI / segments);
            sr.line(
                cx + radius * (float) Math.cos(a1), cy, cz + radius * (float) Math.sin(a1),
                cx + radius * (float) Math.cos(a2), cy, cz + radius * (float) Math.sin(a2));
        }
    }
}