<script setup lang="ts">
/**
 * @file WorldSavesView.vue
 *
 * @description
 * 世界/存档一体视图（重构版）：
 * - /worlds：新游戏入口（世界界面），强调"创建世界 + 加入世界"喵。
 * - /load-game：加载游戏入口（存档界面），强调"浏览存档 + 按存档加载"喵。
 *
 * 重构亮点：
 * - 创建世界独立为弹窗，支持未来扩展更多选项喵。
 * - 卡片式世界列表，更具高级感喵。
 * - 视觉动效优化，整体风格统一喵。
 */
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useWorldSessionStore } from '../stores/worldSession'
import { useEnvironment } from '../utils/environment'
import {
  listWorldSaves,
  createWorldSave,
  joinWorldSave,
  getWorldPlayerState,
  listWorldSaveFiles,
  loadWorldFromSave,
  deleteWorldSave,
  type TickPolicy,
  type WorldSaveItem,
} from '../net/worldSavesApi'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import CreateWorldDialog from '../components/CreateWorldDialog.vue'
import type { CreateWorldForm } from '../components/CreateWorldDialog.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const worldSession = useWorldSessionStore()

const loading = ref(false)
const errorMsg = ref('')
const worlds = ref<WorldSaveItem[]>([])

/**
 * 环境检测：判断当前运行环境喵。
 */
const env = useEnvironment()

/**
 * 角色权限：仅管理员可创建/加载控制世界；普通用户仅可加入运行中世界喵。
 * 在本地开发环境（端口5173）自动启用管理员权限，方便开发测试喵。
 */
const isAdmin = computed(() => {
  const isNormalAdmin = String(auth.role || '').toUpperCase() === 'ADMIN'
  const isDevAdmin = env.shouldAutoAdmin && env.isLocalDev
  return isNormalAdmin || isDevAdmin
})

const saveFilesByWorldId = ref<Record<string, Array<{ fileName: string; saveType: 'latest' | 'auto' | 'manual'; path: string; lastModifiedEpochMs: number; sizeBytes: number }>>>({})

/**
 * 弹窗状态管理喵。
 */
const showCreateDialog = ref(false)
const confirmDialog = ref({
  show: false,
  worldId: '',
  worldName: '',
})

const confirmOptions = computed(() => ({
  title: '确认删除世界',
  message: `确定要删除世界 "${confirmDialog.value.worldName}" 吗？\n\n世界ID: ${confirmDialog.value.worldId}\n\n警告：此操作将永久删除该世界及其所有存档数据，不可恢复！`,
  confirmText: '删除',
  cancelText: '取消',
  danger: true,
}))

/**
 * 页面模式：
 * - worlds：新游戏（世界界面）
 * - saves：加载游戏（存档界面）喵。
 */
const viewMode = computed<'worlds' | 'saves'>(() => {
  const mode = route.meta?.viewMode
  return mode === 'saves' ? 'saves' : 'worlds'
})

/**
 * 运行中世界数量喵。
 */
const runningWorldsCount = computed(() => worlds.value.filter(w => w.active).length)

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

/**
 * 打开创建世界弹窗喵。
 */
function openCreateDialog() {
  showCreateDialog.value = true
}

/**
 * 处理创建世界事件喵。
 */
async function onCreateWorld(form: CreateWorldForm) {
  loading.value = true
  errorMsg.value = ''
  try {
    await createWorldSave({
      worldName: form.worldName,
      worldRadius: form.worldRadius,
      worldSeed: form.worldSeed,
      tickPolicy: form.tickPolicy,
      spawnMode: form.spawnMode,
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

function goToMainMenu() {
  router.push('/main-menu')
}

/**
 * 打开删除确认弹窗喵。
 */
function onDeleteWorld(worldId: string, worldName: string) {
  confirmDialog.value = {
    show: true,
    worldId,
    worldName,
  }
}

function onConfirmDialogConfirm() {
  executeDeleteWorld()
}

function onConfirmDialogCancel() {
  confirmDialog.value.show = false
}

async function executeDeleteWorld() {
  const { worldId, worldName } = confirmDialog.value
  loading.value = true
  errorMsg.value = ''
  try {
    const resp = await deleteWorldSave(worldId)
    if (!resp.ok) {
      throw new Error(resp.error || 'delete_world_failed')
    }
    await refreshWorlds()
  } catch (e: any) {
    errorMsg.value = String(e?.message || e)
  } finally {
    loading.value = false
    confirmDialog.value.show = false
  }
}

/**
 * 格式化日期喵。
 */
function formatDate(epochMs: number): string {
  return new Date(epochMs).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  })
}

/**
 * 格式化模拟刻为可读时间喵。
 */
function formatTick(tick: number): string {
  if (tick < 1000) return tick.toString()
  if (tick < 1000000) return (tick / 1000).toFixed(1) + 'K'
  return (tick / 1000000).toFixed(1) + 'M'
}

onMounted(() => {
  void refreshWorlds()
})
</script>

<template>
  <div class="page">
    <!-- 背景装饰喵 -->
    <div class="bg-decoration">
      <div class="bg-grid"></div>
      <div class="bg-glow"></div>
    </div>

    <section class="panel">
      <!-- 头部喵 -->
      <div class="panel-header">
        <div class="header-left">
          <button class="sa-btn back-btn" @click="goToMainMenu">
            <svg class="icon" viewBox="0 0 24 24" width="18" height="18">
              <path fill="currentColor" d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/>
            </svg>
            返回
          </button>
          <div class="title-section">
            <h1>{{ viewMode === 'worlds' ? '世界管理' : '存档管理' }}</h1>
            <p class="subtitle">
              {{ viewMode === 'worlds' ? '探索并管理你的星际世界' : '浏览并加载历史存档' }}
            </p>
          </div>
        </div>
        <div class="header-right">
          <div class="env-badge" :class="env.badgeClass" :title="env.displayName">
            {{ env.badgeText }}
            <span v-if="env.shouldAutoAdmin" class="admin-badge">⚡</span>
          </div>
          <div class="mode-switcher">
            <router-link :to="{ path: '/worlds' }" class="mode-link" :class="{ active: viewMode === 'worlds' }">
              <svg class="mode-icon" viewBox="0 0 24 24" width="18" height="18">
                <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
              </svg>
              世界
            </router-link>
            <router-link :to="{ path: '/load-game' }" class="mode-link" :class="{ active: viewMode === 'saves' }">
              <svg class="mode-icon" viewBox="0 0 24 24" width="18" height="18">
                <path fill="currentColor" d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM19 18H6c-2.21 0-4-1.79-4-4 0-2.05 1.53-3.76 3.56-3.97l1.07-.11.5-.95C8.08 7.14 9.94 6 12 6c2.62 0 4.88 1.86 5.39 4.43l.3 1.5 1.53.11c1.56.1 2.78 1.41 2.78 2.96 0 1.65-1.35 3-3 3zM13.45 6.5l-3.01 5.5h2.49l-2.56 4.5 4.46-5.5h-2.5l2.12-4.5z"/>
              </svg>
              存档
            </router-link>
          </div>
        </div>
      </div>

      <!-- 统计栏喵 -->
      <div class="stats-bar">
        <div class="stat-item">
          <span class="stat-value">{{ worlds.length }}</span>
          <span class="stat-label">总世界</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-value" :class="{ 'text-glow': runningWorldsCount > 0 }">{{ runningWorldsCount }}</span>
          <span class="stat-label">运行中</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item" v-if="isAdmin">
          <span class="stat-value admin">✓</span>
          <span class="stat-label">管理员</span>
        </div>
      </div>

      <!-- 操作栏喵 -->
      <div class="action-bar" v-if="viewMode === 'worlds' && isAdmin">
        <button class="sa-btn create-world-btn" @click="openCreateDialog" :disabled="loading">
          <svg viewBox="0 0 24 24" width="20" height="20">
            <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm5 11h-4v4h-2v-4H7v-2h4V7h2v4h4v2z"/>
          </svg>
          创建新世界
        </button>
      </div>

      <!-- 加载状态喵 -->
      <div v-if="loading && worlds.length === 0" class="loading-container">
        <div class="loading-spinner"></div>
        <div class="loading-text">正在加载世界数据...</div>
      </div>

      <!-- 世界列表喵 -->
      <div v-else class="worlds-list" :class="{ 'has-scroll': worlds.length > 4 }">
        <!-- 空状态喵 -->
        <div v-if="worlds.length === 0" class="empty-state">
          <div class="empty-icon">
            <svg viewBox="0 0 24 24" width="64" height="64">
              <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/>
            </svg>
          </div>
          <h3 class="empty-title">暂无世界</h3>
          <p class="empty-desc">
            {{ isAdmin ? '点击上方按钮创建你的第一个星际世界喵！' : '请等待管理员创建世界喵。' }}
          </p>
          <button v-if="isAdmin && viewMode === 'worlds'" class="sa-btn create-world-btn mini" @click="openCreateDialog">
            立即创建
          </button>
        </div>

        <!-- 世界卡片喵 -->
        <div v-for="w in worlds" :key="w.worldId" class="world-card" :class="{ active: w.active }">
          <div class="card-glow" v-if="w.active"></div>
          <div class="card-content">
            <!-- 卡片头部喵 -->
            <div class="card-header">
              <div class="world-icon" :class="{ active: w.active }">
                <svg v-if="w.active" viewBox="0 0 24 24" width="28" height="28">
                  <path fill="currentColor" d="M12 7c-2.76 0-5 2.24-5 5s2.24 5 5 5 5-2.24 5-5-2.24-5-5-5zm0 8c-1.65 0-3-1.35-3-3s1.35-3 3-3 3 1.35 3 3-1.35 3-3 3zM2 13h2c.55 0 1-.45 1-1s-.45-1-1-1H2c-.55 0-1 .45-1 1s.45 1 1 1zm18 0h2c.55 0 1-.45 1-1s-.45-1-1-1h-2c-.55 0-1 .45-1 1s.45 1 1 1zM11 2v2c0 .55.45 1 1 1s1-.45 1-1V2c0-.55-.45-1-1-1s-1 .45-1 1zm0 18v2c0 .55.45 1 1 1s1-.45 1-1v-2c0-.55-.45-1-1-1s-1 .45-1 1zM5.99 4.58a.996.996 0 00-1.41 0 .996.996 0 000 1.41l1.06 1.06c.39.39 1.03.39 1.41 0s.39-1.03 0-1.41L5.99 4.58zm12.37 12.37a.996.996 0 00-1.41 0 .996.996 0 000 1.41l1.06 1.06c.39.39 1.03.39 1.41 0 .39-.39.39-1.03 0-1.41l-1.06-1.06zm1.06-10.96a.996.996 0 000-1.41.996.996 0 00-1.41 0l-1.06 1.06c-.39.39-.39 1.03 0 1.41s1.03.39 1.41 0l1.06-1.06zM7.05 18.36a.996.996 0 000 1.41.996.996 0 001.41 0l1.06-1.06c.39-.39.39-1.03 0-1.41s-1.03-.39-1.41 0l-1.06 1.06z"/>
                </svg>
                <svg v-else viewBox="0 0 24 24" width="28" height="28">
                  <path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm-5-9c.83 0 1.5-.67 1.5-1.5S7.83 8 7 8s-1.5.67-1.5 1.5S6.17 11 7 11zm3-4c.83 0 1.5-.67 1.5-1.5S10.83 4 10 4s-1.5.67-1.5 1.5S9.17 7 10 7zm5 0c.83 0 1.5-.67 1.5-1.5S15.83 4 15 4s-1.5.67-1.5 1.5S14.17 7 15 7zm3 4c.83 0 1.5-.67 1.5-1.5S18.83 8 18 8s-1.5.67-1.5 1.5S17.17 11 18 11z"/>
                </svg>
              </div>
              <div class="world-title">
                <h3 class="world-name">{{ w.worldName }}</h3>
                <div class="world-badges">
                  <span class="badge" :class="w.tickPolicy === 'RUN_WHEN_ONLINE' ? 'online' : 'always'">
                    {{ w.tickPolicy === 'RUN_WHEN_ONLINE' ? '在线推进' : '持续推进' }}
                  </span>
                  <span class="badge" :class="w.active ? 'running' : 'stopped'">
                    {{ w.active ? '运行中' : '已停止' }}
                  </span>
                </div>
              </div>
            </div>

            <!-- 卡片数据喵 -->
            <div class="card-stats">
              <div class="stat">
                <span class="stat-icon">
                  <svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8z"/><circle cx="12" cy="12" r="3" fill="currentColor"/></svg>
                </span>
                <span class="stat-label">半径</span>
                <span class="stat-value">{{ w.worldRadius }} ly</span>
              </div>
              <div class="stat">
                <span class="stat-icon">
                  <svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zm.5-13H11v6l5.25 3.15.75-1.23-4.5-2.67z"/></svg>
                </span>
                <span class="stat-label">模拟刻</span>
                <span class="stat-value">{{ formatTick(w.simulationTick) }}</span>
              </div>
              <div class="stat">
                <span class="stat-icon">
                  <svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M19 3h-1V1h-2v2H8V1H6v2H5c-1.11 0-1.99.9-1.99 2L3 19c0 1.1.89 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm0 16H5V8h14v11zM9 10H7v2h2v-2zm4 0h-2v2h2v-2zm4 0h-2v2h2v-2z"/></svg>
                </span>
                <span class="stat-label">创建</span>
                <span class="stat-value">{{ formatDate(w.createdAtEpochMs) }}</span>
              </div>
              <div class="stat id-stat" :title="w.worldId">
                <span class="stat-icon">
                  <svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/></svg>
                </span>
                <span class="stat-label">ID</span>
                <span class="stat-value id-value">{{ w.worldId.slice(0, 8) }}...</span>
              </div>
            </div>

            <!-- 卡片操作喵 -->
            <div class="card-actions">
              <button class="sa-btn join-btn" :disabled="loading" @click="onJoinWorld(w)">
                <svg viewBox="0 0 24 24" width="16" height="16">
                  <path fill="currentColor" d="M12 4l-1.41 1.41L16.17 11H4v2h12.17l-5.58 5.59L12 20l8-8z"/>
                </svg>
                进入世界
              </button>
              <div class="admin-actions" v-if="isAdmin">
                <button class="sa-btn icon-btn" :disabled="loading" @click="onRefreshSaves(w.worldId)" title="刷新存档">
                  <svg viewBox="0 0 24 24" width="18" height="18">
                    <path fill="currentColor" d="M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z"/>
                  </svg>
                </button>
                <button class="sa-btn icon-btn danger" :disabled="loading" @click="onDeleteWorld(w.worldId, w.worldName)" title="删除世界">
                  <svg viewBox="0 0 24 24" width="18" height="18">
                    <path fill="currentColor" d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/>
                  </svg>
                </button>
              </div>
            </div>
          </div>

          <!-- 存档列表（存档模式下显示）喵 -->
          <div v-if="viewMode === 'saves' && isAdmin && saveFilesByWorldId[w.worldId]?.length" class="saves-section">
            <div class="saves-header">
              <span class="saves-title">
                <svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm0 12H4V8h16v10z"/></svg>
                存档文件
              </span>
              <span class="saves-count">{{ saveFilesByWorldId[w.worldId].length }} 个</span>
            </div>
            <div class="saves-list">
              <div v-for="s in saveFilesByWorldId[w.worldId]" :key="`${w.worldId}:${s.fileName}`" class="save-item">
                <div class="save-info">
                  <span class="save-type-icon" :class="s.saveType">
                    <svg v-if="s.saveType === 'latest'" viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M17 3H7c-1.1 0-2 .9-2 2v16l7-3 7 3V5c0-1.1-.9-2-2-2z"/></svg>
                    <svg v-else-if="s.saveType === 'auto'" viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 8 3.59 8 8-3.59 8-8 8zm-1-13h2v6h-2zm0 8h2v2h-2z"/></svg>
                    <svg v-else viewBox="0 0 24 24" width="18" height="18"><path fill="currentColor" d="M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z"/></svg>
                  </span>
                  <span class="save-name">{{ s.fileName }}</span>
                  <span class="save-size">{{ (s.sizeBytes / 1024).toFixed(1) }} KB</span>
                </div>
                <div class="save-time">{{ new Date(s.lastModifiedEpochMs).toLocaleString() }}</div>
                <button class="sa-btn mini load-btn" :disabled="loading" @click="onLoadSave(w.worldId, s.fileName, s.saveType)">
                  加载
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 错误提示喵 -->
      <div v-if="errorMsg" class="error-toast">
        <span class="error-icon">
          <svg viewBox="0 0 24 24" width="20" height="20"><path fill="currentColor" d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/></svg>
        </span>
        <span class="error-text">{{ errorMsg }}</span>
        <button class="close-btn" @click="errorMsg = ''">×</button>
      </div>
    </section>

    <!-- 创建世界弹窗喵 -->
    <CreateWorldDialog
      v-model="showCreateDialog"
      @create="onCreateWorld"
    />

    <!-- 删除确认弹窗喵 -->
    <ConfirmDialog
      v-model="confirmDialog.show"
      :options="confirmOptions"
      @confirm="onConfirmDialogConfirm"
      @cancel="onConfirmDialogCancel"
    />
  </div>
</template>

<style scoped>
/* 页面基础喵 */
.page {
  position: relative;
  padding: 24px;
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  min-height: 100vh;
  background: var(--background-color);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 背景装饰喵 */
.bg-decoration {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  pointer-events: none;
  z-index: 0;
}

.bg-grid {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image:
    linear-gradient(color-mix(in srgb, var(--glow-color) 5%, transparent) 1px, transparent 1px),
    linear-gradient(90deg, color-mix(in srgb, var(--glow-color) 5%, transparent) 1px, transparent 1px);
  background-size: 50px 50px;
  opacity: 0.5;
}

.bg-glow {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 800px;
  height: 800px;
  background: radial-gradient(
    circle,
    color-mix(in srgb, var(--glow-color) 8%, transparent) 0%,
    transparent 70%
  );
  animation: breathe 8s ease-in-out infinite;
}

@keyframes breathe {
  0%, 100% { opacity: 0.5; transform: translate(-50%, -50%) scale(1); }
  50% { opacity: 0.8; transform: translate(-50%, -50%) scale(1.1); }
}

/* 主面板喵 */
.panel {
  position: relative;
  z-index: 1;
  border: 1px solid color-mix(in srgb, var(--border-color) 70%, transparent);
  border-radius: 24px;
  padding: 28px;
  background: linear-gradient(
    165deg,
    color-mix(in srgb, var(--panel-bg) 80%, rgba(0, 0, 0, 0.1)),
    color-mix(in srgb, var(--panel-bg) 60%, rgba(0, 0, 0, 0.3))
  );
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  box-shadow:
    0 0 60px color-mix(in srgb, var(--glow-color) 8%, transparent),
    0 16px 48px rgba(0, 0, 0, 0.4);
  max-width: 1400px;
  margin: 0 auto;
  width: 100%;
  display: flex;
  flex-direction: column;
  flex: 1;
  max-height: calc(100vh - 48px);
  overflow: hidden;
}

/* 头部喵 */
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid color-mix(in srgb, var(--border-color) 40%, transparent);
}

.header-left {
  display: flex;
  align-items: flex-start;
  gap: 20px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  border-radius: 10px;
  font-size: 0.9rem;
  background: color-mix(in srgb, var(--sa-bg1) 40%, transparent);
  border: 1px solid var(--border-color);
  color: var(--text-color);
  cursor: pointer;
  transition: all 0.2s ease;
}

.back-btn:hover {
  border-color: var(--glow-color);
  background: color-mix(in srgb, var(--glow-color) 15%, transparent);
  transform: translateX(-2px);
}

.title-section h1 {
  font-size: 1.8rem;
  margin: 0 0 4px;
  color: var(--text-color-hover);
  text-shadow: 0 0 16px var(--glow-color);
  font-weight: 700;
}

.subtitle {
  margin: 0;
  font-size: 0.9rem;
  color: var(--sa-muted);
}

.header-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 12px;
}

/* 环境徽章喵 */
.env-badge {
  font-size: 0.7rem;
  padding: 4px 10px;
  border-radius: 20px;
  font-weight: bold;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  border: 1px solid transparent;
  background: color-mix(in srgb, var(--sa-bg1) 60%, transparent);
  color: var(--text-color);
  display: flex;
  align-items: center;
  gap: 6px;
}

.env-badge-local-dev {
  background: color-mix(in srgb, var(--glow-color) 20%, transparent);
  color: var(--glow-color);
  border-color: color-mix(in srgb, var(--glow-color) 50%, transparent);
  box-shadow: 0 0 12px color-mix(in srgb, var(--glow-color) 20%, transparent);
}

.admin-badge {
  color: #ffd700;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

/* 模式切换喵 */
.mode-switcher {
  display: flex;
  gap: 8px;
  background: color-mix(in srgb, var(--sa-bg1) 50%, transparent);
  border-radius: 12px;
  padding: 4px;
  border: 1px solid var(--border-color);
}

.mode-link {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 10px;
  font-size: 0.9rem;
  color: var(--sa-muted);
  text-decoration: none;
  transition: all 0.2s ease;
}

.mode-link:hover {
  color: var(--text-color);
  background: color-mix(in srgb, var(--sa-bg1) 70%, transparent);
}

.mode-link.active {
  color: var(--text-color-hover);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--glow-color) 30%, transparent),
    color-mix(in srgb, var(--glow-color) 15%, transparent)
  );
  border: 1px solid color-mix(in srgb, var(--glow-color) 50%, transparent);
  box-shadow: 0 0 16px color-mix(in srgb, var(--glow-color) 30%, transparent);
}

.mode-icon {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 统计栏喵 */
.stats-bar {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 20px;
  margin-bottom: 20px;
  background: color-mix(in srgb, var(--sa-bg1) 30%, transparent);
  border-radius: 12px;
  border: 1px solid color-mix(in srgb, var(--border-color) 50%, transparent);
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stat-value {
  font-size: 1.4rem;
  font-weight: 700;
  color: var(--text-color-hover);
  text-shadow: 0 0 8px var(--glow-color);
}

.stat-value.text-glow {
  color: var(--glow-color);
  animation: glowPulse 2s ease-in-out infinite;
}

.stat-value.admin {
  color: var(--glow-color);
}

@keyframes glowPulse {
  0%, 100% { text-shadow: 0 0 8px var(--glow-color); }
  50% { text-shadow: 0 0 20px var(--glow-color), 0 0 30px var(--glow-color); }
}

.stat-label {
  font-size: 0.85rem;
  color: var(--sa-muted);
}

.stat-divider {
  width: 1px;
  height: 24px;
  background: color-mix(in srgb, var(--border-color) 60%, transparent);
}

/* 操作栏喵 */
.action-bar {
  display: flex;
  justify-content: center;
  margin-bottom: 24px;
}

.create-world-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 28px;
  font-size: 1rem;
  font-weight: 600;
  border-radius: 12px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--glow-color) 35%, transparent),
    color-mix(in srgb, var(--glow-color) 15%, transparent)
  );
  border: 1px solid color-mix(in srgb, var(--glow-color) 60%, transparent);
  color: var(--text-color-hover);
  cursor: pointer;
  transition: all 0.25s ease;
  box-shadow: 0 4px 20px color-mix(in srgb, var(--glow-color) 20%, transparent);
}

.create-world-btn:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 32px color-mix(in srgb, var(--glow-color) 40%, transparent);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--glow-color) 50%, transparent),
    color-mix(in srgb, var(--glow-color) 25%, transparent)
  );
}

.create-world-btn.mini {
  padding: 10px 20px;
  font-size: 0.9rem;
}

/* 加载状态喵 */
.loading-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  min-height: 300px;
}

.loading-spinner {
  width: 56px;
  height: 56px;
  border: 3px solid color-mix(in srgb, var(--border-color) 60%, transparent);
  border-top: 3px solid var(--glow-color);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  box-shadow: 0 0 20px color-mix(in srgb, var(--glow-color) 30%, transparent);
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.loading-text {
  font-size: 1rem;
  color: var(--sa-muted);
}

/* 世界列表喵 */
.worlds-list {
  flex: 1;
  overflow-y: auto;
  padding-right: 8px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 20px;
  align-content: start;
}

.worlds-list.has-scroll::-webkit-scrollbar {
  width: 6px;
}

.worlds-list.has-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.worlds-list.has-scroll::-webkit-scrollbar-thumb {
  background: color-mix(in srgb, var(--border-color) 60%, transparent);
  border-radius: 3px;
}

.worlds-list.has-scroll::-webkit-scrollbar-thumb:hover {
  background: color-mix(in srgb, var(--glow-color) 60%, transparent);
}

/* 空状态喵 */
.empty-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 40px;
  text-align: center;
}

.empty-icon {
  width: 80px;
  height: 80px;
  margin-bottom: 20px;
  color: var(--glow-color);
  opacity: 0.6;
  animation: float 3s ease-in-out infinite;
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

.empty-title {
  font-size: 1.5rem;
  margin: 0 0 8px;
  color: var(--text-color-hover);
}

.empty-desc {
  font-size: 0.95rem;
  color: var(--sa-muted);
  margin: 0 0 24px;
  max-width: 400px;
}

/* 世界卡片喵 */
.world-card {
  position: relative;
  border: 1px solid color-mix(in srgb, var(--border-color) 60%, transparent);
  border-radius: 16px;
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--panel-bg) 70%, rgba(0, 0, 0, 0.1)),
    color-mix(in srgb, var(--panel-bg) 50%, rgba(0, 0, 0, 0.2))
  );
  overflow: hidden;
  transition: all 0.3s ease;
}

.world-card:hover {
  transform: translateY(-4px);
  border-color: color-mix(in srgb, var(--glow-color) 50%, transparent);
  box-shadow: 0 12px 40px color-mix(in srgb, var(--glow-color) 15%, transparent);
}

.world-card.active {
  border-color: color-mix(in srgb, var(--glow-color) 70%, transparent);
}

.card-glow {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(
    90deg,
    transparent,
    var(--glow-color),
    transparent
  );
  animation: scanline 3s linear infinite;
}

@keyframes scanline {
  0% { opacity: 0.3; }
  50% { opacity: 1; }
  100% { opacity: 0.3; }
}

.card-content {
  padding: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 16px;
}

.world-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--glow-color) 25%, transparent),
    color-mix(in srgb, var(--glow-color) 10%, transparent)
  );
  border: 1px solid color-mix(in srgb, var(--glow-color) 40%, transparent);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 16px color-mix(in srgb, var(--glow-color) 20%, transparent);
}

.world-icon.active {
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--glow-color) 45%, transparent),
    color-mix(in srgb, var(--glow-color) 20%, transparent)
  );
  border-color: var(--glow-color);
  box-shadow:
    0 4px 20px color-mix(in srgb, var(--glow-color) 40%, transparent),
    0 0 30px color-mix(in srgb, var(--glow-color) 30%, transparent);
  animation: pulseGlow 2s ease-in-out infinite;
}

@keyframes pulseGlow {
  0%, 100% {
    box-shadow:
      0 4px 20px color-mix(in srgb, var(--glow-color) 40%, transparent),
      0 0 30px color-mix(in srgb, var(--glow-color) 30%, transparent);
  }
  50% {
    box-shadow:
      0 4px 25px color-mix(in srgb, var(--glow-color) 60%, transparent),
      0 0 40px color-mix(in srgb, var(--glow-color) 40%, transparent);
  }
}

.world-title {
  flex: 1;
  min-width: 0;
}

.world-name {
  font-size: 1.15rem;
  font-weight: 700;
  margin: 0 0 8px;
  color: var(--text-color-hover);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.world-badges {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.badge {
  font-size: 0.7rem;
  padding: 3px 8px;
  border-radius: 6px;
  font-weight: 600;
  letter-spacing: 0.3px;
}

.badge.online {
  background: color-mix(in srgb, var(--glow-color) 20%, transparent);
  color: var(--glow-color);
  border: 1px solid color-mix(in srgb, var(--glow-color) 40%, transparent);
}

.badge.always {
  background: color-mix(in srgb, var(--sa-muted) 20%, transparent);
  color: var(--sa-muted);
  border: 1px solid color-mix(in srgb, var(--sa-muted) 40%, transparent);
}

.badge.running {
  background: color-mix(in srgb, #4ade80 20%, transparent);
  color: #4ade80;
  border: 1px solid color-mix(in srgb, #4ade80 40%, transparent);
  animation: pulse 2s infinite;
}

.badge.stopped {
  background: color-mix(in srgb, var(--sa-muted) 15%, transparent);
  color: var(--sa-muted);
  border: 1px solid color-mix(in srgb, var(--sa-muted) 30%, transparent);
}

/* 卡片统计喵 */
.card-stats {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 16px;
  padding: 12px;
  background: color-mix(in srgb, var(--sa-bg1) 30%, transparent);
  border-radius: 10px;
}

.stat {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.85rem;
}

.stat-icon {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0.8;
  color: var(--sa-muted);
}

.stat-label {
  color: var(--sa-muted);
}

.stat-value {
  color: var(--text-color);
  font-weight: 600;
  margin-left: auto;
}

.id-value {
  font-family: monospace;
  font-size: 0.8rem;
}

.id-stat {
  grid-column: 1 / -1;
}

/* 卡片操作喵 */
.card-actions {
  display: flex;
  gap: 10px;
}

.join-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 16px;
  font-size: 0.9rem;
  font-weight: 600;
  border-radius: 10px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--glow-color) 30%, transparent),
    color-mix(in srgb, var(--glow-color) 15%, transparent)
  );
  border: 1px solid color-mix(in srgb, var(--glow-color) 60%, transparent);
  color: var(--text-color-hover);
  cursor: pointer;
  transition: all 0.2s ease;
}

.join-btn:hover {
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--glow-color) 45%, transparent),
    color-mix(in srgb, var(--glow-color) 25%, transparent)
  );
  box-shadow: 0 0 20px color-mix(in srgb, var(--glow-color) 40%, transparent);
  transform: translateY(-2px);
}

.admin-actions {
  display: flex;
  gap: 8px;
}

.icon-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border-radius: 10px;
  background: color-mix(in srgb, var(--sa-bg1) 50%, transparent);
  border: 1px solid var(--border-color);
  color: var(--text-color);
  cursor: pointer;
  transition: all 0.2s ease;
}

.icon-btn:hover {
  border-color: var(--glow-color);
  background: color-mix(in srgb, var(--glow-color) 15%, transparent);
}

.icon-btn.danger:hover {
  border-color: var(--danger-color);
  background: color-mix(in srgb, var(--danger-color) 15%, transparent);
  color: var(--danger-color);
}

/* 存档部分喵 */
.saves-section {
  border-top: 1px solid color-mix(in srgb, var(--border-color) 40%, transparent);
  background: color-mix(in srgb, var(--sa-bg1) 20%, transparent);
  padding: 16px 20px;
}

.saves-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  font-size: 0.85rem;
}

.saves-title {
  font-weight: 600;
  color: var(--text-color);
}

.saves-count {
  color: var(--sa-muted);
}

.saves-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.save-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: color-mix(in srgb, var(--panel-bg) 40%, transparent);
  border-radius: 8px;
  border: 1px solid color-mix(in srgb, var(--border-color) 30%, transparent);
  font-size: 0.85rem;
}

.save-info {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.save-type-icon {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: color-mix(in srgb, var(--sa-bg1) 50%, transparent);
}

.save-type-icon.latest {
  color: var(--glow-color);
  background: color-mix(in srgb, var(--glow-color) 20%, transparent);
}

.save-type-icon.auto {
  color: var(--sa-muted);
  background: color-mix(in srgb, var(--sa-muted) 20%, transparent);
}

.save-type-icon.manual {
  color: var(--text-color);
  background: color-mix(in srgb, var(--glow-color) 15%, transparent);
}

.save-name {
  color: var(--text-color);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.save-size {
  color: var(--sa-muted);
  font-size: 0.75rem;
  white-space: nowrap;
}

.save-time {
  color: var(--sa-muted);
  font-size: 0.75rem;
  white-space: nowrap;
}

.load-btn {
  padding: 6px 12px;
  font-size: 0.8rem;
  border-radius: 6px;
  background: color-mix(in srgb, var(--glow-color) 20%, transparent);
  border: 1px solid color-mix(in srgb, var(--glow-color) 50%, transparent);
  color: var(--text-color-hover);
  cursor: pointer;
  transition: all 0.2s ease;
}

.load-btn:hover {
  background: color-mix(in srgb, var(--glow-color) 35%, transparent);
}

/* 错误提示喵 */
.error-toast {
  position: fixed;
  bottom: 24px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 20px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--danger-color) 20%, rgba(40, 20, 20, 0.95)),
    color-mix(in srgb, var(--danger-color) 10%, rgba(30, 15, 15, 0.95))
  );
  border: 1px solid color-mix(in srgb, var(--danger-color) 50%, transparent);
  border-radius: 12px;
  color: var(--text-color);
  font-size: 0.9rem;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.4), 0 0 20px color-mix(in srgb, var(--danger-color) 20%, transparent);
  z-index: 10000;
  animation: slideUp 0.3s ease;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateX(-50%) translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateX(-50%) translateY(0);
  }
}

.error-icon {
  width: 22px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--danger-color);
}

.error-text {
  flex: 1;
}

.close-btn {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: var(--sa-muted);
  font-size: 1.4rem;
  cursor: pointer;
  transition: color 0.2s ease;
}

.close-btn:hover {
  color: var(--text-color);
}

/* 响应式设计喵 */
@media (max-width: 1024px) {
  .worlds-list {
    grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  }
}

@media (max-width: 768px) {
  .page {
    padding: 16px;
  }

  .panel {
    padding: 20px;
    border-radius: 20px;
  }

  .panel-header {
    flex-direction: column;
    gap: 16px;
  }

  .header-left {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .header-right {
    flex-direction: row;
    align-items: center;
    width: 100%;
  }

  .mode-switcher {
    margin-left: auto;
  }

  .stats-bar {
    flex-wrap: wrap;
    gap: 12px;
  }

  .stat-divider {
    display: none;
  }

  .worlds-list {
    grid-template-columns: 1fr;
  }

  .card-stats {
    grid-template-columns: 1fr;
  }

  .id-stat {
    grid-column: 1;
  }

  .error-toast {
    left: 16px;
    right: 16px;
    transform: none;
  }
}
</style>