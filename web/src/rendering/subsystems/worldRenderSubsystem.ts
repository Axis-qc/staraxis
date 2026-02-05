/**
 * @file worldRenderSubsystem.ts
 *
 * @description
 * 世界渲染子系统接口定义。
 *
 * 作用：
 * - 定义所有渲染子系统必须实现的标准接口。
 * - 提供统一的生命周期管理（init/update/dispose）。
 * - 通过 WorldRenderContext 和 WorldFrameState 接收渲染数据。
 *
 * @usage
 * - 子系统实现此接口：
 *   - export class StarRenderer implements WorldRenderSubsystem { ... }
 *   - 管理器注册子系统：subsystems.push(new StarRenderer())
 *
 * @provides
 * - **标准接口**：init/update/dispose 方法签名。
 * - **数据契约**：WorldRenderContext 和 WorldFrameState 的类型定义。
 *
 * @api
 * - init(ctx: WorldRenderContext): void
 * - update(ctx: WorldRenderContext, frame: WorldFrameState): void
 * - dispose(ctx: WorldRenderContext): void
 *
 * @important_notes
 * - 子系统应该保持无状态或通过 context/frameState 访问状态。
 * - 避免在子系统内部直接操作 DOM 或全局状态。
 */
export type WorldRenderSubsystem = {
    init(ctx: import('../worldRenderManager').WorldRenderContext): void
    update(ctx: import('../worldRenderManager').WorldRenderContext, frame: import('../worldRenderManager').WorldFrameState): void
    dispose(ctx: import('../worldRenderManager').WorldRenderContext): void
}