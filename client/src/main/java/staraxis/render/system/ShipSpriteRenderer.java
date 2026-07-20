package staraxis.render.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.render.WorldCamera;
import staraxis.sprite.SpriteRegistry;

import java.util.List;

/**
 * ShipSpriteRenderer（舰船贴图精灵渲染器）。
 *
 * 从 SpriteRegistry 按 spriteKey 获取舰船纹理，
 * 将 3D 世界坐标投影到屏幕坐标，用 SpriteBatch 绘制 2D 贴图，
 * 替代原有的 3D 正方体模型渲染 + 2D 圆标叠加。
 *
 * 纹理来源：SpriteRegistry（数据驱动，由 assets/sprites/sprite_registry.json 配置）
 * P0 阶段所有舰船统一使用默认贴图（registry.getDefault()）。
 * 后续 Phase 5 造船系统上线后按舰船类型传入不同 spriteKey 切换贴图。
 */
public class ShipSpriteRenderer {

    /** 屏幕基准大小（像素），相机距离为 600GU 时保持此大小 */
    private static final float BASE_SIZE_PX = 48f;

    /** 贴图缩放范围 */
    private static final float MIN_SCALE = 0.4f;
    private static final float MAX_SCALE = 2.5f;

    /** 基准距离（GU）：在此距离上贴图为 BASE_SIZE_PX */
    private static final float REF_DIST = 600f;

    /** 屏幕拾取范围：贴图大小的倍数（>1 = 更容易点到）。 */
    private static final float PICK_RADIUS_MULT = 1.8f;

    /** 纹理注册器，提供 spriteKey → TextureRegion 映射。 */
    private final SpriteRegistry registry;

    private final SpriteBatch spriteBatch;
    private final Vector3 tmpScreenPos = new Vector3();

    /** 每帧缓存的舰船屏幕坐标信息，用于 2D 拾取。 */
    private final java.util.ArrayList<ShipScreenInfo> screenInfos = new java.util.ArrayList<>();

    private List<EntitySnapshot> currentShips = List.of();
    private long highlightShipId = -1L;

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
     * @param registry 纹理注册器（由外部创建并注入）
     */
    public ShipSpriteRenderer(SpriteRegistry registry) {
        this.registry = registry;
        this.spriteBatch = new SpriteBatch();
    }

    /** 设置当前帧要渲染的舰船列表。 */
    public void setShips(List<EntitySnapshot> ships) {
        this.currentShips = ships != null ? ships : List.of();
    }

    /** 设置高亮选中的舰船 ID（-1 = 无）。 */
    public void setHighlightShip(long shipId) {
        this.highlightShipId = shipId;
    }

    /**
     * 渲染所有舰船贴图。
     *
     * 每帧调用，将舰船 3D 坐标投影到屏幕，绘制 2D 贴图。
     * 贴图大小根据相机距离自适应缩放。
     */
    public void render(WorldCamera camera) {
        if (currentShips.isEmpty()) return;

        screenInfos.clear();

        float gfxW = Gdx.graphics.getWidth();
        float gfxH = Gdx.graphics.getHeight();
        Vector3 camPos = camera.camera.position;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        spriteBatch.setProjectionMatrix(new Matrix4().setToOrtho(0f, gfxW, gfxH, 0f, -1f, 1f));
        spriteBatch.begin();

        for (EntitySnapshot snap : currentShips) {
            if (snap.posWorldGU == null) continue;

            float px = (float) snap.posWorldGU.x();
            float py = (float) snap.posWorldGU.y();
            float pz = (float) snap.posWorldGU.z();

            // 3D 坐标投影到屏幕
            tmpScreenPos.set(px, py, pz);
            camera.camera.project(tmpScreenPos);
            if (tmpScreenPos.z < 0f || tmpScreenPos.z > 1f) continue;

            float screenX = tmpScreenPos.x;
            float screenY = gfxH - tmpScreenPos.y;

            // 距离自适应缩放
            double dist = Math.sqrt(
                    (camPos.x - px) * (camPos.x - px) +
                    (camPos.y - py) * (camPos.y - py) +
                    (camPos.z - pz) * (camPos.z - pz));
            float scale = (float) Math.max(MIN_SCALE, Math.min(MAX_SCALE, REF_DIST / dist));
            float size = BASE_SIZE_PX * scale;

            // 缓存屏幕信息用于 2D 拾取（渲染前就存，拾取不依赖颜色）
            screenInfos.add(new ShipScreenInfo(snap.entityId, screenX, screenY, size / 2f * PICK_RADIUS_MULT));

            // 颜色：选中亮黄，默认浅蓝
            if (snap.entityId == highlightShipId) {
                spriteBatch.setColor(1.0f, 0.9f, 0.2f, 1f);
            } else {
                spriteBatch.setColor(0.6f, 0.8f, 1.0f, 1f);
            }

            // P0 阶段：所有舰船统一用默认贴图。后续按 shipSizeId 传入不同 spriteKey
            TextureRegion region = registry.getDefault();
            if (region == null) continue;

            spriteBatch.draw(region,
                    screenX - size / 2f,
                    screenY - size / 2f,
                    size, size);
        }

        spriteBatch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
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

    /**
     * 释放渲染器持有的资源。
     * 注意：纹理生命周期归 SpriteRegistry 管理，
     * 此处只释放 spriteBatch（纹理由 ClientGame.dispose() → registry.dispose() 统一释放）。
     */
    public void dispose() {
        spriteBatch.dispose();
    }
}
