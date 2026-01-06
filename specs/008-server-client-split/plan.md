# Implementation Plan: Server/Client Separation & Communication (008)
 
 **Branch**: `008-server-client-split` | **Date**: 2026-01-06 | **Spec**: [spec.md](./spec.md)
 **Input**: Feature specification from `specs/008-server-client-split/spec.md`
 
 ## Summary
 
 本特性目标：在 StarAxis 中实现“服务端权威世界生成 + 客户端仅渲染与输入转发”的端侧分离架构，并建立第一版可在局域网使用的 HTTP+JSON 通讯闭环。
 
 交付闭环：客户端发送 `StartNewGameRequest`（含 seedText 与生成参数）-> 服务端权威生成世界 -> 返回 `StartNewGameResponse`（`schemaVersion=worldgen_v1`，成功时包含 `WorldSnapshot` + `effectiveConfig`，失败时包含 `ErrorEnvelope`）-> 客户端解析并进入世界界面渲染。

 默认地图规模口径：`mapSizePresetId=medium`。

 状态码策略口径：
 
 - 可修正参数越界（比例类）：服务端 clamp/归一化后返回 200，并在 `effectiveConfig` 回填最终值。
 - 不可修正请求（例如 mapSizePresetId 不存在、JSON 结构错误）：返回 400 并附带 `ErrorEnvelope`。

 ## Technical Context

 **Language/Version**: Java 21 (Gradle toolchain)  
 **Primary Dependencies**: Gradle 多模块（core/shared/lwjgl3）；LibGDX 1.14.0；JUnit 5.10.0  
 **Storage**: N/A（本特性仅通讯与世界生成快照下发，不涉及持久化）  
 **Testing**: JUnit 5（单元测试 + 合约/集成测试）  
 **Target Platform**: 本地开发 Windows；服务端目标形态为 headless（未来可部署 Linux）
 **Project Type**: 多模块游戏项目（客户端 lwjgl3 + 逻辑 core + 共享 shared；本特性计划新增 server 子模块）  
 **Performance Goals**: 默认地图规模（`mapSizePresetId=medium`）下：服务端生成+序列化响应 <= 5s；客户端解析+进入世界 <= 5s；WorldSnapshot JSON <= 20MB  
 **Constraints**: core 可在无图形运行时环境运行；客户端负责本地化；协议包含 schemaVersion；局域网可访问但暂不做认证  
 **Scale/Scope**: 仅 WorldGen 闭环（StartNewGame 请求/响应 + WorldSnapshot 下发），不包含运行时指令/实时同步

 **Naming Note**: 宪章要求文件/文件夹使用 kebab-case，但 Java 公开类文件名必须与类名一致；本仓库现有 Java 代码也遵循该语言约束，因此 Java 源文件在本特性中继续使用 PascalCase 文件名（属于语言级例外）。

 ## Constitution Check
 
 - **UI 层独立性 (Independent UI Layer)**: UI 是否作为独立层级存在？是否通过数据绑定或 UI Model 与逻辑解耦？

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

 - **模块化与可维护性 (Modularization & Maintainability)**: 通过 `shared` 承载 DTO/模型，`core` 承载生成逻辑，`server` 仅承载通讯适配层，避免跨层污染。
 - **架构分层与端侧分离 (Layered Architecture & C/S Separation)**: 客户端不再直接调用生成器；服务端返回权威快照；错误采用结构化 ErrorEnvelope。
 - **规范化命名与注释 (Naming & Documentation)**: 标识符使用英文；注释/文档使用简体中文。命名遵循现有 Java 代码约定（类名与文件名一致）。
 - **扩展性与 Mod 支持 (Extensibility & Mod Support)**: 请求/响应采用 schemaVersion；快照结构允许新增字段；为未来增加查询 API 或实时同步留出扩展点。
 - **游戏模拟驱动 (Simulation-Driven Logic)**: 本特性只涉及生成与快照下发，不引入帧驱动逻辑；未来同步再以模拟时间为准。
 - **版本控制与合并纪律 (Version Control & Merge Discipline)**: 所有 spec/plan/contracts/quickstart 均入库；完成实现+测试+文档对齐后再 push；达成质量门禁后再合并。

## Project Structure

 ### Documentation (this feature)

 ```text
 specs/008-server-client-split/
 |-- spec.md
 |-- plan.md
 |-- research.md
 |-- data-model.md
 |-- quickstart.md
 |-- contracts/
 |   `-- worldgen-api.yaml
 `-- tasks.md
 ```

 ### Source Code (repository root)

 ```text
 core/
 |-- src/main/java/com/staraxis/game/core/
 |   `-- world/ ... (生成逻辑、用例层)
 
 shared/
 |-- src/main/java/com/staraxis/game/shared/
 |   `-- world/ ... (模型与 DTO)
 
 lwjgl3/
 |-- src/main/java/com/staraxis/game/client/
 |   `-- ui/ ... (Screen/Renderer/输入)
 
 server/ (planned)
 |-- src/main/java/com/staraxis/game/server/
 |   |-- http/ ... (HTTP 路由与序列化)
 |   `-- ServerMain.java
 ```

 **Structure Decision**: 采用现有多模块结构并新增 `server` 子模块承载 HTTP 通讯适配层：

 - `shared`: 承载请求/响应 DTO、WorldSnapshot、ErrorEnvelope 等跨端契约
 - `core`: 承载权威世界生成与相关用例（需保证 headless）
 - `server`: 承载 HTTP 服务启动、路由、JSON 编解码；调用 core 用例并返回 DTO
 - `lwjgl3`: 客户端 UI，负责本地化与渲染，调用服务端获取快照

 ## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

 | Violation | Why Needed | Simpler Alternative Rejected Because |
 |-----------|------------|-------------------------------------|
 | 新增 `server` 子模块 | 需要独立承载通讯适配层，避免把 HTTP/序列化逻辑塞进 `core` 或 `lwjgl3` 造成分层破坏 | 继续在客户端直连生成器会破坏端侧分离；把 HTTP 放进 core 会降低 headless 纯度 |
 | 文件命名 kebab-case 与 Java 语言约束冲突 | Java 公开类文件名必须与类名匹配，仓库现有 Java 代码已采用该约束 | 强行 kebab-case 会导致无法编译，属于语言级不可行 |

## Phase 0: Research (output: research.md)

目标：将本特性的关键实现决策固化为可复用结论，避免在实现阶段反复争论。

研究主题：

- HTTP 服务器实现方式（MVP 的依赖最小化、可维护性）
- JSON 编解码方案（DTO 兼容、schemaVersion、错误模型）
- 局域网可访问但无认证的风险收敛方式（可配置暴露范围、明确提示）

## Phase 1: Design & Contracts (outputs: data-model.md, contracts/, quickstart.md)

目标：把 DTO 与 API 契约写清楚，并给出可复现的本地验证步骤。

- 产出 `data-model.md`：列出 StartNewGameRequest/Response、WorldSnapshot、ErrorEnvelope 字段与约束。
- 产出 `contracts/worldgen-api.yaml`：定义 HTTP API、状态码、schemaVersion 与错误响应。
- 产出 `quickstart.md`：说明如何启动服务端与客户端、如何验证确定性、如何验证错误路径。

## Phase 2: Task Breakdown (output: tasks.md via /speckit.tasks)

目标：把 Phase 1 的设计拆解为按用户故事分组的可执行任务清单，并明确测试与手工验证门禁。
