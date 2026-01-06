# Contracts: Stellar World Generation & New Game Config

**Feature**: [spec.md](../spec.md)  
**Branch**: `007-stellar-planet-gen`  
**Created**: 2026-01-06

## Purpose

定义客户端 UI 与逻辑层之间的交互边界（事件/请求模型），避免 UI 直接耦合世界生成算法与数据结构细节。

## Contract 1: Start New Game (配置→生成)

### Request: WorldGenConfig

- `mapSizePresetId`: string（地图大小预设 ID；预设定义六边形半径 R；封闭边界）
- `seedText`: string（可为空；为空随机）
- `habitableRatio`: 0.0-1.0（宜居比例；仅对 `galaxy` 区块生效）
- `starDensity`: 0.0-1.0（恒星密度）
- `planetComplexity`: 0.0-1.0（行星复杂度/数量倾向）
- `nebulaRatio`: 0.0-1.0（星云区块占比）

### Response: WorldMap Summary (最小返回)

- `seedValue`: long（最终使用的数值种子）
- `boundsRadius`: int（最终使用的半径 R）
- `tileCount`: int
- `sectorCounts`: map<string,int>（key 为 `galaxy`/`deep_space`/`nebula`）
- `galaxyTileCount`: int
- `starCount`: int
- `planetCount`: int
- `starsPerSystemMinMax`: string（可选；如 "min=1,max=3"）

> 详细瓦片/星体数据是否需要下发给客户端：若采用“逻辑层权威/客户端只渲染”，则应由逻辑层返回可序列化的 `tiles` 快照；首版也可在单机模式下由客户端直接持有生成结果引用，但必须保持 UI 与生成逻辑的分层边界。

## Contract 2: UI Flow

### UI Screen Routing

- `MainMenuScreen`:
  - 点击“新游戏” → 进入 `NewGameConfigScreen`
- `NewGameConfigScreen`:
  - 点击“开始” → 触发 Contract 1
  - 点击“返回” → 回到主菜单
- `WorldScreen`:
  - 接收生成结果并渲染六边形世界（俯视可读）

## Contract 3: World Interaction (拾取/查看)

- 鼠标悬停/点击：
  - 输入：屏幕坐标（x,y）
  - 输出：HexCoord（x,y,z） + HexTile（若存在）
  - 若 sectorType 为 `galaxy`：可额外提供 StarSystem 的摘要信息（恒星数、行星总数等）

## Failure Modes

- 配置非法（比例越界、seedText 为空/过长/包含非法字符）：
  - 行为：自动修正为合法范围/默认值，并在 UI 上提示（可选）。
- 生成耗时超出预算：
  - 行为：显示加载态，或降级为异步生成（后续扩展）。
