package staraxis.ui.widgets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;

public class StarfieldBackground {

    private static class Star {
        float x, y, speed, size, alpha;
    }

    private static final int LAYER1_COUNT = 200;
    private static final int LAYER2_COUNT = 100;
    private static final int LAYER3_COUNT = 40;

    private final ShapeRenderer sr;
    private final SpriteBatch batch;
    private final Texture background;
    private final Matrix4 projMatrix = new Matrix4();
    private final Matrix4 transformMatrix = new Matrix4();
    private final Star[] stars1;
    private final Star[] stars2;
    private final Star[] stars3;

    private float worldWidth;
    private float worldHeight;

    public StarfieldBackground(ShapeRenderer sr, String backgroundPath) {
        this.sr = sr;
        this.batch = new SpriteBatch();
        this.background = backgroundPath == null || backgroundPath.trim().isEmpty() ? null
                : new Texture(Gdx.files.internal(backgroundPath));
        if (this.background != null) {
            this.background.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            this.background.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        }
        this.worldWidth = Gdx.graphics.getWidth();
        this.worldHeight = Gdx.graphics.getHeight();
        stars1 = new Star[LAYER1_COUNT];
        stars2 = new Star[LAYER2_COUNT];
        stars3 = new Star[LAYER3_COUNT];
        initStars(stars1, 0.4f, 1.2f, 0.6f, 1.8f, 15f, 40f);
        initStars(stars2, 0.6f, 1.6f, 0.8f, 2.2f, 30f, 80f);
        initStars(stars3, 0.9f, 1.0f, 1.5f, 3.0f, 60f, 140f);
    }

    public void resize(int width, int height) {
        this.worldWidth = width;
        this.worldHeight = height;
        projMatrix.setToOrtho2D(0, 0, width, height);
    }

    public void init(int width, int height) {
        resize(width, height);
    }

    private void initStars(Star[] stars, float alphaMin, float alphaMax,
            float sizeMin, float sizeMax, float speedMin, float speedMax) {
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new Star();
            stars[i].x = MathUtils.random(worldWidth);
            stars[i].y = MathUtils.random(worldHeight);
            stars[i].speed = MathUtils.random(speedMin, speedMax);
            stars[i].size = MathUtils.random(sizeMin, sizeMax);
            stars[i].alpha = MathUtils.random(alphaMin, alphaMax);
        }
    }

    public void act(float delta) {
        updateLayer(stars1, delta);
        updateLayer(stars2, delta);
        updateLayer(stars3, delta);
    }

    private void updateLayer(Star[] stars, float delta) {
        for (Star s : stars) {
            s.y -= s.speed * delta;
            if (s.y < -s.size) {
                s.y = worldHeight + s.size;
                s.x = MathUtils.random(worldWidth);
            }
        }
    }

    public void render() {
        if (background != null) {
            batch.setProjectionMatrix(projMatrix);
            batch.setTransformMatrix(transformMatrix.idt());
            batch.begin();
            batch.draw(background, 0, 0, worldWidth, worldHeight, 0, 0,
                    worldWidth / background.getWidth(), worldHeight / background.getHeight());
            batch.end();
        }

        Gdx.gl.glEnable(GL20.GL_BLEND);
        sr.setProjectionMatrix(projMatrix);
        sr.setTransformMatrix(transformMatrix.idt());
        sr.begin(ShapeType.Filled);
        drawStars(stars1, Color.WHITE);
        drawStars(stars2, Color.WHITE);
        drawStars(stars3, Color.WHITE);
        sr.end();
    }

    private void drawStars(Star[] stars, Color baseColor) {
        for (Star s : stars) {
            sr.setColor(baseColor.r, baseColor.g, baseColor.b, s.alpha);
            sr.circle(s.x, s.y, s.size * 0.5f);
        }
    }

    public void dispose() {
        if (background != null) {
            background.dispose();
        }
        batch.dispose();
    }
}
