# Tasks - 014 基础坐标系与比例尺

> 所有任务均遵循“最小可用”策略：先交付核心坐标服务与调试渲染（US1），再补充性能与边界处理。

---

## Phase 1 – Setup

| 目标 | 独立测试标准 |
|------|-------------|
| 初始化目录和依赖，确保核心与客户端模块可编译 | `./gradlew clean build` 成功，无新增编译错误 |

- [x] T001 创建 `core/src/main/java/com/staraxis/game/core/coordinate/` 目录结构
- [x] T002 创建 `client/src/main/java/com/staraxis/render/universe/` 下的调试相关实现目录/文件（复用现有 package：`com.staraxis.render.universe`）
- [x] T003 验证 `core` 模块无 `com.badlogic.gdx.graphics` 依赖：确认 `core/build.gradle` 已包含 `checkNoGraphicsDependencies` 且 `gradlew :core:checkNoGraphicsDependencies` 可通过（注意：当前仓库已有历史测试失败，避免用 `:core:check` 作为通过信号）
- [x] T004 [P] 在 `client/src/main/java/com/staraxis/render/universe/ScaleFormatter.java` 实现比例尺单位格式化（不新增 shared 枚举，避免硬枚举扩散；阈值按 FR-8）

---

## Phase 2 – Foundational

| 目标 | 独立测试标准 |
|------|-------------|
| 实现核心数据结构与服务，并提供单元测试 | `./gradlew :core:test` 通过全部新增测试 |
| 验证大坐标稳定性（SC-2） | 在 `WorldCoordinateStabilityTest` 中验证大坐标下小位移无跳变 |

- [x] T101 创建 `WorldCoordinate.java` 于 `core/src/main/java/com/staraxis/game/core/coordinate/WorldCoordinate.java`
- [x] T102 创建 `ScaleSystem.java` 于 `core/src/main/java/com/staraxis/game/core/coordinate/ScaleSystem.java`
- [x] T103 创建接口 `ICoordinateService.java` 于 `core/src/main/java/com/staraxis/game/core/coordinate/ICoordinateService.java`
- [x] T104 实现 `CoordinateService.java` 于 `core/src/main/java/com/staraxis/game/core/coordinate/CoordinateService.java`
- [x] T105 [P] 编写 `CoordinateServiceTest.java` 于 `core/src/test/java/com/staraxis/game/core/coordinate/CoordinateServiceTest.java`
- [x] T106 [P] 编写 `ScaleSystemTest.java` 于 `core/src/test/java/com/staraxis/game/core/coordinate/ScaleSystemTest.java`（包含断言：zoom=1.0 时 kmPerPixel=1.0，误差≤0.1%）
- [x] T107 [P] 编写 `WorldCoordinateStabilityTest.java` 于 `core/src/test/java/com/staraxis/game/core/coordinate/WorldCoordinateStabilityTest.java`：大 grid 坐标下小幅 LocalOffset 位移的差分计算保持连续（覆盖 SC-2）

---

## Phase 3 – User Story P1 (US1)

**故事目标**：玩家按 F3 时，能看到世界坐标轴与自适应网格渲染，并在 UI 悬浮窗看到坐标/比例尺信息；再次按 F3 隐藏。

| 独立测试标准 |
|---------------|
| 1. 运行桌面启动器，按 F3 能显示/隐藏坐标轴、网格和 DebugOverlay。<br>2. 网格间距屏幕约 100px，随 zoom 调整。<br>3. 比例尺文本与最大缩放 `1px=1km` 相符。 |

- [x] T201 [P] [US1] 定位渲染接入点：`lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/UniverseScreen.java`（UniverseScreen.render）
- [x] T202 [P] [US1] 定位输入接入点：`lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/UniverseScreen.java`（show(): InputMultiplexer.addProcessor(...), Gdx.input.setInputProcessor）
- [x] T203 [US1] 在 `lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/UniverseScreen.java` 的 `show()` 注册 F3 输入：新增 `DebugToggleInputProcessor` 到 InputMultiplexer（仅 UniverseScreen 启用）
- [x] T204 [P] [US1] 创建 `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/debug/DebugSystem.java`：持有 debugEnabled，并从 `core` 的 `CoordinateService/ScaleSystem` 生成 DebugOverlayState（core 不感知 UI 开关）
- [x] T205 [P] [US1] 创建 `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/debug/DebugOverlayController.java`：渲染文本（坐标/zoom/比例尺）
- [x] T206 [P] [US1] 创建 `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/debug/WorldGridRenderer.java`：渲染 XY 网格（目标 100px 间距、1.2x 余量、对齐原点）
- [x] T207 [US1] 创建 `lwjgl3/src/main/java/com/staraxis/game/client/ui/view/debug/ScaleTextUtil.java`：实现 FR-8 的单位阈值与格式化 `1px = N.xx unit`
- [x] T208 [US1] 在 `lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/UniverseScreen.java` 的 `render()` 集成：当 debugEnabled=true 时调用 WorldGridRenderer.render(...) 与 DebugOverlayController.render(...)（同时可复用/保留现有 debugLabel）
- [x] T209 [US1] 在 `lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/UniverseScreen.java` 中实例化 `CoordinateService`（core）并注入 DebugSystem（保持分层）
- [x] T210 [US1] 在 `lwjgl3/src/main/java/com/staraxis/game/client/ui/screens/UniverseScreen.java` 中把相机位置/zoom 映射为 WorldCoordinate（临时可用 grid=0 + offset=camera.position.x/y；后续再接入真实世界坐标）
- [ ] T211 [US1] 手动验证并保存截图/GIF 到 `specs/014-coordinate-system-scale/`（例如 `specs/014-coordinate-system-scale/demo.gif`）以备 PR 审查
- [ ] T211 [US1] 手动验证并保存截图/GIF 到 `specs/014-coordinate-system-scale/`（例如 `specs/014-coordinate-system-scale/demo.gif`）以备 PR 审查

---

## Final Phase – Polish & Cross-Cutting

- [ ] T301 代码走查：确保 `core` 无图形依赖（无 `com.badlogic.gdx.graphics` import），`client` 不包含坐标/比例尺规则计算（仅展示/渲染）
- [ ] T302 更新 `specs/014-coordinate-system-scale/quickstart.md`：补充 F3 渲染验证与性能验证步骤
- [ ] T303 更新 `CHANGELOG.md` 添加 014

---

## Dependencies

```
Phase 1 → Phase 2 → Phase 3 → Final Phase
          |             |
          └── 单元测试 ——┘
```

---

## 并行化机会

- T004 与 Phase 1 其他任务可并行
- T105 / T106 可与 T101~T104 并行（实现接口后即可写测试）
- T202 / T203 可并行实现 UI 与渲染

---

## MVP 建议

完成 **Phase 1 + Phase 2 + T201~T205** 即可交付首个可见版本（F3 开关 + 坐标轴/网格渲染 + Overlay 文本），后续任务逐步增强网格算法与 polish。