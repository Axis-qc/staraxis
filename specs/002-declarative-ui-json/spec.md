# 002 – 声明式 UI（JSON）

> 让所有界面布局从 **Java 代码** 迁移到 **数据驱动**，并通过开发者控制台实现热重载与调试效率最大化。

---

## 0. 元信息
| 项 | 值 |
|----|----|
| Feature Branch | `002-declarative-ui-json` |
| Author | specs team |
| Created | 2026-01-17 |
| Status | Draft |

---

## 1. 目标
1. **数据驱动**：所有 Scene2D UI 由 `.json` 文件描述，无需重新编译即可调整。
2. **热重载**：在开发模式下修改 JSON 后 <1 s 内刷新界面。
3. **可测试**：内置开发者控制台，可发送 `reload_ui` 等命令验证效果。

---

## 2. 用户场景
| ID | 场景 | 验证 |
|----|------|------|
| US-01 | 我修改 `main_menu.json` 新增按钮 | 重启或 `reload_ui main_menu.json` 后界面出现新按钮 |
| US-02 | 我在滑块上拖动，想收到数值变化事件 | `onChange` 触发，日志打印 payload |
| US-03 | 我在控制台输入 `ui_cache info` | 返回当前缓存用量 |

---

## 3. 数据模型（摘要）
完整模型见 `data-model.md`；顶层示例：
```json
{
  "schemaVersion": 1,
  "type": "container",
  "properties": { "fillParent": true },
  "children": [
    { "type": "label", "properties": { "text": "app.title" } },
    { "type": "button", "properties": { "text": "mainMenu.start", "onClick": "START_GAME" } }
  ]
}
```

---

## 4. 体系结构
```
┌─────────────┐   JSON    ┌──────────────┐
│ UiHotReloader│◀────────▶│  UiCache      │
└──────┬──────┘ invalidate └──────┬───────┘
       │                           │Actor tree
   WatchService                 ┌──▼───┐
   file events   ┌──────────┐   │UiFactory│
                 │UiParser  │──▶│(recursive)│
                 └──────────┘   └──────────┘
```

### 4.1 UiParser
- 根据 `component.schema.json` 校验 → 失败写 `gamedata/logs/ui-parse.log`。
- 解析后返回 `ComponentNode` 树供工厂消费。

### 4.2 UiFactory
- 递归创建 Scene2D `Actor`；绑定事件到 `UiActionBus`。

### 4.3 UiCache
| 模式 | 策略 |
|------|------|
| DEV  | 始终 bypass 缓存，或大小=0 |
| PROD | LRU(32) 条目，弱引用 |

### 4.4 UiHotReloader
- DEV 模式启动 `WatchService` 监听 `assets/ui/gui`。
- 变更后调用 `invalidate(path)` 并若当前 Screen 关联则即时 `reloadScreen()`。

---

## 5. Functional Requirements
| 编号 | MUST/SHOULD | 描述 |
|------|-------------|------|
| FR-01 | MUST | 提供 `UiParser` & `UiFactory` 支持 container/label/button/image/slider/progressBar/textField |
| FR-02 | MUST | JSON 顶层字段 `schemaVersion`=1；未知版本拒绝加载 |
| FR-03 | MUST | 在解析前做 JSON Schema 校验；失败写日志并停止加载 |
| FR-04 | MUST | 支持事件字段 `onClick/onChange/onHover/onKey` 并统一派发至 `UiActionBus` |
| FR-05 | SHOULD | DEV 模式文件监听并热重载；PROD 关闭监听 |
| FR-06 | SHOULD | 控制台支持 `reload_ui`, `ui_cache info|clear`, `log_tail`, `toggle_hot_reload` |

---

## 6. Validation & Error Logging
```text
[gamedata/logs/ui-parse.log]
2026-02-01 10:02:15 | main_menu.json | ERROR | /children/1/properties/text: required property missing
```
- PROD 启动若存在任何 ERROR → 终止启动。
- DEV 显示 ErrorActor 占位并继续运行。

---

## 7. DEV 控制台
详见下表：
| 功能 | 说明 |
|------|------|
| 唤出 | `~` 或 `F12` 切换显示 |
| 输入框 | 支持命令历史(`↑/↓`)、补全(`Tab`) |
| 输出 | 滚动日志；最新行高亮 |
| 关键命令 | `reload_ui [file|all]`, `ui_cache info`, `ui_cache clear`, `toggle_hot_reload on/off`, `log_tail 20` |

---

## 8. 里程碑
1. MVP：解析 + UiFactory + 手动重启可加载 UI。
2. DEV 热重载 + UiCache。
3. 控制台 MVP (`reload_ui all`).
4. 扩展命令 & 自动测试基架。

---

## 9. 参考
- `component.schema.json`
- `data-model.md`
