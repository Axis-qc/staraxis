# StarAxis 渲染模块整理与重复功能清理计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 整理 `web/src/rendering` 目录，识别并清理重复功能，统一渲染架构，提高代码可维护性喵

**Architecture:** 全面迁移到分层渲染架构（Layer Architecture），移除旧的子系统（Subsystem）实现，确保渲染系统架构一致喵

**Tech Stack:** TypeScript, Three.js, Vue 3, 分层渲染系统（LayerManager、BaseLayer）

---

## 现状分析

### 1. 架构演进历史
- **旧架构**：基于 `WorldRenderSubsystem` 接口的子系统模式
- **新架构**：基于 `RenderLayer` 接口的分层模式
- **当前状态**：混合架构，存在大量重复实现喵

### 2. 重复功能清单

| 功能模块 | 旧子系统路径 | 新分层路径 | 状态 | 备注 |
|---------|-------------|-----------|------|------|
| 恒星渲染 | `subsystems/starRenderer.ts` | `layers/celestial/renderers/starRenderer.ts` | ✅ 已迁移（旧文件已标记@deprecated） | 旧文件可直接移除喵 |
| 行星渲染 | `subsystems/planetRenderer.ts` | `layers/celestial/renderers/planetRenderer.ts` | ⚠️ 部分迁移（新文件存在，但功能可能不完整） | 需验证新文件完整性喵 |
| 舰船渲染 | `subsystems/shipRenderer.ts` | `layers/entity/renderers/shipRenderer.ts` | ✅ 已迁移（旧文件已标记@deprecated） | 旧文件可直接移除喵 |
| 选中环渲染 | `subsystems/selectionRenderer.ts` | `layers/selection/renderers/selectionRenderer.ts` | ❌ 都是TODO（需要重新实现） | 两个都是占位文件，需统一实现喵 |
| 六边形轮廓 | `subsystems/hexOutlineRenderer.ts` | ❌ 无对应分层实现 | 🔍 未使用 | 检查是否有使用场景喵 |
| 网格渲染 | `subsystems/gridRenderer.ts` | ❌ 无对应分层实现 | 🔍 未使用 | 检查是否有使用场景喵 |

### 3. 当前架构状态
- `worldRenderManager.ts` 已使用 `LayerManager`，注册了以下层：
  - `BackgroundLayer`（背景层）
  - `CelestialLayer`（星体层）- 包含恒星和行星渲染
  - `EntityLayer`（实体层）- 包含舰船渲染
  - `SelectionLayer`（选择层）- 暂时禁用（TODO）
- `renderLoop.ts` 优先使用 `layerManager.updateAll()`，兼容旧的子系统数组（目前为空）
- 旧的子系统未在代码中使用（搜索无导入）

### 4. 核心问题
1. **代码重复**：同一功能在两个位置实现，维护困难喵
2. **架构不一致**：混合架构增加理解成本喵
3. **未使用代码**：`hexOutlineRenderer` 和 `gridRenderer` 可能已废弃喵
4. **TODO项目**：选中环渲染器需要统一实现喵

---

## 阶段目标

### 阶段1：清理已迁移的重复文件（低风险）
- 移除标记为 `@deprecated` 的旧子系统文件喵
- 验证新分层实现的功能完整性喵

### 阶段2：处理未迁移的渲染器（中等风险）
- 检查 `hexOutlineRenderer` 和 `gridRenderer` 是否有使用场景喵
- 根据情况决定：迁移到分层架构或直接移除喵

### 阶段3：统一实现选中环渲染器（高风险）
- 设计并实现统一的选中环渲染器喵
- 集成到 `SelectionLayer` 中喵

### 阶段4：架构清理与文档更新（收尾）
- 移除旧的子系统接口和兼容代码喵
- 更新相关文档和注释喵

---

## 阶段1：清理已迁移的重复文件

### 任务1: 移除已弃用的恒星渲染器子系统

**文件：**
- 删除：`web/src/rendering/subsystems/starRenderer.ts`
- 验证：`web/src/rendering/layers/celestial/renderers/starRenderer.ts` 功能完整

- [ ] **步骤1: 验证新恒星渲染器功能**
  - 检查 `LayerStarRenderer` 类是否实现了所有必要功能喵
  - 对比旧 `StarRenderer` 的功能差异喵
  - 确保纹理加载、颜色映射、对象池等功能正常喵

- [ ] **步骤2: 安全删除旧文件**
  - 备份旧文件（可选）喵
  - 执行删除操作：`rm web/src/rendering/subsystems/starRenderer.ts`喵
  - 检查是否有其他文件导入该文件喵

### 任务2: 移除已弃用的舰船渲染器子系统

**文件：**
- 删除：`web/src/rendering/subsystems/shipRenderer.ts`
- 验证：`web/src/rendering/layers/entity/renderers/shipRenderer.ts` 功能完整

- [ ] **步骤1: 验证新舰船渲染器功能**
  - 检查 `LayerShipRenderer` 是否包含完整的预测和插值逻辑喵
  - 验证路径线条、对象池、状态管理等功能喵
  - 确保与时间管理器和预测纠正器的集成正常喵

- [ ] **步骤2: 安全删除旧文件**
  - 执行删除操作喵
  - 检查导入引用喵

### 任务3: 验证并完善行星渲染器迁移

**文件：**
- 检查：`web/src/rendering/subsystems/planetRenderer.ts`
- 检查：`web/src/rendering/layers/celestial/renderers/planetRenderer.ts`

- [ ] **步骤1: 功能对比分析**
  - 对比新旧行星渲染器的功能差异喵
  - 特别注意轨迹（Trail）渲染是否完整迁移喵
  - 检查纹理加载、LOD处理、透明度计算等逻辑喵

- [ ] **步骤2: 完善新行星渲染器（如有需要）**
  - 将缺失的功能从旧实现复制到新实现喵
  - 保持代码风格一致喵

- [ ] **步骤3: 移除旧行星渲染器**
  - 确认新版本功能完整后删除旧文件喵

---

## 阶段2：处理未迁移的渲染器

### 任务4: 检查六边形轮廓渲染器使用情况

**文件：** `web/src/rendering/subsystems/hexOutlineRenderer.ts`

- [ ] **步骤1: 搜索导入和使用**
  ```bash
  grep -r "HexOutlineRenderer" web/src
  grep -r "hexOutlineRenderer" web/src
  grep -r "new HexOutlineRenderer" web/src
  ```

- [ ] **步骤2: 分析功能需求**
  - 阅读代码理解其功能喵
  - 判断是否仍有使用场景喵
  - 检查是否有对应的UI设置或配置喵

- [ ] **步骤3: 决策与执行**
  - 如果未使用：直接删除文件喵
  - 如果仍需要：规划迁移到分层架构（创建新任务）喵

### 任务5: 检查网格渲染器使用情况

**文件：** `web/src/rendering/subsystems/gridRenderer.ts`

- [ ] **步骤1: 搜索导入和使用**
  ```bash
  grep -r "GridRenderer" web/src
  grep -r "gridRenderer" web/src
  grep -r "new GridRenderer" web/src
  ```

- [ ] **步骤2: 分析功能需求**
  - 理解网格渲染的具体用途喵
  - 检查是否有开关控制（`setGridVisible` API）喵
  - 查看 `worldRenderManager.ts` 中的相关API喵

- [ ] **步骤3: 决策与执行**
  - 如果未使用：直接删除喵
  - 如果仍需要：规划迁移到分层架构喵

---

## 阶段3：统一实现选中环渲染器

### 任务6: 设计选中环渲染器规范

**需求分析：**
- 屏幕空间固定像素圆环虚线喵
- 不受镜头缩放影响喵
- 12像素线宽喵
- 8段虚线（30像素实线 + 15像素间隔）喵
- 缓慢旋转动画喵
- 完整圆环几何体喵

**文件：**
- 主实现：`web/src/rendering/layers/selection/renderers/selectionRenderer.ts`
- 层集成：`web/src/rendering/layers/selection/selectionLayer.ts`

- [ ] **步骤1: 删除旧的TODO文件**
  - 删除两个占位的选择环渲染器文件喵
  - 创建新的实现文件喵

- [ ] **步骤2: 实现绿色虚线环渲染器**
  - 参考之前的实现尝试（如有）喵
  - 实现固定像素宽度的虚线圆环喵
  - 添加旋转动画支持喵

- [ ] **步骤3: 集成到SelectionLayer**
  - 在 `SelectionLayer` 中初始化渲染器喵
  - 实现多实体选中环管理喵
  - 处理选中状态变化喵

- [ ] **步骤4: 启用SelectionLayer**
  - 在 `worldRenderManager.ts` 中取消注释SelectionLayer注册喵
  - 测试选中功能喵

---

## 阶段4：架构清理与文档更新

### 任务7: 清理子系统相关代码

**目标：** 移除旧的子系统架构残留代码喵

- [ ] **步骤1: 检查WorldRenderSubsystem接口使用**
  - 搜索 `WorldRenderSubsystem` 导入喵
  - 检查 `worldRenderSubsystem.ts` 文件喵

- [ ] **步骤2: 清理renderLoop.ts的兼容代码**
  - 移除 `subsystems` 参数（目前为空数组）喵
  - 简化逻辑，只使用 `layerManager`喵

- [ ] **步骤3: 更新类型定义和导出**
  - 清理不再使用的类型导出喵
  - 更新相关注释喵

### 任务8: 验证渲染功能完整性

- [ ] **步骤1: 测试恒星渲染**
  - 缩放测试：远近显示切换喵
  - 纹理加载测试喵
  - 选中状态测试喵

- [ ] **步骤2: 测试行星渲染**
  - 轨道位置正确性喵
  - 轨迹（Trail）显示喵
  - LOD切换测试喵

- [ ] **步骤3: 测试舰船渲染**
  - 移动预测和插值喵
  - 选中路径显示喵
  - 不同舰船类型显示喵

- [ ] **步骤4: 测试选中环渲染**
  - 多实体选中喵
  - 缩放不变性测试喵
  - 旋转动画测试喵

### 任务9: 更新文档和注释

- [ ] **步骤1: 更新架构文档**
  - 在 `CLAUDE.md` 或相关文档中更新渲染架构说明喵
  - 强调分层架构的优势和设计理念喵

- [ ] **步骤2: 代码注释清理**
  - 移除过时的TODO注释喵
  - 更新接口文档喵
  - 添加重要的架构说明喵

---

## 风险与缓解措施

### 高风险项目
1. **选中环渲染器实现**：之前的着色器方案导致画面卡死喵
   - **缓解**：先实现简单的几何体方案，逐步优化喵
   - **测试**：频繁测试，确保不影响主渲染循环喵

2. **行星轨迹渲染迁移**：复杂的Shader和Mesh逻辑喵
   - **缓解**：仔细对比新旧实现，确保功能完整喵
   - **备份**：保留旧文件直到新版本完全验证喵

### 中等风险项目
1. **未使用渲染器的移除**：可能误删仍有潜在用途的代码喵
   - **缓解**：彻底搜索使用情况，咨询项目历史喵
   - **备份**：创建备份分支或标记为废弃而非删除喵

### 低风险项目
1. **已标记@deprecated的文件移除**：相对安全喵
   - **验证**：确保新实现功能完整后再删除喵

---

## 成功标准

1. **架构统一**：完全迁移到分层渲染架构，无子系统残留喵
2. **功能完整**：所有渲染功能正常工作，无明显性能下降喵
3. **代码简洁**：移除重复代码，减少文件数量喵
4. **可维护性**：清晰的架构文档和代码注释喵
5. **无回归**：现有功能测试通过，用户体验不受影响喵

---

## 附录：文件清单

### 待删除文件（确认后）
1. `web/src/rendering/subsystems/starRenderer.ts` - ✅ 已弃用
2. `web/src/rendering/subsystems/shipRenderer.ts` - ✅ 已弃用
3. `web/src/rendering/subsystems/planetRenderer.ts` - ⚠️ 待验证后删除
4. `web/src/rendering/subsystems/hexOutlineRenderer.ts` - 🔍 待检查
5. `web/src/rendering/subsystems/gridRenderer.ts` - 🔍 待检查
6. `web/src/rendering/subsystems/selectionRenderer.ts` - ❌ 占位文件
7. `web/src/rendering/layers/selection/renderers/selectionRenderer.ts` - ❌ 占位文件

### 待修改文件
1. `web/src/rendering/systems/renderLoop.ts` - 移除subsystems兼容代码喵
2. `web/src/rendering/worldRenderManager.ts` - 启用SelectionLayer喵
3. `web/src/rendering/layers/selection/selectionLayer.ts` - 集成新选中环渲染器喵
4. `web/src/rendering/layers/celestial/renderers/planetRenderer.ts` - 可能需要完善喵

### 新建文件
1. `web/src/rendering/layers/selection/renderers/selectionRenderer.ts` - 新的选中环实现喵

---

**最后更新：** 2026-04-17
**负责人：** Claude Code
**状态：** 计划阶段