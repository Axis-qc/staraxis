package staraxis.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
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
 * target 限制在 targetLimit 范围内，但镜头实际位置不限制，避免边缘视角抖动喵。
 *
 * galaxy 视图和 system 视图各持独立实例，参数互不干扰喵。
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
    private double maxOrbitDist = Double.MAX_VALUE;
    private float targetLimit = 1000000f;

    private int lx, ly;
    private boolean rotating;
    private float scrollAccum = 0f;

    /** 默认构造（galaxy 视图用）：near=10, far=1e6, targetLimit=480000 */
    public WorldCamera() {
        this(10f, 1e6f, 480000f);
    }

    /**
     * 带参构造。
     *
     * @param near 近裁剪面（越大 far/near 比值越小，深度精度越高）
     * @param far  远裁剪面（galaxy 1e6 保证拾取精度，system 3e6 容纳大轨道）
     * @param targetLimit target 坐标边界（galaxy ±480000, system ±1000000）
     */
    public WorldCamera(float near, float far, float targetLimit) {
        this.targetLimit = targetLimit;
        camera = new PerspectiveCamera(67f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = near;
        camera.far = far;
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
        // 刷新投影矩阵（aspect ratio 可能随 resize 变化）
        camera.projection.setToProjection(Math.abs(camera.near), Math.abs(camera.far), camera.fieldOfView,
                camera.viewportWidth / camera.viewportHeight);
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

        // 手动用 double 精度计算视图矩阵，避免 camera.lookAt + camera.update 中
        // float 灾难性抵消导致的视角旋转抖动（orbitDist 在 10 万量级时明显）
        //
        // 参考 OpenGL 惯例：view = [right^T    -dot(right, eye)]
        //                         [up^T       -dot(up, eye)]
        //                         [-forward^T  dot(forward, eye)]
        //                         [0 0 0      1]
        // 其中 forward = normalize(target - eye)
        double fx = target.x - camera.position.x;
        double fy = target.y - camera.position.y;
        double fz = target.z - camera.position.z;
        double fLen = Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (fLen > 1e-12) { fx /= fLen; fy /= fLen; fz /= fLen; }

        // right = normalize(forward × up_world)，up_world = (0,1,0)
        // cross(a,b) = (a.y*b.z - a.z*b.y, a.z*b.x - a.x*b.z, a.x*b.y - a.y*b.x)
        double rx = fy * 0.0 - fz * 1.0; // = -fz
        double ry = fz * 0.0 - fx * 0.0; // = 0
        double rz = fx * 1.0 - fy * 0.0; // = fx
        double rLen = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rLen > 1e-12) { rx /= rLen; ry /= rLen; rz /= rLen; }

        // up_actual = right × forward（保证正交基）
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;

        // 平移分量用 double 累加，避免 float 灾难性抵消
        double ex = camera.position.x;
        double ey = camera.position.y;
        double ez = camera.position.z;
        float tx = (float) -(rx * ex + ry * ey + rz * ez);
        float ty = (float) -(ux * ex + uy * ey + uz * ez);
        float tz = (float) (fx * ex + fy * ey + fz * ez); // +dot(forward, eye)，z 轴反向

        // LibGDX Matrix4.val[] 是列主序：val[col*4 + row]
        // 列0=[right.x, up.x, -forward.x, 0]  列1=[right.y, up.y, -forward.y, 0]
        // 列2=[right.z, up.z, -forward.z, 0]  列3=[tx, ty, tz, 1]
        camera.view.set(new float[]{
                (float) rx, (float) ux, (float) -fx, 0f,
                (float) ry, (float) uy, (float) -fy, 0f,
                (float) rz, (float) uz, (float) -fz, 0f,
                tx,         ty,         tz,          1f});

        camera.combined.set(camera.projection);
        Matrix4.mul(camera.combined.val, camera.view.val);

        // 更新 invProjectionView，getPickRay 依赖它
        camera.invProjectionView.set(camera.combined);
        camera.invProjectionView.inv();

        // 更新 direction/up 供 WASD 和 getPickRay 使用
        camera.direction.set((float) fx, (float) fy, (float) fz);
        camera.up.set((float) ux, (float) uy, (float) uz);
    }

    public double getOrbitDistance() {
        return orbitDist;
    }

    /**
     * 设置 far plane。
     *
     * galaxy 视图用 1e6：far/near 比值 1000 万:1，getPickRay 射线方向精度足够命中 40 GU 的恒星球。
     * system 视图用 3e6：容纳最远 ~116 万 GU 的缩放后轨道。
     *
     * 注意：far 不能随意增大，PerspectiveCamera.getPickRay 在 far/near 比值过大时
     * 会出现 float 精度丢失，导致射线方向偏移、无法拾取小物体。
     */
    public void setFarPlane(float far) {
        camera.far = far;
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

        // 限制 target 在 targetLimit 范围内，镜头位置不受限喵
        target.x = MathUtils.clamp(target.x, -targetLimit, targetLimit);
        target.y = MathUtils.clamp(target.y, -targetLimit, targetLimit);
        target.z = MathUtils.clamp(target.z, -targetLimit, targetLimit);

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
        yaw = 45f;
        pitch = 45f;
        target.set(0, 0, 0);
    }

    /**
     * 是否正在被 WASDQE 键移动镜头。
     */
    public boolean isUserControlled() {
        return Gdx.input.isKeyPressed(Input.Keys.W)
            || Gdx.input.isKeyPressed(Input.Keys.S)
            || Gdx.input.isKeyPressed(Input.Keys.A)
            || Gdx.input.isKeyPressed(Input.Keys.D)
            || Gdx.input.isKeyPressed(Input.Keys.Q)
            || Gdx.input.isKeyPressed(Input.Keys.E);
    }
}
