# 智能搜索工具使用指南

## 概述

这个智能搜索工具包旨在帮助你在大型代码库中更有效地搜索，减少全量读取的需求，从而降低token消耗。工具基于关键词进行智能过滤，按相关性排序结果。

## 工具组成

### 主要工具（依赖Python）
1. **`smart_search.py`** - 核心Python搜索脚本（智能过滤、相关性排序）
2. **`search_helper.sh`** - Linux/macOS辅助脚本
3. **`search.bat`** - Windows批处理脚本

### 备用工具（不依赖Python）
4. **`quick_search.sh`** - Linux/macOS快速搜索（使用系统工具）
5. **`qsearch.bat`** - Windows快速搜索（使用系统工具）
6. **`SEARCH_GUIDE.md`** - 本使用指南

## 安装要求

- Python 3.6+
- 标准库（无需额外安装）

## 快速开始

### 方案A：使用Python工具（推荐，功能更强大）

#### Windows用户
```bash
# 基本搜索
search.bat Player

# 搜索并预览第一个结果
search.bat Player --preview

# 只搜索Java文件
search.bat Player --java

# JSON格式输出
search.bat "Game Loop" --json
```

#### Linux/macOS用户
```bash
# 使脚本可执行
chmod +x search_helper.sh

# 基本搜索
./search_helper.sh search Player

# 搜索Java类
./search_helper.sh class Player

# 搜索玩家相关代码
./search_helper.sh player

# 简洁格式输出
./search_helper.sh simple "Game Loop"
```

### 方案B：使用快速搜索工具（不依赖Python）

#### Windows用户
```cmd
# 基本搜索
qsearch.bat Player

# 搜索Java文件
qsearch.bat Player --java

# 搜索配置文件
qsearch.bat config --config

# 预览第一个结果
qsearch.bat Player --preview
```

#### Linux/macOS用户
```bash
# 使脚本可执行
chmod +x quick_search.sh

# 基本搜索
./quick_search.sh Player

# 搜索Java文件
./quick_search.sh Player --java

# 搜索Markdown文档
./quick_search.sh "游戏设计" --md

# 预览第一个结果
./quick_search.sh Player --preview
```

## 工具选择指南

### Python工具 vs 快速搜索工具

| 特性 | Python工具 (`smart_search.py`) | 快速搜索工具 (`qsearch.bat`/`quick_search.sh`) |
|------|--------------------------------|-----------------------------------------------|
| **依赖** | 需要Python 3.6+ | 只需系统工具 (find/grep/findstr) |
| **功能** | 智能过滤、相关性评分、多层级搜索 | 基本文件名和内容搜索 |
| **速度** | 较慢（需要分析内容） | 快速（简单模式匹配） |
| **输出** | 格式丰富，有上下文预览 | 简单文件列表 |
| **适用场景** | 深度代码分析、精确搜索 | 快速文件定位、简单搜索 |

### 推荐使用策略

1. **快速定位文件** → 使用快速搜索工具
2. **深度代码分析** → 使用Python智能搜索
3. **无Python环境** → 使用快速搜索工具
4. **需要相关性排序** → 使用Python智能搜索

## 核心功能

### 1. 智能过滤策略
搜索脚本使用多层级过滤：

1. **文件名匹配**（最高优先级）
2. **类/接口定义匹配**
3. **方法定义匹配**
4. **注释匹配**
5. **一般内容匹配**

### 2. 相关性评分
系统为每个结果计算相关性分数，基于：
- 匹配类型（文件名 > 类定义 > 方法定义 > 注释 > 内容）
- 查询类别（接口优先于具体类）
- 核心系统文件（game/, player/, entity/ 等）
- 抽象程度（接口 > 抽象类 > 具体类）

### 3. 文件类型支持
支持的文件类型：
- Java (`.java`)
- Kotlin (`.kt`)
- Python (`.py`)
- JavaScript/TypeScript (`.js`, `.ts`, `.jsx`, `.tsx`)
- 配置文件 (`.json`, `.xml`, `.yml`, `.properties`)
- 文档文件 (`.md`, `.txt`)

## 使用示例

### 搜索游戏核心类
```bash
# 搜索Game相关的Java类
search.bat "class Game" --java

# 搜索结果示例：
# 1. [CLASS_DEF] game/src/main/java/staraxis/game/StarAxisGameRuntime.java
# 2. [CLASS_DEF] game/src/main/java/staraxis/game/GameRuntime.java
```

### 搜索玩家相关代码
```bash
# 搜索Player相关内容
search.bat Player --preview

# 会自动识别玩家相关文件并优先显示
```

### 搜索配置系统
```bash
# 搜索配置相关
search.bat config --simple
```

### 搜索UI组件
```bash
# 搜索UI相关
search.bat ui --java
```

## 高级用法

### 直接使用Python脚本
```python
# 基本搜索
python smart_search.py "Player" --max 20

# 指定文件类型
python smart_search.py "Entity" --type java

# JSON输出（适合程序处理）
python smart_search.py "Game Loop" --format json

# 预览功能
python smart_search.py "Player" --preview
```

### 搜索策略调整
如果需要修改搜索策略，可以编辑 `smart_search.py`：

1. **调整忽略模式**：修改 `ignore_patterns` 列表
2. **调整评分权重**：修改 `calculate_score` 方法
3. **添加文件类型**：扩展 `file_extensions` 字典
4. **自定义关键词类别**：修改 `keyword_categories` 字典

## 在Claude Code中使用

### 推荐工作流
```bash
# 1. 先用搜索工具定位文件
./search_helper.sh search "PlayerController" --simple

# 2. 查看结果中的文件路径
# 输出: ./game/src/main/java/staraxis/game/player/PlayerController.java

# 3. 在Claude Code中只读取相关部分
Read({file_path: "game/src/main/java/staraxis/game/player/PlayerController.java", limit: 50})
```

### 集成到Claude Code会话
```python
# 在Claude Code中执行搜索命令
Bash({command: "python smart_search.py 'Player' --simple"})

# 根据搜索结果选择性读取文件
```

## 性能优化

### 减少搜索范围
```bash
# 只搜索Java文件（减少搜索时间）
search.bat "update" --java

# 限制结果数量
search.bat "render" --max 10
```

### 使用缓存
搜索脚本不内置缓存，但对于常用查询，你可以：

1. 保存搜索结果到文件：
```bash
search.bat "Player" --json > player_search.json
```

2. 重用搜索结果：
```bash
python smart_search.py "Player" --format json | jq '.[0].file'
```

## 常见问题

### Q: 搜索速度慢怎么办？
A: 使用 `--java` 或 `--type` 参数限制文件类型，减少搜索范围。

### Q: 如何搜索包含空格的关键词？
A: 使用双引号包裹：`search.bat "Game Loop"`

### Q: 如何排除某些目录？
A: 编辑 `smart_search.py` 中的 `ignore_patterns` 列表。

### Q: 支持正则表达式吗？
A: 目前不支持完整正则，但支持简单的模式匹配。

### Q: 如何添加对新文件类型的支持？
A: 在 `file_extensions` 字典中添加新的扩展名映射。

## 扩展建议

### 1. 添加向量搜索支持
如果需要语义搜索，可以考虑集成：
- Sentence Transformers (BGE模型)
- Chroma向量数据库
- 本地嵌入计算

### 2. 添加索引功能
可以预先建立文件索引，加速搜索：
```python
# 索引关键信息：类名、方法名、接口定义等
# 保存到JSON索引文件
# 搜索时先查询索引，再读取文件
```

### 3. 集成到IDE
可以将搜索工具集成到VS Code、IntelliJ等IDE中，作为外部工具调用。

## 贡献与反馈

这是一个基础版本的工具，欢迎根据项目需求进行定制和扩展。主要设计原则：

1. **减少全量读取** - 先搜索，再选择性读取
2. **智能过滤** - 按相关性排序，优先显示重要结果
3. **轻量级** - 不依赖外部库，易于部署

如果有改进建议，请编辑脚本文件或创建新版本。