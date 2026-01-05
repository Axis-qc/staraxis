# Contract: Semantic Zoom Tiers (语义缩放层级规范)

**Feature**: [005-hex-world-gen](../spec.md)
**Status**: DRAFT
**Created**: 2026-01-05

## Overview

定义六边形网格在不同缩放级别下的渲染细节（LOD）与交互规则。

## Zoom Levels (0.1 - 2.0)

| Tier | Zoom Range | Display Name | Rendering Detail (LOD) |
|------|------------|--------------|------------------------|
| **1: Micro** | > 1.2 | 近景 (Detailed) | 显示完整瓦片颜色、高精边框、资源图标（预留）、动画（预留） |
| **2: Normal** | 0.5 - 1.2 | 常规 (Balanced) | 显示瓦片颜色、标准边框、高亮效果 |
| **3: Macro** | < 0.5 | 远景 (Abstract) | 仅显示简化色块，隐藏细边框，优化大规模渲染性能 |

## Behavior Rules

1. **Culling**: 无论何种层级，视口外瓦片均不参与渲染。
2. **Transition**: 缩放越过阈值时，渲染器应立即切换渲染分支。
3. **Panning**: 鼠标右键拖拽或 WASD 平移，速度应与当前缩放级别成反比（缩放越小，平移跨度相对越大）。
4. **Zooming**: 鼠标滚轮缩放，以鼠标指向点为中心。

## Technical Constraints

- **Min Zoom**: 0.1
- **Max Zoom**: 2.0
- **Default**: 1.0 (Normal)
