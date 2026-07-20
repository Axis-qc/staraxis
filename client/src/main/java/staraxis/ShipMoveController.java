package staraxis;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;

import staraxis.game.StarAxisGameRuntime;
import staraxis.game.command.MoveShipCommand;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.state.snapshot.EntitySnapshot.ShipDetails;
import staraxis.render.WorldCamera;
import staraxis.render.system.SystemViewRenderer;

/**
 * ShipMoveController（舰船移动交互控制器）。
 *
 * 负责：
 * - 舰船选中/取消（单击）
 * - 双击聚焦舰船并跟踪镜头
 * - 右键 Homeworld 式 3D 移动（拖拽设目标 + 调整 Y 轴）
 * - 移动目标可视化（大圆环 + 路径线 + 垂直指示线 + 目标点小圆环）
 * - 已发出的移动路径可视化
 * - 镜头跟踪实体
 *
 * 边界约束：本控制器只读访问快照数据，所有状态修改通过 runtime.submitCommand() 发送命令喵。
 */
public class ShipMoveController {

    private long selectedShipId = -1;
    private long cameraFollowTargetId = -1;

    private boolean moveModeActive;
    private boolean yAdjustMode;
    private boolean waitingRightRelease;
    private double moveTargetX, moveTargetY, moveTargetZ;
    private double moveBaseY;
    private double yAdjustBaseY;
    private int moveDragStartScreenY;
    private int lastMouseScreenX = -1, lastMouseScreenY = -1;
    private static final double Y_SENSITIVITY = 50.0;

    private long lastClickTimeNs;
    private int lastClickX = -1, lastClickY = -1;
    private static final long DOUBLE_CLICK_INTERVAL_NS = 400_000_000L;
    private static final int DOUBLE_CLICK_PX_THRESHOLD = 15;
    private final Vector3 focusTmp = new Vector3();
    private GameSnapshotProvider snapshotProvider;

    public void setSnapshotProvider(GameSnapshotProvider provider) {
        this.snapshotProvider = provider;
    }

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
     * 从实时快照中读取舰船快照。
     * 仅用于只读操作（位置查询、类型判断），不修改任何状态喵。
     */
    private EntitySnapshot readShipSnapshotOrNull(RealTimeWorldState state, long shipId) {
        if (state == null) return null;
        for (var snapList : state.getEntitySnapshotsBySystemView().values()) {
            for (EntitySnapshot snap : snapList) {
                if (snap.entityId == shipId && snap.details instanceof ShipDetails) {
                    return snap;
                }
            }
        }
        return null;
    }

    /**
     * 每帧更新移动模式（右键拖动目标点）。
     *
     * 状态机：
     * - 右键按下 -> 进入移动模式（等待右键松开后才开始 XZ 跟随，避免进入即锁定）
     * - 右键松开后 -> XZ 跟随鼠标在舰船平面移动
     * - 再次按住右键 -> 锁定当前 XZ，进入 Y 高度调整
     * - 松开右键 -> 退出 Y 高度调整，XZ 恢复跟随
     * - 左键确认（在 handleLeftClick 中处理）-> 发送命令退出移动模式
     */
    public void updateMoveMode(StarAxisGameRuntime runtime, WorldCamera systemCamera) {
        if (selectedShipId < 0 || runtime == null)
            return;

        EntitySnapshot shipSnap = readShipSnapshotOrNull(snapshotProvider.getRealtimeState(), selectedShipId);
        if (shipSnap == null || shipSnap.posWorldGU == null)
            return;

        boolean rightJustPressed = Gdx.input.isButtonJustPressed(com.badlogic.gdx.Input.Buttons.RIGHT);
        boolean rightPressed = Gdx.input.isButtonPressed(com.badlogic.gdx.Input.Buttons.RIGHT);

        // 进入移动模式：右键首次按下，等待松开后才开始 XZ 跟随，避免进入即锁定喵
        if (rightJustPressed && !moveModeActive) {
            moveModeActive = true;
            yAdjustMode = false;
            waitingRightRelease = true;
            moveBaseY = shipSnap.posWorldGU.y();
            moveTargetY = moveBaseY;
            moveDragStartScreenY = Gdx.input.getY();
            // 立即 ray cast 一次确定初始 XZ（舰船平面）
            updateMoveTargetXZ(systemCamera);
            lastMouseScreenX = Gdx.input.getX();
            lastMouseScreenY = Gdx.input.getY();
            return;
        }

        if (!moveModeActive)
            return;

        // 等待进入移动模式后的右键松开：松开后 XZ 才开始跟随鼠标喵
        if (waitingRightRelease) {
            if (!rightPressed) {
                waitingRightRelease = false;
                updateMoveTargetXZ(systemCamera);
                lastMouseScreenX = Gdx.input.getX();
                lastMouseScreenY = Gdx.input.getY();
            }
            return;
        }

        // Y 高度调整模式切换
        if (rightJustPressed && !yAdjustMode) {
            // 按住右键：锁定当前 XZ，进入 Y 高度调整，以当前 Y 为调整基准
            yAdjustMode = true;
            yAdjustBaseY = moveTargetY;
            moveDragStartScreenY = Gdx.input.getY();
        } else if (!rightPressed && yAdjustMode) {
            // 松开右键：退出 Y 高度调整，XZ 保持锁定值不立即 ray cast（避免 Y 变化后同屏幕位置在新平面交点跳变）
            // 仅同步 lastMouse 为当前鼠标位置，鼠标真正移动后才重新 ray cast 更新 XZ
            yAdjustMode = false;
            lastMouseScreenX = Gdx.input.getX();
            lastMouseScreenY = Gdx.input.getY();
        }

        if (yAdjustMode) {
            // Y 高度调整：XZ 锁定，只更新 Y
            int dy = Gdx.input.getY() - moveDragStartScreenY;
            moveTargetY = yAdjustBaseY - (double) dy * Y_SENSITIVITY;
        } else {
            // XZ 跟随鼠标：仅在鼠标屏幕位置真正变化时才 ray cast，静止时保持目标点不动喵
            int curMouseX = Gdx.input.getX();
            int curMouseY = Gdx.input.getY();
            if (curMouseX != lastMouseScreenX || curMouseY != lastMouseScreenY) {
                updateMoveTargetXZ(systemCamera);
                lastMouseScreenX = curMouseX;
                lastMouseScreenY = curMouseY;
            }
        }
    }

    /**
     * 通过鼠标射线在当前 moveTargetY 平面上计算移动目标 XZ 坐标。
     */
    private void updateMoveTargetXZ(WorldCamera systemCamera) {
        var ray = systemCamera.camera.getPickRay(Gdx.input.getX(), Gdx.input.getY());
        double planeY = moveTargetY;
        double t = (planeY - ray.origin.y) / ray.direction.y;
        moveTargetX = ray.origin.x + ray.direction.x * t;
        moveTargetZ = ray.origin.z + ray.direction.z * t;
    }

    /**
     * 左键按下处理：双击聚焦 / 移动模式确认（发送命令）/ 单击选中。
     */
    public void handleLeftClick(long hoveredId,
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

        var state = snapshotProvider.getRealtimeState();

        if (isDoubleClick) {
            if (hoveredId >= 0) {
                cameraFollowTargetId = hoveredId;
                selectedShipId = hoveredId;
                systemViewRenderer.getBodyPosition(hoveredId, focusTmp);
                systemCamera.target.set(focusTmp);
            }
        } else if (moveModeActive) {
            // 右键移动模式确认 -> 通过命令总线发送移动命令喵
            // ownerNationId 允许为 null 传入:无主舰船由 game 端 MoveShipHandler 直接执行
            EntitySnapshot shipSnap = readShipSnapshotOrNull(snapshotProvider.getRealtimeState(), selectedShipId);
            if (shipSnap != null) {
                String clientCmdId = "client_" + System.currentTimeMillis() + "_" + selectedShipId;
                runtime.submitCommand(new MoveShipCommand(
                        shipSnap.ownerNationId,
                        clientCmdId,
                        selectedShipId,
                        moveTargetX, moveTargetY, moveTargetZ));
            }
            moveModeActive = false;
            yAdjustMode = false;
            waitingRightRelease = false;
        } else {
            // 普通单击选中舰船喵
            EntitySnapshot shipSnap = readShipSnapshotOrNull(snapshotProvider.getRealtimeState(), hoveredId);
            selectedShipId = (shipSnap != null) ? hoveredId : -1;
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
        EntitySnapshot shipSnap = readShipSnapshotOrNull(snapshotProvider.getRealtimeState(), selectedShipId);
        if (shipSnap != null && shipSnap.posWorldGU != null) {
            shipX = shipSnap.posWorldGU.x();
            shipY = shipSnap.posWorldGU.y();
            shipZ = shipSnap.posWorldGU.z();
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

        EntitySnapshot shipSnap = readShipSnapshotOrNull(snapshotProvider.getRealtimeState(), selectedShipId);
        if (shipSnap == null || !(shipSnap.details instanceof ShipDetails sd) || !sd.isMoving || sd.movementTarget == null)
            return;

        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(systemCamera.camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        float sx = (float) shipSnap.posWorldGU.x(), sy = (float) shipSnap.posWorldGU.y(), sz = (float) shipSnap.posWorldGU.z();
        float tx = (float) sd.movementTarget.x(), ty = (float) sd.movementTarget.y(), tz = (float) sd.movementTarget.z();

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
    public void updateCameraFollow(SystemViewRenderer systemViewRenderer,
                                    StarAxisGameRuntime runtime, WorldCamera systemCamera) {
        if (cameraFollowTargetId < 0)
            return;

        if (systemViewRenderer.getBodyPosition(cameraFollowTargetId, focusTmp)) {
            systemCamera.target.set(focusTmp);
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