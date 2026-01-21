package staraxis.ui.json;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import staraxis.ui.Gui;

import java.util.Locale;

/**
 * UI 工厂：将 {@link ComponentNode}（声明式 UI 节点树）构建为 Scene2D 的 {@link Actor} 树。
 *
 * 设计约束：
 * 1. **仅负责 UI 表现**：该类只做 UI 组件创建、属性应用与事件转发，不承载任何会改变游戏结果的规则计算。
 * 2. **数据驱动**：UI 的结构与属性来自 JSON（由 {@link UiParser} 解析），这里按 `type` 分发到不同的 build
 * 方法。
 * 3. **事件统一出口**：交互事件通过 `onClick` / `onChange` 等属性拼成 actionId，最终交给
 * {@link Gui#dispatchAction(String)}。
 *
 * 约定：
 * - `node.type`（组件类型）大小写不敏感。
 * - `node.properties`（属性表）值类型来自 JSON 解析：boolean/number/string/object（见
 * {@link UiParser}）。
 */
public class UiFactory {

    private final Skin skin;
    private final Gui gui;

    public UiFactory(Gui gui) {
        this.gui = gui;
        this.skin = gui.get(Skin.class);
    }

    /**
     * 根据组件定义节点创建对应的 Scene2D Actor。
     *
     * 约定：
     * - `node.type`（组件类型）用于分发到不同的 build 方法。
     * - 未识别的 `type` 会创建一个空的 {@link Group} 作为占位，避免 UI 解析失败直接崩溃。
     */
    public Actor create(ComponentNode node) {
        // NOTE: 这里不做严格校验（例如 node == null / type == null 等），避免 UI 系统因单个配置错误直接崩溃。
        // 解析器侧（UiParser）负责 schema 校验；工厂侧尽量做到“容错渲染”。
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
        applyColorAnim(actor, node);
        return actor;
    }

    /**
     * 将字符串安全地转换为小写。
     *
     * 作用：
     * - 允许 `type`（组件类型）大小写不敏感。
     * - 在 `type` 缺失时返回空串，最终走 default 分支创建占位 Actor。
     */
    private String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }

    /**
     * 应用所有 Actor 通用的属性。
     *
     * 当前支持：
     * - `name`（用于调试定位与 Actor 查找）
     * - `visible`（是否可见）
     */
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

    /**
     * 应用颜色动画（两色往复）。
     *
     * JSON 格式：
     * - `properties.colorAnim`（object）
     * - `from`（string）：起始颜色，支持 `#RRGGBB/#RRGGBBAA` 或 Skin color 名称
     * - `to`（string）：目标颜色，支持 `#RRGGBB/#RRGGBBAA` 或 Skin color 名称
     * - `duration`（number）：从 from 到 to 的耗时（秒）
     *
     * 说明：
     * - 该动画通过 Scene2D 的 Action 在 `act(delta)` 中持续更新 `actor.getColor()`。
     * - 若配置非法会直接忽略（只打日志），不会影响 UI 渲染。
     */
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

        // NOTE: 以 from 作为初始颜色，确保动画起点一致。
        actor.setColor(from);

        // NOTE: 移除同类动画，避免重复叠加导致颜色抖动。
        com.badlogic.gdx.utils.Array<Action> actions = actor.getActions();
        for (int i = actions.size - 1; i >= 0; i--) {
            if (actions.get(i) instanceof PingPongColorAction) {
                actions.removeIndex(i);
            }
        }
        actor.addAction(new PingPongColorAction(from, to, duration));
    }

    /**
     * 在两种颜色之间往复插值的 Action。
     */
    public static class PingPongColorAction extends Action {
        private final Color from;
        private final Color to;
        private final float duration;
        private float time;

        public PingPongColorAction(Color from, Color to, float duration) {
            // NOTE: 防止外部复用 Color 导致被改写，这里做拷贝。
            this.from = new Color(from);
            this.to = new Color(to);
            this.duration = duration;
        }

        @Override
        public boolean act(float delta) {
            if (actor == null)
                return true;

            time += delta;
            float phase = time / duration;

            // ping-pong：0..1..0..1...
            float t = phase % 2f;
            if (t > 1f)
                t = 2f - t;

            actor.getColor().set(
                    from.r + (to.r - from.r) * t,
                    from.g + (to.g - from.g) * t,
                    from.b + (to.b - from.b) * t,
                    from.a + (to.a - from.a) * t);
            return false;
        }
    }

    /**
     * 构建 Label。
     *
     * 支持属性：
     * - `text`（文本；会走 i18n 翻译）
     * - `alignment`（left/center/right）
     * - `color`（颜色；支持 #RRGGBBAA 或 skin color 名称）
     */
    private Label buildLabel(ComponentNode node) {
        Label label = new Label(gui.i18n(node.properties.getOrDefault("text", "").toString()), skin);
        applyLabel(node, label);
        return label;
    }

    /**
     * 将 Label 专有属性应用到实例上。
     */
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

    /**
     * 构建 Table（在 JSON 中用 `type=container` 表示）。
     *
     * 支持属性：
     * - `fillParent`（是否铺满父容器）
     * - `align`（top/bottom/left/right/center 的组合）
     * - `pad`（整体内边距）
     * - `background`（Skin drawable 名称）
     * - `horizontal`（true 时不自动换行；false 时每个 child 后 table.row()）
     */
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

    /**
     * 解析并应用 Table 对齐（`align`）。
     *
     * 示例："topLeft"、"bottomRight"、"center"。
     */
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

    /**
     * 应用子节点的 cell 布局属性。
     *
     * 约定：
     * - 在子节点 properties 中使用 `cell`（Map）承载 Cell 配置。
     * - 所有数值类字段最终按 float 解析。
     *
     * 支持字段（节选）：
     * - `align`
     * - `expand`/`expandX`/`expandY`
     * - `grow`
     * - `fill`/`fillX`/`fillY`
     * - `colspan`
     * - `pad`/`padTop`/`padBottom`/`padLeft`/`padRight`
     * - `width`/`height`
     */
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

    /**
     * 构建 Stack：用于将多个 Actor 叠放在同一位置。
     *
     * 子节点：
     * - `children` 中的每个节点会被依次 add 到 Stack。
     */
    private Stack buildStack(ComponentNode node) {
        Stack stack = new Stack();
        for (ComponentNode child : node.children) {
            stack.add(create(child));
        }
        return stack;
    }

    /**
     * 构建 Position 容器：将唯一子节点按屏幕/父容器对齐定位。
     *
     * 支持属性：
     * - `align`（默认 topRight）
     * - `x`/`y`（显式坐标；若提供则忽略 align 自动定位）
     * - `width`/`height`（显式尺寸；若不提供则使用子控件 pref 尺寸并做 clamp）
     */
    private WidgetGroup buildPosition(ComponentNode node) {
        // NOTE: position 容器当前只支持一个子节点；多余子节点会被忽略。
        final Actor child = node.children.isEmpty() ? new Group() : create(node.children.get(0));
        child.setTouchable(Touchable.enabled);

        final String align = node.properties.getOrDefault("align", "topRight").toString();
        final float x = toFloat(node.properties.get("x"), Float.NaN);
        final float y = toFloat(node.properties.get("y"), Float.NaN);
        final float cfgWidth = toFloat(node.properties.get("width"), Float.NaN);
        final float cfgHeight = toFloat(node.properties.get("height"), Float.NaN);

        // NOTE: 这里的自动宽度 clamp 属于“UI 预览/开发阶段”的经验参数，用于避免窗口过宽影响观感。
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

    /**
     * 构建 ScrollPane。
     *
     * 支持属性：
     * - `scrollX`（是否允许横向滚动；默认 false）
     * - `scrollY`（是否允许纵向滚动；默认 true）
     *
     * 子节点：
     * - 仅使用第一个 child 作为 content。
     */
    private ScrollPane buildScroll(ComponentNode node) {
        Actor content = node.children.isEmpty() ? new Group() : create(node.children.get(0));
        ScrollPane scroll = new ScrollPane(content, skin);
        scroll.setTouchable(Touchable.enabled);

        Object scrollX = node.properties.get("scrollX");
        Object scrollY = node.properties.get("scrollY");
        boolean sx = scrollX != null && Boolean.parseBoolean(scrollX.toString());
        boolean sy = scrollY == null || Boolean.parseBoolean(scrollY.toString());
        scroll.setScrollingDisabled(!sx, !sy);

        // NOTE: 这里统一关闭渐隐/回弹，避免滚动条状态变化影响布局与交互手感。
        scroll.setFadeScrollBars(false);
        scroll.setScrollbarsOnTop(false);
        scroll.setOverscroll(false, false);
        return scroll;
    }

    /**
     * 构建 Window。
     *
     * 支持属性：
     * - `title`（标题；会走 i18n 翻译）
     * - `movable`（可拖拽）
     * - `modal`（模态）
     * - `resizable`（可缩放）
     */
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

    /**
     * 构建 Dialog。
     *
     * 支持属性：
     * - `title`（标题；会走 i18n 翻译）
     * - `movable`（可拖拽）
     * - `modal`（模态）
     * - `resizable`（可缩放）
     */
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

    /**
     * 构建 VerticalGroup。
     *
     * 支持属性：
     * - `spacing`（子项间距）
     * - `align`（top/bottom/left/right/center 组合）
     */
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

    /**
     * 构建 Repeat 容器。
     *
     * 设计：
     * - `repeat` 本身是一个容器，真正的“子项模板”存放在第一个 child 节点。
     * - 模板节点会暂存在 `userObject` 中，后续由
     * {@link #renderRepeatItems(Group, java.util.List, String, String)}
     * 按数据展开渲染。
     */
    private Group buildRepeat(ComponentNode node) {
        Table container = new Table();
        container.top().left();
        if (!node.children.isEmpty()) {
            container.setUserObject(node.children.get(0));
        }
        return container;
    }

    /**
     * 将 repeat 模板按 items 展开为若干行。
     *
     * 行为：
     * - 对模板进行 deep copy，替换模板中字符串属性的占位符：`${item}`、`${selected}`。
     * - 可选：将 `onClick` 中的 `${action}` 替换为 `actionPrefix:item`。
     */
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

    /**
     * 构建 Image。
     *
     * 支持属性：
     * - `drawable`（Skin drawable 名称）
     */
    private Actor buildImage(ComponentNode node) {
        Object drawable = node.properties.get("drawable");
        if (drawable != null) {
            Drawable d = resolveDrawable(drawable.toString());
            if (d != null)
                return new Image(d);
        }
        return new Image();
    }

    /**
     * 构建 Button（当前实现为 TextButton）。
     *
     * 支持属性：
     * - `text`（按钮文字；会走 i18n 翻译）
     * - `background`（可选：覆盖按钮各状态的 drawable）
     * - `onClick`（点击动作 id；触发 {@link Gui#dispatchAction(String)}）
     */
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

    /**
     * 构建 Slider。
     *
     * 支持属性：
     * - `min`/`max`/`step`（范围与步进）
     * - `value`（初始值）
     * - `vertical`（是否垂直）
     * - `onChange`（值变化动作 id；最终触发 `actionId:value`）
     */
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

    /**
     * 构建 SelectBox<String>。
     *
     * 支持属性：
     * - `items`（逗号分隔的字符串列表）
     * - `selected`（默认选中项）
     * - `onChange`（选择变化动作 id；最终触发 `actionId:selected`）
     */
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

    /**
     * 构建 ProgressBar。
     *
     * 支持属性：
     * - `min`/`max`/`step`（范围与步进）
     * - `value`（初始值）
     * - `vertical`（是否垂直）
     */
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

    /**
     * 构建 TextField。
     *
     * 支持属性：
     * - `text`（初始文本）
     * - `messageText`（提示文本；会走 i18n 翻译）
     * - `passwordMode`（密码模式）
     * - `onChange`（文本变化动作 id；最终触发 `actionId:text`）
     */
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

    /**
     * 将 JSON 属性值转换为 float。
     *
     * 说明：
     * - JSON 解析阶段可能已经把 number 转成了 float，但这里仍做一次兜底（例如外部直接塞入字符串）。
     */
    private float toFloat(Object v, float def) {
        if (v == null)
            return def;
        try {
            return Float.parseFloat(v.toString());
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * 从 Skin 中解析 drawable。
     *
     * 失败时只记录日志并返回 null，避免 UI 直接崩溃。
     */
    private Drawable resolveDrawable(String name) {
        try {
            return skin.getDrawable(name);
        } catch (Exception e) {
            Gdx.app.error("UiFactory", "Drawable not found in Skin: " + name);
            return null;
        }
    }

    /**
     * 解析颜色。
     *
     * 支持：
     * - 形如 `#RRGGBB` / `#RRGGBBAA` 的十六进制颜色
     * - Skin 中定义的颜色名称
     */
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

    /**
     * 将数值限制在区间内。
     */
    private float clamp(float v, float min, float max) {
        if (v < min)
            return min;
        if (v > max)
            return max;
        return v;
    }
}
