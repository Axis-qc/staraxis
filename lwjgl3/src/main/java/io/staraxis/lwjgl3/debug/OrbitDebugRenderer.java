package io.staraxis.lwjgl3.debug;

import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.staraxis.game.shared.model.Vector2;
import com.staraxis.game.shared.world.stellar.orbit.OrbitPath;

public class OrbitDebugRenderer {

    private final ShapeRenderer shapeRenderer;

    public OrbitDebugRenderer() {
        this.shapeRenderer = new ShapeRenderer();
    }

    public void setProjectionMatrix(com.badlogic.gdx.math.Matrix4 matrix) {
        shapeRenderer.setProjectionMatrix(matrix);
    }

    public void render(List<OrbitPathRenderItem> items, float visualScale) {
        if (items == null || items.isEmpty()) {
            return;
        }

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(new Color(0.2f, 0.9f, 0.6f, 1.0f));

        for (OrbitPathRenderItem item : items) {
            if (item == null || item.orbitPath == null) {
                continue;
            }
            drawOrbitPath(item.originX, item.originY, item.orbitPath, visualScale);
        }

        shapeRenderer.end();
    }

    private void drawOrbitPath(float originX, float originY, OrbitPath path, float visualScale) {
        List<Vector2> samples = path.getSamples();
        if (samples == null || samples.size() < 2) {
            return;
        }

        for (int i = 0; i < samples.size() - 1; i++) {
            Vector2 a = samples.get(i);
            Vector2 b = samples.get(i + 1);
            shapeRenderer.line(
                    originX + a.x * visualScale,
                    originY + a.y * visualScale,
                    originX + b.x * visualScale,
                    originY + b.y * visualScale);
        }
    }

    public void dispose() {
        shapeRenderer.dispose();
    }

    public static final class OrbitPathRenderItem {

        public final float originX;
        public final float originY;
        public final OrbitPath orbitPath;

        public OrbitPathRenderItem(float originX, float originY, OrbitPath orbitPath) {
            this.originX = originX;
            this.originY = originY;
            this.orbitPath = orbitPath;
        }
    }
}
