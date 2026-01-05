# Research: Localization & UI Beautification

## 1. Parallax Background Implementation
- **Decision**: 使用 `ParallaxBackground` 类，包含多个 `ParallaxLayer`。每一层使用不同的 `scrollSpeed`。
- **Rationale**: LibGDX 社区标准做法。通过设置不同的速度系数（如背景远星 0.1，中景星云 0.3），可以营造深邃的宇宙感。
- **Alternatives considered**: 
    - 简单的静态大图：缺乏深度感，不符合科幻主题。
    - 粒子系统生成背景：性能开销略高，且难以控制特定的视觉构图。

## 2. gdx-freetype Integration
- **Decision**: 在 `LocalizationService` 初始化时，使用 `FreeTypeFontGenerator` 加载 `AlibabaPuHuiTi-3-65-Medium.ttf`，并生成不同尺寸的 `BitmapFont`。
- **Rationale**: TTF 字体体积小（约 10MB），比预生成的位图字体更灵活，支持完整的 6,000+ 常用汉字。
- **Alternatives considered**:
    - `Hiero` 预生成位图字体：中文字符集太大，会导致生成的图片文件过多（几百张 1024x1024），内存压力巨大。

## 3. Real-time I18N Updating
- **Decision**: 采用 **观察者模式 (Observer Pattern)**。`LocalizationService` 维护一个 `ChangeListener` 列表。当语言切换时，遍历列表并调用 `onLanguageChanged()`。
- **Rationale**: 允许 UI 组件（如按钮、标签）在不重启 Screen 的情况下更新其文本。这种方式比全局事件总线更轻量，且易于调试。
- **Alternatives considered**:
    - 重启整个游戏：用户体验极差。
    - 仅重启当前 Screen：简单但可能会丢失当前 Screen 的临时状态（如设置界面中未保存的修改）。

## 4. LibGDX Preferences Storage
- **Decision**: 存储键名为 `language`，值为 `zh_CN` 或 `en_US`。
- **Rationale**: 符合 LibGDX `I18NBundle` 的 Locale 字符串格式。
- **Alternatives considered**:
    - 存储整数索引：可读性差，且难以扩展。
