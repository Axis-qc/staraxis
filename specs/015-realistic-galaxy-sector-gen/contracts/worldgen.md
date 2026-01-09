# Contracts: World Generation (015)

> 本文是模块接口契约（非 Web API）。

## 1. 输入

### 1.1 GameStartSettings（开局设置）
- `galaxyRadiusR: int`
- `seed: long`
- `contentRatios: Map<contentTypeId, double>`（UI 保证和为 1.0）

### 1.2 Presets（预设 JSON）
- `List<GalaxyPreset>`
- 冲突规则：同 HexCoord 被多个预设占用时，按 loadOrder（或加载顺序）“后来者覆盖”

### 1.3 ContentTypeRegistry（内容类型注册）
- `Map<contentTypeId, SectorContentTypeDefinition>`

## 2. 输出

### 2.1 UniverseSnapshot
- 见 `contracts/snapshots.md`

## 3. 生成顺序（必须）

1) 应用预设：占用一批星区（fixed 或 random，random 必须可复现）
2) 对剩余星区按比例分配内容类型（确定性：HexCoord 排序 + seed+HexCoord 派生随机）
3) 根据内容类型生成对应内容数据（本期恒星系仅占位符）

## 4. 错误处理

- 若预设占用导致剩余不足：按 spec 采用“尽量分配，不足截断”，并输出日志/调试信息（不得崩溃）

## 5. 可复现性

- 同配置 + 同 seed：输出 UniverseSnapshot 必须字节级一致（或字段级完全一致）
- 分配与并行/遍历顺序无关
