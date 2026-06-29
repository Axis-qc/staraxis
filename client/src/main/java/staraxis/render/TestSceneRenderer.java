package staraxis.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Plane;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.Ray;

/**
 * TestSceneRenderer — Galaxy / System 双视图喵。
 *
 * 两个独立渲染场景，均以 (0,0,0) 为中心（类似 Stellaris）：
 *
 * Galaxy：600 颗 3D 恒星球体 + 网格，鼠标悬停显示选中框，点击任意恒星进入 System。
 * System：真实比例太阳系（8颗行星+轨道动画+舰船），ESC 返回 Galaxy。
 *
 * 注意：两个场景共用 100,000³ 坐标系，但位置完全独立。
 * 星系中恒星在 (x, y, z) 不代表系统内行星也偏移同样距离。
 */
public class TestSceneRenderer {

    private final WorldCamera cam;
    private ShapeRenderer sr;
    private ModelBatch mb;
    private Environment env;
    private Model starM, planetM;

    private static final float BOX = 50000f;

    private static final int GN = 600;
    private final float[][] gx = new float[GN][3];
    private final Color[] gc = new Color[GN];

    // 太阳系轨道半径（GU），1 AU ≈ 1000 GU 压缩喵
    private static final double[] SORB = { 400, 750, 1000, 1500, 5200, 9500, 19200, 30000 };
    // 行星视觉半径（GU，已放大以肉眼可见）喵
    private static final double[] SRAD = { 2, 4, 5, 3, 20, 16, 8, 7 };
    // 行星颜色喵
    private static final Color[] SCOL = {
            c(0.6f, 0.6f, 0.6f),    // 水星 灰
            c(0.85f, 0.75f, 0.5f),   // 金星 黄
            c(0.2f, 0.5f, 0.8f),     // 地球 蓝
            c(0.8f, 0.35f, 0.2f),    // 火星 红
            c(0.9f, 0.65f, 0.3f),    // 木星 橙
            c(0.8f, 0.7f, 0.4f),     // 土星 金
            c(0.3f, 0.6f, 0.7f),     // 天王星 青
            c(0.15f, 0.2f, 0.6f) };  // 海王星 深蓝
    // 轨道周期（游戏秒）喵
    private static final double[] SPERIOD = { 60, 150, 240, 450, 900, 1500, 2400, 3600 };
    // 三艘测试舰船的本地坐标喵
    private static final double[][] SHIP = { { 800, 0, 600 }, { -600, 50, -400 }, { 1200, -30, 800 } };

    private boolean inSystem = false;
    private int systemIdx = 0;
    private int hoveredStar = -1;
    private float systemTime;  // 系统内累计时间，驱动行星公转喵
    private Model galaStarM;
    private ModelInstance[] gmi;

    private static Color c(float r, float g, float b) {
        return new Color(r, g, b, 1f);
    }

    public TestSceneRenderer() {
        cam = new WorldCamera();
        env = new Environment();
        env.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.3f, 0.3f, 0.4f, 1f));
        env.add(new DirectionalLight().set(0.8f, 0.8f, 0.9f, 0, 1, 0));
        genStars();
    }

    private void genStars() {
        long s = 42L;
        for (int i = 0; i < GN; i++) {
            s = s * 1103515245L + 12345L;
            double dist = ((s & 0x7fffffffL) / (double) 0x7fffffffL) * BOX * 0.85;
            double baseAngle = dist * 0.00025 + (i % 5) * Math.PI * 2 / 5;
            s = s * 1103515245L + 12345L;
            double spread = ((s & 0x7fffffffL) / (double) 0x7fffffffL - 0.5) * 0.4;
            double angle = baseAngle + spread;
            // XZ 盘面：cos→X，sin→Z 喵
            gx[i][0] = (float) (dist * Math.cos(angle));
            gx[i][2] = (float) (dist * Math.sin(angle));
            // Y 只是盘面厚度（微小）喵
            s = s * 1103515245L + 12345L;
            gx[i][1] = (float) (((s & 0x7fffffffL) / (double) 0x7fffffffL - 0.5) * 4000);
            // 颜色喵
            float cr = 0.7f + ((s >> 16) & 0xff) / 1280f;
            float cg = 0.6f + ((s >> 8) & 0xff) / 1280f;
            float cb = 0.5f + (s & 0xff) / 640f;
            gc[i] = new Color(cr, cg, cb, 1f);
        }
    }

    public void resize(int w, int h) {
        cam.resize(w, h);
    }

    public void render(float dt) {
        cam.update(dt);
        Gdx.gl.glClearColor(0.005f, 0.005f, 0.02f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        if (sr == null)
            sr = new ShapeRenderer();
        if (mb == null)
            createModels();

        if (inSystem) {
            systemTime += dt;
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                inSystem = false;
                systemTime = 0;
                cam.target.set(0, 0, 0);
            }
            drawSystem(cam.camera.combined);
        } else {
            updateHoveredStar();
            if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                trySelectStar();
            }
            drawGalaxy(cam.camera.combined);
            drawGrid(cam.camera.combined);
        }
    }

    private void updateHoveredStar() {
        Ray ray = cam.camera.getPickRay(Gdx.input.getX(), Gdx.input.getY());
        Plane xz = new Plane(new Vector3(0, 1, 0), 0);
        Vector3 hit = new Vector3();
        hoveredStar = -1;
        if (Intersector.intersectRayPlane(ray, xz, hit)) {
            float bestD = 800;
            for (int i = 0; i < GN; i++) {
                float dx = hit.x - gx[i][0], dz = hit.z - gx[i][2];
                float d = (float) Math.sqrt(dx * dx + dz * dz);
                if (d < bestD) {
                    bestD = d;
                    hoveredStar = i;
                }
            }
        }
    }

    private void trySelectStar() {
        if (hoveredStar >= 0) {
            inSystem = true;
            systemIdx = hoveredStar;
            // 系统视图独立以 (0,0,0) 为中心渲染，不飞到恒星位置喵
            cam.target.set(0, 0, 0);
            cam.setZoom(5.0);
        }
    }

    private void drawGalaxy(Matrix4 pr) {
        // 懒初始化恒星球体实例喵
        if (gmi == null) {
            gmi = new ModelInstance[GN];
            for (int i = 0; i < GN; i++) {
                gmi[i] = new ModelInstance(galaStarM, gx[i][0], gx[i][1], gx[i][2]);
            }
        }

        // 渲染 3D 球体恒星喵
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        mb.begin(cam.camera);
        for (int i = 0; i < GN; i++) {
            gmi[i].materials.get(0).set(ColorAttribute.createDiffuse(gc[i]));
            mb.render(gmi[i], env);
        }
        mb.end();

        // 悬停选中框喵
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        if (hoveredStar >= 0) {
            float x = gx[hoveredStar][0], z = gx[hoveredStar][2];
            float s = 70f;
            sr.setProjectionMatrix(pr);
            sr.begin(ShapeRenderer.ShapeType.Line);
            sr.setColor(0.3f, 0.8f, 1f, 0.6f);
            Gdx.gl.glLineWidth(2f);
            sr.line(x - s, 0, z - s, x + s, 0, z - s);
            sr.line(x + s, 0, z - s, x + s, 0, z + s);
            sr.line(x + s, 0, z + s, x - s, 0, z + s);
            sr.line(x - s, 0, z + s, x - s, 0, z - s);
            sr.end();
        }
    }

    private void drawGrid(Matrix4 pr) {
        sr.setProjectionMatrix(pr);
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0.06f, 0.06f, 0.12f, 0.2f);
        for (float g = -BOX; g <= BOX; g += 10000) {
            sr.line(g, 0, -BOX, g, 0, BOX);
            sr.line(-BOX, 0, g, BOX, 0, g);
        }
        Gdx.gl.glLineWidth(2f);
        float al = 3000f;
        sr.setColor(0.3f, 0.1f, 0.1f, 0.4f);
        sr.line(0, 0, 0, al, 0, 0);
        sr.setColor(0.1f, 0.3f, 0.1f, 0.4f);
        sr.line(0, 0, 0, 0, al, 0);
        sr.setColor(0.1f, 0.15f, 0.3f, 0.4f);
        sr.line(0, 0, 0, 0, 0, al);
        sr.end();
    }

    private void drawSystem(Matrix4 pr) {
        // 计算行星位置（简单轨道运动）喵
        float[] px = new float[SORB.length];
        float[] pz = new float[SORB.length];
        for (int i = 0; i < SORB.length; i++) {
            double angle = 2 * Math.PI * systemTime / SPERIOD[i];
            px[i] = (float) (SORB[i] * Math.cos(angle));
            pz[i] = (float) (SORB[i] * Math.sin(angle));
        }

        // 坐标轴喵
        sr.setProjectionMatrix(pr);
        sr.begin(ShapeRenderer.ShapeType.Line);
        Gdx.gl.glLineWidth(2f);
        float al = 500f;
        sr.setColor(1f, 0.2f, 0.2f, 0.7f);
        sr.line(0, 0, 0, al, 0, 0);
        sr.setColor(0.2f, 1f, 0.2f, 0.7f);
        sr.line(0, 0, 0, 0, al, 0);
        sr.setColor(0.2f, 0.4f, 1f, 0.7f);
        sr.line(0, 0, 0, 0, 0, al);
        sr.end();

        // 轨道环喵
        sr.setProjectionMatrix(pr);
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(0.2f, 0.35f, 0.6f, 0.25f);
        for (double r : SORB)
            for (int i = 0; i < 128; i++) {
                double a1 = 2 * Math.PI * i / 128, a2 = 2 * Math.PI * (i + 1) / 128;
                sr.line((float) (r * Math.cos(a1)), 0, (float) (r * Math.sin(a1)),
                        (float) (r * Math.cos(a2)), 0, (float) (r * Math.sin(a2)));
            }
        sr.end();

        // 天体 3D 球体喵
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        mb.begin(cam.camera);
        // 太阳喵
        ModelInstance sm = new ModelInstance(starM, 0, 0, 0);
        sm.materials.get(0).set(ColorAttribute.createDiffuse(1f, 0.85f, 0.3f, 1f));
        sm.transform.scl(50f / 30f);
        mb.render(sm, env);
        // 行星喵
        for (int i = 0; i < SORB.length; i++) {
            ModelInstance pm = new ModelInstance(planetM, px[i], 0, pz[i]);
            pm.transform.scl((float) SRAD[i]);
            pm.materials.get(0).set(ColorAttribute.createDiffuse(SCOL[i].r, SCOL[i].g, SCOL[i].b, 1f));
            mb.render(pm, env);
        }
        mb.end();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);

        // 测试舰船喵
        sr.setProjectionMatrix(pr);
        sr.begin(ShapeRenderer.ShapeType.Line);
        sr.setColor(Color.CYAN);
        Gdx.gl.glLineWidth(1.5f);
        for (double[] sp : SHIP) {
            float px2 = (float) sp[0], py = (float) sp[1], pz2 = (float) sp[2], ss = 3f;
            sr.line(px2, py + ss, pz2, px2 + ss, py, pz2);
            sr.line(px2 + ss, py, pz2, px2, py - ss, pz2);
            sr.line(px2, py - ss, pz2, px2 - ss, py, pz2);
            sr.line(px2 - ss, py, pz2, px2, py + ss, pz2);
        }
        sr.end();
    }

    private void createModels() {
        mb = new ModelBatch();
        ModelBuilder b = new ModelBuilder();
        starM = b.createSphere(30f, 30f, 30f, 24, 18, new Material(),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        planetM = b.createSphere(1, 1, 1, 16, 12, new Material(),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        galaStarM = b.createSphere(40f, 40f, 40f, 8, 6, new Material(),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    }

    public WorldCamera getCamera() {
        return cam;
    }

    public void dispose() {
        if (sr != null)
            sr.dispose();
        if (mb != null)
            mb.dispose();
        if (starM != null)
            starM.dispose();
        if (planetM != null)
            planetM.dispose();
        if (galaStarM != null)
            galaStarM.dispose();
    }
}
