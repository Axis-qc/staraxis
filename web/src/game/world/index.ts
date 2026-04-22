/**
 * @file index.ts
 *
 * @description
 * 前端本地可见世界副本模块的入口文件喵。
 *
 * 统一导出所有类型和API，方便其他模块导入喵。
 */

// 类型定义喵
export * from './localVisibleWorldTypes'

// 主实现喵
export { LocalVisibleWorldImpl, getLocalVisibleWorld, resetLocalVisibleWorld } from './localVisibleWorld'

// 查询接口喵
export * from './localVisibleWorldQueries'

// Vue组合式函数喵
export { useLocalVisibleWorld, createWorldReactiveWrapper, useEntityPosition } from './useLocalVisibleWorld'

// 时间推进系统喵
export { LocalVisibleWorldSimulation, getLocalVisibleWorldSimulation, resetLocalVisibleWorldSimulation } from './localVisibleWorldSimulation'

// 快捷导入喵
export { getEntityDisplayPosition, getEntityWorldPosGU } from './localVisibleWorldQueries'