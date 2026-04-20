/**
 * @file worldRenderSubsystem.ts
 *
 * @description
 * 旧版世界渲染子系统接口。
 *
 * @important_notes
 * - 当前渲染架构正在迁移到 layer 模式。
 * - 仍未迁移完成的旧渲染器可暂时继续依赖此接口，直到完成替换。
 */

import type { WorldRenderContext, WorldFrameState } from '../worldRenderManager'

export type WorldRenderSubsystem = {
    init: (ctx: WorldRenderContext) => void
    update: (ctx: WorldRenderContext, frame: WorldFrameState) => void
    dispose: (ctx: WorldRenderContext) => void
}
