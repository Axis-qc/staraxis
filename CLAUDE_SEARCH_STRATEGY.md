# Claude Code 高效搜索策略
## 减少Token消耗的智能搜索方法

### 核心原则
1. **先搜索，再读取** - 不要全量读取文件
2. **分层过滤** - 从宽泛到具体
3. **选择性读取** - 只读关键部分
4. **利用工具** - 使用Claude Code内置工具

### 搜索策略示例

#### 1. 搜索Player相关代码（完整流程）

**步骤1：查找所有包含"Player"的Java文件**
```typescript
Grep({
  pattern: "Player",
  glob: "**/*.java",
  output_mode: "files_with_matches",
  head_limit: 10
})
// 结果：找到6个文件
```

**步骤2：查找Player类定义**
```typescript
Grep({
  pattern: "class.*Player",
  glob: "**/*.java",
  output_mode: "content",
  -n: true,
  head_limit: 5
})
// 结果：找到3个Player类定义
```

**步骤3：选择性读取关键文件**
```typescript
// 只读取PlayerNationDesign.java的前30行了解结构
Read({
  file_path: "game/src/main/java/staraxis/game/nation/design/PlayerNationDesign.java",
  limit: 30
})
```

**步骤4：搜索Player相关方法**
```typescript
Grep({
  pattern: "Player.*\\(|getPlayer|setPlayer",
  glob: "**/*.java",
  output_mode: "content",
  -n: true,
  head_limit: 10
})
```

#### 2. 搜索游戏核心类

**步骤1：查找Game相关类**
```typescript
Grep({
  pattern: "class.*Game",
  glob: "**/*.java",
  output_mode: "content",
  -n: true,
  head_limit: 5
})
```

**步骤2：查看游戏主循环**
```typescript
// 假设找到GameRuntime.java
Grep({
  pattern: "update|tick|loop",
  path: "game/src/main/java/staraxis/game/GameRuntime.java",
  output_mode: "content",
  -n: true,
  context: 2
})
```

**步骤3：读取关键方法**
```typescript
Read({
  file_path: "game/src/main/java/staraxis/game/GameRuntime.java",
  offset: 50,  // 假设update方法在50行左右
  limit: 20
})
```

#### 3. 搜索配置系统

**步骤1：查找配置相关文件**
```typescript
Glob({pattern: "**/*.json"})
Glob({pattern: "**/*.properties"})
Glob({pattern: "**/*.yml"})
```

**步骤2：搜索配置关键词**
```typescript
Grep({
  pattern: "config|setting|property",
  glob: "**/*.{java,json,properties,yml,yaml}",
  output_mode: "files_with_matches",
  head_limit: 10
})
```

#### 4. 搜索UI相关代码

**步骤1：查找UI文件**
```typescript
Glob({pattern: "**/*.vue"})
Glob({pattern: "**/*.jsx"})
Glob({pattern: "**/*.tsx"})
```

**步骤2：搜索UI组件**
```typescript
Grep({
  pattern: "component|Component|Widget|Panel|Screen",
  glob: "**/*.{java,vue,jsx,tsx}",
  output_mode: "content",
  -n: true,
  head_limit: 10
})
```

### 工具组合策略

#### 策略A：探索新系统
1. **Glob** → 了解文件分布
2. **Grep** → 搜索关键定义
3. **Read** → 选择性读取结构
4. **Grep** → 搜索相关方法
5. **Read** → 读取关键方法

#### 策略B：理解现有代码
1. **Grep** → 搜索类/接口定义
2. **Read** → 读取类头部（20-30行）
3. **Grep** → 搜索公共方法
4. **Read** → 读取核心方法
5. **Grep** → 搜索使用示例

#### 策略C：调试问题
1. **Grep** → 搜索错误关键词
2. **Grep** → 搜索相关调用
3. **Read** → 读取问题区域
4. **Grep** → 搜索修复模式

### 优化技巧

#### 1. 使用head_limit限制结果
```typescript
// 避免过多结果消耗token
head_limit: 10  // 只显示前10个结果
```

#### 2. 使用offset和limit分段读取
```typescript
// 不读整个文件
Read({file_path: "...", offset: 100, limit: 30})
```

#### 3. 结合多种搜索条件
```typescript
// 多个Grep并行执行
Grep({pattern: "interface", ...})
Grep({pattern: "abstract class", ...})
Grep({pattern: "class.*Manager", ...})
```

#### 4. 使用Glob先过滤文件类型
```typescript
// 先确定有哪些Java文件
Glob({pattern: "**/*.java"})
// 再在这些文件中搜索
```

### 实际示例：了解玩家系统

```typescript
// 1. 查找玩家相关文件
Grep({pattern: "Player", glob: "**/*.java", output_mode: "files_with_matches", head_limit: 10})

// 2. 查看玩家类定义
Grep({pattern: "class.*Player", glob: "**/*.java", output_mode: "content", -n: true})

// 3. 读取主要玩家类
Read({file_path: "game/src/main/java/staraxis/game/nation/design/PlayerNationDesign.java", limit: 40})

// 4. 搜索玩家操作方法
Grep({pattern: "createPlayer|savePlayer|loadPlayer", glob: "**/*.java", output_mode: "content", -n: true})

// 5. 查看玩家保存逻辑
Read({file_path: "webnet/src/main/java/staraxis/webnet/repo/nation/PlayerNationFileRepository.java", offset: 50, limit: 30})
```

### 内存消耗对比

| 方法 | Token消耗 | 效果 |
|------|-----------|------|
| 全量读取大文件 | 高（1000+） | 完整信息，但浪费 |
| 智能搜索+部分读取 | 低（100-300） | 关键信息，高效 |
| 仅文件名搜索 | 很低（<50） | 初步定位 |

### 最佳实践

1. **总是先搜索** - 用Grep/Glob定位，不要直接Read
2. **限制结果数量** - 使用head_limit避免信息过载
3. **分层深入** - 文件→类→方法→实现
4. **关注抽象层** - 先看接口/抽象类，再看具体实现
5. **缓存搜索结果** - 将重要文件路径记录下来

### 在Claude Code会话中的应用

```typescript
// 示例会话：了解游戏架构

// 用户：我想了解游戏的玩家系统
// 助手：

// 1. 先搜索玩家相关文件
const playerFiles = Grep({pattern: "Player", glob: "**/*.java", output_mode: "files_with_matches", head_limit: 8})

// 2. 根据结果，读取关键文件的部分内容
Read({file_path: "game/src/main/java/staraxis/game/nation/design/PlayerNationDesign.java", limit: 30})

// 3. 搜索玩家相关方法
const playerMethods = Grep({pattern: "getPlayer|setPlayer|createPlayer", glob: "**/*.java", output_mode: "content", -n: true, head_limit: 5})

// 4. 选择性深入
Read({file_path: "webnet/src/main/java/staraxis/webnet/api/nation/PlayerNationApi.java", offset: 1, limit: 40})
```

通过这种策略，你可以用很少的token消耗快速了解代码结构，而不是全量读取大量文件。