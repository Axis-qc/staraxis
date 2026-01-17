# Research: 使用 JSON 实现声明式 UI

## 目标

本研究旨在为“使用 JSON 定义 Scene2D UI”方案确定一套清晰、可扩展的 JSON 结构，并选择最合适的解析技术。

## 决策 1：JSON 解析库选择

-   **Decision**: 使用 LibGDX 内置的 `JsonReader` 和 `Json` 工具类。
-   **Rationale**: `JsonReader` 是一个轻量、高效的流式解析器，而 `Json` 类则提供了方便的对象映射功能。这套组合是 LibGDX 的标准配置，无需引入任何第三方依赖，能确保最佳的兼容性和性能。
-   **Alternatives considered**:
    -   **Gson / Jackson**: 功能强大，但属于重量级第三方库，会增加项目的依赖复杂度和打包体积，对于仅用于 UI 定义的场景来说有些过度。

## 决策 2：UI 组件的 JSON 基础结构

-   **Decision**: 采用“类型 + 属性 + 子节点”的通用结构。每个 JSON 对象都代表一个 UI 组件，并包含以下核心字段：
    -   `type`: (String) 组件类型，如 `"container"`, `"label"`, `"button"`。
    -   `name`: (String, Optional) 组件的唯一标识，用于后续在代码中查找或绑定事件。
    -   `properties`: (Object, Optional) 组件的各种属性，如 `size`, `position`, `text`, `font` 等。
    -   `children`: (Array, Optional) 子组件列表，用于构建层级结构。

-   **Rationale**: 这种结构清晰、可扩展，并且非常适合递归地构建 UI 树。新的组件类型可以轻松地通过增加新的 `type` 值来支持。

-   **示例**:
    ```json
    {
      "type": "container",
      "name": "main_menu_window",
      "properties": {
        "size": { "width": "100%", "height": "100%" },
        "fillParent": true
      },
      "children": [
        {
          "type": "label",
          "name": "title_label",
          "properties": {
            "text": "app.title"
          }
        },
        {
          "type": "button",
          "name": "exit_button",
          "properties": {
            "text": "mainMenu.exit",
            "onClick": "EXIT_CLICK"
          }
        }
      ]
    }
    ```
