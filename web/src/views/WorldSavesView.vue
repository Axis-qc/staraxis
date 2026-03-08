<script setup lang="ts">
/**
 * @file WorldSavesView.vue
 *
 * @description
 * 世界/存档一体视图：
 * - /worlds：新游戏入口（世界界面），强调“创建世界 + 加入世界”喵。
 * - /load-game：加载游戏入口（存档界面），强调“浏览存档 + 按存档加载”喵。
 *
 * 说明：
 * - 该视图通过 route.meta.viewMode 区分界面模式，避免重复维护两套页面逻辑喵。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useWorldSessionStore } from '../stores/worldSession'
import {
  listWorldSaves,
  createWorldSave,
  joinWorldSave,
  getWorldPlayerState,
  listWorldSaveFiles,
  loadWorldFromSave,
  type TickPolicy,
  type WorldSaveItem,
} from '../net/worldSavesApi'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const worldSession = useWorldSessionStore()

const loading = ref(false)
const errorMsg = ref('')
const worlds = ref<WorldSaveItem[]>([])

const createForm = ref({
  worldName: '',
  worldRadius: 12,
  worldSeed: '',
  tickPolicy: 'RUN_WHEN_ONLINE' as TickPolicy,
  spawnMode: 'manual' as 'manual' | 'random',
})


/**
 * 角色权限：仅管理员可创建/加载控制世界；普通用户仅可加入运行中世界喵。
 */
const isAdmin = computed(() => String(auth.role || '').toUpperCase() === 'ADMIN')
const saveFilesByWorldId = ref<Record<string, Array<{ fileName: string; saveType: 'latest' | 'auto' | 'manual'; path: string; lastModifiedEpochMs: number; sizeBytes: number }>>>({})

/**
 * 页面模式：
 * - worlds：新游戏（世界界面）
 * - saves：加载游戏（存档界面）喵。
 */
const viewMode = computed<'worlds' | 'saves'>(() => {
  const mode = route.meta?.viewMode
  return mode === 'saves' ? 'saves' : 'worlds'
})

async function refreshWorlds() {
  loading.value = true
  errorMsg.value = ''
  try {
    const resp = await listWorldSaves()
    worlds.value = resp.worlds || []
  } catch (e: any) {
    errorMsg.value = String(e?.message || e)
  } finally {
    loading.value = false
  }
}

async function onCreateWorld() {
  loading.value = true
  errorMsg.value = ''
  try {
    await createWorldSave({
      worldName: createForm.value.worldName,
      worldRadius: createForm.value.worldRadius,
      worldSeed: createForm.value.worldSeed,
      tickPolicy: createForm.value.tickPolicy,
      spawnMode: createForm.value.spawnMode,
      creatorPlayerId: auth.playerId || '',
    })
    await refreshWorlds()
  } catch (e: any) {
    errorMsg.value = String(e?.message || e)
  } finally {
    loading.value = false
  }
}

async function onJoinWorld(world: WorldSaveItem) {
  if (!auth.playerId) {
    errorMsg.value = 'playerId_required'
    return
  }

  loading.value = true
  errorMsg.value = ''
  try {
    const joinResp = await joinWorldSave(world.worldId, auth.playerId)
    if (!joinResp.ok) {
      throw new Error(joinResp.error || 'join_failed')
    }

    worldSession.setSelectedWorld(world.worldId, world.worldName)
    worldSession.setPlayerWorldState((joinResp.playerState as any) || 'SPAWN_PENDING')
    worldSession.markJoinedNow()

    // SPAWNED 玩家进入时，从 players.json 同步 nationId 到前端会话喵。
    if (joinResp.nationId && String(joinResp.nationId).trim()) {
      auth.setSelectedNationId(String(joinResp.nationId))
    }

    // 加入后立即请求世界数据（通过进入 in-game 建立快照流）喵
    const stateResp = await getWorldPlayerState(world.worldId, auth.playerId)
    if (stateResp.ok) {
      worldSession.setPlayerWorldState((stateResp.playerState as any) || 'SPAWN_PENDING')
      const stateNationId = (stateResp.playerRole as any)?.nationId
      if (stateNationId && String(stateNationId).trim()) {
        auth.setSelectedNationId(String(stateNationId))
      }
    }

    // 出生流程统一并入 in-game 页面喵（创建世界时仅设置策略）
    router.push('/in-game')
  } catch (e: any) {
    errorMsg.value = String(e?.message || e)
  } finally {
    loading.value = false
  }
}

async function onRefreshSaves(worldId: string) {
  loading.value = true
  errorMsg.value = ''
  try {
    const resp = await listWorldSaveFiles(worldId)
    if (!resp.ok) {
      throw new Error(resp.error || 'list_saves_failed')
    }
    saveFilesByWorldId.value = {
      ...saveFilesByWorldId.value,
      [worldId]: resp.saves || [],
    }
  } catch (e: any) {
    errorMsg.value = String(e?.message || e)
  } finally {
    loading.value = false
  }
}

async function onLoadSave(worldId: string, fileName: string, saveType: 'latest' | 'auto' | 'manual') {
  loading.value = true
  errorMsg.value = ''
  try {
    const loadType = saveType === 'latest' ? 'latest' : (saveType === 'auto' ? 'auto' : 'manual')
    const resp = await loadWorldFromSave(worldId, { loadType, fileName })
    if (!resp.ok) {
      throw new Error(resp.error || 'load_world_failed')
    }
    await refreshWorlds()
  } catch (e: any) {
    errorMsg.value = String(e?.message || e)
  } finally {
    loading.value = false
  }
}


onMounted(() => {
  void refreshWorlds()
})
</script>

<template>
  <div class="page">
    <section class="panel">
      <h1>{{ viewMode === 'worlds' ? '世界界面' : '存档界面' }}</h1>
      <p v-if="viewMode === 'worlds'">创建新世界并加入，进入出生流程喵。</p>
      <p v-else>浏览并加载现有存档，再加入世界喵。</p>

      <div v-if="viewMode === 'worlds' && isAdmin" class="row">
        <input v-model="createForm.worldName" placeholder="世界名称" />
        <input v-model.number="createForm.worldRadius" type="number" min="1" max="512" placeholder="半径" />
        <input v-model="createForm.worldSeed" placeholder="种子（可选）" />
        <select v-model="createForm.tickPolicy">
          <option value="RUN_WHEN_ONLINE">有玩家在线才推进</option>
          <option value="ALWAYS_RUN">无视在线持续推进</option>
        </select>
        <select v-model="createForm.spawnMode">
          <option value="manual">进入游戏后手动选出生点</option>
          <option value="random">创建世界时随机出生</option>
        </select>
        <button :disabled="loading" @click="onCreateWorld">创建世界</button>
      </div>

      <div class="list">
        <div v-for="w in worlds" :key="w.worldId" class="item-col">
          <div class="item">
            <div>
              <div class="name">{{ w.worldName }}</div>
              <div class="meta">{{ w.worldId }} | R={{ w.worldRadius }} | {{ w.tickPolicy }} | tick={{ w.simulationTick }}</div>
            </div>
            <div class="row">
              <button :disabled="loading" @click="onJoinWorld(w)">加入</button>
              <button v-if="isAdmin" :disabled="loading" @click="onRefreshSaves(w.worldId)">刷新存档</button>
            </div>
          </div>

          <div v-if="viewMode === 'saves' && isAdmin && saveFilesByWorldId[w.worldId]?.length" class="list">
            <div v-for="s in saveFilesByWorldId[w.worldId]" :key="`${w.worldId}:${s.fileName}`" class="save-item">
              <div class="meta">{{ s.fileName }} | {{ s.saveType }} | {{ s.sizeBytes }} bytes</div>
              <button :disabled="loading" @click="onLoadSave(w.worldId, s.fileName, s.saveType)">加载</button>
            </div>
          </div>
        </div>
      </div>


      <div v-if="errorMsg" class="error">{{ errorMsg }}</div>
    </section>
  </div>
</template>

<style scoped>
.page { padding: 20px; color: var(--text-color); }
.panel { border: 1px solid #345; border-radius: 10px; padding: 16px; background: rgba(0,0,0,0.35); }
.row { display: flex; gap: 8px; margin: 10px 0; flex-wrap: wrap; }
.list { display: grid; gap: 8px; margin-top: 12px; }
.item-col { display: grid; gap: 8px; }
.item { display: flex; justify-content: space-between; align-items: center; border: 1px solid #345; border-radius: 8px; padding: 10px; }
.save-item { display: flex; justify-content: space-between; align-items: center; border: 1px dashed #345; border-radius: 8px; padding: 8px; }
.name { font-weight: 700; }
.meta { font-size: 12px; opacity: 0.8; }
.spawn-box { margin-top: 16px; border-top: 1px dashed #456; padding-top: 12px; }
.spawn-item { display: flex; align-items: center; gap: 8px; border: 1px solid #345; border-radius: 8px; padding: 8px; }
.error { color: #ff6b6b; margin-top: 10px; }
input, select, button { background: #111a; color: var(--text-color); border: 1px solid #345; border-radius: 8px; padding: 6px 10px; }
</style>
