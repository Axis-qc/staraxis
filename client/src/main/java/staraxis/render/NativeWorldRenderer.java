package staraxis.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

import staraxis.game.entity.EntityType;
import staraxis.game.state.RealTimeWorldState;
import staraxis.game.state.snapshot.EntitySnapshot;
import staraxis.game.world.Vec2d;

/**
 * NativeWorldRenderer
 *
 * 原生 OpenGL 世界渲染器：只读取 RealTimeWorldState 快照，绘制星区、天体和舰船占位图形。
 */
public class NativeWorldRenderer {

    private static final double GU_PER_RENDER_UNIT = 1_000_000.0;

    private final OrthographicCamera camera;
    private final Viewport viewport;
    private final ShapeRenderer shapes;
    private final Vector3 pointerWorld = new Vector3();

    public NativeWorldRenderer() {
        camera = new OrthographicCamera();
        viewport = new ScreenViewport(camera);
        shapes = new ShapeRenderer();
        camera.position.set(0f, 0f, 0f);
        camera.zoom = 1f;
        camera.update();
    }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
        camera.update();
    }

    public void render(RealTimeWorldState state) {
        Gdx.gl.glClearColor(0.015f, 0.018f, 0.03f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        updateCameraFromInput();

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        renderBackdrop();
        renderSectors(state);
        renderEntities(state);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        renderSectorLinks(state);
        renderCrosshair();
        shapes.end();
    }

    public void dispose() {
        shapes.dispose();
    }

    private void updateCameraFromInput() {
        float dt = Gdx.graphics.getDeltaTime();
        float speed = 600f * camera.zoom * dt;

        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.A)) {
            camera.position.x -= speed;
        }
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.D)) {
            camera.position.x += speed;
        }
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.W)) {
            camera.position.y += speed;
        }
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.S)) {
            camera.position.y -= speed;
        }
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.Q)) {
            camera.zoom = Math.min(12f, camera.zoom * (1f + dt));
        }
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.E)) {
            camera.zoom = Math.max(0.08f, camera.zoom * (1f - dt));
        }

        camera.update();
    }

    private void renderBackdrop() {
        shapes.setColor(0.03f, 0.04f, 0.075f, 1f);
        shapes.circle(0f, 0f, 8f, 32);
        shapes.setColor(0.09f, 0.12f, 0.22f, 0.5f);
        shapes.circle(0f, 0f, 3f, 24);
    }

    private void renderSectors(RealTimeWorldState state) {
        if (state == null) {
            return;
        }
        shapes.setColor(0.08f, 0.12f, 0.22f, 0.55f);
        for (Vec2d center : state.getSectorCentersWorldGUView().values()) {
            shapes.circle(toRender(center.x()), toRender(center.y()), 3f, 16);
        }
    }

    private void renderSectorLinks(RealTimeWorldState state) {
        if (state == null) {
            return;
        }
        shapes.setColor(0.12f, 0.18f, 0.34f, 0.24f);
        for (Vec2d center : state.getSectorCentersWorldGUView().values()) {
            float x = toRender(center.x());
            float y = toRender(center.y());
            shapes.circle(x, y, 28f, 6);
        }
    }

    private void renderEntities(RealTimeWorldState state) {
        if (state == null) {
            return;
        }
        for (EntitySnapshot snapshot : state.getEntitySnapshotsView()) {
            if (snapshot == null || snapshot.posWorldGU == null || snapshot.entityType == null) {
                continue;
            }
            renderEntity(snapshot);
        }
    }

    private void renderEntity(EntitySnapshot snapshot) {
        float x = toRender(snapshot.posWorldGU.x());
        float y = toRender(snapshot.posWorldGU.y());
        if (snapshot.entityType == EntityType.STAR) {
            shapes.setColor(1f, 0.78f, 0.28f, 0.95f);
            shapes.circle(x, y, 8f, 24);
            shapes.setColor(1f, 0.55f, 0.18f, 0.28f);
            shapes.circle(x, y, 18f, 32);
            return;
        }
        if (snapshot.entityType == EntityType.PLANET) {
            shapes.setColor(0.2f, 0.55f, 1f, 0.9f);
            shapes.circle(x + entityOffset(snapshot.entityId, 24f), y + entityOffset(snapshot.entityId / 7L, 16f), 4f, 16);
            return;
        }
        if (snapshot.entityType == EntityType.SHIP) {
            shapes.setColor(Color.CYAN);
            shapes.triangle(x, y + 7f, x - 5f, y - 5f, x + 5f, y - 5f);
            return;
        }
        if (snapshot.entityType == EntityType.SYSTEM_BARYCENTER) {
            shapes.setColor(0.75f, 0.85f, 1f, 0.35f);
            shapes.circle(x, y, 2f, 12);
        }
    }

    private void renderCrosshair() {
        viewport.unproject(pointerWorld.set(Gdx.input.getX(), Gdx.input.getY(), 0f));
        shapes.setColor(0.5f, 0.65f, 1f, 0.22f);
        shapes.line(pointerWorld.x - 8f, pointerWorld.y, pointerWorld.x + 8f, pointerWorld.y);
        shapes.line(pointerWorld.x, pointerWorld.y - 8f, pointerWorld.x, pointerWorld.y + 8f);
    }

    private float entityOffset(long id, float scale) {
        return (float) (((id * 1103515245L + 12345L) & 0xffffL) / 65535.0 * scale - scale * 0.5);
    }

    private float toRender(double gu) {
        return (float) (gu / GU_PER_RENDER_UNIT);
    }
}
