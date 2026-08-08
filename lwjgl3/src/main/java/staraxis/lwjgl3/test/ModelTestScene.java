package staraxis.lwjgl3.test;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.Renderable;
import com.badlogic.gdx.graphics.g3d.Shader;
import com.badlogic.gdx.graphics.g3d.attributes.DirectionalLightsAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.DefaultShaderProvider;
import com.badlogic.gdx.graphics.g3d.utils.DepthShaderProvider;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import staraxis.game_asset.loader.GltfLoader;
import staraxis.game_asset.loader.LoadedModel;
import staraxis.render.adapter.MeshDataToModel;
import staraxis.render.shader.PlayerColorAttribute;
import staraxis.render.shader.ShipShader;

/**
 * ModelTestScene（独立模型测试场景）。
 *
 * 在游戏外单独加载 glTF 模型，验证渲染效果：
 * - 法线凹凸（F 键切换法线可视化 / 正常渲染）
 * - 贴图表现（diffuse/normal/specular）
 * - 光照效果（Q/E 调光仰角、A/D 调光方位角，掠射角下凹凸最明显）
 * - 模型朝向（自动旋转或鼠标拖拽观察）
 *
 * 运行方式：`test\model_test.bat`（或 gradlew :lwjgl3:modelTest）。
 *
 * 操作：
 * - 左键拖拽：旋转观察
 * - 滚轮：缩放
 * - 右键拖拽：平移
 * - F：切换法线可视化
 * - 空格：自动旋转
 * - Q/E：光仰角调整
 * - A/D：光方位角调整
 * - R：重置相机
 */
public class ModelTestScene extends ApplicationAdapter {

    /** 测试模型路径（相对 assets 根目录）。 */
    private static final String MODEL_PATH = "ship/star_eater/satr_eater.gltf";

    /** glTF 文件所在目录（用于拼接贴图相对路径）。 */
    private static final String MODEL_BASE_PATH = "ship/star_eater/";

    /** 模型缩放系数。 */
    private static final float MODEL_SCALE = 1f;

    /** 坐标轴长度（GU），约为模型包围盒半径（star_eater 约 ±19）。 */
    private static final float AXIS_LENGTH = 25f;

    /** 世界向上向量（相机保持水平用，与游戏内部 WorldCamera 一致）。 */
    private static final Vector3 WORLD_UP = new Vector3(0f, 1f, 0f);

    // 渲染对象
    private Model model;
    private ModelInstance instance;
    private ModelBatch modelBatch;
    private Environment environment;
    private DirectionalLight light;
    private PerspectiveCamera camera;

    // 坐标轴渲染
    private ShapeRenderer shapeRenderer;

    // 相机控制状态
    private float yaw = 30f;
    private float pitch = 25f;
    private float dist = 120f;
    private final Vector3 target = new Vector3();
    private int lastTouchX;
    private int lastTouchY;

    // 相机临时向量（避免每帧分配）
    private final Vector3 tmpForward = new Vector3();
    private final Vector3 tmpRight = new Vector3();
    private final Vector3 tmpUp = new Vector3();

    // 光控制
    private float lightElevation = 35f;   // 光仰角（度）
    private float lightAzimuth = 0f;      // 光方位角（度）

    // 显示状态
    private boolean debugNormal;
    private boolean autoRotate;
    private float rotationAngle;

    // UI
    private SpriteBatch uiBatch;
    private BitmapFont font;

    @Override
    public void create() {
        // 1. 加载模型（走完整管线：GltfLoader 解析 -> MeshDataToModel 转换）
        LoadedModel loaded = GltfLoader.load(
                Gdx.files.internal(MODEL_PATH).read(),
                Gdx.files.internal(MODEL_BASE_PATH + "satr_eater.bin").readBytes(),
                MODEL_BASE_PATH);
        model = MeshDataToModel.convert(loaded.mesh, MeshDataToModel.convertMaterial(loaded.material));

        // 挂玩家颜色属性，让舰船材质走 ShipShader（逐像素光照 + normal mapping）
        model.materials.get(0).set(new PlayerColorAttribute(1f, 1f, 1f));

        instance = new ModelInstance(model);

        // 2. 相机
        camera = new PerspectiveCamera(60f, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.5f;
        camera.far = 10000f;
        camera.position.set(0, 0, dist);
        camera.lookAt(target);
        camera.update();

        // 3. 环境光（单方向光，位置可调）
        environment = new Environment();
        light = new DirectionalLight();
        light.set(1.0f, 1.0f, 1.0f, 0f, 1f, 0f);
        environment.add(light);

        // 4. 输入
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                lastTouchX = screenX;
                lastTouchY = screenY;
                return true;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                float dx = screenX - lastTouchX;
                float dy = screenY - lastTouchY;
                lastTouchX = screenX;
                lastTouchY = screenY;

                // 左键：旋转（手抓模型感觉：鼠标左移模型左转，鼠标上移从上方看）
                if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
                    yaw -= dx * 0.5f;
                    pitch = clamp(pitch + dy * 0.5f, -89f, 89f);
                }
                // 右键：平移
                if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
                    float panSpeed = dist * 0.002f;
                    Vector3 right = camera.direction.cpy().crs(camera.up).nor();
                    Vector3 up = camera.up.cpy().nor();
                    target.add(right.scl(-dx * panSpeed));
                    target.add(up.scl(dy * panSpeed));
                }
                return true;
            }

            @Override
            public boolean scrolled(float amountX, float amountY) {
                dist *= (amountY > 0 ? 1.15f : 1f / 1.15f);
                dist = clamp(dist, 5f, 5000f);
                return true;
            }

            @Override
            public boolean keyDown(int keycode) {
                switch (keycode) {
                    case Input.Keys.F -> debugNormal = !debugNormal;
                    case Input.Keys.SPACE -> autoRotate = !autoRotate;
                    case Input.Keys.Q -> lightElevation += 5f;
                    case Input.Keys.E -> lightElevation -= 5f;
                    case Input.Keys.A -> lightAzimuth -= 5f;
                    case Input.Keys.D -> lightAzimuth += 5f;
                    case Input.Keys.R -> {
                        yaw = 30f;
                        pitch = 25f;
                        dist = 120f;
                        target.set(0, 0, 0);
                        lightElevation = 35f;
                        lightAzimuth = 0f;
                    }
                    default -> {
                    }
                }
                return true;
            }
        });

        // 5. UI
        uiBatch = new SpriteBatch();
        font = new BitmapFont();
        font.getData().setScale(1.5f);

        Gdx.app.log("ModelTestScene", "loaded " + MODEL_PATH + " verts=" + loaded.mesh.vertexCount()
                + " idx=" + loaded.mesh.indexCount());

        // 诊断：打印模型结构与材质信息
        Gdx.app.log("ModelTestScene", "model meshes=" + model.meshes.size
                + " meshParts=" + model.meshParts.size
                + " nodes=" + model.nodes.size
                + " materials=" + model.materials.size);
        for (com.badlogic.gdx.graphics.g3d.Attribute attr : model.materials.get(0)) {
            Gdx.app.log("ModelTestScene", "material attr: " + attr.getClass().getSimpleName());
        }
        for (com.badlogic.gdx.graphics.g3d.model.MeshPart part : model.meshParts) {
            Gdx.app.log("ModelTestScene", "meshPart id=" + part.id + " offset=" + part.offset
                    + " size=" + part.size + " primType=" + part.primitiveType
                    + " verts=" + part.mesh.getNumVertices() + " idx=" + part.mesh.getNumIndices());
            // 诊断：dump 前 3 个顶点（验证顶点数据解析是否正确）
            float[] v = new float[part.mesh.getVertexSize() / 4 * 3];
            part.mesh.getVertices(v);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < Math.min(v.length, 36); i++) {
                sb.append(v[i]).append(", ");
            }
            Gdx.app.log("ModelTestScene", "first verts: " + sb);
            // dump 前 6 个索引
            short[] idx = new short[part.mesh.getNumIndices()];
            part.mesh.getIndices(idx);
            Gdx.app.log("ModelTestScene", "first idx: " + idx[0] + "," + idx[1] + "," + idx[2]
                    + "," + idx[3] + "," + idx[4] + "," + idx[5]);
        }

        // 坐标轴渲染器
        shapeRenderer = new ShapeRenderer();
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();

        // 自动旋转
        if (autoRotate) {
            rotationAngle += dt * 30f;
        }

        // 更新相机（轨道环绕，保持水平：与游戏内部 WorldCamera 一致）
        float radYaw = (float) Math.toRadians(yaw);
        float radPitch = (float) Math.toRadians(pitch);
        float cx = target.x + dist * (float) Math.cos(radPitch) * (float) Math.sin(radYaw);
        float cy = target.y + dist * (float) Math.sin(radPitch);
        float cz = target.z + dist * (float) Math.cos(radPitch) * (float) Math.cos(radYaw);
        camera.position.set(cx, cy, cz);

        // 相机保持水平（无翻滚）：up 恒为世界 Y(0,1,0) 的正交基。
        // right = forward × up_world，up = right × forward。
        // 避免 camera.lookAt 的 up 投影算法在俯仰时产生翻滚。
        Vector3 forward = tmpForward.set(target).sub(camera.position).nor();
        Vector3 right = tmpRight.set(forward).crs(WORLD_UP).nor();
        Vector3 up = tmpUp.set(right).crs(forward);
        camera.direction.set(forward);
        camera.up.set(up);
        camera.update();

        // 更新光照方向（仰角 + 方位角，可调观察掠射角效果）
        float elRad = (float) Math.toRadians(lightElevation);
        float azRad = (float) Math.toRadians(lightAzimuth);
        float lx = (float) (Math.cos(elRad) * Math.sin(azRad));
        float ly = (float) Math.sin(elRad);
        float lz = (float) (Math.cos(elRad) * Math.cos(azRad));
        light.set(1f, 1f, 1f, lx, ly, lz);

        // 更新模型实例（自动旋转）
        instance.transform.setToRotation(Vector3.Y, rotationAngle);
        instance.transform.scale(MODEL_SCALE, MODEL_SCALE, MODEL_SCALE);

        // 渲染（必须先清颜色 + 深度缓冲，否则 ModelBatch 深度测试全失败导致模型不可见）
        Gdx.gl.glClearColor(0.08f, 0.08f, 0.12f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        ensureModelBatch();
        modelBatch.begin(camera);
        modelBatch.render(instance, environment);
        modelBatch.end();

        // 绘制 XYZ 坐标轴（游戏世界坐标系：X 红右 / Y 绿上 / Z 蓝前）
        drawAxes();

        // 诊断：每 60 帧打印相机与实例状态
        frameCount++;
        if (frameCount % 60 == 0) {
            Gdx.app.log("ModelTestScene", "cam pos=(" + camera.position.x + "," + camera.position.y + ","
                    + camera.position.z + ") dir=(" + camera.direction.x + "," + camera.direction.y + ","
                    + camera.direction.z + ") dist=" + dist + " instanceTrs=" + instance.transform);
        }

        // UI 信息
        drawInfo();
    }

    /** 绘制 XYZ 世界坐标轴（与游戏世界坐标系一致：X 右 / Y 上 / Z 前，正方向彩色，负方向淡色）。 */
    private void drawAxes() {
        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        // 正方向（彩色）
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.line(0, 0, 0, AXIS_LENGTH, 0, 0);
        shapeRenderer.setColor(Color.GREEN);
        shapeRenderer.line(0, 0, 0, 0, AXIS_LENGTH, 0);
        shapeRenderer.setColor(Color.BLUE);
        shapeRenderer.line(0, 0, 0, 0, 0, AXIS_LENGTH);
        // 负方向（淡色，帮助判断朝向）
        shapeRenderer.setColor(0.4f, 0f, 0f, 1f);
        shapeRenderer.line(0, 0, 0, -AXIS_LENGTH, 0, 0);
        shapeRenderer.setColor(0f, 0.4f, 0f, 1f);
        shapeRenderer.line(0, 0, 0, 0, -AXIS_LENGTH, 0);
        shapeRenderer.setColor(0f, 0f, 0.4f, 1f);
        shapeRenderer.line(0, 0, 0, 0, 0, -AXIS_LENGTH);
        shapeRenderer.end();
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();
    }

    @Override
    public void dispose() {
        if (modelBatch != null) {
            modelBatch.dispose();
        }
        model.dispose();
        shapeRenderer.dispose();
        uiBatch.dispose();
        font.dispose();
    }

    /** 法线可视化切换时重建 modelBatch（shader prefix 不同，需重新编译）。 */
    private void ensureModelBatch() {
        boolean needDebug = debugNormal;
        boolean needCreate = (modelBatch == null);
        if (needCreate || (debugNormal != lastDebugNormal)) {
            if (modelBatch != null) {
                modelBatch.dispose();
            }
            modelBatch = new ModelBatch(new DebugShipShaderProvider(needDebug));
            lastDebugNormal = debugNormal;
        }
    }

    /** 上一次构建 modelBatch 时的调试模式（用于检测切换）。 */
    private boolean lastDebugNormal;

    /** 帧计数（诊断日志用）。 */
    private int frameCount;

    /** 入口：创建 LWJGL3 窗口启动测试场景。 */
    public static void main(String[] args) {
        com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration config =
                new com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration();
        config.setTitle("ModelTestScene");
        config.setWindowedMode(1280, 720);
        config.setForegroundFPS(60);
        config.setIdleFPS(60);
        new com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application(new ModelTestScene(), config);
    }

    /** 绘制屏幕信息（FPS / 操作提示 / 当前状态）。 */
    private void drawInfo() {
        uiBatch.begin();
        String info = "FPS: " + Gdx.graphics.getFramesPerSecond()
                + "  [" + (debugNormal ? "NORMAL DEBUG" : "RENDER") + "]"
                + (autoRotate ? "  [AUTO-ROTATE]" : "")
                + "\nAxes: X=red  Y=green  Z=blue  (world: Y up, +Z forward)"
                + "\nLight elev: " + lightElevation + "  azim: " + lightAzimuth
                + "\n[F] normal debug  [Space] rotate  [Q/E] light elev  [A/D] light azim  [R] reset";
        font.setColor(Color.WHITE);
        font.draw(uiBatch, info, 10, Gdx.graphics.getHeight() - 10);
        uiBatch.end();
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    /**
     * 调试 ShaderProvider：舰船材质走 ShipShader（逐像素光照 + normal mapping），
     * 调试模式下追加 debugNormalFlag 启用法线可视化。
     */
    private static class DebugShipShaderProvider extends DefaultShaderProvider {

        private final boolean debugNormal;

        DebugShipShaderProvider(boolean debugNormal) {
            this.debugNormal = debugNormal;
        }

        @Override
        protected Shader createShader(Renderable renderable) {
            if (ShipShader.supports(renderable)) {
                String prefix = ShipShader.createPrefix(renderable, config);
                if (debugNormal) {
                    prefix += "#define debugNormalFlag\n";
                }
                try {
                    Shader shader = new ShipShader(renderable, config, prefix);
                    Gdx.app.log("ModelTestScene", "ship shader created"
                            + (debugNormal ? " (debugNormal)" : ""));
                    return shader;
                } catch (RuntimeException e) {
                    Gdx.app.error("ModelTestScene", "ShipShader create failed", e);
                    throw e;
                }
            }
            Shader shader = super.createShader(renderable);
            Gdx.app.log("ModelTestScene", "default shader created for " + renderable.meshPart.id);
            return shader;
        }
    }
}
