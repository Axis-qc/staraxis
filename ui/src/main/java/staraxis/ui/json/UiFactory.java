package staraxis.ui.json;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import staraxis.ui.Gui;

import java.util.Locale;

public class UiFactory {

    private final Skin skin;
    private final Gui gui;

    public UiFactory(Gui gui) {
        this.gui = gui;
        this.skin = gui.get(Skin.class);
    }

    public Actor create(ComponentNode node) {
        Actor actor;
        switch (safeLower(node.type)) {
            case "container":
                actor = buildTable(node);
                break;
            case "stack":
                actor = buildStack(node);
                break;
            case "scroll":
                actor = buildScroll(node);
                break;
            case "window":
                actor = buildWindow(node);
                break;
            case "label":
                actor = buildLabel(node);
                break;
            case "button":
                actor = buildButton(node);
                break;
            case "image":
                actor = buildImage(node);
                break;
            case "slider":
                actor = buildSlider(node);
                break;
            case "progressbar":
                actor = buildProgressBar(node);
                break;
            case "textfield":
                actor = buildTextField(node);
                break;
            default:
                actor = new Group();
        }

        applyCommonActorProps(actor, node);
        return actor;
    }

    private String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private void applyCommonActorProps(Actor actor, ComponentNode node) {
        if (node.name != null)
            actor.setName(node.name);

        Object visible = node.properties.get("visible");
        if (visible != null) {
            try {
                actor.setVisible(Boolean.parseBoolean(visible.toString()));
            } catch (Exception ignored) {
            }
        }
    }

    private Label buildLabel(ComponentNode node) {
        Label label = new Label(gui.i18n(node.properties.getOrDefault("text", "").toString()), skin);
        applyLabel(node, label);
        return label;
    }

    private void applyLabel(ComponentNode node, Label label) {
        Object alignment = node.properties.get("alignment");
        if (alignment != null) {
            String a = alignment.toString().toLowerCase(Locale.ROOT);
            if (a.contains("center"))
                label.setAlignment(Align.center);
            else if (a.contains("left"))
                label.setAlignment(Align.left);
            else if (a.contains("right"))
                label.setAlignment(Align.right);
        }

        Object color = node.properties.get("color");
        if (color != null) {
            Color c = resolveColor(color.toString());
            if (c != null)
                label.setColor(c);
        }
    }

    private Table buildTable(ComponentNode node) {
        Table table = new Table();

        if (Boolean.TRUE.equals(node.properties.get("fillParent"))) {
            table.setFillParent(true);
        }

        applyTableAlign(table, node.properties.get("align"));

        Object pad = node.properties.get("pad");
        if (pad != null) {
            try {
                table.pad(Float.parseFloat(pad.toString()));
            } catch (Exception ignored) {
            }
        }

        Object background = node.properties.get("background");
        if (background != null) {
            Drawable d = resolveDrawable(background.toString());
            if (d != null)
                table.setBackground(d);
        }

        for (ComponentNode child : node.children) {
            Actor childActor = create(child);
            Cell<?> cell = table.add(childActor);
            applyCell(child, cell);
            table.row();
        }
        return table;
    }

    private void applyTableAlign(Table table, Object alignValue) {
        if (alignValue == null)
            return;
        String a = alignValue.toString().toLowerCase(Locale.ROOT);

        if (a.contains("top"))
            table.top();
        if (a.contains("bottom"))
            table.bottom();
        if (a.contains("left"))
            table.left();
        if (a.contains("right"))
            table.right();
        if (a.contains("center"))
            table.center();
    }

    private void applyCell(ComponentNode child, Cell<?> cell) {
        Object cellObj = child.properties.get("cell");
        if (!(cellObj instanceof java.util.Map))
            return;
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> c = (java.util.Map<String, Object>) cellObj;

        if (c.containsKey("align")) {
            String a = c.get("align").toString().toLowerCase(Locale.ROOT);
            if (a.contains("center"))
                cell.center();
            if (a.contains("left"))
                cell.left();
            if (a.contains("right"))
                cell.right();
            if (a.contains("top"))
                cell.top();
            if (a.contains("bottom"))
                cell.bottom();
        }

        if (Boolean.TRUE.equals(c.get("expand")))
            cell.expand();
        if (Boolean.TRUE.equals(c.get("grow")))
            cell.grow();

        if (Boolean.TRUE.equals(c.get("expandX")))
            cell.expandX();
        if (Boolean.TRUE.equals(c.get("expandY")))
            cell.expandY();

        if (Boolean.TRUE.equals(c.get("fill")))
            cell.fill();
        if (Boolean.TRUE.equals(c.get("fillX")))
            cell.fillX();
        if (Boolean.TRUE.equals(c.get("fillY")))
            cell.fillY();

        if (c.containsKey("colspan"))
            cell.colspan(Integer.parseInt(c.get("colspan").toString()));

        if (c.containsKey("pad"))
            cell.pad(Float.parseFloat(c.get("pad").toString()));
        if (c.containsKey("padBottom"))
            cell.padBottom(Float.parseFloat(c.get("padBottom").toString()));
        if (c.containsKey("padTop"))
            cell.padTop(Float.parseFloat(c.get("padTop").toString()));
        if (c.containsKey("padLeft"))
            cell.padLeft(Float.parseFloat(c.get("padLeft").toString()));
        if (c.containsKey("padRight"))
            cell.padRight(Float.parseFloat(c.get("padRight").toString()));

        if (c.containsKey("width"))
            cell.width(Float.parseFloat(c.get("width").toString()));
        if (c.containsKey("height"))
            cell.height(Float.parseFloat(c.get("height").toString()));
    }

    private Stack buildStack(ComponentNode node) {
        Stack stack = new Stack();
        for (ComponentNode child : node.children) {
            stack.add(create(child));
        }
        return stack;
    }

    private ScrollPane buildScroll(ComponentNode node) {
        Actor content = node.children.isEmpty() ? new Group() : create(node.children.get(0));
        ScrollPane scroll = new ScrollPane(content, skin);

        Object scrollX = node.properties.get("scrollX");
        Object scrollY = node.properties.get("scrollY");
        if (scrollX != null || scrollY != null) {
            boolean sx = scrollX == null || Boolean.parseBoolean(scrollX.toString());
            boolean sy = scrollY == null || Boolean.parseBoolean(scrollY.toString());
            scroll.setScrollingDisabled(!sx, !sy);
        }

        return scroll;
    }

    private Window buildWindow(ComponentNode node) {
        String title = gui.i18n(node.properties.getOrDefault("title", "").toString());
        Window window = new Window(title, skin);

        Object movable = node.properties.get("movable");
        if (movable != null)
            window.setMovable(Boolean.parseBoolean(movable.toString()));
        Object modal = node.properties.get("modal");
        if (modal != null)
            window.setModal(Boolean.parseBoolean(modal.toString()));
        Object resizable = node.properties.get("resizable");
        if (resizable != null)
            window.setResizable(Boolean.parseBoolean(resizable.toString()));

        for (ComponentNode child : node.children) {
            Actor childActor = create(child);
            window.add(childActor).row();
        }
        window.pack();
        return window;
    }

    private Actor buildImage(ComponentNode node) {
        Object drawable = node.properties.get("drawable");
        if (drawable != null) {
            Drawable d = resolveDrawable(drawable.toString());
            if (d != null)
                return new Image(d);
        }
        return new Image();
    }

    private Actor buildButton(ComponentNode node) {
        TextButton btn = new TextButton(gui.i18n(node.properties.getOrDefault("text", "").toString()), skin);

        Object action = node.properties.get("onClick");
        if (action != null) {
            final String actionId = action.toString();
            btn.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    return true;
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    if (btn.isOver()) {
                        gui.dispatchAction(actionId);
                    }
                }
            });
        }

        return btn;
    }

    private Actor buildSlider(ComponentNode node) {
        float min = toFloat(node.properties.get("min"), 0f);
        float max = toFloat(node.properties.get("max"), 1f);
        float step = toFloat(node.properties.get("step"), 0.01f);
        float value = toFloat(node.properties.get("value"), min);
        boolean vertical = Boolean.TRUE.equals(node.properties.get("vertical"));

        Slider slider = new Slider(min, max, step, vertical, skin);
        slider.setValue(value);

        Object onChange = node.properties.get("onChange");
        if (onChange != null) {
            final String actionId = onChange.toString();
            slider.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    gui.dispatchAction(actionId + ":" + slider.getValue());
                }
            });
        }

        return slider;
    }

    private Actor buildProgressBar(ComponentNode node) {
        float min = toFloat(node.properties.get("min"), 0f);
        float max = toFloat(node.properties.get("max"), 1f);
        float step = toFloat(node.properties.get("step"), 0.01f);
        float value = toFloat(node.properties.get("value"), min);
        boolean vertical = Boolean.TRUE.equals(node.properties.get("vertical"));

        ProgressBar pb = new ProgressBar(min, max, step, vertical, skin);
        pb.setValue(value);
        return pb;
    }

    private Actor buildTextField(ComponentNode node) {
        TextField tf = new TextField(node.properties.getOrDefault("text", "").toString(), skin);

        Object messageText = node.properties.get("messageText");
        if (messageText != null)
            tf.setMessageText(gui.i18n(messageText.toString()));

        Object passwordMode = node.properties.get("passwordMode");
        if (passwordMode != null)
            tf.setPasswordMode(Boolean.parseBoolean(passwordMode.toString()));

        Object onChange = node.properties.get("onChange");
        if (onChange != null) {
            final String actionId = onChange.toString();
            tf.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    gui.dispatchAction(actionId + ":" + tf.getText());
                }
            });
        }

        return tf;
    }

    private float toFloat(Object v, float def) {
        if (v == null)
            return def;
        try {
            return Float.parseFloat(v.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private Drawable resolveDrawable(String name) {
        try {
            return skin.getDrawable(name);
        } catch (Exception e) {
            Gdx.app.error("UiFactory", "Drawable not found in Skin: " + name);
            return null;
        }
    }

    private Color resolveColor(String value) {
        try {
            if (value.startsWith("#")) {
                return Color.valueOf(value);
            }
            return skin.getColor(value);
        } catch (Exception e) {
            return null;
        }
    }
}
