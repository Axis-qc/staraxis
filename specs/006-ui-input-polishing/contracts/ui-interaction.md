# Contract: UI Skin & Interaction Events (UI皮肤与交互事件协议)

**Feature**: [006-ui-input-polishing](../spec.md)
**Status**: DRAFT
**Created**: 2026-01-05

## 1. UI Skin Styles (皮肤样式要求)

所有 UI 控件必须支持以下状态的视觉反馈，通过 `Skin` 配置文件或程序化增强实现。

### Slider (滑动条)
- **Background**: 半透明深蓝色背景，细边框。
- **Knob**: 霓虹青色，圆角或科技感几何形状。
- **Focus**: 悬停时 Knob 亮度增加 20%。

### TextField (文本框)
- **Background**: 极深背景色，支持 `cursor` 闪烁。
- **Selection**: 半透明天蓝色覆盖。
- **Focus**: 边框由灰色转为霓虹青色，并带有轻微的发光 (Glow) 效果。

### SelectBox (下拉框)
- **Background**: 与 TextField 一致。
- **List Style**: 展开列表项悬停时背景变为霓虹洋红色。

## 2. Input Events (输入事件定义)

输入控制器应当分发或响应以下逻辑指令：

| Event Name | Type | Payload | Description |
|------------|------|---------|-------------|
| `CAMERA_MOVE` | Logic | `Vector2 velocity` | 控制镜头按当前速度平移 |
| `CAMERA_ZOOM` | Logic | `float amount, Vector2 center` | 控制以特定点为中心的缩放 |
| `INPUT_FOCUS_GAIN` | State | `Actor source` | 锁定键盘镜头控制 |
| `INPUT_FOCUS_LOST` | State | `Actor source` | 释放键盘镜头控制 |

## 3. Localization Keys (本地化键值规范)

新增文本必须遵循以下前缀：
- `config_`: 用于新游戏配置界面。
- `status_`: 用于加载和后台处理状态。
- `error_`: 用于用户交互错误提示。
