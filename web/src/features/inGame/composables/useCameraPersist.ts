/**
 * @file useCameraPersist.ts
 *
 * @description
 * 玩家镜头状态持久化逻辑（SessionStorage）。
 *
 * 本 Composable 负责：
 * - 将镜头的世界坐标（cameraWorldPosGU）与缩放（zoom）持久化到 sessionStorage。
 * - 支持按 playerId（玩家ID）分 key 存储，实现多用户/多角色的状态隔离。
 * - 采用 requestAnimationFrame (RAF) 节流机制，避免在拖拽或缩放时频繁触发 IO。
 * - 实现“仅防刷新”效果：界面刷新时保留镜头，完全关闭浏览器/重启会话后重置到默认位置（如首都）。
 *
 * @usage
 * - 在进入游戏视图时调用 `loadPersistedCameraState(playerId)` 获取初始状态。
 * - 在渲染器就绪后，通过 `createCameraStatePersister` 创建并 `attach()` 持久化器。
 *
 * @security
 * - 仅存储非敏感的 UI 状态，不包含任何游戏逻辑权限喵。
 */
import type { Ref } from 'vue'

import type { WorldRenderer } from '../../../rendering/worldRenderManager'

export type PersistedCameraState = {
    cameraWorldPosGU: { x: number; y: number }
    zoom: number
}

const STORAGE_PREFIX = 'staraxis_camera_state_'

function safeJsonParse<T>(raw: string): T | null {
    try {
        return JSON.parse(raw) as T
    } catch {
        return null
    }
}

/**
 * 加载持久化的镜头状态喵
 * @param playerId 玩家ID喵
 */
export function loadPersistedCameraState(playerId: string): PersistedCameraState | null {
    if (!playerId) return null
    try {
        const raw = sessionStorage.getItem(STORAGE_PREFIX + playerId)
        if (!raw) return null
        const parsed = safeJsonParse<PersistedCameraState>(raw)
        if (!parsed) return null

        const x = parsed.cameraWorldPosGU?.x
        const y = parsed.cameraWorldPosGU?.y
        const z = parsed.zoom

        if (!Number.isFinite(x) || !Number.isFinite(y) || !Number.isFinite(z)) return null

        return {
            cameraWorldPosGU: { x, y },
            zoom: z,
        }
    } catch {
        return null
    }
}

/**
 * 创建相机状态持久化器喵
 * @param renderer 渲染器引用喵
 * @param playerId 玩家ID喵
 */
export function createCameraStatePersister(renderer: Ref<WorldRenderer | null>, playerId: Ref<string>) {
    let rafId: number | null = null
    let pending: PersistedCameraState | null = null

    const flush = () => {
        rafId = null
        const pid = playerId.value
        if (!pending || !pid) return
        try {
            sessionStorage.setItem(STORAGE_PREFIX + pid, JSON.stringify(pending))
        } catch {
            // ignore
        }
    }

    const schedulePersist = () => {
        const r = renderer.value
        const pid = playerId.value
        if (!r || !pid) return

        pending = {
            cameraWorldPosGU: { x: r.cameraWorldPosGU.x, y: r.cameraWorldPosGU.y },
            zoom: r.zoom.value,
        }

        if (rafId !== null) return
        rafId = window.requestAnimationFrame(flush)
    }

    let unsubscribe: null | (() => void) = null

    const attach = () => {
        const r = renderer.value
        if (!r) return
        unsubscribe = r.onCameraChanged(() => {
            schedulePersist()
        })
    }

    const detach = () => {
        if (unsubscribe) {
            unsubscribe()
            unsubscribe = null
        }
        if (rafId !== null) {
            window.cancelAnimationFrame(rafId)
            rafId = null
        }
        pending = null
    }

    return {
        attach,
        detach,
        schedulePersist,
    }
}
