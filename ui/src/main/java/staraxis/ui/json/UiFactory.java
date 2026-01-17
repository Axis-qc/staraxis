package staraxis.ui.json;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import staraxis.ui.Gui;

import java.util.Locale;

/**
 * 根据 ComponentNode 递归创建 Scene2D Actor。支持 cell 布局属性。
 */
public class UiFactory {

    private final Skin skin;
    private final Gui gui;

    public UiFactory(Gui gui) {
        this.gui = gui;
        this.skin = gui.get(Skin.class);
    }

    public Actor create(ComponentNode node) {
        Actor actor;
        switch (node.type.toLowerCase(Locale.ROOT)) {
            case "container":
                actor = buildTable(node);
                break;
            case "label":
                actor = new Label(gui.i18n(node.properties.getOrDefault("text", "").toString()), skin);
                applyLabel(node, (Label) actor);
                break;
            case "button":
                actor = buildButton(node);
                break;
            default:
                actor = new Group();
        }
        if (node.name != null) actor.setName(node.name);
        return actor;
    }

    private void applyLabel(ComponentNode node, Label label) {
        Object alignment = node.properties.get("alignment");
        if (alignment != null) {
            String a = alignment.toString().toLowerCase(Locale.ROOT);
            if (a.contains("center")) label.setAlignment(Align.center);
            else if (a.contains("left")) label.setAlignment(Align.left);
            else if (a.contains("right")) label.setAlignment(Align.right);
        }
    }

    private Table buildTable(ComponentNode node) {
        Table table = new Table();
        if (Boolean.TRUE.equals(node.properties.get("fillParent"))) {
            table.setFillParent(true);
        }
        Object pad = node.properties.get("pad");
        if (pad != null) {
            try { table.pad(Float.parseFloat(pad.toString())); } catch (Exception ignored) {}
        }
        for (ComponentNode child : node.children) {
            Actor childActor = create(child);
            Cell<?> cell = table.add(childActor);
            applyCell(child, cell);
            table.row();
        }
        return table;
    }

    private void applyCell(ComponentNode child, Cell<?> cell) {
        Object cellObj = child.properties.get("cell");
        if (!(cellObj instanceof java.util.Map)) return;
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> c = (java.util.Map<String, Object>) cellObj;

        if (c.containsKey("align")) {
            String a = c.get("align").toString().toLowerCase(Locale.ROOT);
            if (a.contains("center")) cell.center();
            if (a.contains("left")) cell.left();
            if (a.contains("right")) cell.right();
            if (a.contains("top")) cell.top();
            if (a.contains("bottom")) cell.bottom();
        }
        if (Boolean.TRUE.equals(c.get("expand"))) cell.expand();
        if (Boolean.TRUE.equals(c.get("grow"))) cell.grow();

        if (c.containsKey("colspan")) cell.colspan(Integer.parseInt(c.get("colspan").toString()));

        if (c.containsKey("pad")) cell.pad(Float.parseFloat(c.get("pad").toString()));
        if (c.containsKey("padBottom")) cell.padBottom(Float.parseFloat(c.get("padBottom").toString()));
        if (c.containsKey("padTop")) cell.padTop(Float.parseFloat(c.get("padTop").toString()));
        if (c.containsKey("padLeft")) cell.padLeft(Float.parseFloat(c.get("padLeft").toString()));
        if (c.containsKey("padRight")) cell.padRight(Float.parseFloat(c.get("padRight").toString()));

        if (c.containsKey("width")) cell.width(Float.parseFloat(c.get("width").toString()));
        if (c.containsKey("height")) cell.height(Float.parseFloat(c.get("height").toString()));
    }

    private Actor buildButton(ComponentNode node) {
        TextButton btn = new TextButton(gui.i18n(node.properties.getOrDefault("text", "").toString()), skin);
        Object action = node.properties.get("onClick");
        if (action != null) {
            final String actionId = action.toString();
            btn.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    // 必须返回 true 才能收到 touchUp
                    return true;
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    // 只在真正点击（按下并在按钮范围内抬起）时触发
                    if (btn.isOver()) {
                        gui.dispatchMainMenuAction(actionId);
                    }
                }
            });
        }
        return btn;
    }
}
