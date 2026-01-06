# Quickstart: Stellar & Planet Generation

**Feature**: [spec.md](./spec.md)  
**Branch**: `007-stellar-planet-gen`  
**Created**: 2026-01-06

## Prerequisites

- 已安装 JDK（项目使用 Java 21）
- Gradle Wrapper 可用

## Run

在仓库根目录执行：

- `./gradlew :lwjgl3:run`

## Manual Verification Checklist

### 1) 新游戏流程
- 在主菜单点击“新游戏”
- 期望：进入“世界生成设置/新游戏配置”界面（不是直接开始游戏）

### 2) 配置界面字段
- 期望显示并可编辑：
  - 地图大小（预设：small/medium/large；含说明：六边形半径 R；封闭边界）
  - Seed（字符串，可为空）
  - 恒星密度（0.0-1.0）
  - 行星复杂度（0.0-1.0 或预设）
  - 星云占比（0.0-1.0）

### 3) 多恒星系统
- 开始游戏后随机抽样多个“星系类区块”
- 期望：每个星系区块存在 1 个星系系统，恒星数量在 1..3

### 4) 行星归属
- 在一个包含 2..3 恒星的星系区块内
- 期望：行星明确归属到某一颗恒星（可通过 UI 信息/调试面板/日志验证）；不出现“系统重心行星”

### 5) 确定性
- 输入种子：`STARAXIS`，点击开始
- 重启游戏后重复相同配置
- 期望：六边形世界布局、星系/恒星/行星统计一致（可通过 `WorldScreen` 左上角 debug overlay 的 `stats: sectorCounts/starCount/planetCount` 对比验证）

## Performance Validation (SC-001 / SC-002)

### 1) World Generation Speed (SC-001)
- **Method**: 点击“开始游戏”后检查应用日志
- **Metric**: 搜索类似 `[WorldGen] Generated world: ... duration=Xms` 的日志
- **Criteria**: 默认地图预设下，生成耗时 `< 300ms`

### 2) Rendering Performance (SC-002)
- **Method**: 观察 `WorldScreen` 的 FPS 显示/性能表现
- **Criteria**: 1080p 下稳定 `60 FPS`（或显示器刷新率），无明显卡顿
