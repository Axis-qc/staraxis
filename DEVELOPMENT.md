# StarAxis 开发规范 (Naming & Header Conventions)

## 1. 命名规范 (Naming Conventions)
根据项目宪章和规格书，所有变量、方法及字段命名后需紧跟括号说明其具体职责。

### 示例：
- **方法**: `void calculatePosition /* 计算位置 */()`
- **变量**: `float currentHealth /* 当前生命值 */`
- **类**: `class GameServer /* 游戏服务端 */`

## 2. 文件头注释 (File Headers)
每个文件头必须包含标准注释块，说明文件作用、使用的接口、提供的接口及作用。

```java
/**
 * [文件作用简述]
 * 
 * 使用的接口 (Used Interfaces):
 * - InterfaceA: 用于...
 * 
 * 提供的接口 (Provided Interfaces):
 * - InterfaceB: 供...调用以实现...
 */
```

## 3. 核心原则
- **禁止硬编码**: 使用配置文件或常量类。
- **模块化**: 保持 core 与 lwjgl3 的物理隔离。
- **模拟驱动**: 逻辑必须基于 Tick (20Hz)，严禁依赖渲染帧率。
