package staraxis.render.galaxy;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Intersector;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.math.collision.Ray;

import staraxis.game.entity.EntityType;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.state.snapshot.EntitySnapshot.StarDetails;
import staraxis.render.WorldCamera;
import staraxis.render.util.TemperatureColor;

/**
 * StarBatchRenderer（恒星批量渲染器）。
 *
 * 从 RealTimeWorldState 中读取所有 STAR 类型的实体快照，按光谱温度着色，
 * 使用固定大小球体模型批量渲染。
 */
public class StarBatchRenderer {

    private static final float STAR_RADIUS = 40f;

    private final ModelBatch modelBatch;
    private final Model starModel;
    private final BoundingBox bounds = new BoundingBox();

    private ModelInstance[] instances;
    private int instanceCount = 0;

    /** 缓存的当前帧恒星列表（由 pick() 设置，render() 消费复用），避免重复过滤和变换 */
    private final java.util.List<EntitySnapshot> cachedStars = new java.util.ArrayList<>();
    private boolean cacheValid = false;

    public StarBatchRenderer() {
        modelBatch = new ModelBatch();

        ModelBuilder builder = new ModelBuilder();
        starModel = builder.createSphere(
                STAR_RADIUS * 2, STAR_RADIUS * 2, STAR_RADIUS * 2,
                8, 6,
                new Material(),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    }

    /** 准备当前帧的实例数据：过滤、扩容、计算位置变换。供 pick() 和 render() 复用 */
    private void prepareStars(RealTimeWorldState state) {
        filterStars(state, cachedStars);
        int needed = cachedStars.size();
        ensureInstances(needed);
        instanceCount = needed;
        for (int i = 0; i < instanceCount; i++) {
            EntitySnapshot snap = cachedStars.get(i);
            ModelInstance inst = instances[i];
            inst.transform.idt();
            inst.transform.translate(
                    (float) snap.posWorldGU.x(),
                    (float) snap.posWorldGU.y(),
                    (float) snap.posWorldGU.z());
        }
        cacheValid = true;
    }

    public void render(RealTimeWorldState state, WorldCamera camera, long hoveredStarId) {
        // 如果 pick() 已经准备了缓存，直接复用；否则自行准备
        if (!cacheValid) {
            prepareStars(state);
        }
        cacheValid = false; // 消费后重置，下帧必须由 pick() 重新准备

        for (int i = 0; i < instanceCount; i++) {
            EntitySnapshot snap = cachedStars.get(i);
            ModelInstance inst = instances[i];

            StarDetails details = (StarDetails) snap.details;
            float[] rgb;
            if (snap.entityId == hoveredStarId) {
                rgb = new float[] { 1f, 1f, 1f };
            } else if (details != null) {
                rgb = TemperatureColor.temperatureToRgb(details.temperatureK);
            } else {
                rgb = new float[] { 1f, 0.92f, 0.6f };
            }
            inst.materials.get(0).set(ColorAttribute.createDiffuse(rgb[0], rgb[1], rgb[2], 1f));
        }

        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
        modelBatch.begin(camera.camera);
        for (int i = 0; i < instanceCount; i++) {
            modelBatch.render(instances[i]);
        }
        modelBatch.end();
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
    }

    public long pick(Ray ray, RealTimeWorldState state) {
        // pick 总是先于 render 被调用，所以负责准备实例数据
        prepareStars(state);

        Vector3 hitPos = new Vector3();
        long hitId = -1;
        float bestDist = Float.MAX_VALUE;

        for (int i = 0; i < instanceCount; i++) {
            ModelInstance inst = instances[i];

            inst.calculateBoundingBox(bounds);
            bounds.mul(inst.transform);

            if (Intersector.intersectRayBounds(ray, bounds, hitPos)) {
                float dist = ray.origin.dst(hitPos);
                if (dist < bestDist) {
                    bestDist = dist;
                    hitId = cachedStars.get(i).entityId;
                }
            }
        }

        return hitId;
    }

    private void filterStars(RealTimeWorldState state, java.util.List<EntitySnapshot> out) {
        out.clear();
        for (EntitySnapshot snap : state.getEntitySnapshotsView()) {
            if (snap != null && snap.entityType == EntityType.STAR) {
                out.add(snap);
            }
        }
    }

    private void ensureInstances(int needed) {
        if (instances == null || instances.length < needed) {
            instances = new ModelInstance[needed];
            for (int i = 0; i < needed; i++) {
                instances[i] = new ModelInstance(starModel);
            }
        }
    }

    public void dispose() {
        modelBatch.dispose();
        starModel.dispose();
    }
}
