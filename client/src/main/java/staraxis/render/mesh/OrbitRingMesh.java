package staraxis.render.mesh;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;

import staraxis.game.space.OrbitSolver;
import staraxis.game.space.OrbitalElements;
import staraxis.game.space.SpacePosition;

/**
 * OrbitRingMesh（轨道环线网格）。
 *
 * 渲染行星轨道为半透明圆环。
 * 考虑偏心率（椭圆）和倾角（倾斜）。
 *
 * 轨道顶点在轨道根数未变化时自动缓存，避免每帧重复计算旋转矩阵和三角函数。
 */
public class OrbitRingMesh {

    private static final int SEGMENTS = 128;
    private final ShapeRenderer shapeRenderer;

    // ── 轨道顶点缓存 ──────────────────────────────────────────
    private double cachedA, cachedE, cachedI, cachedOmega, cachedW;
    private double[] cachedVerts; // [x0,y0,z0, x1,y1,z1, ...]，星系坐标系（不含 gravity 偏移）
    private boolean cacheValid;

    public OrbitRingMesh() {
        shapeRenderer = new ShapeRenderer();
    }

    /**
     * 渲染轨道环（带重力中心偏移）。
     *
     * @param orbit 轨道根数
     * @param offsetX 引力中心 X 偏移
     * @param offsetY 引力中心 Y 偏移
     * @param offsetZ 引力中心 Z 偏移
     * @param projectionView 投影视图矩阵
     * @param color 轨道颜色
     */
    public void render(OrbitalElements orbit, float offsetX, float offsetY, float offsetZ, Matrix4 projectionView, Color color) {
        double a = orbit.semiMajorAxis();
        double e = orbit.eccentricity();
        double i = orbit.inclination();
        double omega = orbit.longitudeOfAscendingNode();
        double w = orbit.argumentOfPeriapsis();

        // 轨道根数未变时复用缓存顶点
        if (!cacheValid || cachedA != a || cachedE != e || cachedI != i
                || cachedOmega != omega || cachedW != w) {
            buildCache(a, e, i, omega, w);
        }

        // 显式确保深度测试开启，保证轨道环被行星/恒星正确遮挡
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);

        shapeRenderer.setProjectionMatrix(projectionView);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(color);
        Gdx.gl.glLineWidth(1f);

        for (int seg = 0; seg <= SEGMENTS; seg++) {
            int idx = seg * 3;
            float x = (float) (cachedVerts[idx]     + offsetX);
            float y = (float) (cachedVerts[idx + 1] + offsetY);
            float z = (float) (cachedVerts[idx + 2] + offsetZ);

            if (seg > 0) {
                shapeRenderer.line(
                    (float) (cachedVerts[idx - 3] + offsetX),
                    (float) (cachedVerts[idx - 2] + offsetY),
                    (float) (cachedVerts[idx - 1] + offsetZ),
                    x, y, z
                );
            }
        }

        shapeRenderer.end();
    }

    /**
     * 计算 128 段轨道顶点并缓存。
     * 使用 OrbitSolver.rotateToGalaxyFrame 将轨道面坐标变换到星系坐标系。
     */
    private void buildCache(double a, double e, double i, double omega, double w) {
        cachedA = a;
        cachedE = e;
        cachedI = i;
        cachedOmega = omega;
        cachedW = w;

        int vertCount = SEGMENTS + 1;
        if (cachedVerts == null || cachedVerts.length != vertCount * 3) {
            cachedVerts = new double[vertCount * 3];
        }

        for (int seg = 0; seg < vertCount; seg++) {
            double nu = 2.0 * Math.PI * seg / SEGMENTS;

            // 轨道平面极坐标 -> 直角坐标（轨道面在 XZ）
            double r = a * (1.0 - e * e) / (1.0 + e * Math.cos(nu));
            double xLocal = r * Math.cos(nu);
            double zLocal = r * Math.sin(nu);

            // 旋转到星系坐标系（使用 OrbitSolver 共享方法，消除矩阵重复）
            SpacePosition pos = OrbitSolver.rotateToGalaxyFrame(xLocal, zLocal, omega, i, w);

            int idx = seg * 3;
            cachedVerts[idx]     = pos.x();
            cachedVerts[idx + 1] = pos.y();
            cachedVerts[idx + 2] = pos.z();
        }

        cacheValid = true;
    }

    public void setCacheDirty() {
        cacheValid = false;
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
