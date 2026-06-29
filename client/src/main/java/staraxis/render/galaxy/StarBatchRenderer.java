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
    private final Vector3 hitPos = new Vector3();

    private ModelInstance[] instances;
    private int instanceCount = 0;

    public StarBatchRenderer() {
        modelBatch = new ModelBatch();

        ModelBuilder builder = new ModelBuilder();
        starModel = builder.createSphere(
            STAR_RADIUS * 2, STAR_RADIUS * 2, STAR_RADIUS * 2,
            8, 6,
            new Material(),
            VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal
        );
    }

    public void render(RealTimeWorldState state, WorldCamera camera, long hoveredStarId) {
        java.util.List<EntitySnapshot> stars = filterStars(state);
        int needed = stars.size();

        ensureInstances(needed);
        instanceCount = needed;

        for (int i = 0; i < instanceCount; i++) {
            EntitySnapshot snap = stars.get(i);
            ModelInstance inst = instances[i];

            inst.transform.idt();
            inst.transform.translate(
                (float) snap.posWorldGU.x(),
                (float) snap.posWorldGU.y(),
                (float) snap.posWorldGU.z()
            );

            StarDetails details = (StarDetails) snap.details;
            float[] rgb;
            if (snap.entityId == hoveredStarId) {
                rgb = new float[]{1f, 1f, 1f};
            } else if (details != null) {
                rgb = TemperatureColor.temperatureToRgb(details.temperatureK);
            } else {
                rgb = new float[]{1f, 0.92f, 0.6f};
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
        java.util.List<EntitySnapshot> stars = filterStars(state);
        int needed = stars.size();
        ensureInstances(needed);
        instanceCount = needed;

        long hitId = -1;
        float bestDist = Float.MAX_VALUE;

        for (int i = 0; i < instanceCount; i++) {
            EntitySnapshot snap = stars.get(i);
            ModelInstance inst = instances[i];

            inst.transform.idt();
            inst.transform.translate(
                (float) snap.posWorldGU.x(),
                (float) snap.posWorldGU.y(),
                (float) snap.posWorldGU.z()
            );

            inst.calculateBoundingBox(bounds);
            bounds.mul(inst.transform);

            if (Intersector.intersectRayBounds(ray, bounds, hitPos)) {
                float dist = ray.origin.dst(hitPos);
                if (dist < bestDist) {
                    bestDist = dist;
                    hitId = snap.entityId;
                }
            }
        }

        return hitId;
    }

    private java.util.List<EntitySnapshot> filterStars(RealTimeWorldState state) {
        java.util.List<EntitySnapshot> result = new java.util.ArrayList<>();
        for (EntitySnapshot snap : state.getEntitySnapshotsView()) {
            if (snap != null && snap.entityType == EntityType.STAR) {
                result.add(snap);
            }
        }
        return result;
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
