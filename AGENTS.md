# StarAxis - AI 代理指南

本文档为在 StarAxis 项目中工作的 AI 代理提供必要信息喵。

## 项目概述

StarAxis 是一个采用分离架构的太空策略游戏喵：
- **后端**：Java 21 + libGDX 的游戏模拟引擎（`game` 模块）
- **前端**：Vue 3 + TypeScript + Three.js 的 Web 渲染界面（`web` 模块）
- **通信**：`webnet` 模块作为 WebSocket 服务器连接游戏和网页
- **架构**：浏览器前端渲染 + Java 后端模拟的权威架构

## 构建命令

### Java 后端（Gradle）
```bash
# 构建所有模块
./gradlew build

# 运行所有测试
./gradlew test

# 运行特定模块测试（如 game 模块）
./gradlew :game:test

# 运行单个测试类
./gradlew :game:test --tests "staraxis.game.astro.AstroDataTest"

# 清理构建
./gradlew clean

# 运行 Web 版本（仅启动 webnet 服务器）
./gradlew runWeb
./gradlew :webnet:run

# 构建可分发 jar
./gradlew :webnet:fatJar
./gradlew :webnet:distLauncher  # 复制到根目录为 StarAxis.jar

# 运行原生桌面版本
./gradlew :lwjgl3:run
```

### Web 前端（npm）
```bash
cd web

# 开发服务器
npm run dev

# 生产构建
npm run build

# 预览生产构建
npm run preview

# 类型检查
npx vue-tsc -b
```

### 组合开发（Windows 批处理）
```bash
# 同时启动后端和前端
run-web-dev.bat
```

## 代码风格指南

### Java 后端
- **Java 版本**：21（源/目标兼容性）
- **缩进**：4 个空格（根据 `.editorconfig`）
- **行尾**：LF（Unix 风格）
- **字符集**：UTF-8
- **导入**：标准库导入在前，第三方库在后
- **命名**：
  - 类：`PascalCase`
  - 方法/变量：`camelCase`
  - 常量：`UPPER_SNAKE_CASE`
  - 包：`staraxis.module.subpackage`
- **注释**：所有代码必须有注释（CLAUDE.md 强制要求）
  - 公有方法：记录功能和参数
  - 复杂逻辑：解释实现思路
  - 关键算法：包含性能考虑
- **错误处理**：可恢复错误使用受检异常，编程错误使用运行时异常
- **不可变性**：尽可能使用不可变数据结构（参考 `AstroData` 示例）
- **注解**：在适当位置使用 `@Nullable`/`@NotNull`

### TypeScript/JavaScript 前端
- **TypeScript**：启用严格模式（tsconfig.app.json）
- **缩进**：2 个空格（根据 Vue/TS 惯例）
- **Vue 3**：优先使用组合式 API
- **导入**：使用路径别名 `@/` 指向 `src/` 目录
- **命名**：
  - 组件：`PascalCase`（如 `StarMap.vue`）
  - 变量/函数：`camelCase`
  - 常量：`UPPER_SNAKE_CASE`
  - 接口/类型：`PascalCase`
- **类型安全**：启用所有严格编译器选项（`noUnusedLocals`、`noUnusedParameters` 等）

### Gradle 文件
- **缩进**：2 个空格（根据 `.editorconfig`）

## 测试

### Java 测试
- **位置**：每个模块的 `src/test/java/`
- **框架**：JUnit（基于标准 Gradle Java 项目推断）
- **命名**：测试类以 `Test` 结尾（如 `AstroDataTest.java`）
- **运行单个测试**：使用 `./gradlew :module:test --tests "fully.qualified.TestClassName"`

### 前端测试
- 当前未配置测试框架
- 通过 `npm run dev` 进行手动测试

## 架构约束（关键）

来自 CLAUDE.md 的 12 条硬规则：

1. **模拟层权威**：所有改变游戏结果的逻辑只在 `game` 模块执行喵
2. **确定性优先**：相同输入与初始状态必须得到相同结果喵
3. **模拟时间驱动**：游戏推进与结算绑定 `simulationTick`/`gameDatetimeDay`，不能由 FPS 或实时时钟驱动喵
4. **多核并行是强制项**：可并行计算要拆分为 Job；但权威写入必须在"落账点"串行化处理喵
5. **双快照口径**：
   - **上一日结算状态（DailySettlementState）**：UI 层展示经济、生产、人口等具有固定结算周期（按"日"）的数据喵
   - **实时世界状态（RealTimeWorldState）**：战斗、移动、即时事件等实时系统，以及需要即时数据的 UI 展示喵
6. **命名/术语统一**：所有字段写法必须一致，文档中出现字段必须用 `fieldName`（解释）格式喵
7. **代码必须带注释**：所有代码都必须带注释，禁止不写注释，禁止删除已有注释，仅允许修改喵
8. **模块化、可维护性与数据驱动**：所有功能必须以模块化方式设计喵
9. **禁止硬编码**：代码内禁止出现硬编码与硬枚举，应该数据驱动喵
10. **数据驱动优先**：游戏行为与内容应由外部数据（如 JSON/表）定义喵
11. **配置加载口径**：所有可配置项必须通过配置系统加载，禁止散落的魔法数字与硬编码字符串喵
12. **可演进性**：配置与数据结构需要允许未来迭代与 Mod 扩展喵

## 模块职责

- **`game`**：权威模拟层 - 核心游戏逻辑、天文数据生成、行星/恒星系统、命令总线、经济系统喵
- **`webnet`**：通信服务器 - 连接 `game` 模块与前端喵
- **`web`**：客户端界面 - Vue 3 + TypeScript + Three.js，纯展示层喵
- **`client`**：客户端应用层（未来原生 Java 客户端使用，当前未使用）
- **`ui`**：UI 层（未来原生 Java 客户端使用，当前未使用）

## 通信流向
```
game（游戏模拟） → webnet（通信中转） → web（前端展示）
```
**严格禁止**：web 和 game 之间的直接交互，所有通信必须经过 webnet 模块喵。

## 开发工作流

1. **搜索优先**：在修改或添加代码前，必须先搜索现有代码库喵
2. **配置管理**：集中管理所有配置项，支持热重载，为 Mod 提供扩展点喵
3. **国际化支持**：所有用户可见文本必须支持国际化喵
4. **资源管理**：资源存储在 `/assets` 路径内，预加载字体，按需加载音频喵

## Cursor 规则（.cursor/rules/specify-rules.mdc）

- **活跃技术**：Java 21 + libGDX 1.12.1 + LWJGL3 后端
- **结构**：多模块 Gradle（`shared/` + `lwjgl3/`）
- **序列化**：Kryo（现有 KryoSerializer）
- **日志**：logback
- **网络**：需要明确（Netty/Java NIO/其他）

## 代码检查和格式化

### Java
- **EditorConfig**：强制执行 4 空格缩进、LF 行尾、UTF-8 字符集
- **Checkstyle**：当前未配置
- **推荐**：如果添加了 spotless 插件，运行 `./gradlew spotlessApply`

### TypeScript/JavaScript
- **TypeScript 编译器**：通过 `tsconfig.app.json` 提供严格检查
- **ESLint**：当前未配置
- **Prettier**：当前未配置

## 性能考虑

- **内存管理**：大对象使用对象池，及时释放不再使用的资源喵
- **渲染优化**：Three.js 渲染使用实例化渲染，避免每帧创建新对象喵
- **网络优化**：WebSocket 消息使用二进制协议，状态更新使用增量更新喵

## 常见陷阱避免

1. **禁止硬编码游戏逻辑** - 使用数据驱动配置喵
2. **禁止从前端修改游戏状态** - 前端仅负责展示喵
3. **始终添加注释** - 每个方法和复杂逻辑块都需要解释喵
4. **遵循命名约定** - 保持代码库一致性喵
5. **保持确定性** - 确保相同输入产生相同输出喵
6. **尊重模块边界** - 不要绕过 `webnet` 进行游戏-前端通信喵

## 快速参考

```bash
# 启动完整开发环境
./gradlew runWeb & cd web && npm run dev

# 构建所有内容
./gradlew build && cd web && npm run build

# 运行测试
./gradlew test

# 创建分发版本
./gradlew :webnet:distLauncher
```

---
*最后更新：2026-02-13*
*基于 build.gradle、package.json、CLAUDE.md 和代码库结构分析*
