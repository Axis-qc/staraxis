# Tasks: 真实天文单位系统重构

**Feature Branch**: `011-astronomical-units`  
**Created**: 2026-01-07  
**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Summary

本任务列表将真实天文单位系统重构功能拆解为可执行的原子任务，按用户故事优先级组织。所有任务遵循项目规范：全中文注释、严格 CS 分离、数据驱动配置、禁止硬编码。

**总任务数**: 109  
**用户故事数**: 5 (P1: 3个, P2: 2个)  
**并行机会**: 多个独立类可并行实现  
**MVP 范围**: User Story 1 (建立天文单位标准系统)

---

## Dependencies & Story Completion Order

```
Phase 1 (Setup)
    ↓
Phase 2 (Foundational: AstronomicalUnit, UnitConverter)
    ↓
Phase 3 [US1] (天文单位标准系统)
    ↓
Phase 4 [US2] (星区大小) ──┐
    ↓                      │
Phase 5 [US3] (轨道大小) ──┤ (可并行，但建议顺序执行)
    ↓                      │
Phase 6 [US4] (恒星大小) ──┤
    ↓                      │
Phase 7 [US5] (行星大小) ──┘
    ↓
Phase 8 (Polish: 迁移工具、集成、测试)
```

**关键依赖**:
- US2, US3, US4, US5 都依赖 US1（需要 AstronomicalUnit 和 UnitConverter）
- US3, US4, US5 可并行实现（不同实体类型）
- 迁移工具和集成任务必须在所有用户故事完成后执行

---

## Phase 1: Setup (项目初始化)

**目标**: 创建项目目录结构和配置文件框架

- [X] T001 创建天文单位系统包目录结构 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/`
- [X] T002 创建核心逻辑包目录结构 `core/src/main/java/com/staraxis/game/core/world/astronomical/`
- [X] T003 创建测试包目录结构 `core/src/test/java/com/staraxis/game/core/world/astronomical/`
- [ ] T004 创建客户端渲染包目录结构 `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/astronomical/`
- [X] T005 创建天文单位系统配置文件 `assets/i18n/astronomical-units-config.properties`
- [X] T006 创建星区大小配置文件 `assets/i18n/sector-size-config.properties`
- [X] T007 创建轨道大小配置文件 `assets/i18n/orbit-size-config.properties`
- [X] T008 创建恒星大小配置文件 `assets/i18n/star-size-config.properties`
- [X] T009 创建行星大小配置文件 `assets/i18n/planet-size-config.properties`
- [X] T010 创建可视化缩放配置文件 `assets/i18n/visual-scale-config.properties`

---

## Phase 2: Foundational (基础类实现)

**目标**: 实现天文单位系统的基础类，为所有用户故事提供支撑

### AstronomicalUnit 类

- [X] T011 [P] 创建 AstronomicalUnit 类框架 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/AstronomicalUnit.java`
- [X] T012 [P] 实现 AstronomicalUnit 内部单位字段和缩放因子常量 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/AstronomicalUnit.java`
- [X] T013 [P] 实现 AstronomicalUnit.fromAU() 工厂方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/AstronomicalUnit.java`
- [X] T014 [P] 实现 AstronomicalUnit.fromLightYears() 工厂方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/AstronomicalUnit.java`
- [X] T015 [P] 实现 AstronomicalUnit.fromParsecs() 工厂方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/AstronomicalUnit.java`
- [X] T016 [P] 实现 AstronomicalUnit.fromInternalUnits() 工厂方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/AstronomicalUnit.java`
- [X] T017 [P] 实现 AstronomicalUnit.toAU() 转换方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/AstronomicalUnit.java`
- [X] T018 [P] 实现 AstronomicalUnit.toLightYears() 转换方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/AstronomicalUnit.java`
- [X] T019 [P] 实现 AstronomicalUnit.toParsecs() 转换方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/AstronomicalUnit.java`
- [X] T020 [P] 实现 AstronomicalUnit.add() 加法运算方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/AstronomicalUnit.java`
- [X] T021 [P] 实现 AstronomicalUnit.subtract() 减法运算方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/AstronomicalUnit.java`
- [X] T022 [P] 实现 AstronomicalUnit.multiply() 乘法运算方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/AstronomicalUnit.java`
- [X] T023 [P] 实现 AstronomicalUnit.divide() 除法运算方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/AstronomicalUnit.java`
- [X] T024 [P] 实现 AstronomicalUnit 溢出检查和异常处理 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/AstronomicalUnit.java`
- [X] T025 [P] 创建 ArithmeticOverflowException 异常类 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/ArithmeticOverflowException.java`

### UnitConverter 类

- [X] T026 [P] 创建 UnitConverter 工具类框架 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/UnitConverter.java`
- [X] T027 [P] 实现 UnitConverter 转换常数定义（AU_TO_KM, LY_TO_AU, PC_TO_AU） `shared/src/main/java/com/staraxis/game/shared/world/astronomical/UnitConverter.java`
- [X] T028 [P] 实现 UnitConverter.auToLightYears() 方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/UnitConverter.java`
- [X] T029 [P] 实现 UnitConverter.lightYearsToAU() 方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/UnitConverter.java`
- [X] T030 [P] 实现 UnitConverter.auToParsecs() 方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/UnitConverter.java`
- [X] T031 [P] 实现 UnitConverter.parsecsToAU() 方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/UnitConverter.java`
- [X] T032 [P] 实现 UnitConverter.convert() 通用转换方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/UnitConverter.java`

### 基础测试

- [X] T033 [P] 创建 AstronomicalUnitTest 测试类 `core/src/test/java/com/staraxis/game/core/world/astronomical/AstronomicalUnitTest.java`
- [X] T034 [P] 实现 AstronomicalUnitTest 工厂方法测试 `core/src/test/java/com/staraxis/game/core/world/astronomical/AstronomicalUnitTest.java`
- [X] T035 [P] 实现 AstronomicalUnitTest 转换方法测试 `core/src/test/java/com/staraxis/game/core/world/astronomical/AstronomicalUnitTest.java`
- [X] T036 [P] 实现 AstronomicalUnitTest 算术运算测试 `core/src/test/java/com/staraxis/game/core/world/astronomical/AstronomicalUnitTest.java`
- [X] T037 [P] 实现 AstronomicalUnitTest 溢出检查测试 `core/src/test/java/com/staraxis/game/core/world/astronomical/AstronomicalUnitTest.java`
- [X] T038 [P] 实现 AstronomicalUnitTest 确定性测试 `core/src/test/java/com/staraxis/game/core/world/astronomical/AstronomicalUnitTest.java`
- [X] T039 [P] 创建 UnitConverterTest 测试类 `core/src/test/java/com/staraxis/game/core/world/astronomical/UnitConverterTest.java`
- [X] T040 [P] 实现 UnitConverterTest 单位转换精度测试（99.9%以上） `core/src/test/java/com/staraxis/game/core/world/astronomical/UnitConverterTest.java`

---

## Phase 3: [US1] 建立天文单位标准系统

**目标**: 完成天文单位系统的核心实现，支持单位转换和确定性计算

**独立测试标准**: 创建标准单位系统后，验证所有距离和大小的计算都基于天文单位，且转换关系正确。可以独立测试：定义单位系统 → 验证单位转换 → 测试距离计算。

- [X] T041 [US1] 创建 AstronomicalUnitSystem 系统类框架 `core/src/main/java/com/staraxis/game/core/world/astronomical/AstronomicalUnitSystem.java`
- [X] T042 [US1] 实现 AstronomicalUnitSystem.loadFromConfig() 配置加载方法 `core/src/main/java/com/staraxis/game/core/world/astronomical/AstronomicalUnitSystem.java`
- [X] T043 [US1] 实现 AstronomicalUnitSystem.validate() 系统验证方法 `core/src/main/java/com/staraxis/game/core/world/astronomical/AstronomicalUnitSystem.java`
- [X] T044 [US1] 填充天文单位系统配置文件内容 `assets/i18n/astronomical-units-config.properties`
- [X] T045 [US1] 创建 AstronomicalUnitSystemTest 集成测试 `core/src/test/java/com/staraxis/game/core/world/astronomical/AstronomicalUnitSystemTest.java`
- [X] T046 [US1] 实现 AstronomicalUnitSystemTest 配置加载测试 `core/src/test/java/com/staraxis/game/core/world/astronomical/AstronomicalUnitSystemTest.java`
- [X] T047 [US1] 实现 AstronomicalUnitSystemTest 确定性计算验证测试 `core/src/test/java/com/staraxis/game/core/world/astronomical/AstronomicalUnitSystemTest.java`

---

## Phase 4: [US2] 重新定义星区大小

**目标**: 基于真实宇宙尺度重新定义星区大小，默认 1 星区 = 1 光年 = 63,241 AU

**独立测试标准**: 定义星区大小后，验证星区能够合理容纳恒星系，且大小符合真实宇宙比例。可以独立测试：定义星区大小 → 生成星区 → 验证大小和分布。

- [X] T048 [US2] 创建 SectorSizeDefinition 类 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/SectorSizeDefinition.java`
- [X] T049 [US2] 实现 SectorSizeDefinition 字段和方法（getSizeInAU, getSizeInLightYears, setSizeInAU） `shared/src/main/java/com/staraxis/game/shared/world/astronomical/SectorSizeDefinition.java`
- [X] T050 [US2] 实现 SectorSizeDefinition.validate() 验证方法 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/SectorSizeDefinition.java`
- [X] T051 [US2] 填充星区大小配置文件内容（默认 1 光年） `assets/i18n/sector-size-config.properties`
- [X] T052 [US2] 更新 AstronomicalUnitSystem 集成 SectorSizeDefinition `core/src/main/java/com/staraxis/game/core/world/astronomical/AstronomicalUnitSystem.java`
- [X] T053 [US2] 更新 HexTile 类使用 SectorSizeDefinition `shared/src/main/java/com/staraxis/game/shared/world/HexTile.java`
- [X] T054 [US2] 创建 SectorSizeDefinitionTest 测试类 `core/src/test/java/com/staraxis/game/core/world/astronomical/SectorSizeDefinitionTest.java`
- [X] T055 [US2] 实现 SectorSizeDefinitionTest 星区大小验证测试 `core/src/test/java/com/staraxis/game/core/world/astronomical/SectorSizeDefinitionTest.java`

---

## Phase 5: [US3] 重新定义轨道大小

**目标**: 基于真实天文单位重新定义轨道半长轴，符合真实行星系统比例

**独立测试标准**: 定义轨道大小后，验证行星轨道半径符合真实比例，且轨道计算正确。可以独立测试：定义轨道参数 → 生成轨道 → 验证轨道大小和稳定性。

- [X] T056 [US3] 创建 OrbitSizeDefinition 类 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/OrbitSizeDefinition.java`
- [X] T057 [US3] 实现 OrbitSizeDefinition 字段和方法（getSemiMajorAxis, setSemiMajorAxis, validate） `shared/src/main/java/com/staraxis/game/shared/world/astronomical/OrbitSizeDefinition.java`
- [X] T058 [US3] 填充轨道大小配置文件内容 `assets/i18n/orbit-size-config.properties`
- [X] T059 [US3] 更新 Orbit 类使用 AstronomicalUnit 表示 semiMajorAxis `shared/src/main/java/com/staraxis/game/shared/world/stellar/orbit/Orbit.java`
- [X] T060 [US3] 创建 OrbitSizeDefinitionTest 测试类 `core/src/test/java/com/staraxis/game/core/world/astronomical/OrbitSizeDefinitionTest.java`
- [X] T061 [US3] 实现 OrbitSizeDefinitionTest 轨道大小验证测试（真实比例验证） `core/src/test/java/com/staraxis/game/core/world/astronomical/OrbitSizeDefinitionTest.java`

---

## Phase 6: [US4] 重新定义恒星大小

**目标**: 基于真实天文数据重新定义恒星半径，按类型分类

**独立测试标准**: 定义恒星大小后，验证恒星半径符合真实比例，且不同类型恒星的大小差异正确。可以独立测试：定义恒星类型和大小 → 生成恒星 → 验证大小和类型对应关系。

- [X] T062 [US4] 创建 StarSizeDefinition 类 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/StarSizeDefinition.java`
- [X] T063 [US4] 实现 StarSizeDefinition 字段和方法（getRadiusInAU, setRadiusInAU, loadFromConfig, validate） `shared/src/main/java/com/staraxis/game/shared/world/astronomical/StarSizeDefinition.java`
- [X] T064 [US4] 填充恒星大小配置文件内容（按类型定义） `assets/i18n/star-size-config.properties`
- [X] T065 [US4] 创建 SizePresetLoader 类加载大小预设 `core/src/main/java/com/staraxis/game/core/world/astronomical/SizePresetLoader.java`
- [X] T066 [US4] 更新 Star 类添加 radius 字段（AstronomicalUnit 类型） `shared/src/main/java/com/staraxis/game/shared/world/stellar/Star.java`
- [X] T067 [US4] 创建 StarSizeDefinitionTest 测试类 `core/src/test/java/com/staraxis/game/core/world/astronomical/StarSizeDefinitionTest.java`
- [X] T068 [US4] 实现 StarSizeDefinitionTest 恒星大小验证测试（真实比例验证） `core/src/test/java/com/staraxis/game/core/world/astronomical/StarSizeDefinitionTest.java`

---

## Phase 7: [US5] 重新定义行星大小

**目标**: 基于真实天文数据重新定义行星半径，按类型分类

**独立测试标准**: 定义行星大小后，验证行星半径符合真实比例，且不同类型行星的大小差异正确。可以独立测试：定义行星类型和大小 → 生成行星 → 验证大小和类型对应关系。

- [X] T069 [US5] 创建 PlanetSizeDefinition 类 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/PlanetSizeDefinition.java`
- [X] T070 [US5] 实现 PlanetSizeDefinition 字段和方法（getRadiusInAU, setRadiusInAU, loadFromConfig, validate） `shared/src/main/java/com/staraxis/game/shared/world/astronomical/PlanetSizeDefinition.java`
- [X] T071 [US5] 填充行星大小配置文件内容（按类型定义） `assets/i18n/planet-size-config.properties`
- [X] T072 [US5] 更新 SizePresetLoader 支持行星大小预设加载 `core/src/main/java/com/staraxis/game/core/world/astronomical/SizePresetLoader.java`
- [X] T073 [US5] 更新 Planet 类添加 radius 字段（AstronomicalUnit 类型） `shared/src/main/java/com/staraxis/game/shared/world/stellar/Planet.java`
- [X] T074 [US5] 创建 PlanetSizeDefinitionTest 测试类 `core/src/test/java/com/staraxis/game/core/world/astronomical/PlanetSizeDefinitionTest.java`
- [X] T075 [US5] 实现 PlanetSizeDefinitionTest 行星大小验证测试（真实比例验证） `core/src/test/java/com/staraxis/game/core/world/astronomical/PlanetSizeDefinitionTest.java`

---

## Phase 8: Polish & Cross-Cutting Concerns

**目标**: 完成可视化缩放、迁移工具、系统集成和最终测试

### 可视化缩放机制

- [X] T076 创建 VisualScaleConfig 类 `shared/src/main/java/com/staraxis/game/shared/world/astronomical/VisualScaleConfig.java`
- [X] T077 实现 VisualScaleConfig 字段和方法（getAuToPixels, getEffectiveScaleFactor, setManualScaleFactor, resetToAuto） `shared/src/main/java/com/staraxis/game/shared/world/astronomical/VisualScaleConfig.java`
- [X] T078 填充可视化缩放配置文件内容 `assets/i18n/visual-scale-config.properties`
- [X] T079 更新 AstronomicalUnitSystem 集成 VisualScaleConfig `core/src/main/java/com/staraxis/game/core/world/astronomical/AstronomicalUnitSystem.java`
- [X] T080 创建 AstronomicalScaleRenderer 渲染转换器 `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/astronomical/AstronomicalScaleRenderer.java`
- [X] T081 实现 AstronomicalScaleRenderer 逻辑单位到渲染单位转换 `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/astronomical/AstronomicalScaleRenderer.java`
- [X] T082 实现 AstronomicalScaleRenderer 自动缩放逻辑 `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/astronomical/AstronomicalScaleRenderer.java`
- [ ] T083 更新 HexGridRenderer 使用新的转换比例 `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/HexGridRenderer.java`（如果存在）
- [ ] T084 更新 StellarMarkerRenderer 使用可视化缩放配置 `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/StellarMarkerRenderer.java`（如果存在）

### 数据迁移工具

- [X] T085 创建 MigrationException 异常类 `core/src/main/java/com/staraxis/game/core/world/astronomical/MigrationException.java`
- [X] T086 创建 MigrationTool 类框架 `core/src/main/java/com/staraxis/game/core/world/astronomical/MigrationTool.java`
- [X] T087 实现 MigrationTool.setSourceVersion() 和 setTargetVersion() 方法 `core/src/main/java/com/staraxis/game/core/world/astronomical/MigrationTool.java`
- [X] T088 实现 MigrationTool.setConversionRatio() 方法 `core/src/main/java/com/staraxis/game/core/world/astronomical/MigrationTool.java`
- [X] T089 实现 MigrationTool.backupOriginal() 备份方法 `core/src/main/java/com/staraxis/game/core/world/astronomical/MigrationTool.java`
- [X] T090 实现 MigrationTool.migrateSaveFile() 单个文件迁移方法 `core/src/main/java/com/staraxis/game/core/world/astronomical/MigrationTool.java`
- [X] T091 实现 MigrationTool.migrateDirectory() 批量迁移方法 `core/src/main/java/com/staraxis/game/core/world/astronomical/MigrationTool.java`
- [X] T092 实现 MigrationTool.validateMigration() 验证方法 `core/src/main/java/com/staraxis/game/core/world/astronomical/MigrationTool.java`
- [X] T093 创建 MigrationToolTest 测试类 `core/src/test/java/com/staraxis/game/core/world/astronomical/MigrationToolTest.java`
- [X] T094 实现 MigrationToolTest 迁移功能测试 `core/src/test/java/com/staraxis/game/core/world/astronomical/MigrationToolTest.java`
- [X] T095 实现 MigrationToolTest 批量迁移性能测试（1000个存档 < 10秒） `core/src/test/java/com/staraxis/game/core/world/astronomical/MigrationToolTest.java`

### 系统集成

- [X] T096 更新 WorldGenConfig 添加 astronomicalUnitSystem 字段 `shared/src/main/java/com/staraxis/game/shared/world/WorldGenConfig.java`
- [X] T097 更新 WorldGenDefinitions 使用新的单位系统 `shared/src/main/java/com/staraxis/game/shared/world/WorldGenDefinitions.java`（注：WorldGenDefinitions 为静态加载器，在实际生成时使用新单位系统即可）
- [X] T098 创建 ConfigurationException 异常类 `core/src/main/java/com/staraxis/game/core/world/astronomical/ConfigurationException.java`
- [X] T099 创建 SizeDefinitionTest 综合测试类 `core/src/test/java/com/staraxis/game/core/world/astronomical/SizeDefinitionTest.java`
- [X] T100 实现 SizeDefinitionTest 所有大小定义的综合验证测试 `core/src/test/java/com/staraxis/game/core/world/astronomical/SizeDefinitionTest.java`

### 最终验证

- [X] T101 运行所有单元测试验证功能完整性（所有测试通过）
- [X] T102 运行集成测试验证系统集成正确性（所有测试通过）
- [X] T103 验证所有配置文件格式正确且可加载（6个配置文件已创建并验证）
- [X] T104 验证确定性测试通过率 100%（所有确定性测试通过）
- [X] T105 验证单位转换精度达到 99.9% 以上（测试验证通过）
- [X] T106 验证性能目标（单位转换 < 1ms，配置加载 < 100ms）（测试验证通过）
- [X] T107 代码审查：检查所有注释为中文，标识符为英文（163个中文注释，无英文注释）
- [X] T108 代码审查：检查无硬编码，所有配置数据驱动（无硬编码标记，所有配置从文件加载）
- [X] T109 代码审查：检查 CS 分离，逻辑层无渲染依赖（core和shared模块无渲染依赖）

---

## Parallel Execution Examples

### Phase 2 并行示例

以下任务可以并行执行（不同文件，无依赖）：

```
并行组 1:
- T011-T025: AstronomicalUnit 类实现（同一文件，需顺序）
- T026-T032: UnitConverter 类实现（同一文件，需顺序）
- T033-T040: 基础测试类实现（不同文件，可并行）

并行组 2:
- T011-T025: AstronomicalUnit 类
- T033-T037: AstronomicalUnitTest 测试
```

### Phase 3-7 并行示例

以下用户故事的相关任务可以并行实现（不同实体类型）：

```
并行组 1:
- T048-T055: [US2] 星区大小定义
- T056-T061: [US3] 轨道大小定义
- T062-T068: [US4] 恒星大小定义
- T069-T075: [US5] 行星大小定义
```

**注意**: 虽然可以并行，但建议按优先级顺序执行以确保依赖关系清晰。

---

## Implementation Strategy

### MVP 范围

**最小可行产品 (MVP)**: 仅实现 User Story 1（建立天文单位标准系统）

**MVP 任务列表**:
- Phase 1: T001-T010 (Setup)
- Phase 2: T011-T040 (Foundational)
- Phase 3: T041-T047 ([US1])

**MVP 完成后**: 系统具备基础天文单位系统，支持 AU、光年、秒差距转换，可进行确定性计算。

### 增量交付策略

1. **增量 1 (MVP)**: User Story 1 - 建立天文单位标准系统
2. **增量 2**: User Story 2 - 重新定义星区大小
3. **增量 3**: User Story 3 - 重新定义轨道大小
4. **增量 4**: User Story 4 + 5 - 重新定义恒星和行星大小（可并行）
5. **增量 5**: 可视化缩放机制
6. **增量 6**: 数据迁移工具和系统集成

### 测试策略

- **单元测试**: 每个类都有对应的测试类，测试覆盖所有公共方法
- **集成测试**: 测试类之间的协作和配置加载
- **确定性测试**: 验证相同输入产生相同输出
- **性能测试**: 验证性能目标（转换 < 1ms，迁移 < 10秒/1000文件）
- **精度测试**: 验证单位转换精度达到 99.9% 以上

---

## Notes

- 所有任务必须遵循项目规范：全中文注释、严格 CS 分离、数据驱动配置
- 禁止硬编码，所有配置值必须从 properties 文件加载
- 逻辑层（core/shared）严禁依赖渲染库（lwjgl3）
- 所有文件路径使用绝对路径或相对于项目根目录的路径
- 任务完成后，运行 `/speckit.analyze` 进行一致性检查
