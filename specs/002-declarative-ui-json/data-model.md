# Data Model: 声明式 UI (JSON)

> 本数据模型详细描述了用于定义 Scene2D UI 的 JSON 结构。

## 文件头字段

- **`schemaVersion`** (Integer, Required) : 声明当前 JSON 文件遵循的数据模型版本。从 `1` 起递增；若未来出现破坏性调整，将提升该值。

## 通用约定

- **单位**: 所有数值（`size.width/height`, `position.x/y`, `pad` 等）均为 **屏幕像素(px)**。
- **百分比字符串**: 形如 `"50%"` 时，基准为当前 **父容器尺寸**；若组件为根节点，则基准为 **屏幕尺寸**。
- **尺寸继承**: 若组件未显式指定 `size`, 默认跟随父容器；根节点无父时默认占满屏幕。
- **Skin 资源取值**:
  - `font` / `color` / `style` / `background` / `drawable` 等字段均引用 **当前激活的 `Skin` 实例** 中同名资源。
  - 若未指定自定义 Skin, 系统加载 `assets/ui/skin/defaultSkin.json` 作为默认资源库。
  - `color` 字段额外支持 `#RRGGBB` / `#RRGGBBAA` 十六进制直写，优先级高于 Skin 颜色名。

### 单位与坐标体系

- **绝对像素 (`px`)**：字段为 `number` 时，解释为物理像素，不受 DPI 缩放影响。
- **相对百分比 (`%`)**：当字段写成字符串，如 `"50%"`，基于父容器当前布局尺寸；若组件为根节点，则基于屏幕可视区域。
- **自动值 (`auto`)**：仅适用于 `size.*` 字段，表示使用对应 Actor 的 `prefWidth/prefHeight`。

坐标系统遵循项目全局规范：原点位于 **屏幕左下角**，`X` 向右为正，`Y` 向上为正。

- `position.x/y` 仅在绝对定位组件（如 `group`, `image`）生效；Table 等流式布局容器内部不建议同时设置绝对坐标。
- 允许负值，例如 `position.y: -32`，表示相对于基准向反方向偏移。

**Z 顺序**

- 默认由 JSON 层级 + 声明顺序决定（后声明绘制在上层）。
- 若组件 `properties` 提供 `zIndex`，解析器应调用 `Actor#setZIndex()` 覆盖默认顺序。

### 属性命名与 Skin 解析规则

| 属性字段 | 对应 Skin 资源类型 | 解析顺序 (高 → 低) |
|----------|------------------|--------------------|
| `font` | `BitmapFont` / `FreeTypeFontGenerator` | 1. 组件 `properties.font` 显式指定 <br>2. `style` 中关联字体 <br>3. Skin 默认字体 (`default-font`) |
| `color` | `Color` | 1. 十六进制直写 `#RRGGBB(AA)` <br>2. Skin 中同名颜色 <br>3. `WHITE` |
| `style` | `LabelStyle` / `ButtonStyle` 等任意 *Style | 1. `properties.style` 显式指定 <br>2. Skin 中同组件默认 Style (`default`) |
| `drawable` / `background` | `Drawable` | 1. `properties.drawable/background` 指定 <br>2. `style` 中引用 <br>3. 无背景 |

**命名规范**

- 资源 ID 使用 **kebab-case** 或 **camelCase**，与 Skin 中键保持一致；禁止使用中文或空格。
- 新增资源必须在 `defaultSkin.json` 或自定义 Skin 中注册，并放置于 `assets/ui/` 及子目录。
- 若解析器在指定 Skin 中找不到资源，将输出告警并使用回退值；不会导致应用崩溃。

**示例**

```json
{
  "type": "label",
  "properties": {
    "text": "mainMenu.exit",
    "style": "menu-label",          // Style 将解析到 Skin.menu-label
    "color": "#FFDD88",             // 直接使用十六进制颜色
    "font": "bold-28"               // 指定字体，需在 Skin 中存在
  }
}
```

- **国际化 (i18n)**:
  - 所有 `text` 字段若形如 `"mainMenu.exit"`，则视为 i18n key。
  - 解析器通过 `I18nLoader.get(key)` 获取本地化字符串；若无对应 key，则原样显示并在日志警告。

---

## 事件系统

任何组件都可在 `properties` 中声明下列事件动作标识：

| 字段 | 触发时机 |
|------|----------|
| `onClick`  | 在组件收到 `InputEvent.Type.touchDown -> touchUp` 且点击未拖动时 |
| `onChange` | 数值或文本变化时（`slider`, `textField` 等） |
| `onHover`  | 指针进入/离开组件时 |
| `onKey`    | 组件拥有输入焦点且触发键盘事件时 |

解析器将把事件绑定至 **统一动作分发接口**：

```java
public interface UiActionBus {
    void dispatch(String actionId, Actor source, @Nullable Object payload);
}
```

- 默认实现 `GuiActionBus` 注入到解析器，负责路由到具体业务逻辑。  
- 若 `actionId` 未注册，则在 `gamedata/logs/ui-parse.log` 打警告。

---

## 布局：Table Cell 属性

对于 `container` (Table) 子组件，可在子节点 `properties.cell` 对象中声明以下字段，用来映射 `Cell` API：

| 字段 | 类型 | 说明 |
|------|------|------|
| `row` | Integer | 指定行号（可选，默认自动换行） |
| `colspan` | Integer | 跨列数 |
| `align` | String | `center` `left` `right` `top` `bottom` 组合 |
| `expand` | Boolean | 是否占据剩余空间 |
| `grow` | Boolean | 类似 `expand` 但允许多个组件均分空间 |

示例：

```json
{
  "type": "container",
  "children": [
    {
      "type": "label",
      "properties": {
        "text": "UserName",
        "cell": { "align": "right", "row": 0 }
      }
    },
    {
      "type": "textField",
      "properties": {
        "cell": { "expand": true, "grow": true, "row": 0 }
      }
    }
  ]
}
```

---

## 核心对象：Component

每个 UI 元素都是一个 `Component` 对象，其基础结构如下：

```json
{
  "type": "string",
  "name": "string (optional)",
  "properties": {},
  "children": []
}
```

### 字段说明

- **`type`** (String, Required): 组件的类型。解析器将根据此字段决定创建哪个 Scene2D `Actor`。
- **`name`** (String, Optional): 组件的唯一标识符。用于在代码中查找特定组件或绑定事件。
- **`properties`** (Object, Optional): 一个包含该组件所有属性的键值对对象。
- **`children`** (Array<Component>, Optional): 一个包含所有子组件的数组，用于构建层级结构。

---

## 支持的组件类型 (`type`)

以下组件列表将随着需求扩展，可向下兼容。

### 1. `container`
- **映射到**: `com.badlogic.gdx.scenes.scene2d.ui.Table`
- **常用属性**:
  - `size`: `{ "width": "number|string", "height": "number|string" }`
  - `position`: `{ "x": number, "y": number }`
  - `fillParent`: `boolean`
  - `pad`: `number`
  - `background`: `string`

### 2. `label`
- **映射到**: `com.badlogic.gdx.scenes.scene2d.ui.Label`
- **常用属性**:
  - `text`: `string`
  - `font`: `string`
  - `color`: `string`
  - `alignment`: `string`

### 3. `button`
- **映射到**: `com.badlogic.gdx.scenes.scene2d.ui.TextButton`
- **常用属性**:
  - `text`: `string`
  - `style`: `string`
  - `onClick`: `string`

### 4. `image`
- **映射到**: `com.badlogic.gdx.scenes.scene2d.ui.Image`
- **常用属性**:
  - `drawable`: `string`
  - `scale`: `string` (`"fit" | "fill" | "stretch" | "none"`)

### 5. `slider`
- **映射到**: `com.badlogic.gdx.scenes.scene2d.ui.Slider`
- **常用属性**:
  - `min`: `number`
  - `max`: `number`
  - `value`: `number`
  - `step`: `number`
  - `vertical`: `boolean`
  - `onChange`: `string`

### 6. `progressBar`
- **映射到**: `com.badlogic.gdx.scenes.scene2d.ui.ProgressBar`
- **常用属性**:
  - `min`: `number`
  - `max`: `number`
  - `value": `number`
  - `style": `string`

### 7. `textField`
- **映射到**: `com.badlogic.gdx.scenes.scene2d.ui.TextField`
- **常用属性**:
  - `text": `string`
  - `messageText": `string`
  - `passwordMode": `boolean`
  - `font": `string`
  - `color": `string`
  - `onChange": `string`

---

## 示例 JSON

```json
{
  "schemaVersion": 1,
  "type": "container",
  "name": "main_menu_root",
  "properties": { "fillParent": true, "pad": 24 },
  "children": [
    { "type": "label", "properties": { "text": "app.title", "alignment": "center" } },
    { "type": "button", "properties": { "text": "mainMenu.start", "onClick": "START_GAME" } },
    { "type": "slider", "properties": { "min": 0, "max": 100, "value": 50, "onChange": "VOLUME_CHANGE" } },
    { "type": "button", "properties": { "text": "mainMenu.exit", "onClick": "EXIT_CLICK" } }
  ]
}
```
