package staraxis.render.system;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.render.WorldCamera;

import java.util.List;

/**
 * ShipSpriteRenderer（舰船贴图精灵渲染器）。
 *
 * 使用 simpleSpace_tilesheet 中的舰船贴图在 System View 中渲染舰船。
 * 将 3D 世界坐标投影到屏幕坐标，用 SpriteBatch 绘制 2D 贴图，
 * 替代原有的 3D 正方体模型渲染 + 2D 圆标叠加。
 *
 * 贴图来源：assets/ui/ship/simpleSpace_tilesheet@2.png
 * 当前使用：第3行第4列（重甲主力舰风格）
 */
public class ShipSpriteRenderer {

    private static final String TILESHEET_PATH = "ui/ship/simpleSpace_tilesheet@2.png";
    private static final int TILE_SIZE = 128;
    private static final int COLS = 8;

    /** 第3行第4列（0-indexed: 行2列3） */
    private static final int TILE_ROW = 2;
    private static final int TILE_COL = 3;

    /** 屏幕基准大小（像素），相机距离为 600GU 时保持此大小 */
    private static final float BASE_SIZE_PX = 48f;

    /** 贴图缩放范围 */
    private static final float MIN_SCALE = 0.4f;
    private static final float MAX_SCALE = 2.5f;

    /** 基准距离（GU）：在此距离上贴图为 BASE_SIZE_PX */
    private static final float REF_DIST = 600f;

    private final Texture tilesheet;
    private final TextureRegion shipRegion;
    private final SpriteBatch spriteBatch;
    private final Vector3 tmpScreenPos = new Vector3();

    /** 每帧缓存的舰船屏幕坐标信息，用于 2D 拾取。 */
    private final java.util.ArrayList<ShipScreenInfo> screenInfos = new java.util.ArrayList<>();

    private List<EntitySnapshot> currentShips = List.of();
    private long highlightShipId = -1L;

    /** 屏幕拾取范围：贴图大小的倍数（>1 = 更容易点到）。 */
    private static final float PICK_RADIUS_MULT = 1.8f;

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

    public ShipSpriteRenderer() {
        tilesheet = new Texture(Gdx.files.internal(TILESHEET_PATH));
        shipRegion = new TextureRegion(tilesheet,
                TILE_COL * TILE_SIZE,
                TILE_ROW * TILE_SIZE,
                TILE_SIZE, TILE_SIZE);
        spriteBatch = new SpriteBatch();
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

            spriteBatch.draw(shipRegion,
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

    public void dispose() {
        tilesheet.dispose();
        spriteBatch.dispose();
    }
}
