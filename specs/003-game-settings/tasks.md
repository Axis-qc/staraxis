# 003 Game Settings - 任务清单

> 目标：提供“设置界面 + 设置持久化 + 运行时/重启生效策略”，并严格遵循分层边界：
> - **UI/表现层** 负责展示、输入与平台配置应用（窗口/渲染参数）。
> - **核心模拟层** 不包含任何 UI 或平台设置逻辑。

## P0 - 必做（MVP）

1. **定义设置项范围与生效策略** [Done]
   - 分辨率（是否允许运行时切换/是否需重启）
   - 全屏/窗口（若纳入）
   - VSync
   - FPS 上限
   - 画质预设（可先只保存不生效）

2. **设置数据模型与持久化** [Done]
   - 文件位置：`gamedata/settings.json`
   - 增加 `schemaVersion`
   - 默认值策略（文件缺失/字段缺失/类型错误时回退）
   - 保存时机（点击保存/即时保存）

3. **Settings UI（声明式 UI JSON）** [Done]
   - 新增 `assets/ui/gameui/settings/settings.json`
   - 控件：下拉/按钮/开关（可用按钮模拟）
   - 页面按钮：保存、返回

4. **SettingsScreen 与路由** [Done]
   - 新增 `SettingsScreen`（UI 层）
   - 主菜单 `设置` 按钮 action 改为 `OPEN_SETTINGS`
   - `Gui.dispatchMainMenuAction` 增加处理：打开设置界面、返回主菜单

5. **应用设置（平台/表现层）**
   - VSync：立即生效
   - FPS 上限：立即生效（或明确需重启）
   - 分辨率/全屏：第一版建议“保存后重启生效”，并在 UI 明示

## P1 - 增强

6. **设置项 UI 交互优化**
   - 当前值高亮/禁用
   - 修改后提示“未保存”
   - 保存成功提示

7. **支持 Mod 扩展设置（预留）**
   - 预留 `gamedata/mods/*/settings.json` 合并策略（可先不实现）

## P2 - 工具与测试

8. **手工验证清单**
   - 首次启动无 settings 文件 → 自动生成默认 settings
   - 修改设置 → 保存 → 重启/即时生效验证
   - settings.json 字段损坏 → 回退默认并写入修复

9. **开发者控制台命令（可选）**
   - `settings dump`
   - `settings reload`
   - `settings apply`
