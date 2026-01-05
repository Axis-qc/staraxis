# Contracts: World Generation & New Game Config

**Feature**: [spec.md](../spec.md)  
**Branch**: `005-hex-world-gen`  
**Created**: 2026-01-05

## Purpose

定义客户端 UI 与逻辑层之间的交互边界（事件/请求模型），避免 UI 直接耦合生成算法。

## Contract 1: Start New Game (配置→生成)

### Request: WorldGenConfig

- `mapSizePresetId`: string（地图大小预设 ID；预设定义半径 R）
- `habitableRatio`: 0.0-1.0（星系格宜居概率）
- `seedText`: string（可为空；为空随机）
- `aiCount`: int（预留；本期不生效）
- `techLevelPresetId`: string（预留；本期不生效）

### Response: WorldMap Summary (最小返回)

- `seedValue`: long（最终使用的数值种子）
- `boundsRadius`: int（最终使用的半径 R）
- `tileCount`: int

> 详细瓦片数据是否需要下发给客户端：首版可直接在客户端请求生成结果；若采用“逻辑层权威/客户端只渲染”，则应由逻辑层返回可序列化的 `tiles` 快照。

## Contract 2: UI Flow

### UI Screen Routing

- `MainMenuScreen`:
  - 点击“新游戏” → 进入 `NewGameConfigScreen`
- `NewGameConfigScreen`:
  - 点击“开始” → 触发 Contract 1
  - 点击“返回” → 回到主菜单
- `WorldScreen`:
  - 接收生成结果并渲染六边形世界

## Contract 3: User Interaction on World

- 鼠标悬停/点击：
  - 输入：屏幕坐标（x,y）
  - 输出：HexCoord（x,y,z） + HexTile（若存在）

## Failure Modes

- 配置非法（如 ratio 越界、seedText 不可解析）：
  - 行为：自动修正为合法范围/默认值，并在 UI 上提示（可选）。
- 生成耗时超出预算：
  - 行为：显示加载态，或降级为异步生成（后续扩展）。
