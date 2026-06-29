package staraxis.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * WorldCamera — 轨道相机喵。
 *
 * 缩放 = 镜头到 target 的距离（orbitDist），限制最大不超过世界边界。
 * WASD：移动 target（镜头中心点）
 * 右键拖拽：旋转视角
 * Q/E：缩放（增大/减小 orbitDist） R：重置
 */
public class WorldCamera {

    public static final double MIN_ZOOM = 1.0;
    public static final double MAX_ZOOM = 7.0;
    private static final float ROT_SPEED = 0.3f;
    private static final float MOVE_SPEED = 500f;

    public final PerspectiveCamera camera;
    public final Vector3 target = new Vector3();

    public float yaw = 45f, pitch = 45f; // 45 度俯视，看清星系盘面喵
    public double zoomLevel = 4.0;
    private double targetZoom = 4.0;
    private double orbitDist = 2000;

    private int lx, ly;
    private boolean rotating;

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
        // 缩放 = 镜头到 target 的距离喵
        orbitDist = Math.pow(10, 2 + 0.67 * (7 - zoomLevel));
        // 最大距离不超过世界边界，保证拉到底也不会出界喵
        if (orbitDist > 480000) orbitDist = 480000;

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

    private void handleInput(float dt) {
        // Q/E 缩放喵
        if (Gdx.input.isKeyPressed(Input.Keys.Q))
            targetZoom = MathUtils.clamp(targetZoom + 2f * dt, MIN_ZOOM, MAX_ZOOM);
        if (Gdx.input.isKeyPressed(Input.Keys.E))
            targetZoom = MathUtils.clamp(targetZoom - 2f * dt, MIN_ZOOM, MAX_ZOOM);
        if (Gdx.input.isKeyJustPressed(Input.Keys.R))
            resetView();

        // WASD 镜头移动（地平面方向）喵
        float sp = MOVE_SPEED * dt;
        Vector3 fwd = new Vector3(camera.direction).nor();
        fwd.y = 0;
        if (fwd.len() < 0.01f)
            fwd.set(0, 0, -1);
        fwd.nor();
        Vector3 rgt = new Vector3(fwd).crs(Vector3.Y).nor();

        if (Gdx.input.isKeyPressed(Input.Keys.W))
            target.add(fwd.scl(sp));
        if (Gdx.input.isKeyPressed(Input.Keys.S))
            target.add(fwd.scl(-sp));
        if (Gdx.input.isKeyPressed(Input.Keys.A))
            target.add(rgt.scl(-sp));
        if (Gdx.input.isKeyPressed(Input.Keys.D))
            target.add(rgt.scl(sp));

        // 限制在 100万³ 世界坐标内喵
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
