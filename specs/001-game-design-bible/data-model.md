# Phase 1 Data Model: 001-game-design-bible（文档域数据模型）

> 注意：此 data-model 指“文档产物本身的结构化模型”，不是游戏运行时数据模型。

## Entities

### DesignBibleDocument

- **Purpose**: Design Bible 的结构化集合，用于组织章节、链接、版本与约束。
- **Core fields**:
  - `documentId`：唯一标识（用于引用）
  - `title`：文档标题
  - `versionTag`：版本标记（与 Git tag/commit 对齐）
  - `authoringScope`：覆盖范围（系统域列表）
  - `outOfScope`：明确范围外清单（对应 FR-018）
  - `sourceReferences`：引用来源与差异记录（对应 FR-016）

### Section

- **Purpose**: 文档章节（总览或细节文档皆可）。
- **Core fields**:
  - `sectionId`
  - `title`
  - `ownerDomain`：所属主题域（coordinates/economy/combat/...）
  - `normativeLevel`：约束级别（must/should/example）
  - `links`：指向其他章节/外部文档的链接

### Term

- **Purpose**: 术语条目（中英对照、禁用同义词治理）。
- **Core fields**:
  - `termCn`：规范中文名
  - `termEn`：规范英文名
  - `abbr`：缩写（可选）
  - `definition`：定义
  - `scope`：适用范围（例如：星区视角/经济/战斗）
  - `deprecatedSynonyms`：旧称/禁用同义词列表（对应 FR-023）
  - `firstMentionRule`：首次出现旧称的标注规则

### UnitConvention

- **Purpose**: 单位与换算约定。
- **Core fields**:
  - `quantityName`：量纲名称（distance/time/population/...) 
  - `internalUnit`：内部单位（例如 AU、百万人）
  - `displayUnit`：显示单位（K/M/G、px 等）
  - `conversionFormula`：换算公式（可复制）
  - `precisionRule`：精度/舍入规则

### CoordinateConvention

- **Purpose**: 坐标系与精度治理（对应 FR-020）。
- **Core fields**:
  - `worldCoordinateDef`：世界坐标定义
  - `localCoordinateDef`：恒星系本地坐标定义
  - `transformRules`：转换规则
  - `usageDomains`：相机/渲染/交互/物理等使用域
  - `rebaseTriggers`：重定位触发条件
  - `errorBudget`：误差上限与验收口径

### TimeConvention

- **Purpose**: 时间推进与时间停止口径（对应 FR-006/FR-021）。
- **Core fields**:
  - `tickDefinition`：基础时间粒度
  - `dayCycleRule`：24h=1d，1h=1s 等
  - `settlementCadence`：结算周期（按日）
  - `timeStopPolicy`：分域冻结规则
  - `exceptions`：例外清单

### NamingConvention

- **Purpose**: 命名规则与示例覆盖治理（对应 FR-012）。
- **Core fields**:
  - `identifierLanguage`：标识符英文
  - `caseRules`：camelCase/PascalCase/...
  - `forbiddenPatterns`：禁止项（拼音/随意缩写等）
  - `examples`：示例列表（覆盖指定类别）

## Relationships

- `DesignBibleDocument 1..n -> Section`
- `Section 0..n -> Term`（章节引用术语）
- `Section 0..n -> UnitConvention / CoordinateConvention / TimeConvention`
- `NamingConvention 0..n -> examples`（示例条目可引用 Term）

## Validation Rules (from spec)

- **VR-001**: 必须存在“核心总览文档”并能链接到所有细节文档（FR-015）
- **VR-002**: 若引用输入文档，需记录来源与差异（FR-016）
- **VR-003**: 关键术语必须提供中英对照（FR-019）
- **VR-004**: 必须存在旧称/禁用同义词治理规则（FR-023）
- **VR-005**: 经济日结算必须给出可复算示例（FR-022）
- **VR-006**: 坐标防抖必须给出可执行说明与误差预算（FR-020）
