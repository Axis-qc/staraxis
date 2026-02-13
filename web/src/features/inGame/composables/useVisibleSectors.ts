import { computed, onUnmounted, ref } from 'vue'
import type { Ref } from 'vue'
import type { SnapshotWsClient } from '../../../net/snapshotWs'
import type { WorldRenderer } from '../../../rendering/worldRenderManager'
import { SECTOR_SIZE_GU } from '../../../rendering/hexSectorGeometry'

export function useVisibleSectors(
    renderer: Ref<WorldRenderer | null>,
    wsClient: Ref<SnapshotWsClient | null>,
    hubEntities: Ref<any[]>,
    selectedIds: Ref<number[]>
) {
    const lastVisibleSectorsKey = ref('')
    const sectorGraceMap = new Map<string, number>()
    const currentSubscribedKeys = new Set<string>()

    const REPORT_THROTTLE_MS = 200
    const UNSUBSCRIBE_GRACE_MS = 1000
    const SUBSCRIBE_AABB_SCALE = 1.3

    let lastReportTime = 0
    let cacheCleanerTimer: number | null = null

    const selectedIdsSet = computed(() => new Set(selectedIds.value))

    function updateServerVisibleSectors() {
        const r = renderer.value
        const ws = wsClient.value
        if (!r || !ws) return

        const now = Date.now()
        if (now - lastReportTime < REPORT_THROTTLE_MS) return
        lastReportTime = now

        const cullingAabb = r.getCullingAabbGU()
        if (!cullingAabb) return

        const width = cullingAabb.maxX - cullingAabb.minX
        const height = cullingAabb.maxY - cullingAabb.minY
        const subMinX = cullingAabb.minX - (width * (SUBSCRIBE_AABB_SCALE - 1)) / 2
        const subMaxX = cullingAabb.maxX + (width * (SUBSCRIBE_AABB_SCALE - 1)) / 2
        const subMinY = cullingAabb.minY - (height * (SUBSCRIBE_AABB_SCALE - 1)) / 2
        const subMaxY = cullingAabb.maxY + (height * (SUBSCRIBE_AABB_SCALE - 1)) / 2

        const size = SECTOR_SIZE_GU
        const minR = Math.floor(subMinY / (size * 1.5)) - 1
        const maxR = Math.ceil(subMaxY / (size * 1.5)) + 1

        const activeKeysInFrame = new Set<string>()

        for (let rCoord = minR; rCoord <= maxR; rCoord++) {
            const xOffsetForR = (size * Math.sqrt(3) / 2) * rCoord
            const minQ = Math.floor((subMinX - xOffsetForR) / (size * Math.sqrt(3))) - 1
            const maxQ = Math.ceil((subMaxX - xOffsetForR) / (size * Math.sqrt(3))) + 1

            for (let qCoord = minQ; qCoord <= maxQ; qCoord++) {
                const key = `${qCoord},${rCoord}`
                activeKeysInFrame.add(key)
                currentSubscribedKeys.add(key)
                sectorGraceMap.delete(key)
            }
        }

        const keysToRemove: string[] = []
        for (const subbedKey of currentSubscribedKeys) {
            if (!activeKeysInFrame.has(subbedKey)) {
                if (!sectorGraceMap.has(subbedKey)) {
                    sectorGraceMap.set(subbedKey, now + UNSUBSCRIBE_GRACE_MS)
                } else if (now > (sectorGraceMap.get(subbedKey) || 0)) {
                    keysToRemove.push(subbedKey)
                }
            }
        }
        for (const k of keysToRemove) {
            currentSubscribedKeys.delete(k)
            sectorGraceMap.delete(k)
        }

        const reportKey = Array.from(currentSubscribedKeys).sort().join('|')
        if (reportKey !== lastVisibleSectorsKey.value) {
            lastVisibleSectorsKey.value = reportKey
            const finalSectors = Array.from(currentSubscribedKeys).map(k => {
                const parts = k.split(',')
                const q = parseInt(parts[0] ?? '0', 10)
                const r = parseInt(parts[1] ?? '0', 10)
                return { q, r }
            })
            console.log(`[VisibleSectors] Reporting ${finalSectors.length} sectors to server 喵`)
            ws.updateVisibleSectors(finalSectors)
        }
    }

    function cleanUnsubscribedEntitiesCache() {
        const r = renderer.value
        if (!r) return

        const sIds = selectedIdsSet.value
        const entitiesToClear: number[] = []
        const sectorsToClear: string[] = []

        for (const entity of hubEntities.value) {
            const key = `${entity.sectorCoord.q},${entity.sectorCoord.r}`
            if (!currentSubscribedKeys.has(key) && !sIds.has(entity.entityId)) {
                entitiesToClear.push(entity.entityId)
                sectorsToClear.push(key)
            }
        }

        if (entitiesToClear.length > 0) {
            const uniqueSectors = Array.from(new Set(sectorsToClear))
            r.removeEntitiesFromCache(entitiesToClear)
            r.removeSectorsFromCache(uniqueSectors)
            console.log(`[CacheCleaner] Cleaned ${entitiesToClear.length} entities, ${uniqueSectors.length} sectors.喵`)
        }
    }

    // 移除固定轮询，改为事件驱动机制喵
    let unbindCamera: (() => void) | null = null;

    // 监听渲染器相机变化喵
    const stopWatcher = () => {
        if (unbindCamera) {
            unbindCamera();
            unbindCamera = null;
        }
    };

    // 当渲染器实例发生变化时，重新绑定监听器喵
    const handleRendererChange = (newRenderer: WorldRenderer | null) => {
        stopWatcher();
        if (newRenderer) {
            unbindCamera = newRenderer.onCameraChanged(() => {
                updateServerVisibleSectors();
            });
            // 绑定后立即执行一次，确保初始视野正确喵
            updateServerVisibleSectors();
        }
    };

    // 启动清理器
    cacheCleanerTimer = window.setInterval(cleanUnsubscribedEntitiesCache, 5 * 60 * 1000)

    onUnmounted(() => {
        if (cacheCleanerTimer) window.clearInterval(cacheCleanerTimer)
        stopWatcher();
    })

    return {
        currentSubscribedKeys,
        updateServerVisibleSectors,
        handleRendererChange // 暴露给外部进行绑定喵
    }
}
