# staraxis.ui.json（声明式 UI JSON）

本目录用于支持“声明式 UI”：通过 JSON 描述 UI 结构，再由解析器与工厂在运行时构建为 LibGDX Scene2D 的 `Actor` 树。

## 目录内文件说明

- `ComponentNode.java`
  - **作用**：UI 组件树的轻量数据结构（POJO）。
  - **字段**：
    - `type`（组件类型）
    - `name`（组件名称，可选，用于调试/查找）
    - `properties`（属性表，key 为字符串，value 为 boolean/number/string/object）
    - `children`（子节点列表）

- `UiParser.java`
  - **作用**：将 UI 定义 JSON 解析为 `ComponentNode` 树。
  - **关键特性**：
    - **Schema 校验优先**：解析前会使用 `ui/src/main/resources/ui/component.schema.json` 进行校验。
    - **Gdx-First IO**：推荐用 `parseInternal()` 从 `Gdx.files.internal()` 读取，避免 workingDir 差异。
    - **失败策略**：校验/解析失败返回 `null` 并记录日志（schema 加载失败属于致命错误会抛异常）。

- `UiFactory.java`
  - **作用**：把 `ComponentNode` 树构建为 Scene2D `Actor` 树，并应用属性、绑定交互事件。
  - **职责边界**：仅负责 UI 表现与事件转发（通过 `Gui#dispatchAction(String)`），不承载任何会改变游戏结果的规则计算。

## UI JSON 基本结构

每个节点结构如下：

```json
{
  "type": "label",
  "name": "title",
  "properties": {
    "text": "ui.main.title"
  },
  "children": []
}
```

- `type`（必填）：组件类型，大小写不敏感。
- `name`（可选）：会设置到 `Actor#setName()`，用于调试定位。
- `properties`（可选）：组件属性表。
- `children`（可选）：子节点数组。

## UiFactory 支持的组件类型（type）与格式

> 说明：下文所有“支持属性”均来自 `UiFactory` 的实际实现；未识别的 `type` 会创建一个空 `Group` 作为占位。

### 1) 通用属性（所有组件）

- `name`：设置 `Actor#setName(name)`。
- `properties.visible`（可选，boolean/string）：是否可见。

### 2) container（Table）

- **type**：`container`
- **构建**：`new Table()`
- **支持属性**：
  - `fillParent`（boolean）：`table.setFillParent(true)`
  - `align`（string）：包含 `top/bottom/left/right/center` 的组合，例如 `topLeft`、`bottomRight`、`center`
  - `pad`（number/string）：`table.pad(pad)`
  - `background`（string）：Skin drawable 名称
  - `horizontal`（boolean）：
    - `true`：子项不自动换行
    - `false`：每个 child 之后 `table.row()`
- **子节点**：
  - 每个 child 会被 `create()` 构建为 Actor 并 `table.add(actor)`
  - 每个 child 可通过 `properties.cell` 配置布局（见“Cell 属性”）

#### Cell 属性（properties.cell）

在子节点 `properties` 内使用 `cell`（object）配置 Table Cell：

- `align`（string）：包含 `top/bottom/left/right/center` 的组合
- `expand`（boolean）
- `grow`（boolean）
- `expandX` / `expandY`（boolean）
- `fill` / `fillX` / `fillY`（boolean）
- `colspan`（number/string）
- `pad` / `padTop` / `padBottom` / `padLeft` / `padRight`（number/string）
- `width` / `height`（number/string）

### 3) stack（Stack）

- **type**：`stack`
- **构建**：`new Stack()`
- **子节点**：`children` 中每个 child 依次 `stack.add(create(child))`

### 4) position（WidgetGroup 定位容器）

- **type**：`position`
- **构建**：自定义 `WidgetGroup`，在 `layout()` 中计算子控件位置
- **支持属性**：
  - `align`（string，默认 `topRight`）
  - `x`/`y`（number）：显式坐标；若同时提供则忽略 `align`
  - `width`/`height`（number）：显式尺寸；未提供时使用子控件 `prefWidth/prefHeight` 并对宽度做 clamp
- **子节点**：仅使用第一个 child 作为内容（多余 child 会被忽略）

### 5) scroll（ScrollPane）

- **type**：`scroll`
- **构建**：`new ScrollPane(content, skin)`
- **支持属性**：
  - `scrollX`（boolean，默认 `false`）：允许横向滚动
  - `scrollY`（boolean，默认 `true`）：允许纵向滚动
- **子节点**：仅使用第一个 child 作为 content

### 6) window（Window）

- **type**：`window`
- **构建**：`new Window(title, skin)`
- **支持属性**：
  - `title`（string）：标题，会走 `gui.i18n()` 翻译
  - `movable`（boolean）：可拖拽
  - `modal`（boolean）：模态
  - `resizable`（boolean）：可缩放
- **子节点**：每个 child `window.add(create(child)).row()`，最后 `window.pack()`

### 7) dialog（Dialog）

- **type**：`dialog`
- **构建**：`new Dialog(title, skin)`
- **支持属性**：
  - `title`（string）：标题，会走 `gui.i18n()` 翻译
  - `movable`（boolean）：可拖拽
  - `modal`（boolean）：模态
  - `resizable`（boolean）：可缩放
- **子节点**：添加到 `dialog.getContentTable()`，并对每个 child 的 `cell` 属性生效，最后 `dialog.pack()`

### 8) verticalgroup（VerticalGroup）

- **type**：`verticalgroup`
- **构建**：`new VerticalGroup()`
- **支持属性**：
  - `spacing`（number/string）：子项间距
  - `align`（string）：包含 `top/bottom/left/right/center` 的组合
- **子节点**：每个 child `group.addActor(create(child))`

### 9) repeat（Repeat 容器）

- **type**：`repeat`
- **构建**：`Table container = new Table()`（top/left）
- **设计**：
  - `repeat` 的第一个 child 作为“模板节点”，会存入 `container.setUserObject(templateNode)`
  - 实际渲染由 `UiFactory#renderRepeatItems(...)` 完成

### 10) label（Label）

- **type**：`label`
- **构建**：`new Label(gui.i18n(text), skin)`
- **支持属性**：
  - `text`（string）：文本，会走 `gui.i18n()`
  - `alignment`（string）：包含 `left/center/right`
  - `color`（string）：`#RRGGBB/#RRGGBBAA` 或 Skin color 名称

### 11) button（TextButton）

- **type**：`button`
- **构建**：`new TextButton(gui.i18n(text), skin)`
- **支持属性**：
  - `text`（string）：按钮文字，会走 `gui.i18n()`
  - `background`（string）：Skin drawable 名称（会覆盖 up/over/down/focused）
  - `onClick`（string）：点击动作 id，触发 `gui.dispatchAction(onClick)`

### 12) image（Image）

- **type**：`image`
- **构建**：`new Image(drawable)`
- **支持属性**：
  - `drawable`（string）：Skin drawable 名称

### 13) slider（Slider）

- **type**：`slider`
- **构建**：`new Slider(min, max, step, vertical, skin)`
- **支持属性**：
  - `min`/`max`/`step`（number/string）
  - `value`（number/string）：初始值（缺省为 min）
  - `vertical`（boolean）：是否垂直
  - `onChange`（string）：变化动作 id，触发 `actionId + ":" + sliderValue`

### 14) selectbox（SelectBox<String>）

- **type**：`selectbox`
- **构建**：`new SelectBox<>(skin)`
- **支持属性**：
  - `items`（string）：逗号分隔，例如 `"Low,Medium,High"`
  - `selected`（string）：默认选中项
  - `onChange`（string）：变化动作 id，触发 `actionId + ":" + selectedItem`

### 15) progressbar（ProgressBar）

- **type**：`progressbar`
- **构建**：`new ProgressBar(min, max, step, vertical, skin)`
- **支持属性**：
  - `min`/`max`/`step`（number/string）
  - `value`（number/string）：初始值
  - `vertical`（boolean）：是否垂直

### 16) textfield（TextField）

- **type**：`textfield`
- **构建**：`new TextField(text, skin)`
- **支持属性**：
  - `text`（string）：初始文本
  - `messageText`（string）：提示文本，会走 `gui.i18n()`
  - `passwordMode`（boolean）：密码模式
  - `onChange`（string）：变化动作 id，触发 `actionId + ":" + tf.getText()`

## 事件字段（onClick/onChange）格式约定

- `onClick`：按钮点击后触发 `Gui#dispatchAction(onClick)`。
- `onChange`：控件值变化后触发 `Gui#dispatchAction(onChange + ":" + value)`。

具体 `actionId` 的解析与执行逻辑不在本目录内（由 `staraxis.ui.Gui` 负责）。

## 示例

### 示例 1：主菜单布局（container + label + button）

```json
{
  "type": "container",
  "name": "root",
  "properties": {
    "fillParent": true,
    "align": "center",
    "pad": 16
  },
  "children": [
    {
      "type": "label",
      "name": "title",
      "properties": {
        "text": "ui.main.title",
        "alignment": "center",
        "color": "#FFFFFFFF",
        "cell": {
          "padBottom": 12
        }
      }
    },
    {
      "type": "button",
      "name": "start",
      "properties": {
        "text": "ui.main.start",
        "onClick": "mainMenu:start",
        "cell": {
          "growX": true,
          "fillX": true,
          "padBottom": 8
        }
      }
    },
    {
      "type": "button",
      "name": "settings",
      "properties": {
        "text": "ui.main.settings",
        "onClick": "mainMenu:settings",
        "cell": {
          "growX": true,
          "fillX": true
        }
      }
    }
  ]
}
```

## 注意事项

- `UiParser` 会先做 schema 校验；如果你新增了 `type` 或属性字段，需要同步更新 `component.schema.json`。
- `UiFactory` 侧采取“容错渲染”策略：
  - 未识别的 `type` 不会抛异常，会用空 `Group` 占位。
  - drawable 缺失只会打日志。
## 特殊节点：ref（外部文件引用）

`ref` 节点用于将一个外部 JSON 文件的内容内联到当前位置，降低单个 JSON 文件的嵌套层级。

### 语法

```json
{ "ref": "ui/gameui/settings/tabs/general.json" }
```

### 行为

1. 解析器在 `toNode()` 阶段遇到 `ref` 节点时，会通过 `Gdx.files.internal(ref)` 加载并解析目标文件。
2. 目标文件的根节点替换当前 `ref` 节点，递归处理其子节点。
3. `ref` 节点的 `name` 会覆盖被引用文件的根节点 `name`（用于 Actor 查找）。
4. `ref` 节点的 `properties` 和 `children` 会追加到被引用节点上（扩展机制）。

### 注意

- 目标文件必须是合法的 UI JSON（通过 schema 校验）。
- 不支持循环引用。
- `ref` 在解析阶段展开，工厂侧（`UiFactory`）无需感知。

### 示例：settings.json 拆分

主文件使用 `ref` 引用子模块：

```json
{
  "type": "container",
  "name": "settings_root",
  "children": [
    { "ref": "ui/gameui/settings/left_panel.json" },
    {
      "type": "position",
      "children": [
        { "ref": "ui/gameui/settings/tabs/general.json" },
        { "ref": "ui/gameui/settings/tabs/graphics.json" }
      ]
    }
  ]
}
```

子文件 `tabs/general.json` 是完整的合法 JSON：

```json
{
  "type": "scroll",
  "name": "settings_scroll",
  "children": [
    {
      "type": "container",
      "children": [
        { "type": "label", "properties": { "text": "UI缩放" } }
      ]
    }
  ]
}
```