import { ref, onMounted } from 'vue'
import { fetchMods, saveMods, type ModItem } from '../services/backend'

/**
 * @description 管理 Mods 列表、加载、排序和保存的 Composable。
 */
export function useMods() {
    // --- 响应式状态 ---
    const mods = ref<ModItem[]>([])
    const modsLoading = ref(false)
    const modsError = ref<string | null>(null)
    const modsSaveState = ref<'idle' | 'saving' | 'saved' | 'error'>('idle')
    const expandedModId = ref<string | null>(null)

    // --- 暴露给外部的方法 ---
    async function loadMods() {
        modsLoading.value = true
        modsError.value = null
        try {
            const data = await fetchMods()
            // 确保按 orderIndex 排序
            mods.value = [...data.mods].sort((a, b) => a.orderIndex - b.orderIndex)
        } catch (e) {
            modsError.value = (e as Error).message
        } finally {
            modsLoading.value = false
        }
    }

    function moveMod(index: number, dir: -1 | 1) {
        const next = index + dir
        if (next < 0 || next >= mods.value.length) return

        const arr = [...mods.value]
        const a = arr[index]
        const b = arr[next]
        if (!a || !b) return

        // 交换位置
        arr[index] = b
        arr[next] = a

        // 更新 orderIndex 并赋值回响应式引用
        mods.value = arr.map((m, i) => ({ ...m, orderIndex: i }))
    }

    function toggleModEnabled(id: string, enabled: boolean) {
        mods.value = mods.value.map((m) => (m.id === id ? { ...m, enabled } : m))
    }

    async function saveModsToServer() {
        if (modsSaveState.value === 'saving') return
        modsSaveState.value = 'saving'
        try {
            const order = mods.value.map((m) => m.id)
            const disabled = mods.value.filter((m) => !m.enabled).map((m) => m.id)
            await saveMods(order, disabled)
            modsSaveState.value = 'saved'
        } catch {
            modsSaveState.value = 'error'
        }
        // 1.6秒后重置保存状态
        setTimeout(() => {
            if (modsSaveState.value !== 'saving') {
                modsSaveState.value = 'idle'
            }
        }, 1600)
    }

    // --- 生命周期钩子 ---
    onMounted(() => {
        loadMods()
    })

    return {
        mods,
        modsLoading,
        modsError,
        modsSaveState,
        expandedModId,
        loadMods,
        moveMod,
        toggleModEnabled,
        saveModsToServer,
    }
}
