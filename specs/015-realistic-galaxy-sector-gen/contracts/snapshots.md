# Contracts: Snapshots (shared DTO)

## 1. 设计原则
- 只读、可序列化、可扩展
- 客户端只消费快照；不直接依赖生成器内部对象
- 本项目不要求向后兼容，但字段应避免阻碍未来扩展

## 2. UniverseSnapshot
- `galaxyId: String`
- `radiusR: int`
- `seed: long`（建议保留，便于复现）
- `sectors: List<SectorSnapshot>`

## 3. SectorSnapshot
- `hexQ: int`
- `hexR: int`
- `worldCenterXKm: double`
- `worldCenterYKm: double`
- `worldCenterZKm: double`（可选，默认 0）
- `contentTypeId: String`（数据驱动：`star-system` / `nebula` / `deep-space` 等）
- `starSystemId: String?`（contentTypeId == star-system 时存在，占位符）
- `occupancySource: String?`（调试用：preset/allocated）

## 4. 版本策略
- 本期不做版本字段强制要求。
- 若后续需要网络同步/存档，可在 shared 增加 `snapshotVersion` 字段。
