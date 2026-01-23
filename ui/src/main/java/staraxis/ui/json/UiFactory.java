package staraxis.ui.json;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Action;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;

/**
 * UI 工厂：将 {@link ComponentNode}（声明式 UI 节点树）构建为 Scene2D 的 {@link Actor} 树。
 */
public class UiFactory {

    private final Skin skin;
    private final Gui gui;

    private static final Map<String, Map<String, Object>> CLASS_STYLES = new HashMap<>();
    private static Map<String, ComponentNode> COMPONENT_LIB = java.util.Collections.emptyMap();

    static {
        // 注册默认 class 样式
        Map<String, Object> modRow = new HashMap<>();
        modRow.put("background", "Small_rounded_button");
        modRow.put("pad", 4f);
        CLASS_STYLES.put("mod-row", modRow);

        Map<String, Object> btnSmall = new HashMap<>();
        btnSmall.put("background", "Small_rounded_button");
        CLASS_STYLES.put("btn-small", btnSmall);

        // 预加载组件库（可忽略失败）
        try {
            com.badlogic.gdx.files.FileHandle fh = Gdx.files.internal("ui/common/components.json");
            if (fh.exists()) {
                COMPONENT_LIB = new java.util.HashMap<>();
                String json = fh.readString("UTF-8");
                com.badlogic.gdx.utils.JsonReader jr = new com.badlogic.gdx.utils.JsonReader();
                com.badlogic.gdx.utils.JsonValue root = jr.parse(json);
                UiParser p = new UiParser();
                for (com.badlogic.gdx.utils.JsonValue child : root) {
                    String key = child.name;
                    String childJson = child.toJson(com.badlogic.gdx.utils.JsonWriter.OutputType.json);
                    ComponentNode n = p.parseString("component-lib:" + key, childJson);
                    if (n != null)
                        COMPONENT_LIB.put(key, n);
                }
            }
        } catch (Exception e) {
            Gdx.app.error("UiFactory", "Failed to load components.json", e);
        }
    }

    public UiFactory(Gui gui) {
        this.gui = gui;
        this.skin = gui.get(Skin.class);
    }

    public Actor create(ComponentNode node) {
        // 处理 include
        if (node.include != null && COMPONENT_LIB.containsKey(node.include)) {
            ComponentNode tpl = deepCopy(COMPONENT_LIB.get(node.include));
            if (node.params != null)
                substituteProps(tpl, node.params);
            if (node.children != null && !node.children.isEmpty())
                tpl.children.addAll(node.children); // 允许额外 children
            node = tpl;
        }

        ComponentNode effectiveNode = mergeStyleNode(node);

        Actor actor;
        switch (safeLower(effectiveNode.type)) {
            case "container":
                actor = buildTable(effectiveNode);
                break;
            case "stack":
                actor = buildStack(effectiveNode);
                break;
            case "position":
                actor = buildPosition(effectiveNode);
                break;
            case "scroll":
                actor = buildScroll(effectiveNode);
                break;
            case "window":
                actor = buildWindow(effectiveNode);
                break;
            case "dialog":
                actor = buildDialog(effectiveNode);
                break;
            case "verticalgroup":
                actor = buildVerticalGroup(effectiveNode);
                break;
            case "horizontalgroup":
                actor = buildHorizontalGroup(effectiveNode);
                break;
            case "repeat":
                actor = buildRepeat(effectiveNode);
                break;
            case "label":
                actor = buildLabel(effectiveNode);
                break;
            case "button":
            case "textbutton":
                actor = buildButton(effectiveNode);
                break;
            case "checkbox":
                actor = buildCheckBox(effectiveNode);
                break;
            case "imagebutton":
                actor = buildImageButton(effectiveNode);
                break;
            case "image":
                actor = buildImage(effectiveNode);
                break;
            case "slider":
                actor = buildSlider(effectiveNode);
                break;
            case "selectbox":
                actor = buildSelectBox(effectiveNode);
                break;
            case "progressbar":
                actor = buildProgressBar(effectiveNode);
                break;
            case "textfield":
                actor = buildTextField(effectiveNode);
                break;
            default:
                actor = new Group();
        }

        applyCommonActorProps(actor, effectiveNode);
        applyColorAnim(actor, effectiveNode);
        applyInheritance(actor, effectiveNode);
        applyPostCreate(actor, effectiveNode);
        return actor;
    }

    private String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    private ComponentNode mergeStyleNode(ComponentNode node) {
        if (node == null) {
            return null;
        }

        ComponentNode merged = deepCopy(node);

        Map<String, Object> mergedProps = new HashMap<>();

        Map<String, Object> defaultProps = defaultPropsForNode(merged);
        if (defaultProps != null) {
            mergedProps.putAll(defaultProps);
        }

        Object classValue = merged.properties.get("class");
        if (classValue != null) {
            List<String> classes = parseClassList(classValue.toString());
            for (String clazz : classes) {
                Map<String, Object> styleProps = CLASS_STYLES.get(clazz);
                if (styleProps != null) {
                    mergedProps.putAll(styleProps);
                }
            }
        }

        Object styleValue = merged.properties.get("style");
        if (styleValue instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> inlineStyle = (Map<String, Object>) styleValue;
            mergedProps.putAll(inlineStyle);
        }

        mergedProps.putAll(merged.properties);
        merged.properties = mergedProps;

        for (int i = 0; i < merged.children.size(); i++) {
            merged.children.set(i, mergeStyleNode(merged.children.get(i)));
        }

        return merged;
    }

    private List<String> parseClassList(String value) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        String[] parts = value.trim().split("\\s+");
        for (String p : parts) {
            if (p != null) {
                String s = p.trim();
                if (!s.isEmpty()) {
                    result.add(s);
                }
            }
        }
        return result;
    }

    private Map<String, Object> defaultPropsForNode(ComponentNode node) {
        Map<String, Object> defaults = new HashMap<>();

        String type = safeLower(node.type);
        if ("container".equals(type) || "repeat".equals(type)) {
            if (!node.properties.containsKey("display")) {
                defaults.put("display", "block");
            }
            if (!node.properties.containsKey("layout")) {
                defaults.put("layout", "column");
            }
        }

        return defaults;
    }

    private void applyInheritance(Actor actor, ComponentNode node) {
        if (actor == null || node == null) {
            return;
        }

        Object inherit = node.properties.get("inherit");
        if (!(inherit instanceof Boolean) || !((Boolean) inherit)) {
            return;
        }

        if (actor.getParent() == null) {
            return;
        }

        Actor parent = actor.getParent();
        if (node.properties.get("color") == null) {
            actor.setColor(parent.getColor());
        }
    }

    private void applyPostCreate(Actor actor, ComponentNode node) {
        if (actor == null || node == null) {
            return;
        }

        Object display = node.properties.get("display");
        if (display != null && "block".equalsIgnoreCase(display.toString())) {
            if (actor instanceof Table) {
                ((Table) actor).defaults().growX().fillX();
            }
        }
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

        Object userObject = node.properties.get("userObject");
        if (userObject != null) {
            actor.setUserObject(userObject);
        }
    }

    private void applyColorAnim(Actor actor, ComponentNode node) {
        Object colorAnimObj = node.properties.get("colorAnim");
        if (!(colorAnimObj instanceof java.util.Map))
            return;

        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> anim = (java.util.Map<String, Object>) colorAnimObj;

        Object fromObj = anim.get("from");
        Object toObj = anim.get("to");
        if (fromObj == null || toObj == null)
            return;

        float duration = toFloat(anim.get("duration"), 1f);
        if (duration <= 0)
            duration = 1f;

        Color from = resolveColor(fromObj.toString());
        Color to = resolveColor(toObj.toString());
        if (from == null || to == null) {
            Gdx.app.error("UiFactory", "Invalid colorAnim: from=" + fromObj + ", to=" + toObj);
            return;
        }

        actor.setColor(from);

        com.badlogic.gdx.utils.Array<Action> actions = actor.getActions();
        for (int i = actions.size - 1; i >= 0; i--) {
            if (actions.get(i) instanceof PingPongColorAction) {
                actions.removeIndex(i);
            }
        }
        actor.addAction(new PingPongColorAction(from, to, duration));
    }

    public static class PingPongColorAction extends Action {
        private final Color from, to;
        private final float duration;
        private float time;

        public PingPongColorAction(Color from, Color to, float duration) {
            this.from = new Color(from);
            this.to = new Color(to);
            this.duration = duration;
        }

        @Override
        public boolean act(float delta) {
            if (actor == null)
                return true;
            time += delta;
            float t = (time / duration) % 2f;
            if (t > 1f)
                t = 2f - t;
            actor.getColor().set(from.r + (to.r - from.r) * t, from.g + (to.g - from.g) * t,
                    from.b + (to.b - from.b) * t, from.a + (to.a - from.a) * t);
            return false;
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
        if (Boolean.TRUE.equals(node.properties.get("fillParent")))
            table.setFillParent(true);
        applyTableAlign(table, node.properties.get("align"));
        Object pad = node.properties.get("pad");
        if (pad != null)
            try {
                table.pad(Float.parseFloat(pad.toString()));
            } catch (Exception ignored) {
            }
        Object background = node.properties.get("background");
        if (background != null) {
            Drawable d = resolveDrawable(background.toString());
            if (d != null)
                table.setBackground(d);
        }
        boolean horizontal = Boolean.TRUE.equals(node.properties.get("horizontal"));
        Object layout = node.properties.get("layout");
        if (layout != null && "row".equalsIgnoreCase(layout.toString())) {
            horizontal = true;
        }

        for (ComponentNode child : node.children) {
            Actor childActor = create(child);
            Cell<?> cell = table.add(childActor);

            applyDefaultBlockCell(node, child, cell, horizontal);
            applyCell(child, cell);

            if (!horizontal)
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

    private void applyDefaultBlockCell(ComponentNode parentNode, ComponentNode childNode, Cell<?> cell,
            boolean horizontal) {
        if (parentNode == null || childNode == null || cell == null) {
            return;
        }

        Object parentLayout = parentNode.properties.get("layout");
        if (parentLayout != null && "row".equalsIgnoreCase(parentLayout.toString())) {
            return;
        }

        if (horizontal) {
            return;
        }

        Object display = childNode.properties.get("display");
        if (display == null || !"block".equalsIgnoreCase(display.toString())) {
            return;
        }

        Object cellObj = childNode.properties.get("cell");
        if (cellObj instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> c = (Map<String, Object>) cellObj;
            if (Boolean.TRUE.equals(c.get("grow")) || Boolean.TRUE.equals(c.get("growX"))
                    || Boolean.TRUE.equals(c.get("fillX")) || Boolean.TRUE.equals(c.get("expandX"))
                    || c.containsKey("width")) {
                return;
            }
        }

        cell.growX().fillX();
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
        for (ComponentNode child : node.children)
            stack.add(create(child));
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
        final float minAutoWidth = 200f, maxAutoWidth = 520f;
        WidgetGroup g = new WidgetGroup() {
            @Override
            public void layout() {
                float baseW = getWidth(), baseH = getHeight();
                if (baseW <= 0 || baseH <= 0) {
                    baseW = Gdx.graphics.getWidth();
                    baseH = Gdx.graphics.getHeight();
                    setSize(baseW, baseH);
                }
                float targetW = !Float.isNaN(cfgWidth) ? cfgWidth
                        : clamp(child instanceof Widget ? ((Widget) child).getPrefWidth() : child.getWidth(),
                                minAutoWidth, maxAutoWidth);
                float targetH = !Float.isNaN(cfgHeight) ? cfgHeight
                        : (child instanceof Widget ? ((Widget) child).getPrefHeight() : child.getHeight());
                if (Float.isNaN(cfgWidth))
                    targetW = Math.max(targetW, minAutoWidth);
                child.setSize(targetW, targetH);
                float cx, cy;
                if (!Float.isNaN(x) && !Float.isNaN(y)) {
                    cx = x;
                    cy = y;
                } else {
                    String a = align.toLowerCase(Locale.ROOT);
                    if (a.contains("left"))
                        cx = 0;
                    else if (a.contains("right"))
                        cx = baseW - child.getWidth();
                    else
                        cx = (baseW - child.getWidth()) / 2f;
                    if (a.contains("top"))
                        cy = baseH - child.getHeight();
                    else if (a.contains("bottom"))
                        cy = 0;
                    else
                        cy = (baseH - child.getHeight()) / 2f;
                }
                child.setPosition(cx, cy);
            }

            @Override
            public Actor hit(float x, float y, boolean touchable) {
                if (!isVisible() || (touchable && getTouchable() != Touchable.enabled))
                    return null;
                float cx = child.getX(), cy = child.getY();
                return (x < cx || y < cy || x > cx + child.getWidth() || y > cy + child.getHeight()) ? null
                        : super.hit(x, y, touchable);
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
        boolean sx = node.properties.get("scrollX") != null
                && Boolean.parseBoolean(node.properties.get("scrollX").toString());
        boolean sy = node.properties.get("scrollY") == null
                || Boolean.parseBoolean(node.properties.get("scrollY").toString());
        scroll.setScrollingDisabled(!sx, !sy);
        scroll.setFadeScrollBars(false);
        scroll.setScrollbarsOnTop(false);
        scroll.setOverscroll(false, false);
        return scroll;
    }

    private Window buildWindow(ComponentNode node) {
        Window window = new Window(gui.i18n(node.properties.getOrDefault("title", "").toString()), skin);
        if (node.properties.get("movable") != null)
            window.setMovable(Boolean.parseBoolean(node.properties.get("movable").toString()));
        if (node.properties.get("modal") != null)
            window.setModal(Boolean.parseBoolean(node.properties.get("modal").toString()));
        if (node.properties.get("resizable") != null)
            window.setResizable(Boolean.parseBoolean(node.properties.get("resizable").toString()));
        for (ComponentNode child : node.children)
            window.add(create(child)).row();
        window.pack();
        return window;
    }

    private Dialog buildDialog(ComponentNode node) {
        Dialog dialog = new Dialog(gui.i18n(node.properties.getOrDefault("title", "").toString()), skin);
        if (node.properties.get("modal") != null)
            dialog.setModal(Boolean.parseBoolean(node.properties.get("modal").toString()));
        if (node.properties.get("movable") != null)
            dialog.setMovable(Boolean.parseBoolean(node.properties.get("movable").toString()));
        if (node.properties.get("resizable") != null)
            dialog.setResizable(Boolean.parseBoolean(node.properties.get("resizable").toString()));
        for (ComponentNode child : node.children) {
            Cell<?> cell = dialog.getContentTable().add(create(child));
            applyCell(child, cell);
            dialog.getContentTable().row();
        }
        dialog.pack();
        return dialog;
    }

    private VerticalGroup buildVerticalGroup(ComponentNode node) {
        VerticalGroup group = new VerticalGroup();
        if (node.properties.get("spacing") != null)
            group.space(toFloat(node.properties.get("spacing"), 0));
        applyVerticalAlign(group, node.properties.get("align"));
        for (ComponentNode child : node.children)
            group.addActor(create(child));
        return group;
    }

    private HorizontalGroup buildHorizontalGroup(ComponentNode node) {
        HorizontalGroup group = new HorizontalGroup();
        if (node.properties.get("spacing") != null)
            group.space(toFloat(node.properties.get("spacing"), 0));
        applyHorizontalAlign(group, node.properties.get("align"));
        for (ComponentNode child : node.children)
            group.addActor(create(child));
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

    private void applyHorizontalAlign(HorizontalGroup group, Object alignValue) {
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
        if (!node.children.isEmpty())
            container.setUserObject(node.children.get(0));
        return container;
    }

    public void renderRepeatItems(Group repeatActor, java.util.List<Map<String, Object>> itemsData) {
        Object uo = repeatActor.getUserObject();
        if (!(uo instanceof ComponentNode))
            return;
        ComponentNode template = (ComponentNode) uo;
        if (!(repeatActor instanceof Table)) {
            Gdx.app.error("UiFactory", "Repeat actor must be a 'container' (Table) to use renderRepeatItems.");
            return;
        }
        Table t = (Table) repeatActor;
        t.clearChildren();
        t.top().left();
        for (int i = 0; i < itemsData.size(); i++) {
            Map<String, Object> data = itemsData.get(i);
            ComponentNode inst = deepCopy(template);
            substituteProps(inst, data);
            Actor row = create(inst);
            row.setName((inst.name != null ? inst.name : "repeat_item") + "_" + i);
            t.add(row).growX().fillX().row();
        }
        t.invalidateHierarchy();
    }

    public void renderRepeatItemsWithActors(Group repeatActor, java.util.List<Map<String, Object>> itemsData,
            java.util.function.BiConsumer<Actor, Map<String, Object>> actorBinder) {
        Object uo = repeatActor.getUserObject();
        if (!(uo instanceof ComponentNode))
            return;
        ComponentNode template = (ComponentNode) uo;
        if (!(repeatActor instanceof Table)) {
            Gdx.app.error("UiFactory",
                    "Repeat actor must be a 'container' (Table) to use renderRepeatItemsWithActors.");
            return;
        }
        Table t = (Table) repeatActor;
        t.clearChildren();
        t.top().left();
        for (int i = 0; i < itemsData.size(); i++) {
            Map<String, Object> data = itemsData.get(i);
            ComponentNode inst = deepCopy(template);
            substituteProps(inst, data);
            Actor row = create(inst);
            row.setName((inst.name != null ? inst.name : "repeat_item") + "_" + i);
            if (actorBinder != null) {
                try {
                    actorBinder.accept(row, data);
                } catch (Exception e) {
                    Gdx.app.error("UiFactory", "Failed to bind repeat item actor at index=" + i, e);
                }
            }
            t.add(row).growX().fillX().row();
        }
        t.invalidateHierarchy();
    }

    private ComponentNode deepCopy(ComponentNode src) {
        ComponentNode dst = new ComponentNode(src.type);
        dst.name = src.name;
        dst.properties = new java.util.HashMap<>(src.properties);
        for (ComponentNode c : src.children)
            dst.children.add(deepCopy(c));
        return dst;
    }

    private void substituteProps(ComponentNode node, Map<String, Object> data) {
        for (Map.Entry<String, Object> e : node.properties.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String) {
                String s = (String) v;
                for (Map.Entry<String, Object> dataEntry : data.entrySet()) {
                    String placeholder = "${" + dataEntry.getKey() + "}";
                    if (s.contains(placeholder)) {
                        s = s.replace(placeholder, dataEntry.getValue() != null ? dataEntry.getValue().toString() : "");
                    }
                }
                e.setValue(s);
            }
        }
        for (ComponentNode c : node.children)
            substituteProps(c, data);
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
        if (bg != null)
            try {
                Drawable d = resolveDrawable(bg.toString());
                if (d != null) {
                    TextButton.TextButtonStyle s = new TextButton.TextButtonStyle(
                            skin.get(TextButton.TextButtonStyle.class));
                    s.up = d;
                    s.over = d;
                    s.down = d;
                    s.focused = d;
                    btn.setStyle(s);
                }
            } catch (Exception ignored) {
            }
        Object action = node.properties.get("onClick");
        if (action != null) {
            final String actionId = action.toString();
            btn.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent e, float x, float y, int p, int b) {
                    return true;
                }

                @Override
                public void touchUp(InputEvent e, float x, float y, int p, int b) {
                    if (btn.isOver())
                        gui.dispatchAction(actionId);
                }
            });
        }
        return btn;
    }

    private Actor buildCheckBox(ComponentNode node) {
        CheckBox checkBox = new CheckBox(gui.i18n(node.properties.getOrDefault("text", "").toString()), skin);
        Object checked = node.properties.get("checked");
        if (checked != null)
            try {
                checkBox.setChecked(Boolean.parseBoolean(checked.toString()));
            } catch (Exception ignored) {
            }
        Object onChange = node.properties.get("onChange");
        if (onChange != null) {
            final String actionId = onChange.toString();
            checkBox.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    gui.dispatchAction(actionId + ":" + checkBox.isChecked());
                }
            });
        }
        return checkBox;
    }

    private Actor buildImageButton(ComponentNode node) {
        ImageButton.ImageButtonStyle style;
        Object styleName = node.properties.get("style");
        if (styleName != null && skin.has(styleName.toString(), ImageButton.ImageButtonStyle.class)) {
            style = skin.get(styleName.toString(), ImageButton.ImageButtonStyle.class);
        } else {
            style = new ImageButton.ImageButtonStyle(skin.get(Button.ButtonStyle.class));
        }
        if (node.properties.get("imageUp") != null)
            style.imageUp = resolveDrawable(node.properties.get("imageUp").toString());
        if (node.properties.get("imageDown") != null)
            style.imageDown = resolveDrawable(node.properties.get("imageDown").toString());
        if (node.properties.get("imageOver") != null)
            style.imageOver = resolveDrawable(node.properties.get("imageOver").toString());
        if (node.properties.get("imageChecked") != null)
            style.imageChecked = resolveDrawable(node.properties.get("imageChecked").toString());
        ImageButton btn = new ImageButton(style);
        Object action = node.properties.get("onClick");
        if (action != null) {
            final String actionId = action.toString();
            btn.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent e, float x, float y, int p, int b) {
                    return true;
                }

                @Override
                public void touchUp(InputEvent e, float x, float y, int p, int b) {
                    if (btn.isOver())
                        gui.dispatchAction(actionId);
                }
            });
        }
        return btn;
    }

    private Actor buildSlider(ComponentNode node) {
        float min = toFloat(node.properties.get("min"), 0f), max = toFloat(node.properties.get("max"), 1f),
                step = toFloat(node.properties.get("step"), 0.01f), value = toFloat(node.properties.get("value"), min);
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
        if (node.properties.get("items") instanceof String) {
            String[] parts = ((String) node.properties.get("items")).split(",");
            for (int i = 0; i < parts.length; i++)
                parts[i] = parts[i].trim();
            sb.setItems(parts);
        }
        if (node.properties.get("selected") != null)
            try {
                sb.setSelected(node.properties.get("selected").toString());
            } catch (Exception ignored) {
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
        float min = toFloat(node.properties.get("min"), 0f), max = toFloat(node.properties.get("max"), 1f),
                step = toFloat(node.properties.get("step"), 0.01f), value = toFloat(node.properties.get("value"), min);
        boolean vertical = Boolean.TRUE.equals(node.properties.get("vertical"));
        ProgressBar pb = new ProgressBar(min, max, step, vertical, skin);
        pb.setValue(value);
        return pb;
    }

    private Actor buildTextField(ComponentNode node) {
        TextField tf = new TextField(node.properties.getOrDefault("text", "").toString(), skin);
        if (node.properties.get("messageText") != null)
            tf.setMessageText(gui.i18n(node.properties.get("messageText").toString()));
        if (node.properties.get("passwordMode") != null)
            tf.setPasswordMode(Boolean.parseBoolean(node.properties.get("passwordMode").toString()));
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
            return value.startsWith("#") ? Color.valueOf(value) : skin.getColor(value);
        } catch (Exception e) {
            return null;
        }
    }

    private float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }
}
