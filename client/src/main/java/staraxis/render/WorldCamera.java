package staraxis.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * WorldCamera — 轨道相机喵。
 *
 * 缩放 = 镜头到 target 的距离（orbitDist）。
 * WASD：移动 target（镜头中心点，地平面方向）
 * Q/E：控制 target 的 Y 轴上下移动
 * 右键拖拽：旋转视角
 * 滚轮：缩放（增大/减小 orbitDist） R：重置
 *
 * target 限制在世界边界（±480000）内，但镜头实际位置不限制，避免边缘视角抖动喵。
 */
public class WorldCamera {

    public static final double MIN_ZOOM = 1.0;
    public static final double MAX_ZOOM = 7.0;
    private static final float ROT_SPEED = 0.3f;
    private static final float MOVE_SPEED = 500f;
    private static final float SCROLL_ZOOM_SPEED = 0.5f;

    public final PerspectiveCamera camera;
    public final Vector3 target = new Vector3();

    public float yaw = 45f, pitch = 45f; // 45 度俯视，看清星系盘面喵
    public double zoomLevel = 4.0;
    private double targetZoom = 4.0;
    private double orbitDist = 2000;
    private double maxOrbitDist = Double.MAX_VALUE; // 外部可设上限（Galaxy 5万 / System 2万）

    private int lx, ly;
    private boolean rotating;
    private float scrollAccum = 0f;

    public WorldCamera() {
        camera = new PerspectiveCamera(67f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 1e6f;
        camera.position.set(0, 0, 2000);
        camera.lookAt(0, 0, 0);
        camera.up.set(0, 1, 0);
        camera.update();
    }

    public void resize(int w, int h) {
        camera.viewportWidth = w;
        camera.viewportHeight = h;
    }

    public void update(float dt) {
        handleInput(dt);
        zoomLevel += (targetZoom - zoomLevel) * Math.min(dt * 5f, 1f);
        // 缩放 = 镜头到 target 的距离喵，受 maxOrbitDist 限制
        orbitDist = Math.pow(10, 2 + 0.67 * (7 - zoomLevel));
        if (orbitDist > maxOrbitDist) {
            orbitDist = maxOrbitDist;
            // 逆推最小 zoomLevel，阻止 zoom 继续往远了漂
            double minZoom = 7 - (Math.log10(maxOrbitDist) - 2) / 0.67;
            if (zoomLevel < minZoom)
                zoomLevel = minZoom;
            if (targetZoom < minZoom)
                targetZoom = minZoom;
        }
        // 镜头位置不限制，允许自由越界喵

        float yr = yaw * MathUtils.degRad, pr = pitch * MathUtils.degRad;
        camera.position.set(
                target.x + (float) (orbitDist * Math.cos(pr) * Math.sin(yr)),
                target.y + (float) (orbitDist * Math.sin(pr)),
                target.z + (float) (orbitDist * Math.cos(pr) * Math.cos(yr)));
        camera.lookAt(target.x, target.y, target.z);
        camera.up.set(0, 1, 0);
        camera.normalizeUp();
        camera.update();
    }

    public double getOrbitDistance() {
        return orbitDist;
    }

    /** 直接设置缩放等级（进入系统视图时可重置初始距离）喵 */
    public void setZoom(double level) {
        zoomLevel = MathUtils.clamp(level, MIN_ZOOM, MAX_ZOOM);
        targetZoom = zoomLevel;
    }

    /** 设置镜头最远距离（GUI 单位），Galaxy 5万 / System 2万 */
    public void setMaxOrbitDist(double max) {
        this.maxOrbitDist = max;
    }

    public double getMaxOrbitDist() {
        return maxOrbitDist;
    }

    /** 接收滚轮事件（由 InputProcessor 转发），积累滚轮偏移量喵 */
    public void onScroll(float amountY) {
        scrollAccum += amountY;
    }

    private void handleInput(float dt) {
        // 移动速度与 orbitDist 成正比，镜头越远移动越快喵
        float sp = MOVE_SPEED * dt * (float) (orbitDist / 2000.0);
        // Q/E 控制 target 的 Y 轴上下喵
        if (Gdx.input.isKeyPressed(Input.Keys.Q))
            target.y -= sp;
        if (Gdx.input.isKeyPressed(Input.Keys.E))
            target.y += sp;
        // R 重置视角喵
        if (Gdx.input.isKeyJustPressed(Input.Keys.R))
            resetView();

        // 滚轮缩放喵
        if (scrollAccum != 0f) {
            targetZoom = MathUtils.clamp(targetZoom - scrollAccum * SCROLL_ZOOM_SPEED, MIN_ZOOM, MAX_ZOOM);
            scrollAccum = 0f;
        }

        // WASD 镜头移动（地平面方向）—— 使用 cpy().scl() 避免 scl() 原地修改向量喵
        Vector3 fwd = new Vector3(camera.direction).nor();
        fwd.y = 0;
        if (fwd.len() < 0.01f)
            fwd.set(0, 0, -1);
        fwd.nor();
        Vector3 rgt = new Vector3(fwd).crs(Vector3.Y).nor();

        if (Gdx.input.isKeyPressed(Input.Keys.W))
            target.add(fwd.cpy().scl(sp));
        if (Gdx.input.isKeyPressed(Input.Keys.S))
            target.add(fwd.cpy().scl(-sp));
        if (Gdx.input.isKeyPressed(Input.Keys.A))
            target.add(rgt.cpy().scl(-sp));
        if (Gdx.input.isKeyPressed(Input.Keys.D))
            target.add(rgt.cpy().scl(sp));

        // 限制 target（镜头中心点）在 100万³ 世界坐标内，镜头位置不受限喵
        float lim = 480000f;
        target.x = MathUtils.clamp(target.x, -lim, lim);
        target.y = MathUtils.clamp(target.y, -lim, lim);
        target.z = MathUtils.clamp(target.z, -lim, lim);

        // 右键拖拽旋转喵
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            rotating = true;
            lx = Gdx.input.getX();
            ly = Gdx.input.getY();
        }
        if (!Gdx.input.isButtonPressed(Input.Buttons.RIGHT))
            rotating = false;
        if (rotating) {
            int dx = Gdx.input.getX() - lx, dy = Gdx.input.getY() - ly;
            yaw -= dx * ROT_SPEED;
            pitch += dy * ROT_SPEED;
            pitch = MathUtils.clamp(pitch, -89f, 89f); // 限制 pitch 避免万向锁
            lx = Gdx.input.getX();
            ly = Gdx.input.getY();
        }
    }

    public void resetView() {
        targetZoom = 4.0;
        yaw = 45f;
        pitch = 45f;
        target.set(0, 0, 0);
    }
}
