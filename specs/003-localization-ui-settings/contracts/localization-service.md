# Localization Service Contract

## Service Interface: `LocalizationService`

负责管理游戏的本地化状态、资源加载和变更通知。

### Methods

#### `void init()`
- **Description**: 初始化服务，加载默认语言或从 `Preferences` 读取保存的语言。
- **Side Effects**: 加载 `I18NBundle`，初始化 `FreeTypeFontGenerator`。

#### `String get(String key)`
- **Description**: 根据键获取当前语言的翻译文本。
- **Parameters**: `key` - 资源文件中的键。
- **Returns**: 翻译后的文本。如果键不存在，返回键名。

#### `void setLanguage(String localeCode)`
- **Description**: 切换游戏语言。
- **Parameters**: `localeCode` - 如 "zh_CN" 或 "en_US"。
- **Side Effects**: 更新 `Preferences`，重新加载 `I18NBundle`，触发 `LanguageChangeListener`。

#### `void addListener(LanguageChangeListener listener)`
- **Description**: 注册语言变更监听器。

### Interface: `LanguageChangeListener`

#### `void onLanguageChanged()`
- **Description**: 当语言发生变更时触发。UI 组件应在此方法中更新其显示的文本。

---

## UI Component Contract: `AnimatedButton`

### Properties
- `hoverScale`: 悬停时的缩放比例 (默认 1.1)。
- `animationDuration`: 动画持续时间 (默认 0.15s)。

### Events
- `onHoverEnter`: 开始缩放动画和发光效果。
- `onHoverExit`: 恢复原始状态。
