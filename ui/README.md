# ui - 客户端 UI 系统

基于 LibGDX Scene2D 的自定义矢量 UI 系统，提供菜单、设置、HUD、控件等。**只读展示**，不直接修改 `WorldState`。

## 职责

- **矢量 UI 控件**：`VectorButton`、`VectorLabel`、`VectorWindow`、`VectorSlider` 等全套自定义控件
- **JSON UI**：`UiParser` / `UiFactory` 支持从 JSON 定义动态生成 UI 布局
- **效果系统**：`EffectRegistry` 管理控件动画效果（淡入、滑动、缩放等）
- **国际化**：`I18nService` 支持多语言
- **主题系统**：`UiTheme` 统一管理颜色、字体、样式
- **开发者控制台**：`DevConsole` 提供运行时调试能力
- **设置管理**：`GameSettings` 管理游戏设置持久化

## 技术栈

- **LibGDX Scene2D**：UI 框架基础
- **LibGDX FreeType**：字体渲染
- **ShapeDrawer**（`space.earlygrey:shapedrawer`）：矢量图形绘制
- **Jackson**：JSON 解析
- **json-schema-validator**：JSON UI 定义校验
- **SLF4J**：日志门面

## 矢量 UI 系统

所有 `Vector*` 控件（如 `VectorButton`、`VectorLabel`）使用 ShapeDrawer 进行矢量渲染，不依赖位图图片，支持运行时主题切换和效果动画。

## 关键包

| 包         | 说明                                   |
| ---------- | -------------------------------------- |
| `widgets`  | 矢量 UI 控件实现                       |
| `effects`  | 控件动画效果                           |
| `screens`  | 游戏内各界面（设置、世界创建、HUD 等） |
| `json`     | JSON UI 解析与工厂                     |
| `theme`    | 主题与样式定义                         |
| `settings` | 游戏设置持久化                         |
| `i18n`     | 国际化服务                             |
| `console`  | 开发者控制台                           |
| `layout`   | 屏幕布局管理                           |
| `debug`    | UI 调试覆盖层                          |
