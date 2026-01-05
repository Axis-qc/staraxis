# Quickstart: Hex World Generation & New Game Config

**Feature**: [spec.md](./spec.md)  
**Branch**: `005-hex-world-gen`  
**Created**: 2026-01-05

## Prerequisites

- 已安装 JDK（项目使用 Java 21）
- Gradle Wrapper 可用

## Run

在仓库根目录执行：

- `./gradlew :lwjgl3:run`

## Manual Verification Checklist

### 1) 主菜单跳转
- 在主菜单点击“新游戏”
- 期望：进入“新游戏配置”界面（不是直接开始游戏）

### 2) 配置界面字段
- 期望显示并可编辑：
  - 地图大小（预设：small/medium/large；含说明：半径 R）
  - 宜居星球比例（0.0-1.0）
  - 地图种子（字符串，可为空）
- 期望显示但不可编辑（置灰/标注开发中）：
  - AI 数量
  - 技术等级

### 3) 种子确定性
- 输入种子：`STARAXIS`，点击开始
- 重启游戏后重复相同配置
- 期望：生成的地图布局一致

### 4) 世界渲染
- 进入世界后：
  - 显示六边形网格
  - 顶部正交视角（无透视缩放）
  - 支持语义缩放（低缩放抽象、高缩放细节）

### 5) 拾取/高亮
- 鼠标悬停/点击某个六边形
- 期望：该格高亮，并能获取到对应网格坐标
