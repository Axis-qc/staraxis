package staraxis.ui.json;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
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
import staraxis.ui.effects.EffectRegistry;
import staraxis.ui.effects.MenuEntryEffect;
import staraxis.ui.effects.VectorButtonEffect;
import staraxis.ui.effects.VectorCheckBoxEffect;
import staraxis.ui.effects.VectorImageEffect;
import staraxis.ui.effects.VectorLabelEffect;
import staraxis.ui.effects.VectorProgressBarEffect;
import staraxis.ui.effects.VectorScrollPaneEffect;
import staraxis.ui.effects.VectorSelectBoxEffect;
import staraxis.ui.effects.VectorSliderEffect;
import staraxis.ui.effects.VectorTextFieldEffect;
import staraxis.ui.effects.VectorWindowEffect;
import staraxis.ui.widgets.MenuEntry;
import staraxis.ui.widgets.VectorButton;
import staraxis.ui.widgets.VectorCheckBox;
import staraxis.ui.widgets.VectorDialog;
import staraxis.ui.widgets.VectorImage;
import staraxis.ui.widgets.VectorLabel;
import staraxis.ui.widgets.VectorProgressBar;
import staraxis.ui.widgets.VectorScrollPane;
import staraxis.ui.widgets.VectorSelectBox;
import staraxis.ui.widgets.VectorSlider;
import staraxis.ui.widgets.VectorTextField;
import staraxis.ui.widgets.VectorWindow;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * UI 工厂：将 {@link ComponentNode}（声明式 UI 节点树）构建为 Scene2D 的 {@link Actor} 树。
 */
public class UiFactory {

    private final Skin skin;
    private final Gui gui;
    private EffectRegistry effectRegistry;
    private ShapeRenderer shapeRenderer;
    private BitmapFont bitmapFont;
    private DataProvider dataProvider;

    private static Map<String, ComponentNode> COMPONENT_LIB = java.util.Collections.emptyMap();

    static {
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

    public void setEffectRegistry(EffectRegistry registry) {
        this.effectRegistry = registry;
    }

    public void setShapeRenderer(ShapeRenderer sr) {
        this.shapeRenderer = sr;
    }

    public void setBitmapFont(BitmapFont font) {
        this.bitmapFont = font;
    }

    public void setDataProvider(DataProvider provider) {
        this.dataProvider = provider;
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
            case "list":
                actor = buildList(node);
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
            case "horizontalgroup":
                actor = buildHorizontalGroup(node);
                break;
            case "repeat":
                actor = buildRepeat(node);
                break;
            case "label":
                actor = buildLabel(node);
                break;
            case "button":
            case "textbutton":
                actor = buildButton(node);
                break;
            case "checkbox":
                actor = buildCheckBox(node);
                break;
            case "imagebutton":
                actor = buildImageButton(node);
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
            case "menu_entry":
                actor = buildMenuEntry(node);
                break;
            case "vector_button":
                actor = buildVectorButton(node);
                break;
            case "vector_label":
                actor = buildVectorLabel(node);
                break;
            // ---- 新矢量控件（逐步替换 skin 控件） ----
            case "vector_image":
                actor = buildVectorImage(node);
                break;
            case "vector_checkbox":
                actor = buildVectorCheckBox(node);
                break;
            case "vector_slider":
                actor = buildVectorSlider(node);
                break;
            case "vector_progressbar":
                actor = buildVectorProgressBar(node);
                break;
            case "vector_textfield":
                actor = buildVectorTextField(node);
                break;
            case "vector_selectbox":
                actor = buildVectorSelectBox(node);
                break;
            case "vector_scrollpane":
                actor = buildVectorScrollPane(node);
                break;
            case "vector_window":
                actor = buildVectorWindow(node);
                break;
            case "vector_dialog":
                actor = buildVectorDialog(node);
                break;
            default:
                actor = new Group();
        }

        applyCommonActorProps(actor, node);
        applyInheritance(actor, node);
        applyPostCreate(actor, node);
        return actor;
    }

    private String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
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
            if (actor instanceof Table t) {
                t.defaults().growX().fillX();
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

        Object color = node.properties.get("color");
        if (color != null) {
            Color c = resolveColor(color.toString());
            if (c != null) {
                actor.setColor(c);
            }
        }

        Object userObject = node.properties.get("userObject");
        if (userObject != null) {
            actor.setUserObject(userObject);
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

    private Actor buildTable(ComponentNode node) {
        Table table = new Table();
        boolean isFillParent = Boolean.TRUE.equals(node.properties.get("fillParent"));
        if (isFillParent)
            table.setFillParent(true);
        applyTableAlign(table, node.properties.get("align"));
        Object pad = node.properties.get("pad");
        if (pad != null)
            try {
                table.pad(Float.parseFloat(pad.toString()));
            } catch (NumberFormatException ignored) {
            }
        Object background = node.properties.get("background");
        if (background != null) {
            Drawable d = resolveDrawable(background.toString());
            if (d != null)
                table.setBackground(d);
        }
        // 固定尺寸（不依赖 fillParent 时使用）
        float cfgWidth = toFloat(node.properties.get("width"), 0);
        float cfgHeight = toFloat(node.properties.get("height"), 0);
        boolean horizontal = Boolean.TRUE.equals(node.properties.get("horizontal"));
        Object layout = node.properties.get("layout");
        if (layout != null && "row".equalsIgnoreCase(layout.toString())) {
            horizontal = true;
        }

        // 垂直布局 + fillParent 时，预扫描是否包含底部对齐的子节点
        boolean hasBottomChild = false;
        if (!horizontal) {
            for (ComponentNode child : node.children) {
                Object a = child.properties.get("align");
                if (a != null && a.toString().toLowerCase(Locale.ROOT).contains("bottom")) {
                    hasBottomChild = true;
                    break;
                }
            }
        }

        boolean bottomExpandHandled = false;
        for (ComponentNode child : node.children) {
            Actor childActor = create(child);
            Cell<?> cell = table.add(childActor);

            // 将子节点的 align 属性同步到父 cell 对齐
            applyCellAlignFromChild(cell, child);

            // fillParent 垂直布局：首个非底部行 expandY 把底部行压到底
            // 同时设 top 避免非底部行在扩展空间中居中
            if (!horizontal && isFillParent && hasBottomChild && !bottomExpandHandled) {
                Object a = child.properties.get("align");
                boolean isBottom = a != null
                        && a.toString().toLowerCase(Locale.ROOT).contains("bottom");
                if (!isBottom) {
                    cell.expandY();
                    cell.top(); // keep at top of expanded row, not center
                    bottomExpandHandled = true;
                }
            }

            applyDefaultBlockCell(node, child, cell, horizontal);
            applyCell(child, cell);

            if (!horizontal)
                table.row();
        }

        // 固定尺寸：覆盖 layout 计算结果
        if (cfgWidth > 0 || cfgHeight > 0) {
            table.setSize(cfgWidth, cfgHeight);
        }

        // border 属性：borderColor + borderWidth → ShapeRenderer 画边框
        float borderW = toFloat(node.properties.get("borderWidth"), 0);
        final Color borderColor = resolveColor((String) node.properties.get("borderColor"));
        if (borderW > 0 && borderColor != null && shapeRenderer != null) {
            Group g = new Group() {
                @Override
                public void draw(com.badlogic.gdx.graphics.g2d.Batch b, float pa) {
                    super.draw(b, pa);
                    b.end();
                    shapeRenderer.setProjectionMatrix(b.getProjectionMatrix());
                    shapeRenderer.setTransformMatrix(b.getTransformMatrix());
                    shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
                    shapeRenderer.setColor(borderColor);
                    shapeRenderer.rect(getX(), getY(), getWidth(), getHeight());
                    shapeRenderer.end();
                    b.begin();
                }
            };
            if (isFillParent) {
                table.setFillParent(false);
            }
            if (cfgWidth > 0 || cfgHeight > 0)
                g.setSize(cfgWidth > 0 ? cfgWidth : table.getWidth(),
                        cfgHeight > 0 ? cfgHeight : table.getHeight());
            else
                g.setSize(table.getWidth(), table.getHeight());
            g.addActor(table);
            return g;
        }

        return table;
    }

    /**
     * 将子节点的 "align" 属性翻译为父 Table cell 的对齐方式。
     * 使 HUD 中的 top/right/bottom/left 能真正把子容器贴到父容器边缘。
     * left/right 同时设置 expandX，让列撑满父容器宽度，否则对齐在小宽度 cell 内无效。
     */
    private void applyCellAlignFromChild(Cell<?> cell, ComponentNode child) {
        Object alignValue = child.properties.get("align");
        if (alignValue == null)
            return;
        String a = alignValue.toString().toLowerCase(Locale.ROOT);
        if (a.contains("top"))
            cell.top();
        if (a.contains("bottom"))
            cell.bottom();
        if (a.contains("left")) {
            cell.left();
            cell.expandX(); // 列撑满父容器，使左对齐生效
        }
        if (a.contains("right")) {
            cell.right();
            cell.expandX(); // 列撑满父容器，使右对齐生效
        }
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
            } catch (NumberFormatException ignored) {
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
        final float pad = toFloat(node.properties.get("pad"), 0);
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
                        : clamp(child instanceof Widget w ? w.getPrefWidth() : child.getWidth(),
                                minAutoWidth, maxAutoWidth);
                float targetH = !Float.isNaN(cfgHeight) ? cfgHeight
                        : (child instanceof Widget w ? w.getPrefHeight() : child.getHeight());
                if (Float.isNaN(cfgWidth))
                    targetW = Math.max(targetW, minAutoWidth);
                child.setSize(targetW, targetH);
                float cx, cy;
                // x 与 y 独立处理：允许仅设置其中一个，未设置的方向回退到 align 定位
                String a = align.toLowerCase(Locale.ROOT);
                if (!Float.isNaN(x)) {
                    cx = x;
                } else if (a.contains("left")) {
                    cx = pad;
                } else if (a.contains("right")) {
                    cx = baseW - child.getWidth() - pad;
                } else {
                    cx = (baseW - child.getWidth()) / 2f;
                }
                if (!Float.isNaN(y)) {
                    cy = y;
                } else if (a.contains("top")) {
                    cy = baseH - child.getHeight() - pad;
                } else if (a.contains("bottom")) {
                    cy = pad;
                } else {
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

        String dataSource = (String) node.properties.get("dataSource");
        if (dataSource != null && dataProvider != null) {
            java.util.List<Map<String, Object>> items = dataProvider.getData(dataSource);
            if (items != null && !items.isEmpty()) {
                renderRepeatItems(container, items);
            }
        }

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
        // 颜色字符串 → 纯色 Drawable（颜色直接画进 pixmap 纹理，不用 tint）
        Color c = resolveColor(name);
        if (c != null) {
            com.badlogic.gdx.graphics.Pixmap pm = new com.badlogic.gdx.graphics.Pixmap(1, 1,
                    com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
            pm.setColor(c);
            pm.fill();
            com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable d =
                    new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(
                            new com.badlogic.gdx.graphics.g2d.TextureRegion(
                                    new com.badlogic.gdx.graphics.Texture(pm)));
            pm.dispose();
            d.setMinWidth(0);
            d.setMinHeight(0);
            return d;
        }
        // Skin 为空时不再尝试查找 drawable
        if (skin == null) return null;
        try {
            return skin.getDrawable(name);
        } catch (Exception e) {
            Gdx.app.error("UiFactory", "Drawable not found in Skin: " + name);
            return null;
        }
    }

    private Color resolveColor(String value) {
        try {
            return value.startsWith("#") ? Color.valueOf(value) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    private Actor buildMenuEntry(ComponentNode node) {
        String effectName = (String) node.properties.get("effect");
        MenuEntryEffect effect = null;
        if (effectRegistry != null && effectName != null) {
            effect = effectRegistry.get(effectName, MenuEntryEffect.class);
        }
        String text = gui.i18n((String) node.properties.getOrDefault("text", ""));
        String tagKey = (String) node.properties.get("tag");
        String tagText = tagKey != null ? gui.i18n(tagKey) : null;
        String onClick = (String) node.properties.get("onClick");

        Runnable action = onClick != null ? () -> gui.dispatchAction(onClick) : null;

        if (shapeRenderer == null || bitmapFont == null) {
            Gdx.app.error("UiFactory", "ShapeRenderer/BitmapFont not set for menu_entry");
            return new Group();
        }

        return new MenuEntry(shapeRenderer, bitmapFont, effect, text, tagText, action);
    }

    private Actor buildVectorButton(ComponentNode node) {
        String effectName = (String) node.properties.get("effect");
        VectorButtonEffect effect = null;
        if (effectRegistry != null && effectName != null) {
            effect = effectRegistry.get(effectName, VectorButtonEffect.class);
        }
        String text = gui.i18n((String) node.properties.getOrDefault("text", ""));
        String onClick = (String) node.properties.get("onClick");

        Runnable action = onClick != null ? () -> gui.dispatchAction(onClick) : null;

        if (shapeRenderer == null || bitmapFont == null) {
            Gdx.app.error("UiFactory", "ShapeRenderer/BitmapFont not set for vector_button");
            return new Group();
        }

        VectorButton btn = new VectorButton(shapeRenderer, bitmapFont, effect, text, action);
        float w = toFloat(node.properties.get("width"), 0);
        float h = toFloat(node.properties.get("height"), 0);
        if (w > 0 || h > 0) btn.setSize(w, h);
        return btn;
    }

    private Actor buildVectorLabel(ComponentNode node) {
        String effectName = (String) node.properties.get("effect");
        VectorLabelEffect effect = null;
        if (effectRegistry != null && effectName != null) {
            effect = effectRegistry.get(effectName, VectorLabelEffect.class);
        }
        String text = gui.i18n((String) node.properties.getOrDefault("text", ""));

        if (bitmapFont == null) {
            Gdx.app.error("UiFactory", "BitmapFont not set for vector_label");
            return new Group();
        }

        VectorLabel label = new VectorLabel(bitmapFont, effect, text);
        // color 属性可覆盖效果的文本颜色（支持 #RRGGBBAA 格式）
        Object color = node.properties.get("color");
        if (color != null) {
            Color c = resolveColor(color.toString());
            if (c != null) label.setTextColor(c);
        }
        float w = toFloat(node.properties.get("width"), 0);
        float h = toFloat(node.properties.get("height"), 0);
        if (w > 0 || h > 0) label.setSize(w, h);
        return label;
    }

    // ==================== list（可滚动列表） ====================

    /**
     * 构建 list（可滚动列表）：ScrollPane 包裹 Table，第一个 child 作为行模板。
     * 通过 dataSource 从 DataProvider 获取数据，每行可点击选中。
     *
     * 支持属性：
     * - dataSource（string）：数据源标识，传给 DataProvider#getData()
     * - onSelect（string）：选中项时派发的 action id，格式为 "actionId:index"
     * - spacing（number）：行间距，默认 0
     * - scrollX / scrollY（boolean）：滚动方向，默认仅纵向滚动
     * - width / height（number）：列表整体尺寸
     */
    private ScrollPane buildList(ComponentNode node) {
        Table content = new Table();
        content.top().left();

        // 第一个 child 作为行模板
        if (!node.children.isEmpty())
            content.setUserObject(node.children.get(0));

        String dataSource = (String) node.properties.get("dataSource");
        String onSelect = (String) node.properties.get("onSelect");
        float spacing = toFloat(node.properties.get("spacing"), 0f);

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

        float w = toFloat(node.properties.get("width"), 0);
        float h = toFloat(node.properties.get("height"), 0);
        if (w > 0 || h > 0) scroll.setSize(w, h);

        // 在 ScrollPane 的 userObject 存储列表运行时配置
        Map<String, Object> listConfig = new HashMap<>();
        listConfig.put("onSelect", onSelect);
        listConfig.put("spacing", spacing);
        listConfig.put("selectedIndex", -1);
        scroll.setUserObject(listConfig);

        // 初始加载数据
        if (dataSource != null && dataProvider != null) {
            List<Map<String, Object>> items = dataProvider.getData(dataSource);
            if (items != null) {
                listConfig.put("itemsData", items);
                renderListItems(scroll);
            }
        }

        return scroll;
    }

    /**
     * 渲染列表行。每次调用会清空并重建所有行（基于当前 itemsData 与 selectedIndex）。
     * 每行注入 inputListener 实现点击选中 + 派发 onSelect 事件。
     */
    public void renderListItems(ScrollPane listActor) {
        Object configObj = listActor.getUserObject();
        if (!(configObj instanceof Map))
            return;
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) configObj;

        Actor content = listActor.getActor();
        if (!(content instanceof Table))
            return;
        Table t = (Table) content;

        Object uo = t.getUserObject();
        if (!(uo instanceof ComponentNode))
            return;
        ComponentNode template = (ComponentNode) uo;

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsData = (List<Map<String, Object>>) config.get("itemsData");
        if (itemsData == null || itemsData.isEmpty())
            return;

        String onSelect = (String) config.get("onSelect");
        float spacing = toFloat(config.get("spacing"), 0f);
        int selectedIndex = config.get("selectedIndex") instanceof Number
                ? ((Number) config.get("selectedIndex")).intValue()
                : -1;

        t.clearChildren();
        t.top().left();

        for (int i = 0; i < itemsData.size(); i++) {
            Map<String, Object> data = itemsData.get(i);
            ComponentNode inst = deepCopy(template);
            substituteProps(inst, data);

            // 注入选中状态到行数据
            if (i == selectedIndex) {
                inst.properties.put("selected", true);
            }

            Actor row = create(inst);
            row.setName((inst.name != null ? inst.name : "list_item") + "_" + i);

            // 点击选择
            final int index = i;
            row.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent e, float x, float y, int p, int b) {
                    return true;
                }

                @Override
                public void touchUp(InputEvent e, float x, float y, int p, int b) {
                    config.put("selectedIndex", index);
                    renderListItems(listActor); // 重渲染以刷新选中高亮
                    if (onSelect != null && !onSelect.isEmpty()) {
                        gui.dispatchAction(onSelect + ":" + index);
                    }
                }
            });

            t.add(row).growX().fillX();
            if (spacing > 0 && i < itemsData.size() - 1)
                t.row().padTop(spacing);
            else
                t.row();
        }
        t.invalidateHierarchy();
    }

    /**
     * 设置列表数据并重新渲染。
     *
     * @param listActor buildList 返回的 ScrollPane
     * @param itemsData 新数据列表
     */
    public void setListData(ScrollPane listActor, List<Map<String, Object>> itemsData) {
        Object configObj = listActor.getUserObject();
        if (!(configObj instanceof Map))
            return;
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) configObj;
        config.put("itemsData", itemsData);
        config.put("selectedIndex", -1); // 重置选中
        renderListItems(listActor);
    }

    /**
     * 获取当前选中行索引，无选中返回 -1。
     */
    public int getListSelectedIndex(ScrollPane listActor) {
        Object configObj = listActor.getUserObject();
        if (!(configObj instanceof Map))
            return -1;
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) configObj;
        return config.get("selectedIndex") instanceof Number
                ? ((Number) config.get("selectedIndex")).intValue()
                : -1;
    }

    /**
     * 程序化选中指定行。
     */
    public void selectListItem(ScrollPane listActor, int index) {
        Object configObj = listActor.getUserObject();
        if (!(configObj instanceof Map))
            return;
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) configObj;
        config.put("selectedIndex", index);
        renderListItems(listActor);
    }

    // ==================== 新矢量控件构建方法 ====================

    private Actor buildVectorImage(ComponentNode node) {
        String effectName = (String) node.properties.get("effect");
        VectorImageEffect effect = null;
        if (effectRegistry != null && effectName != null) {
            effect = effectRegistry.get(effectName, VectorImageEffect.class);
        }
        if (shapeRenderer == null) {
            Gdx.app.error("UiFactory", "ShapeRenderer not set for vector_image");
            return new Group();
        }
        VectorImage img = new VectorImage(shapeRenderer, effect);
        Object color = node.properties.get("color");
        if (color != null) {
            Color c = resolveColor(color.toString());
            if (c != null) img.setTint(c);
        }
        float w = toFloat(node.properties.get("width"), 0);
        float h = toFloat(node.properties.get("height"), 0);
        if (w > 0) img.setSize(w, h > 0 ? h : img.getHeight());
        return img;
    }

    private Actor buildVectorCheckBox(ComponentNode node) {
        String effectName = (String) node.properties.get("effect");
        VectorCheckBoxEffect effect = null;
        if (effectRegistry != null && effectName != null) {
            effect = effectRegistry.get(effectName, VectorCheckBoxEffect.class);
        }
        if (shapeRenderer == null || bitmapFont == null) {
            Gdx.app.error("UiFactory", "ShapeRenderer/BitmapFont not set for vector_checkbox");
            return new Group();
        }
        String text = gui.i18n((String) node.properties.getOrDefault("text", ""));
        boolean checked = Boolean.parseBoolean((String) node.properties.getOrDefault("checked", "false"));
        String onChange = (String) node.properties.get("onChange");
        Runnable action = onChange != null ? () -> gui.dispatchAction(onChange) : null;
        VectorCheckBox cb = new VectorCheckBox(shapeRenderer, bitmapFont, effect, text, checked, action);
        float w = toFloat(node.properties.get("width"), 0);
        float h = toFloat(node.properties.get("height"), 0);
        if (w > 0 || h > 0) cb.setSize(w > 0 ? w : cb.getWidth(), h > 0 ? h : cb.getHeight());
        return cb;
    }

    private Actor buildVectorSlider(ComponentNode node) {
        String effectName = (String) node.properties.get("effect");
        VectorSliderEffect effect = null;
        if (effectRegistry != null && effectName != null) {
            effect = effectRegistry.get(effectName, VectorSliderEffect.class);
        }
        if (shapeRenderer == null) {
            Gdx.app.error("UiFactory", "ShapeRenderer not set for vector_slider");
            return new Group();
        }
        float min = toFloat(node.properties.get("min"), 0f);
        float max = toFloat(node.properties.get("max"), 1f);
        float step = toFloat(node.properties.get("step"), 0.01f);
        float value = toFloat(node.properties.get("value"), min);
        boolean vertical = Boolean.TRUE.equals(node.properties.get("vertical"));
        VectorSlider slider = new VectorSlider(shapeRenderer, effect, min, max, step, vertical);
        slider.setValue(value);
        String onChange = (String) node.properties.get("onChange");
        if (onChange != null) {
            slider.setOnChange(v -> gui.dispatchAction(onChange + ":" + v));
        }
        float w = toFloat(node.properties.get("width"), 0);
        float h = toFloat(node.properties.get("height"), 0);
        if (w > 0 || h > 0) slider.setSize(w > 0 ? w : slider.getWidth(), h > 0 ? h : slider.getHeight());
        return slider;
    }

    private Actor buildVectorProgressBar(ComponentNode node) {
        String effectName = (String) node.properties.get("effect");
        VectorProgressBarEffect effect = null;
        if (effectRegistry != null && effectName != null) {
            effect = effectRegistry.get(effectName, VectorProgressBarEffect.class);
        }
        if (shapeRenderer == null) {
            Gdx.app.error("UiFactory", "ShapeRenderer not set for vector_progressbar");
            return new Group();
        }
        float min = toFloat(node.properties.get("min"), 0f);
        float max = toFloat(node.properties.get("max"), 1f);
        float step = toFloat(node.properties.get("step"), 0.01f);
        float value = toFloat(node.properties.get("value"), min);
        boolean vertical = Boolean.TRUE.equals(node.properties.get("vertical"));
        VectorProgressBar pb = new VectorProgressBar(shapeRenderer, effect, min, max, step, vertical);
        pb.setValue(value);
        float w = toFloat(node.properties.get("width"), 0);
        float h = toFloat(node.properties.get("height"), 0);
        if (w > 0 || h > 0) pb.setSize(w > 0 ? w : pb.getWidth(), h > 0 ? h : pb.getHeight());
        return pb;
    }

    private Actor buildVectorTextField(ComponentNode node) {
        String effectName = (String) node.properties.get("effect");
        VectorTextFieldEffect effect = null;
        if (effectRegistry != null && effectName != null) {
            effect = effectRegistry.get(effectName, VectorTextFieldEffect.class);
        }
        if (shapeRenderer == null || bitmapFont == null) {
            Gdx.app.error("UiFactory", "ShapeRenderer/BitmapFont not set for vector_textfield");
            return new Group();
        }
        String text = (String) node.properties.getOrDefault("text", "");
        VectorTextField tf = new VectorTextField(shapeRenderer, bitmapFont, effect, text);
        if (node.properties.get("messageText") != null) {
            tf.setMessageText(gui.i18n(node.properties.get("messageText").toString()));
        }
        if (node.properties.get("passwordMode") != null) {
            tf.setPasswordMode(Boolean.parseBoolean(node.properties.get("passwordMode").toString()));
        }
        String onChange = (String) node.properties.get("onChange");
        if (onChange != null) {
            tf.setOnChange(v -> gui.dispatchAction(onChange + ":" + v));
        }
        float w = toFloat(node.properties.get("width"), 0);
        float h = toFloat(node.properties.get("height"), 0);
        if (w > 0 || h > 0) tf.setSize(w > 0 ? w : tf.getWidth(), h > 0 ? h : tf.getHeight());
        return tf;
    }

    private Actor buildVectorSelectBox(ComponentNode node) {
        String effectName = (String) node.properties.get("effect");
        VectorSelectBoxEffect effect = null;
        if (effectRegistry != null && effectName != null) {
            effect = effectRegistry.get(effectName, VectorSelectBoxEffect.class);
        }
        if (shapeRenderer == null || bitmapFont == null) {
            Gdx.app.error("UiFactory", "ShapeRenderer/BitmapFont not set for vector_selectbox");
            return new Group();
        }
        VectorSelectBox sb = new VectorSelectBox(shapeRenderer, bitmapFont, effect);
        if (node.properties.get("items") instanceof String) {
            String[] parts = ((String) node.properties.get("items")).split(",");
            for (int i = 0; i < parts.length; i++) parts[i] = parts[i].trim();
            sb.setItems(parts);
        }
        if (node.properties.get("selected") != null) {
            sb.setSelected(node.properties.get("selected").toString());
        }
        String onChange = (String) node.properties.get("onChange");
        if (onChange != null) {
            sb.setOnChange(v -> gui.dispatchAction(onChange + ":" + v));
        }
        float w = toFloat(node.properties.get("width"), 0);
        float h = toFloat(node.properties.get("height"), 0);
        if (w > 0 || h > 0) sb.setSize(w > 0 ? w : sb.getWidth(), h > 0 ? h : sb.getHeight());
        return sb;
    }

    private Actor buildVectorScrollPane(ComponentNode node) {
        String effectName = (String) node.properties.get("effect");
        VectorScrollPaneEffect effect = null;
        if (effectRegistry != null && effectName != null) {
            effect = effectRegistry.get(effectName, VectorScrollPaneEffect.class);
        }
        if (shapeRenderer == null) {
            Gdx.app.error("UiFactory", "ShapeRenderer not set for vector_scrollpane");
            return new Group();
        }
        Actor content = node.children.isEmpty() ? new Group() : create(node.children.get(0));
        VectorScrollPane sp = new VectorScrollPane(shapeRenderer, effect, content);
        boolean sx = node.properties.get("scrollX") != null
                && Boolean.parseBoolean(node.properties.get("scrollX").toString());
        boolean sy = node.properties.get("scrollY") == null
                || Boolean.parseBoolean(node.properties.get("scrollY").toString());
        sp.setScrollingDisabled(!sx, !sy);
        return sp;
    }

    private Actor buildVectorWindow(ComponentNode node) {
        String effectName = (String) node.properties.get("effect");
        VectorWindowEffect effect = null;
        if (effectRegistry != null && effectName != null) {
            effect = effectRegistry.get(effectName, VectorWindowEffect.class);
        }
        if (shapeRenderer == null || bitmapFont == null) {
            Gdx.app.error("UiFactory", "ShapeRenderer/BitmapFont not set for vector_window");
            return new Group();
        }
        VectorWindow win = new VectorWindow(shapeRenderer, bitmapFont, effect,
                gui.i18n((String) node.properties.getOrDefault("title", "")));
        if (node.properties.get("movable") != null)
            win.setMovable(Boolean.parseBoolean(node.properties.get("movable").toString()));
        if (node.properties.get("resizable") != null)
            win.setResizable(Boolean.parseBoolean(node.properties.get("resizable").toString()));
        for (ComponentNode child : node.children) {
            Actor a = create(child);
            win.getContentGroup().addActor(a);
        }
        win.pack();
        return win;
    }

    private Actor buildVectorDialog(ComponentNode node) {
        String effectName = (String) node.properties.get("effect");
        VectorWindowEffect effect = null;
        if (effectRegistry != null && effectName != null) {
            effect = effectRegistry.get(effectName, VectorWindowEffect.class);
        }
        if (shapeRenderer == null || bitmapFont == null) {
            Gdx.app.error("UiFactory", "ShapeRenderer/BitmapFont not set for vector_dialog");
            return new Group();
        }
        VectorDialog dlg = new VectorDialog(shapeRenderer, bitmapFont, effect,
                gui.i18n((String) node.properties.getOrDefault("title", "")),
                gui.i18n((String) node.properties.getOrDefault("text", "")));
        String buttonText = (String) node.properties.get("buttonText");
        if (buttonText != null) {
            String onClick = (String) node.properties.get("onClick");
            dlg.setButton(gui.i18n(buttonText),
                    onClick != null ? () -> gui.dispatchAction(onClick) : dlg::hide);
        }
        return dlg;
    }
}
