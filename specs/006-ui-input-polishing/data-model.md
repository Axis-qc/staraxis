# Data Model: UI & Input Polishing

**Feature**: [006-ui-input-polishing](../spec.md)
**Branch**: `006-ui-input-polishing`
**Created**: 2026-01-05

## Overview

本数据模型涵盖：UI 皮肤样式定义、输入映射配置以及本地化键值对结构。

## Entities

### 1) UISkin (UI 皮肤定义)

| Field | Type | Rules |
|------|------|-------|
| background | Drawable | 半透明背景颜色 (0.02, 0.02, 0.05, 0.8) |
| borderColor | Color | 霓虹青色 (0.0, 1.0, 1.0, 1.0) |
| highlightColor | Color | 霓虹洋红色 (1.0, 0.0, 1.0, 1.0) |
| borderWidth | float | 细线边框 (1px - 2px) |
| font | BitmapFont | 引用 `LocalizationService` 提供的 FreeType 字体 |

### 2) InputState (输入控制器状态)

| Field | Type | Rules |
|------|------|-------|
| velocity | Vector2 | 当前镜头平移速度，受惯性衰减影响 |
| targetZoom | float | 目标缩放级别 |
| friction | float | 摩擦系数，用于控制滑动停止速度 |
| isIntercepted | boolean | 当 UI 获取焦点时为 true，拦截所有 WASD 操作 |

### 3) LocalizationBundle (本地化资源)

| Key Prefix | Scope | Examples |
|------------|-------|----------|
| `main_menu_` | 主菜单文本 | `main_menu_new_game` |
| `config_` | 新游戏配置界面 | `config_map_size`, `config_generating` |
| `world_` | 世界界面调试/交互 | `world_hovered_coord`, `world_zoom_level` |

## Validation

- 所有 `messages.properties` 中的键必须在 `messages_en.properties` 中有对应项。
- `velocity` 必须在每一帧根据 `friction` 乘以 `delta time` 进行衰减。
- `isIntercepted` 状态切换必须即时生效。
