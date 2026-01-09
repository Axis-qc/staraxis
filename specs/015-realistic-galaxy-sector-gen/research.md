# 015 - 真实比例六边形星区与内容分配：技术调研

> 本文由 `/speckit.plan` 生成，用于将实现关键的不确定点固化为明确决策，降低返工风险。

## 1. 1ly → 世界单位（km）换算与 014 对齐

- **Decision**: 1 光年（1ly）在逻辑世界单位中以 **km** 表示，采用天文学常用常量：`1 ly = 9_460_730_472_580.8 km`。
- **Rationale**: spec 已假设世界单位为 km，并且需要量化验证（SC-002）。使用标准常量便于测试与复现。
- **Alternatives considered**:
  - 将 1ly 设为任意缩放单位（不真实）→ 与“真实比例”目标冲突。
  - 使用米/浮点缩放 → 与既有 014 km 基准不一致。

## 2. HexCoord 的稳定排序规则

- **Decision**: HexCoord 使用稳定字典序排序：先 `q` 后 `r`（或项目既定字段顺序），升序；若使用 cube 坐标，则按 `(x, y, z)` 的固定字典序。
- **Rationale**: 该排序仅用于“确定性处理”的输入稳定化，越简单越不易出错，且跨平台一致。
- **Alternatives considered**:
  - 按环序/螺旋序 → 更复杂、收益不明显。

## 3. seed + HexCoord 派生随机：实现方式

- **Decision**: 使用纯函数派生：`derivedSeed = hash64(seed, q, r)`，再用 `SplittableRandom(derivedSeed)`（或等价无状态 RNG）生成该星区的伪随机序列。
- **Rationale**:
  - 与遍历顺序/并发无关（满足 spec Clarifications）。
  - SplittableRandom 适合“可分裂/可复现”的场景。
- **Alternatives considered**:
  - 共享 Random 并按遍历顺序 next → 并发/顺序会改变结果。
  - ThreadLocalRandom → 不可复现。

## 4. 内容分配算法（比例 + 确定性）

- **Decision**: 采用“配额 + 打分排序”算法：
  1) 在“剩余星区”集合上，先计算每种内容类型的目标数量（配额）：`quota = round(ratio * remainingCount)`，并调整使总和等于 remainingCount。
  2) 对每个星区计算一个确定性随机分数 `score = rng.nextDouble()`。
  3) 按 score 排序后，按配额分段赋值；若发生“剩余不足截断”，按固定顺序（恒星系→星云→深空）进行裁剪。
- **Rationale**:
  - 统计意义上接近比例（满足 SC-003）。
  - 完全确定性，且易于解释与测试。
- **Alternatives considered**:
  - 单次抽样（按概率独立决定类型）→ 误差较大，N=200 时可能波动 > ±10%。

## 5. JSON 数据驱动：schema 方向

- **Decision**:
  - 内容类型注册表（示例字段）：`typeId`（字符串）、`displayName`、`debugColor`/`iconKey`（客户端渲染映射用）。
  - GalaxyPreset（示例字段）：`presetId`、`placement`（fixed/random）、`hexCoords`（fixed）、`count` + `constraints`（random）、`priority`/`loadOrder`（用于冲突覆盖顺序）。
- **Rationale**: 满足数据驱动与 Mod 扩展，避免硬枚举。
- **Alternatives considered**:
  - 仅代码注册 → 违背数据驱动原则。

## 6. 快照结构复用与字段

- **Decision**: 优先复用 shared 已存在的 `UniverseSnapshot`/`SectorSnapshot`，补齐必须字段：
  - HexCoord
  - worldCenter（或可由客户端计算，但为简化验收建议直接下发）
  - contentTypeId（字符串）
  - starSystemId（占位，若类型为 star-system）
- **Rationale**: 与既有快照/序列化体系一致，符合 C/S 分离。
- **Alternatives considered**:
  - 客户端自行根据 HexCoord 计算 worldCenter → 可行但会分散关键逻辑与验收路径；若做需在 plan 的 contracts 中明确。
