# Data Model: Localization & UI Settings

## Entities

### Settings (Persistent)
存储在 LibGDX `Preferences` ("staraxis-settings") 中的数据。

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| language | String | "zh_CN" | 当前选择的语言标识符 (Locale string) |
| musicVolume | Float | 0.8 | 背景音乐音量 (0.0 - 1.0) |
| soundVolume | Float | 0.8 | 音效音量 (0.0 - 1.0) |

### LocalizationBundle (Runtime)
从 `.properties` 文件加载的键值对。

| Key | Example (zh_CN) | Example (en_US) |
|-----|-----------------|-----------------|
| main_menu_new_game | 新游戏 | New Game |
| main_menu_load_game | 加载游戏 | Load Game |
| main_menu_multiplayer | 多人游戏 | Multiplayer |
| main_menu_settings | 设置 | Settings |
| main_menu_exit | 退出 | Exit |
| settings_language | 语言 | Language |
| settings_apply | 应用 | Apply |
| settings_back | 返回 | Back |

## Validation Rules
- `language` 必须是有效的 Locale 字符串（如 "zh_CN", "en_US"）。
- 音量值必须在 [0.0, 1.0] 范围内。

## State Transitions
1. **Initial Load**: 读取 `Preferences` -> 加载对应 `I18NBundle` -> 应用到 UI。
2. **Language Switch**: 用户选择新语言 -> 更新 `Preferences` -> 重新加载 `I18NBundle` -> 触发所有 UI 监听器更新文本。
