package staraxis.render.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

import staraxis.game_asset.data.MaterialData;
import staraxis.game_asset.loader.GltfLoader;
import staraxis.game_asset.loader.LoadedModel;
import staraxis.render.adapter.MeshDataToModel;
import staraxis.render.WorldCamera;
import staraxis.render.model.ShipModelDef;
import staraxis.render.model.ShipModelRegistry;
import staraxis.render.shader.PlayerColorAttribute;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.state.snapshot.EntitySnapshot.ShipDetails;

import java.util.List;

/**
 * StarEaterShipRenderer（噬星者 3D 舰船模型渲染器，测试用）。
 *
 * 用 game_asset 模块 GltfLoader 加载 Stellaris 导出的 glTF 模型（star_eater），
 * 以 3D ModelInstance 形式替换原 2D 贴图舰船渲染，验证外部模型加载链路。
 *
 * 拾取仍沿用 2D 屏幕空间方案：渲染时同步投影缓存屏幕包围区。
 * 模型缩放：由 model_registry.json 中对应模型定义的 scale 驱动。
 *
 * 材质扩展（Stellaris 同款玩家颜色自发光）：
 * - normal 贴图蓝色通道作为发光掩码（glTF 原生解析，无需手动挂载）
 * - 每艘舰船按归属国家颜色设置 PlayerColorAttribute，由 ShipShader 混合
 */
public class StarEaterShipRenderer {

    /** 模型配置表 key（对应 assets/ship/model/model_registry.json 的 models 键）。 */
    private static final String MODEL_KEY = "star_eater";

    /** 屏幕拾取范围：模型投影大小的倍数（>1 = 更容易点到）。 */
    private static final float PICK_RADIUS_MULT = 1.8f;

    /** 屏幕基准大小（像素），相机距离为 600GU 时保持此大小 */
    private static final float BASE_SIZE_PX = 48f;

    /** 贴图缩放范围 */
    private static final float MIN_SCALE = 0.4f;
    private static final float MAX_SCALE = 2.5f;

    /** 基准距离（GU）：在此距离上贴图为 BASE_SIZE_PX */
    private static final float REF_DIST = 600f;

    /**
     * 游戏世界舰首前方轴（+Z）。
     * 注意：Blender 导出 glTF 默认执行坐标变换（Forward: -Z, Up: Y），
     * 导出的 glTF 文件内已是"舰首 +Z、上 +Y"，与游戏坐标系一致，无需基础变换。
     */
    private static final Vector3 WORLD_FORWARD = new Vector3(0f, 0f, 1f);

    /** 速度向量长度阈值（GU/秒），低于此值视为静止。 */
    private static final float VELOCITY_EPSILON = 0.001f;

    /** 转向插值系数（0~1，越大转向越快）。每帧向目标朝向 slerp，避免朝向突变造成视觉扭曲。 */
    private static final float TURN_LERP = 0.15f;

    /** 加载的舰船模型（单例，全部舰船共享）。 */
    private final Model model;

    /** 模型缩放系数（默认 1.0 = 原始大小，不缩放）。 */
    private final float modelScale;

    /** 对象池：舰船 ModelInstance（懒增长）。 */
    private final java.util.ArrayList<ModelInstance> shipInstances = new java.util.ArrayList<>();

    /** 每帧缓存的舰船屏幕坐标信息，用于 2D 拾取。 */
    private final java.util.ArrayList<ShipScreenInfo> screenInfos = new java.util.ArrayList<>();

    /** 当前帧待渲染的舰船快照列表。 */
    private List<EntitySnapshot> currentShips = List.of();

    /** 当前选中的舰船 ID（高亮预留）。 */
    private long highlightShipId = -1L;

    /** 舰船 ID -> 当前平滑朝向四元数（持续插值，静止时保持）。 */
    private final java.util.HashMap<Long, Quaternion> shipFacing = new java.util.HashMap<>();

    /** 临时四元数，避免每帧分配。 */
    private final Quaternion tmpQuat = new Quaternion();
    private final Quaternion targetQuat = new Quaternion();

    /** 临时方向向量，避免每帧分配。 */
    private final Vector3 tmpDir = new Vector3();

    /**
     * 光照方向更新器：渲染每艘舰船前调用，用于按舰船位置设置方向光。
     * 由外部（SystemViewRenderer）提供，实现"光源来自最近恒星"语义。
     */
    public interface LightDirectionUpdater {
        /** 更新方向光，使其从恒星指向目标位置 (px, py, pz)。 */
        void update(float px, float py, float pz);
    }

    /** 临时向量，避免每帧分配。 */
    private final Vector3 tmpScreenPos = new Vector3();

    /**
     * 当前帧已更新 transform 的舰船模型实例列表。
     * 供模型法向调试可视化使用（法向渲染发生在舰船渲染之后）。
     */
    public java.util.List<ModelInstance> getInstances() {
        return shipInstances;
    }

    private static class ShipScreenInfo {
        final long entityId;
        final float centerX;
        final float centerY;
        final float halfSize; // 拾取半边长

        ShipScreenInfo(long id, float cx, float cy, float hs) {
            entityId = id;
            centerX = cx;
            centerY = cy;
            halfSize = hs;
        }
    }

    /**
     * 加载 glTF 模型。
     * 模型路径与缩放系数从模型渲染配置表（model_registry.json）获取，
     * 避免资源路径与缩放散落在渲染代码中。
     * 加载失败时抛出异常（模型资产为测试硬依赖，缺失即中断）。
     */
    public StarEaterShipRenderer() {
        ShipModelDef def = new ShipModelRegistry().get(MODEL_KEY);
        if (def == null || def.path == null || def.path.isBlank()) {
            throw new RuntimeException("模型渲染配置表缺少 modelKey=" + MODEL_KEY);
        }
        String modelPath = def.path;
        // glTF 所在目录（含末尾斜杠），供拼接 .bin 与贴图相对路径
        String basePath = modelPath.substring(0, modelPath.lastIndexOf('/') + 1);

        LoadedModel loaded = GltfLoader.load(
                Gdx.files.internal(modelPath).read(),
                Gdx.files.internal(basePath + binFileName(modelPath)).readBytes(),
                basePath);

        MaterialData materialData = loaded.material;
        model = MeshDataToModel.convert(loaded.mesh, MeshDataToModel.convertMaterial(materialData));
        modelScale = def.scale;

        Gdx.app.log("StarEaterShipRenderer", "loaded " + modelPath + " scale=" + modelScale);
    }

    /**
     * 从 glTF 文件名推导 .bin 二进制文件名（glTF 缓冲名通常与模型同名）。
     *
     * @param gltfPath glTF 文件路径（相对 assets 根目录）
     * @return .bin 文件名（不含目录前缀）
     */
    private static String binFileName(String gltfPath) {
        String name = gltfPath.substring(gltfPath.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return (dot >= 0 ? name.substring(0, dot) : name) + ".bin";
    }

    /** 设置当前帧要渲染的舰船列表。 */
    public void setShips(List<EntitySnapshot> ships) {
        this.currentShips = ships != null ? ships : List.of();
    }

    /** 设置高亮选中的舰船 ID（-1 = 无）。 */
    public void setHighlightShip(long shipId) {
        this.highlightShipId = shipId;
    }

    /** 确保舰船实例池足够大。 */
    private void ensureInstances(int needed) {
        while (shipInstances.size() < needed) {
            // ModelInstance 构造时已自动深拷贝材质（node.part.material 与
            // instance.materials[0] 同一对象，渲染读的是 node.part.material），
            // 直接在该材质上设置玩家颜色即可，禁止 clear+add 替换（会破坏引用导致渲染读旧材质）
            ModelInstance instance = new ModelInstance(model, 0, 0, 0);
            // 默认玩家颜色（白色）：材质需先含 PlayerColorAttribute，
            // ShipShader 创建时才识别 playerColorFlag；实际颜色渲染时按国家覆盖
            instance.materials.get(0).set(new PlayerColorAttribute(1f, 1f, 1f));
            shipInstances.add(instance);
        }
    }

    /**
     * 渲染所有舰船 3D 模型。
     * 应在 modelBatch begin/end 之外调用（内部自行管理批次），
     * 且需在深度测试开启期间调用以获得正确遮挡。
     *
     * @param lightUpdater 每艘舰船渲染前的光照方向更新器（可空）
     */
    public void render(ModelBatch modelBatch, Environment environment, WorldCamera camera,
                       LightDirectionUpdater lightUpdater) {
        if (currentShips.isEmpty()) return;

        screenInfos.clear();

        float gfxW = Gdx.graphics.getWidth();
        float gfxH = Gdx.graphics.getHeight();
        Vector3 camPos = camera.camera.position;

        ensureInstances(currentShips.size());

        modelBatch.begin(camera.camera);
        for (int i = 0; i < currentShips.size(); i++) {
            EntitySnapshot snap = currentShips.get(i);
            if (snap.posWorldGU == null) continue;

            float px = (float) snap.posWorldGU.x();
            float py = (float) snap.posWorldGU.y();
            float pz = (float) snap.posWorldGU.z();

            // 按舰船位置更新方向光（光源来自最近恒星）
            if (lightUpdater != null) {
                lightUpdater.update(px, py, pz);
            }

            // 朝向：只做"舰首(+Z)对齐移动方向"这一个旋转，模型姿态本身固定（不翻转、不拉伸）
            // 优先使用 game 权威下发的 facing（单位向量），缺失时回退速度方向
            boolean hasTarget = false;
            if (snap.details instanceof ShipDetails shipDetails) {
                tmpDir.set(0, 0, 0);
                if (shipDetails.facing != null) {
                    tmpDir.set((float) shipDetails.facing.x(),
                            (float) shipDetails.facing.y(),
                            (float) shipDetails.facing.z());
                } else if (shipDetails.velocity != null) {
                    tmpDir.set((float) shipDetails.velocity.x(),
                            (float) shipDetails.velocity.y(),
                            (float) shipDetails.velocity.z());
                }
                if (tmpDir.len() > VELOCITY_EPSILON) {
                    tmpDir.nor();
                    hasTarget = true;
                }
            }

            // 计算目标朝向四元数；静止无目标时保持当前朝向
            if (hasTarget) {
                // setFromCross 退化保护：目标方向与 +Z 同向/反向时叉积趋零无法求轴
                float dot = WORLD_FORWARD.dot(tmpDir);
                if (dot > 0.9999f) {
                    // 同向：无旋转
                    targetQuat.idt();
                } else if (dot < -0.9999f) {
                    // 反向：绕 Y 轴转 180 度
                    targetQuat.setFromAxisRad(0f, 1f, 0f, (float) Math.PI);
                } else {
                    targetQuat.setFromCross(WORLD_FORWARD, tmpDir);
                }
            }
            Quaternion facing = hasTarget ? targetQuat : shipFacing.get(snap.entityId);

            // 平滑转向：已有朝向时向目标 slerp，避免每帧突变
            // 注意：slerp 不保证归一化，浮点误差逐帧累积会导致旋转矩阵非正交（模型拉伸），故 nor()
            if (hasTarget) {
                Quaternion current = shipFacing.get(snap.entityId);
                if (current != null) {
                    tmpQuat.set(current).slerp(targetQuat, TURN_LERP).nor();
                    facing = tmpQuat;
                }
                shipFacing.put(snap.entityId, new Quaternion(facing).nor());
            }

            ModelInstance instance = shipInstances.get(i);

            // 玩家颜色自发光：按快照下发的国家颜色（0xRRGGBB）设置材质属性
            // 无归属（-1）时回退白色
            PlayerColorAttribute playerAttr = (PlayerColorAttribute)
                    instance.materials.get(0).get(PlayerColorAttribute.Type);
            if (playerAttr != null) {
                int rgb = -1;
                if (snap.details instanceof ShipDetails sd) {
                    rgb = sd.nationColorRgb;
                }
                if (rgb < 0) {
                    playerAttr.color.set(1f, 1f, 1f, 1f);
                } else {
                    playerAttr.color.set(((rgb >> 16) & 0xFF) / 255f,
                            ((rgb >> 8) & 0xFF) / 255f,
                            (rgb & 0xFF) / 255f, 1f);
                }
            }

            instance.transform.idt();
            instance.transform.translate(px, py, pz);
            // 注意：scl 必须放在 rotate 之前——Matrix4.scl() 只乘对角线元素，
            // 若先 rotate 再 scl 会破坏旋转矩阵正交性，造成模型拉伸（正方形变长方形）
            instance.transform.scl(modelScale);
            if (facing != null) {
                instance.transform.rotate(facing);
            }

            modelBatch.render(instance, environment);

            // ModelBatch 是延迟提交的；下一艘舰船会更新同一个 Environment 的方向光。
            // 在更新共享光照环境前立即刷新，避免整批舰船最终使用最后一艘的光照方向。
            if (lightUpdater != null) {
                modelBatch.flush();
            }

            // 3D 坐标投影到屏幕，缓存拾取信息（渲染前就存，拾取不依赖颜色）
            tmpScreenPos.set(px, py, pz);
            camera.camera.project(tmpScreenPos);
            if (tmpScreenPos.z < 0f || tmpScreenPos.z > 1f) continue;

            float screenX = tmpScreenPos.x;
            float screenY = gfxH - tmpScreenPos.y;

            // 距离自适应缩放（与旧 2D 方案一致，保证拾取手感）
            double dist = Math.sqrt(
                    (camPos.x - px) * (camPos.x - px) +
                            (camPos.y - py) * (camPos.y - py) +
                            (camPos.z - pz) * (camPos.z - pz));
            float scale = (float) Math.max(MIN_SCALE, Math.min(MAX_SCALE, REF_DIST / dist));
            float size = BASE_SIZE_PX * scale;

            screenInfos.add(new ShipScreenInfo(snap.entityId, screenX, screenY, size / 2f * PICK_RADIUS_MULT));
        }
        modelBatch.end();
    }

    /**
     * 2D 屏幕空间拾取。根据点击坐标检测命中的舰船。
     *
     * @param screenX 鼠标屏幕 X
     * @param screenY 鼠标屏幕 Y（Gdx 坐标系，原点左上角）
     * @return 命中的舰船 entityId，无命中返回 -1
     */
    public long pick(int screenX, int screenY) {
        long bestId = -1;
        // 从后往前遍历，上层舰船优先
        for (int i = screenInfos.size() - 1; i >= 0; i--) {
            ShipScreenInfo info = screenInfos.get(i);
            float dx = screenX - info.centerX;
            float dy = screenY - info.centerY;
            if (Math.abs(dx) <= info.halfSize && Math.abs(dy) <= info.halfSize) {
                bestId = info.entityId;
                break;
            }
        }
        return bestId;
    }

    /** 释放渲染器持有的资源（模型 + 内部实例）。 */
    public void dispose() {
        model.dispose();
        shipInstances.clear();
        shipFacing.clear();
    }
}
