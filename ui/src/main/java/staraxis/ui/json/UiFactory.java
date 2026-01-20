package staraxis.ui.json;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
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
            case "position":
                actor = buildPosition(node);
                break;
            case "scroll":
                actor = buildScroll(node);
                break;
            case "window":
                actor = buildWindow(node);
                break;
            case "dialog":
                actor = buildDialog(node);
                break;
            case "verticalgroup":
                actor = buildVerticalGroup(node);
                break;
            case "repeat":
                actor = buildRepeat(node);
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
            case "selectbox":
                actor = buildSelectBox(node);
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

        boolean horizontal = Boolean.TRUE.equals(node.properties.get("horizontal"));
        for (int i = 0; i < node.children.size(); i++) {
            ComponentNode child = node.children.get(i);
            Actor childActor = create(child);
            Cell<?> cell = table.add(childActor);
            applyCell(child, cell);
            if (!horizontal) {
                table.row();
            }
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
            try {
                cell.colspan(Math.round(Float.parseFloat(c.get("colspan").toString())));
            } catch (Exception ignored) {
            }

        if (c.containsKey("pad"))
            cell.pad(toFloat(c.get("pad"), 0));
        if (c.containsKey("padBottom"))
            cell.padBottom(toFloat(c.get("padBottom"), 0));
        if (c.containsKey("padTop"))
            cell.padTop(toFloat(c.get("padTop"), 0));
        if (c.containsKey("padLeft"))
            cell.padLeft(toFloat(c.get("padLeft"), 0));
        if (c.containsKey("padRight"))
            cell.padRight(toFloat(c.get("padRight"), 0));

        if (c.containsKey("width"))
            cell.width(toFloat(c.get("width"), 0));
        if (c.containsKey("height"))
            cell.height(toFloat(c.get("height"), 0));
    }

    private Stack buildStack(ComponentNode node) {
        Stack stack = new Stack();
        for (ComponentNode child : node.children) {
            stack.add(create(child));
        }
        return stack;
    }

    private WidgetGroup buildPosition(ComponentNode node) {
        final Actor child = node.children.isEmpty() ? new Group() : create(node.children.get(0));
        child.setTouchable(Touchable.enabled);

        final String align = node.properties.getOrDefault("align", "topRight").toString();
        final float x = toFloat(node.properties.get("x"), Float.NaN);
        final float y = toFloat(node.properties.get("y"), Float.NaN);
        final float cfgWidth = toFloat(node.properties.get("width"), Float.NaN);
        final float cfgHeight = toFloat(node.properties.get("height"), Float.NaN);

        final float minAutoWidth = 200f;
        final float maxAutoWidth = 520f;

        WidgetGroup g = new WidgetGroup() {
            @Override
            public void layout() {
                float baseW = getWidth();
                float baseH = getHeight();
                if (baseW <= 0 || baseH <= 0) {
                    baseW = Gdx.graphics.getWidth();
                    baseH = Gdx.graphics.getHeight();
                    setSize(baseW, baseH);
                }

                float targetW;
                if (!Float.isNaN(cfgWidth)) {
                    targetW = cfgWidth;
                } else {
                    float prefW = child.getWidth();
                    if (child instanceof Widget) {
                        prefW = ((Widget) child).getPrefWidth();
                    }
                    targetW = clamp(prefW, minAutoWidth, maxAutoWidth);
                }

                float targetH;
                if (!Float.isNaN(cfgHeight)) {
                    targetH = cfgHeight;
                } else {
                    targetH = child.getHeight();
                    if (child instanceof Widget) {
                        targetH = ((Widget) child).getPrefHeight();
                    }
                }

                // 确保最小宽度确实生效（有些情况下 prefWidth 会因为内容未 layout 而异常）
                if (Float.isNaN(cfgWidth)) {
                    targetW = Math.max(targetW, minAutoWidth);
                }

                child.setSize(targetW, targetH);

                float cx;
                float cy;

                if (!Float.isNaN(x) && !Float.isNaN(y)) {
                    cx = x;
                    cy = y;
                } else {
                    float childW = child.getWidth();
                    float childH = child.getHeight();
                    String a = align.toLowerCase(Locale.ROOT);

                    if (a.contains("left")) {
                        cx = 0;
                    } else if (a.contains("right")) {
                        cx = baseW - childW;
                    } else {
                        cx = (baseW - childW) / 2f;
                    }

                    if (a.contains("top")) {
                        cy = baseH - childH;
                    } else if (a.contains("bottom")) {
                        cy = 0;
                    } else {
                        cy = (baseH - childH) / 2f;
                    }
                }

                child.setPosition(cx, cy);
            }

            @Override
            public Actor hit(float x, float y, boolean touchable) {
                if (!isVisible())
                    return null;
                if (touchable && getTouchable() != Touchable.enabled)
                    return null;

                float cx = child.getX();
                float cy = child.getY();
                if (x < cx || y < cy || x > cx + child.getWidth() || y > cy + child.getHeight()) {
                    return null;
                }
                return super.hit(x, y, touchable);
            }
        };

        g.setTouchable(Touchable.enabled);
        g.addActor(child);
        return g;
    }

    private ScrollPane buildScroll(ComponentNode node) {
        Actor content = node.children.isEmpty() ? new Group() : create(node.children.get(0));
        ScrollPane scroll = new ScrollPane(content, skin);
        scroll.setTouchable(Touchable.enabled);

        Object scrollX = node.properties.get("scrollX");
        Object scrollY = node.properties.get("scrollY");
        boolean sx = scrollX != null && Boolean.parseBoolean(scrollX.toString());
        boolean sy = scrollY == null || Boolean.parseBoolean(scrollY.toString());
        scroll.setScrollingDisabled(!sx, !sy);

        scroll.setFadeScrollBars(false);
        scroll.setScrollbarsOnTop(false);
        scroll.setOverscroll(false, false);
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

    private Dialog buildDialog(ComponentNode node) {
        String title = gui.i18n(node.properties.getOrDefault("title", "").toString());
        Dialog dialog = new Dialog(title, skin);

        Object modal = node.properties.get("modal");
        if (modal != null)
            dialog.setModal(Boolean.parseBoolean(modal.toString()));
        Object movable = node.properties.get("movable");
        if (movable != null)
            dialog.setMovable(Boolean.parseBoolean(movable.toString()));
        Object resizable = node.properties.get("resizable");
        if (resizable != null)
            dialog.setResizable(Boolean.parseBoolean(resizable.toString()));

        for (ComponentNode child : node.children) {
            Actor childActor = create(child);
            Cell<?> cell = dialog.getContentTable().add(childActor);
            applyCell(child, cell);
            dialog.getContentTable().row();
        }
        dialog.pack();
        return dialog;
    }

    private VerticalGroup buildVerticalGroup(ComponentNode node) {
        VerticalGroup group = new VerticalGroup();

        Object spacing = node.properties.get("spacing");
        if (spacing != null) {
            group.space(toFloat(spacing, 0));
        }

        applyVerticalAlign(group, node.properties.get("align"));

        for (ComponentNode child : node.children) {
            group.addActor(create(child));
        }
        return group;
    }

    private void applyVerticalAlign(VerticalGroup group, Object alignValue) {
        if (alignValue == null)
            return;
        String a = alignValue.toString().toLowerCase(Locale.ROOT);
        if (a.contains("top"))
            group.top();
        if (a.contains("bottom"))
            group.bottom();
        if (a.contains("left"))
            group.left();
        if (a.contains("right"))
            group.right();
        if (a.contains("center"))
            group.center();
    }

    private Group buildRepeat(ComponentNode node) {
        Table container = new Table();
        container.top().left();
        if (!node.children.isEmpty()) {
            container.setUserObject(node.children.get(0));
        }
        return container;
    }

    public void renderRepeatItems(Group repeatActor, java.util.List<String> items, String selected,
            String actionPrefix) {
        Object uo = repeatActor.getUserObject();
        if (!(uo instanceof ComponentNode))
            return;
        ComponentNode template = (ComponentNode) uo;

        if (repeatActor instanceof Table) {
            Table t = (Table) repeatActor;
            t.clearChildren();
            t.top().left();

            for (int i = 0; i < items.size(); i++) {
                String item = items.get(i);
                ComponentNode inst = deepCopy(template);

                substituteProps(inst, item, selected != null && selected.equals(item));

                if (actionPrefix != null) {
                    substituteAction(inst, actionPrefix + ":" + item);
                }

                Actor row = create(inst);
                row.setName((inst.name != null ? inst.name : "repeat_item") + "_" + i);
                t.add(row).growX().fillX().row();
            }

            t.invalidateHierarchy();
            t.pack();
            return;
        }

        repeatActor.clearChildren();
        Table table = new Table();
        table.top().left();
        repeatActor.addActor(table);

        for (int i = 0; i < items.size(); i++) {
            String item = items.get(i);
            ComponentNode inst = deepCopy(template);

            substituteProps(inst, item, selected != null && selected.equals(item));

            if (actionPrefix != null) {
                substituteAction(inst, actionPrefix + ":" + item);
            }

            Actor row = create(inst);
            row.setName((inst.name != null ? inst.name : "repeat_item") + "_" + i);
            table.add(row).growX().fillX().row();
        }

        table.invalidateHierarchy();
        table.pack();
    }

    private ComponentNode deepCopy(ComponentNode src) {
        ComponentNode dst = new ComponentNode(src.type);
        dst.name = src.name;
        dst.properties = new java.util.HashMap<>(src.properties);
        for (ComponentNode c : src.children) {
            dst.children.add(deepCopy(c));
        }
        return dst;
    }

    private void substituteProps(ComponentNode node, String item, boolean isSelected) {
        for (java.util.Map.Entry<String, Object> e : node.properties.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String) {
                String s = (String) v;
                s = s.replace("${item}", item);
                s = s.replace("${selected}", isSelected ? "true" : "false");
                e.setValue(s);
            }
        }
        for (ComponentNode c : node.children) {
            substituteProps(c, item, isSelected);
        }
    }

    private void substituteAction(ComponentNode node, String action) {
        Object onClick = node.properties.get("onClick");
        if (onClick instanceof String) {
            String s = (String) onClick;
            node.properties.put("onClick", s.replace("${action}", action));
        }
        for (ComponentNode c : node.children) {
            substituteAction(c, action);
        }
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

        Object bg = node.properties.get("background");
        if (bg != null) {
            try {
                Drawable d = resolveDrawable(bg.toString());
                if (d != null) {
                    TextButton.TextButtonStyle base = skin.get(TextButton.TextButtonStyle.class);
                    TextButton.TextButtonStyle s = new TextButton.TextButtonStyle(base);
                    s.up = d;
                    s.over = d;
                    s.down = d;
                    s.focused = d;
                    btn.setStyle(s);
                }
            } catch (Exception ignored) {
            }
        }

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

    private Actor buildSelectBox(ComponentNode node) {
        SelectBox<String> sb = new SelectBox<>(skin);

        Object items = node.properties.get("items");
        if (items instanceof String) {
            String[] parts = ((String) items).split(",");
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i].trim();
            }
            sb.setItems(parts);
        }

        Object selected = node.properties.get("selected");
        if (selected != null) {
            try {
                sb.setSelected(selected.toString());
            } catch (Exception ignored) {
            }
        }

        Object onChange = node.properties.get("onChange");
        if (onChange != null) {
            final String actionId = onChange.toString();
            sb.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    gui.dispatchAction(actionId + ":" + sb.getSelected());
                }
            });
        }

        return sb;
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

    private float clamp(float v, float min, float max) {
        if (v < min)
            return min;
        if (v > max)
            return max;
        return v;
    }
}
