package staraxis.render.mesh;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;

import staraxis.game.space.OrbitalElements;

/**
 * OrbitRingMesh（轨道环线网格）。
 *
 * 渲染行星轨道为半透明圆环。
 * 考虑偏心率（椭圆）和倾角（倾斜）。
 */
public class OrbitRingMesh {

    private static final int SEGMENTS = 128;
    private final ShapeRenderer shapeRenderer;

    public OrbitRingMesh() {
        shapeRenderer = new ShapeRenderer();
    }

    /**
     * 渲染轨道环。
     *
     * @param orbit 轨道根数
     * @param projectionView 投影视图矩阵
     * @param color 轨道颜色
     */
    public void render(OrbitalElements orbit, Matrix4 projectionView, Color color) {
        double a = orbit.semiMajorAxis();
        double e = orbit.eccentricity();
        double i = orbit.inclination();
        double omega = orbit.longitudeOfAscendingNode();
        double w = orbit.argumentOfPeriapsis();

        // 预计算旋转矩阵（轨道面在 XZ，Y 朝上）
        double cosI = Math.cos(i);
        double sinI = Math.sin(i);
        double cosO = Math.cos(omega);
        double sinO = Math.sin(omega);
        double cosW = Math.cos(w);
        double sinW = Math.sin(w);

        double p11 = cosO * cosW - sinO * cosI * sinW;
        double p13 = cosO * sinW + sinO * cosI * cosW;
        double p21 = sinO * sinI;
        double p23 = -cosO * sinI;
        double p31 = -sinO * cosW - cosO * cosI * sinW;
        double p33 = -sinO * sinW + cosO * cosI * cosW;

        // 显式确保深度测试开启，保证轨道环被行星/恒星正确遮挡
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);

        shapeRenderer.setProjectionMatrix(projectionView);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(color);
        Gdx.gl.glLineWidth(1f);

        double prevX = 0, prevY = 0, prevZ = 0;

        for (int seg = 0; seg <= SEGMENTS; seg++) {
            double nu = 2.0 * Math.PI * seg / SEGMENTS;

            // 轨道平面极坐标 -> 直角坐标（轨道面在 XZ）
            double r = a * (1.0 - e * e) / (1.0 + e * Math.cos(nu));
            double xLocal = r * Math.cos(nu);
            double zLocal = r * Math.sin(nu);

            // 旋转到星系坐标系
            double x = p11 * xLocal + p13 * zLocal;
            double y = p21 * xLocal + p23 * zLocal;
            double z = p31 * xLocal + p33 * zLocal;

            if (seg > 0) {
                shapeRenderer.line(
                    (float) prevX, (float) prevY, (float) prevZ,
                    (float) x, (float) y, (float) z
                );
            }

            prevX = x;
            prevY = y;
            prevZ = z;
        }

        shapeRenderer.end();
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}
