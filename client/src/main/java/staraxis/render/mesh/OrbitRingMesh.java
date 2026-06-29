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

        // 计算轨道平面上的点，然后旋转到星系坐标系
        shapeRenderer.setProjectionMatrix(projectionView);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(color);
        Gdx.gl.glLineWidth(1f);

        double cosI = Math.cos(i);
        double sinI = Math.sin(i);
        double cosO = Math.cos(omega);
        double sinO = Math.sin(omega);
        double cosW = Math.cos(w);
        double sinW = Math.sin(w);

        double prevX = 0, prevY = 0, prevZ = 0;

        for (int seg = 0; seg <= SEGMENTS; seg++) {
            double nu = 2.0 * Math.PI * seg / SEGMENTS;

            // 轨道平面上的极坐标 -> 直角坐标
            double r = a * (1.0 - e * e) / (1.0 + e * Math.cos(nu));
            double xLocal = r * Math.cos(nu);
            double yLocal = r * Math.sin(nu);

            // 3-1-3 欧拉旋转（天文学标准：轨道面在 XY）
            double solverX = (cosO * cosW - sinO * sinW * cosI) * xLocal
                     + (-cosO * sinW - sinO * cosW * cosI) * yLocal;
            double solverY = (sinO * cosW + cosO * sinW * cosI) * xLocal
                     + (-sinO * sinW + cosO * cosW * cosI) * yLocal;
            double solverZ = (sinW * sinI) * xLocal
                     + (cosW * sinI) * yLocal;

            // 转换为渲染坐标：交换 Y/Z，使轨道面在 XZ 水平面
            double x = solverX;
            double y = solverZ;
            double z = -solverY;

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
