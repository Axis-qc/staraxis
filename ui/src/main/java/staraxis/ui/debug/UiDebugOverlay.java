package staraxis.ui.debug;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.Viewport;

public class UiDebugOverlay extends InputAdapter implements Disposable {

    private final Stage stage;
    private final Skin skin;

    private final Table root;
    private final Label label;

    private final ShapeRenderer shapeRenderer;

    private boolean enabled = false;

    private Actor lastHit;
    private final Vector2 tmp = new Vector2();

    public UiDebugOverlay(Stage stage, Skin skin) {
        this.stage = stage;
        this.skin = skin;

        this.root = new Table(skin);
        this.root.setFillParent(true);
        this.root.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        this.root.top().left();

        this.label = new Label("UI Debug", skin);
        this.label.setAlignment(Align.topLeft);

        this.root.add(label).pad(8).left().top();
        this.root.setVisible(false);

        this.shapeRenderer = new ShapeRenderer();

        stage.addActor(root);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void toggle() {
        setEnabled(!enabled);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        root.setVisible(enabled);
        if (enabled) {
            lastHit = null;
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.F10) {
            toggle();
            return true;
        }
        return false;
    }

    public void update() {
        if (!enabled) {
            return;
        }

        int sx = Gdx.input.getX();
        int sy = Gdx.input.getY();

        tmp.set(sx, sy);
        stage.screenToStageCoordinates(tmp);

        Actor hit = stage.hit(tmp.x, tmp.y, true);
        if (hit != null && hit.isDescendantOf(root)) {
            // overlay 本身不算
            hit = null;
        }

        lastHit = hit;

        StringBuilder sb = new StringBuilder(256);
        sb.append("UI DEBUG (F10 toggle)\n");
        sb.append("screen: ").append(sx).append(",").append(sy).append("\n");
        sb.append("stage:  ").append(fmt(tmp.x)).append(",").append(fmt(tmp.y)).append("\n");

        Viewport vp = stage.getViewport();
        if (vp != null) {
            sb.append("viewport world: ").append(fmt(vp.getWorldWidth())).append("x").append(fmt(vp.getWorldHeight()))
                    .append("\n");
        }

        if (hit == null) {
            sb.append("hit: (none)\n");
        } else {
            sb.append("hit: ").append(hit.getClass().getSimpleName());
            if (hit.getName() != null) {
                sb.append(" name=").append(hit.getName());
            }
            sb.append("\n");

            sb.append("actor local: x=").append(fmt(hit.getX())).append(" y=").append(fmt(hit.getY())).append(" w=")
                    .append(fmt(hit.getWidth())).append(" h=").append(fmt(hit.getHeight())).append("\n");

            Vector2 stagePos = new Vector2(0, 0);
            hit.localToStageCoordinates(stagePos);
            sb.append("actor stage: x=").append(fmt(stagePos.x)).append(" y=").append(fmt(stagePos.y)).append("\n");

            sb.append("visible=").append(hit.isVisible()).append(" touchable=").append(hit.getTouchable()).append("\n");
        }

        label.setText(sb.toString());
    }

    public void render() {
        if (!enabled) {
            return;
        }
        if (lastHit == null) {
            return;
        }

        // 以 actor 的 stage 坐标绘制边框（使用 ShapeRenderer，投影矩阵对齐 stage camera）
        Vector2 stagePos = new Vector2(0, 0);
        lastHit.localToStageCoordinates(stagePos);

        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.rect(stagePos.x, stagePos.y, lastHit.getWidth(), lastHit.getHeight());
        shapeRenderer.end();
    }

    private String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v);
    }

    @Override
    public void dispose() {
        try {
            shapeRenderer.dispose();
        } catch (Exception ignored) {
        }
        try {
            root.remove();
        } catch (Exception ignored) {
        }
    }
}
