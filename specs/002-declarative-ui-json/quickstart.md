# Quickstart: 声明式 UI (JSON)

## 目标

本指南旨在说明如何创建一个简单的 UI 布局文件 (`.json`)，并在游戏中加载它。

## 1. 创建 UI 定义文件

在 `assets/ui/gui/` 目录下创建一个新的 `.json` 文件，例如 `my_screen.json`。

文件内容必须遵循 `component.schema.json` 定义的结构。一个最小的示例如下：

```json
{
  "type": "container",
  "properties": { "fillParent": true },
  "children": [
    {
      "type": "label",
      "properties": { "text": "Hello, Declarative UI!" }
    }
  ]
}
```

## 2. 在代码中加载 UI

在你的 UI 控制器或屏幕类中，注入 `UIFactory` 服务，并调用其 `create(String jsonPath)` 方法：

```java
// In your Screen or UI Controller

private final UIFactory uiFactory;

public void show() {
    Actor root = uiFactory.create("ui/gui/my_screen.json");
    stage.addActor(root);
}
```

## 3. 使用更多组件

下面示例展示 `image / slider / progressBar / textField` 的最小用法（字段含义详见 `data-model.md`）：

```json
{
  "type": "container",
  "properties": { "fillParent": true, "pad": 24 },
  "children": [
    {
      "type": "image",
      "properties": { "drawable": "logo" }
    },
    {
      "type": "slider",
      "properties": {
        "min": 0,
        "max": 100,
        "value": 50,
        "step": 1,
        "onChange": "VOLUME_CHANGE"
      }
    },
    {
      "type": "progressBar",
      "properties": { "min": 0, "max": 1, "value": 0.3, "style": "default" }
    },
    {
      "type": "textField",
      "properties": { "messageText": "Enter name", "onChange": "NAME_CHANGE" }
    }
  ]
}
```

## 4. 绑定事件

要在 JSON 中绑定一个点击事件，只需在按钮的 `properties` 中添加一个 `onClick` 字段：

```json
{
  "type": "button",
  "properties": {
    "text": "mainMenu.exit",
    "onClick": "EXIT_CLICK"
  }
}
```

然后，在你的 UI 控制器中，监听并处理这个动作标识：

```java
// In your UI Controller (e.g., Gui.java)

public void handleAction(String action) {
    if ("EXIT_CLICK".equals(action)) {
        Gdx.app.exit();
    }
}
```
