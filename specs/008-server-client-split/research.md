# Research: 008 Server/Client Separation & Communication

**Created**: 2026-01-06  
**Feature**: [spec.md](./spec.md)  

## Decision 1: HTTP 服务端实现方式

- **Decision**: 采用 JDK 自带的轻量 HTTP Server（JDK HTTP Server）作为 MVP 的服务端 HTTP 容器。
- **Rationale**:
  - 依赖最少，降低引入第三方框架的复杂度。
  - 满足本特性仅需 1-2 个请求/响应端点（WorldGen 闭环）的范围。
  - 便于在 core/headless 目标下保持“通讯适配层”集中在 server 模块。
- **Alternatives considered**:
  - Spring Boot：能力强但对 MVP 过重，且会引入大量依赖与配置。
  - Netty：更底层，需自行补齐大量基础设施。

## Decision 2: JSON 编解码方案

- **Decision**: 采用成熟的 JSON 序列化库（优先 Jackson 或同等级方案）实现 DTO 的 JSON 编解码。
- **Rationale**:
  - 需要稳定的字段映射、可扩展性、错误处理与（未来）兼容策略。
  - DTO 将长期作为跨端契约，序列化能力需要“可维护、可演进”。
- **Alternatives considered**:
  - Gson：可用，但对未来更复杂的配置（例如严格模式/兼容策略）可控性相对弱。
  - 自写 JSON：风险高，维护成本大。

**Note**: HTTP 容器与 JSON 库属于实现层面的选择（可替换）；只要满足 OpenAPI/DTO 契约，本特性的跨端协议语义不应依赖具体实现框架。

## Decision 3: 协议版本与兼容策略

- **Decision**: 响应体必须包含 `schemaVersion`，客户端必须校验；不匹配时返回可诊断错误并不崩溃。
- **Rationale**:
  - 后续迭代会新增字段或调整结构，需要显式版本门禁。
- **Alternatives considered**:
  - 仅靠 URL 版本：不足以表达 DTO 结构变化的细粒度兼容。

## Decision 4: 局域网可访问但无认证的风险收敛

- **Decision**: MVP 允许服务端在局域网可访问，但必须满足：
  - 监听地址与端口可配置（默认端口固定但可覆盖）。
  - 明确标注“开发/测试用途”，并在 UI 中提示未启用认证。
- **Rationale**:
  - 满足局域网测试的需求，同时避免误用扩散。
- **Alternatives considered**:
  - 仅 localhost：更安全，但不满足局域网测试。
  - 立刻做认证：超出本特性范围（已在 spec out-of-scope）。

## Decision 5: Seed 权威规则

- **Decision**: 服务端权威解析 `seedText` 并回填 `seedValue` 到响应/快照。
- **Rationale**:
  - 保障“权威端”的一致性，避免客户端与服务端对同一 seedText 的解析分叉。

## Decision 6: 快照内容层级

- **Decision**: WorldSnapshot 对星系瓦片下发完整结构（StarSystem/Star/Planet 列表）。
- **Rationale**:
  - 满足客户端现有渲染与 debug/hover 信息需求，避免再引入额外查询 API。
