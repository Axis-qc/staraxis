# Feature Specification: Server/Client Separation & Communication
 
 **Feature Branch**: `008-server-client-split`  
 **Created**: 2026-01-06  
 **Status**: Draft  
 **Input**: User description: "创建008，进行完整的服务端和客户端分离，建立通讯系统，遵循 .specify\memory\constitution.md"

## Clarifications

### Session 2026-01-06

- Q: 通讯系统的“第一版传输层”选型（MVP）？ → A: HTTP + JSON 请求/响应
- Q: 服务端可访问范围 / 安全边界（MVP）？ → A: 局域网可访问（暂不做认证）
- Q: 008 的“通讯系统”范围边界？ → A: 仅 WorldGen 闭环（StartNewGame 请求/响应 + WorldSnapshot 下发）
- Q: 错误响应的结构化格式与本地化策略（MVP）？ → A: ErrorEnvelope（errorCode + messageKey + details），客户端使用 messageKey 本地化展示
- Q: WorldSnapshot 的星系细节层级（MVP）？ → A: 包含完整星系结构（StarSystem/Star/Planet 列表）
- Q: Seed 的权威来源与回填规则（MVP）？ → A: 服务端权威解析 seedText 并在响应/快照中回填 seedValue
- Q: MVP 的性能/规模验收阈值？ → A: 默认地图规模下，服务端生成+序列化响应 <= 5s；客户端解析+进入世界 <= 5s；WorldSnapshot JSON <= 20MB
- Q: schemaVersion 的固定值（MVP）？ → A: 固定为 `worldgen_v1`
- Q: “默认地图规模”的定义（MVP）？ → A: `mapSizePresetId=medium`
- Q: 参数非法/越界的处理策略（MVP）？ → A: 服务端对可修正项进行 clamp/归一化，并在响应中回填最终采用的有效参数
- Q: 确定性验收的 seedText 规则（MVP）？ → A: 确定性验证必须使用非空 seedText；空 seedText 仅用于随机世界，不参与确定性对比
- Q: 局域网可访问的监听/连接口径（MVP）？ → A: 服务端监听地址（bindAddress）必须可配置，默认监听 `0.0.0.0`；客户端默认连接 `http://127.0.0.1:8080`，若跨机器联调则由用户填写服务端真实 IP

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Server 权威世界生成与通讯最小闭环 (Priority: P1)
 
 作为玩家，我点击“新游戏”后，客户端将生成参数发送给服务端；服务端完成世界生成并返回可渲染的世界快照；客户端使用快照进入世界界面。
 
 **Why this priority**: 这是“端侧分离”的根基；只有形成请求/响应边界，后续的渲染与交互才能不再耦合生成逻辑。
 
 **Independent Test**: 在不依赖客户端渲染的情况下，仅启动服务端并发送“开始新游戏”请求，能够返回结构完整的世界快照（包含边界、瓦片列表、统计信息）。
 
 **Acceptance Scenarios**:
 
 1. **Given** 服务端未运行世界实例，**When** 客户端请求开始新游戏并包含生成参数，**Then** 服务端返回世界快照，客户端进入世界界面且可渲染。
 2. **Given** 同一组参数与同一 seedText，**When** 连续两次发起新游戏请求，**Then** 返回的统计摘要一致（可用于确定性验证）。

### User Story 2 - Client 只渲染与输入转发 (Priority: P2)
 
 作为玩家，在世界界面中，我看到网格、星系标记与调试统计；客户端不再直接调用世界生成器，而是仅消费服务端下发的快照，并将输入（例如悬停/点击坐标）转为请求或本地查询。
 
 **Why this priority**: 保证 UI 层独立性与端侧分离，避免“客户端持有权威逻辑对象引用”的隐性耦合。
 
 **Independent Test**: 客户端在无生成器依赖的情况下，加载一份世界快照即可正确渲染网格与星系标记，并能显示快照统计。
 
 **Acceptance Scenarios**:
 
 1. **Given** 客户端已获得世界快照，**When** 进入世界界面，**Then** 所有渲染与调试信息均来自快照数据而非本地生成。

### User Story 3 - Core 可服务端运行（无 Gdx 依赖） (Priority: P3)
 
 作为开发者，我希望核心逻辑模块可以独立在服务端环境运行，不依赖客户端图形/资源系统；本地化、字体与 UI 资源完全属于客户端。
 
 **Why this priority**: 这是长期演进到联机、可部署服务端、可测试模拟的关键质量门禁。
 
 **Independent Test**: 在无图形运行时环境下，核心逻辑模块仍可编译、运行并完成世界生成与序列化输出。
 
 **Acceptance Scenarios**:
 
 1. **Given** 仅服务端环境（无图形库），**When** 运行服务端并执行世界生成请求，**Then** 生成成功且不发生图形/资源依赖错误。

### Edge Cases

 - 服务端不可用或连接中断时，客户端如何提示并允许用户返回？
 - 请求参数越界/非法（比例不在 [0,1]、seedText 异常长度/字符）时，服务端如何修正（clamp/归一化）并在响应中体现最终使用值？
 - JSON schemaVersion 不匹配时，客户端如何拒绝加载并提供可诊断信息？
 - 世界生成耗时较长时，客户端如何展示加载态与允许用户取消/返回？
 - 世界快照过大导致传输/解析超时，系统如何处理（返回错误还是降级快照）？
 - 服务端允许局域网访问且未启用认证时，如何降低误用风险（例如仅用于开发/测试环境的提示、默认端口与暴露范围可配置）？
 - 在默认地图规模内仍出现超时或过大 JSON 时，如何提供诊断信息（例如返回 errorCode / details，或记录服务端生成耗时与快照大小）？
 - 生成结果出现“快照不满足约束”（例如 stars 数量越界、tiles 为空、boundsRadius=0）时，如何处理与诊断？

 ## Requirements *(mandatory)*

 ### Functional Requirements
 
 - **FR-001**: 端侧分离：服务端负责世界生成与权威数据；客户端仅负责渲染与输入转发，不得直接调用生成器或持有权威逻辑对象引用。
 - **FR-002**: 通讯协议：第一版传输层采用 HTTP（局域网可访问）；客户端与服务端之间必须通过明确的请求/响应消息模型交互，消息体使用 JSON；响应必须包含 `schemaVersion` 且固定为 `worldgen_v1`。该 `schemaVersion` 作用域覆盖整个响应与其中的 `WorldSnapshot`（MVP 不引入独立的快照版本字段）。
 - **FR-003**: 世界快照：服务端必须返回可渲染的世界快照，至少包含瓦片坐标与类型、边界、统计摘要；对包含星系的瓦片，必须下发完整星系结构（StarSystem/Star/Planet 列表），以支持客户端直接渲染与调试。
 - **FR-004**: 确定性：相同生成参数与相同 seedText 在服务端侧必须产生一致的统计摘要；服务端必须在响应/快照中回填最终采用的 `seedValue`，用于验收与回归对比。确定性验收必须使用非空 seedText。
 - **FR-005**: Core 可服务端运行：核心逻辑模块不得依赖客户端图形/资源系统；本地化、字体、皮肤、贴图与 UI 资源必须属于客户端。
 - **FR-006**: 错误处理：服务端必须对非法参数进行修正或拒绝，并返回结构化错误信息（包含 `errorCode` 与 `messageKey`；可选 `details` 仅用于诊断）；对于可修正的参数越界，服务端应进行 clamp/归一化，并在成功响应中通过 `effectiveConfig` 回填最终采用的有效参数；客户端必须基于 `messageKey` 做本地化呈现。

   状态码规则（MVP 固化，避免双方实现分叉）：

   - **200**：成功响应。对于比例类越界参数（例如 `habitableRatio` 等），服务端必须先 clamp/归一化，再通过 `effectiveConfig` 回填最终采用值。
   - **400**：不可修正请求（例如 `mapSizePresetId` 不存在、JSON 结构错误）。必须返回 `ErrorEnvelope`（`errorCode` + `messageKey`）。
   - **500**：服务端异常（未捕获错误或内部失败）。必须返回 `ErrorEnvelope`（`errorCode` + `messageKey`）。

   处理策略补充（MVP 固化，避免返工）：

   - 生成耗时过长、响应体过大、或生成结果不满足快照约束（例如 `stars` 数量越界、`tiles` 为空、`boundsRadius<=0`）时，MVP **不做降级快照/分片**；服务端应记录诊断信息，并返回 **500** + `ErrorEnvelope`（`INTERNAL_ERROR/worldgen.internal_error`，并可在 `details` 给出耗时/字节数/约束失败原因）。
   - 客户端在请求进行中必须展示加载态；允许用户取消/返回主菜单（取消为 best-effort：客户端中止等待即可，服务端无需支持取消）。
   - “局域网可访问但无认证”仅用于开发/测试：服务端启动日志与客户端 UI 必须明确提示“未启用认证，仅开发/测试用途”。

   资源/性能提示（对齐宪章性能原则）：

   - 客户端解析与构建渲染数据必须在“加载阶段”完成，不得在高频 update/渲染路径中持续进行大规模分配与解析，以避免帧内卡顿。

   最小错误码与消息键集合（MVP 固化，便于前后端对齐与本地化）：

   - `INVALID_MAP_PRESET` / `worldgen.invalid_map_preset`
   - `INVALID_JSON` / `worldgen.invalid_json`
   - `INTERNAL_ERROR` / `worldgen.internal_error`
   - `SCHEMA_MISMATCH` / `worldgen.schema_mismatch`（客户端校验失败时使用）
   - `SERVER_UNREACHABLE` / `worldgen.server_unreachable`（客户端连接失败时使用）
 - **FR-007**: 可扩展性：协议与快照结构必须允许未来扩展（新增字段、扩展星系细节）而不破坏旧客户端的基本运行。
 - **FR-008**: 宪章约束：禁止硬编码/硬枚举；命名与注释遵循宪章要求；所有新增文件包含标准文件头注释块且注释使用中文。

### Out of Scope

- 运行时指令与权威同步（例如移动/战斗命令、tick 推进、状态回滚、实时同步）。
- 长连接/实时协议（例如 WebSocket）。
- 认证/鉴权/加密（本 feature 默认用于开发/测试阶段；后续 feature 再补充安全能力）。

 ### Key Entities *(include if feature involves data)*
 
 - **StartNewGameRequest**: 客户端发起新游戏的请求消息，包含生成参数与 `seedText`；最终 `seedValue` 由服务端权威解析。
 - **StartNewGameResponse**: 服务端返回的响应消息，包含固定的 `schemaVersion`（`worldgen_v1`）、生成结果快照或错误信息；成功时必须包含 `effectiveConfig` 回填最终采用的有效参数。
 - **WorldSnapshot**: 世界快照（服务端权威输出），包含边界、瓦片列表、统计摘要与最终采用的 `seedValue`；对包含星系的瓦片，包含完整的 StarSystem/Star/Planet 结构。
 - **ErrorEnvelope**: 结构化错误对象，至少包含 `errorCode`、`messageKey`，可选包含 `details`（仅用于诊断，不直接展示给玩家）。

 ## Success Criteria *(mandatory)*

 ### Measurable Outcomes
 
 - **SC-001**: 客户端不直接调用生成器，仍可完成“新游戏 -> 世界界面可渲染”的闭环。
 - **SC-002**: 服务端返回的世界快照包含 `schemaVersion`，客户端在版本不匹配时不会崩溃，并能给出明确错误提示。
 - **SC-003**: 对于同一组参数与同一 seedText（非空），重复请求返回的统计摘要一致（用于确定性验证）。
 
   确定性比较口径（MVP 固化）：

   - 比较对象至少包含：`world.seedValue` 与 `world.stats`（其中每个字段应完全一致）；若任一字段不一致，则视为确定性失败（不引入容差）。
 - **SC-004**: core 模块可在服务端环境运行：不依赖客户端图形/资源框架与本地资源加载机制，且可在无图形运行时环境下完成生成与响应输出。
 - **SC-005**: 默认地图规模（`mapSizePresetId=medium`）下，服务端完成世界生成并返回 JSON 响应耗时不超过 5 秒（计时口径：服务端收到请求 -> 序列化完成并写出响应）。
 - **SC-006**: 默认地图规模（`mapSizePresetId=medium`）下，客户端完成 JSON 解析并进入世界界面耗时不超过 5 秒（计时口径：客户端收到响应 -> 完成解析并进入世界界面）。
 - **SC-007**: 默认地图规模（`mapSizePresetId=medium`）下，WorldSnapshot JSON 大小不超过 20MB（计量口径：HTTP 响应体字节数）。
