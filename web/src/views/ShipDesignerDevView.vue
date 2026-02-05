<script setup lang="ts">
/**
 * ShipDesignerDevView.vue
 *
 * 文件作用：
 * - 舰船设计器开发模式专用视图（/ship-designer/dev）。
 * - 左侧纹理列表、中间渲染界面、右侧数据面板。
 * - 在渲染界面创建坐标点，显示在右侧数据面板，并设置坐标的作用类型。
 *
 * 提供的接口 API：
 * - 调用 GET /api/ship/textures 获取纹理列表（含已使用标记）。
 * - 调用 PUT /api/ship/modules/{moduleId}/mount-points 保存挂载点与模块元数据。
 *
 * 使用方式：
 * - 通过 Vue Router 路由进入（例如 `router.push('/ship-designer/dev')`）。
 * - 依赖 i18n 文案：所有可见文本通过 `t('...')` 获取。
 *
 * 注意事项：
 * - 当前为开发专用界面，未来可配置化或隐藏。
 * - 坐标系：模块纹理中心为 (0,0)，画布 1:1，屏幕 1px = 1 坐标单位。
 */
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'

const { t } = useI18n()
const router = useRouter()

interface TextureItem {
  path: string
  used: boolean
}

interface MountPoint {
  x: number
  y: number
  relLayer?: -1 | 0 | 1
  type: 'engineMount' | 'fireMount' | 'turretCenter' | 'center'
}

interface ModuleData {
  moduleId: string
  category: string
  nameKey: string
  descriptionKey: string
  slotType: string
  size: number
  mass: number
  mountPoints: {
    engineMount?: MountPoint
    fireMount?: MountPoint
    turretCenter?: MountPoint
    center?: MountPoint
  }
}

const textures = ref<TextureItem[]>([])
const selectedTexture = ref<string>('')
const moduleData = ref<ModuleData>({
  moduleId: '',
  category: 'ENGINE',
  nameKey: '',
  descriptionKey: '',
  slotType: 'ENGINE_SLOT',
  size: 1,
  mass: 100,
  mountPoints: {}
})

const canvasRef = ref<HTMLCanvasElement | null>(null)
const imageRef = ref<HTMLImageElement | null>(null)
const isDragging = ref<MountPoint | null>(null)
const mountPoints = ref<MountPoint[]>([])

// 右上角浮层 UI 状态
const mouseWorldPos = ref({ x: 0, y: 0 })
const selectedMountPoint = ref<MountPoint | null>(null)

const zoomLabel = computed(() => zoom.value.toFixed(2))
const mouseWorldLabel = computed(() => `(${mouseWorldPos.value.x.toFixed(1)}, ${mouseWorldPos.value.y.toFixed(1)})`)
const selectedMountPointLabel = computed(() => {
  if (!selectedMountPoint.value) return '-'
  return `(${selectedMountPoint.value.x.toFixed(1)}, ${selectedMountPoint.value.y.toFixed(1)})`
})

// 缩放与相机（镜头）中心
// zoom 语义：数值越小画面越大（镜头越近）
const zoom = ref(1.0)
// cameraOffset 语义：镜头中心在世界坐标系的位置
const cameraOffset = ref({ x: 0, y: 0 })
const isPanning = ref(false)
const panStart = ref({ x: 0, y: 0 })

// 世界空间大小（固定 5000x5000）
const WORLD_WIDTH = 5000
const HALF_WORLD = WORLD_WIDTH / 2

// 模块类型选项（与后端分类 JSON 对齐）
const moduleCategories = [
  { value: 'ENGINE', label: '引擎模块' },
  { value: 'WEAPON', label: '武器模块' },
  { value: 'ARMOR', label: '装甲模块' },
  { value: 'ELECTRONIC', label: '电子模块' },
  { value: 'STRUCTURE', label: '结构模块' },
  { value: 'UTILITY', label: '功能模块' },
  { value: 'PRESET_SHIP', label: '预设舰船' }
]

function goBack() {
  router.push('/ship-designer')
}

async function loadTextures(force = false) {
  try {
    let url = '/api/ship/textures'
    if (force) {
      url += `?_=${Date.now()}`
    }
    const resp = await fetch(url)
    const data = await resp.json()
    if (data.ok) {
      textures.value = data.textures
    }
  } catch (e) {
    console.error('Failed to load textures:', e)
  }
}

async function selectTexture(path: string) {
  selectedTexture.value = path
  // 根据 path 推断 moduleId（去掉扩展名，用路径作为 ID）
  const base = path.replace(/\.[^.]+$/, '').replace(/[\\/]/g, '_')
  moduleData.value.moduleId = base
  moduleData.value.nameKey = `ship.module.${base}`
  moduleData.value.descriptionKey = `ship.module.${base}.desc`

  // 如果纹理已被使用，尝试加载现有模块数据
  const used = textures.value.find(t => t.path === path)?.used
  if (used) {
    try {
      const resp = await fetch(`/api/ship/modules/by-texture?path=${encodeURIComponent(path)}`)
      if (resp.ok) {
        const data = await resp.json()
        if (data.ok && data.module) {
          const m = data.module
          moduleData.value.moduleId = m.moduleId || ''
          moduleData.value.category = m.category || 'ENGINE'
          moduleData.value.nameKey = m.nameKey || ''
          moduleData.value.descriptionKey = m.descriptionKey || ''
          moduleData.value.slotType = m.slotType || ''
          moduleData.value.size = m.size || 1
          moduleData.value.mass = m.mass || 100
          moduleData.value.mountPoints = m.mountPoints || {}

          // 确保中心点存在（兼容旧数据）
          if (!moduleData.value.mountPoints.center) {
            moduleData.value.mountPoints.center = { x: 0, y: 0, relLayer: 0, type: 'center' }
          } else {
            moduleData.value.mountPoints.center.relLayer = 0
          }

          // 同步挂载点列表
          mountPoints.value = Object.entries(m.mountPoints || {}).map(([type, pt]: [string, any]) => ({
            x: pt.x,
            y: pt.y,
            type: type as MountPoint['type']
          }))
        }
      }
    } catch (e) {
      console.warn('Failed to load existing module data:', e)
    }
  } else {
    // 新纹理：创建默认的中心点
    const centerPoint: MountPoint = { x: 0, y: 0, relLayer: 0, type: 'center' }
    mountPoints.value = [centerPoint]
    moduleData.value.mountPoints = { center: centerPoint }
  }

  // 加载纹理并重绘
  await loadTextureImage()
  requestRedraw()
}

async function loadTextureImage() {
  if (!selectedTexture.value) return
  return new Promise<void>((resolve) => {
    const img = new Image()
    img.onload = () => {
      imageRef.value = img
      resolve()
    }
    img.onerror = () => {
      console.error('Failed to load texture:', selectedTexture.value)
      resolve()
    }
    img.src = '/assets/ship/' + selectedTexture.value
  })
}

function drawCanvas() {
  const canvas = canvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return

  // 画布占满整个面板
  canvas.width = canvas.clientWidth
  canvas.height = canvas.clientHeight

  ctx.clearRect(0, 0, canvas.width, canvas.height)

  // 应用缩放与镜头
  ctx.save()
  ctx.translate(canvas.width / 2, canvas.height / 2)

  // zoom 语义：数值越小画面越大（镜头越近） => 实际缩放使用 1/zoom
  const invZoom = 1 / zoom.value
  ctx.scale(invZoom, invZoom)

  // cameraOffset 语义：镜头中心在世界坐标系的位置
  // 渲染时将世界反向平移，使 cameraOffset 对准屏幕中心
  ctx.translate(-cameraOffset.value.x, -cameraOffset.value.y)

  // 绘制坐标系（网格、轴线、刻度）
  drawCoordinateSystem(ctx)

  if (!selectedTexture.value || !imageRef.value) {
    ctx.restore()
    return
  }

  // 绘制纹理（不透明）
  const img = imageRef.value
  ctx.drawImage(img, -img.width / 2, -img.height / 2)

  // 绘制挂载点
  mountPoints.value.forEach(pt => {
    const color = MOUNT_COLORS[pt.type]

    // 如果 relLayer 为 -1，则半透明
    if (pt.relLayer === -1) {
      ctx.globalAlpha = 0.5
    }

    // 绘制十字
    ctx.strokeStyle = color
    ctx.lineWidth = 2
    const size = 8
    ctx.beginPath()
    ctx.moveTo(pt.x - size, pt.y)
    ctx.lineTo(pt.x + size, pt.y)
    ctx.moveTo(pt.x, pt.y - size)
    ctx.lineTo(pt.x, pt.y + size)
    ctx.stroke()

    // 绘制标签
    ctx.fillStyle = '#fff'
    ctx.font = '12px Orbitron'
    ctx.fillText(pt.type, pt.x + 10, pt.y - 10)

    // 绘制坐标值（精度 0.1）
    ctx.fillStyle = color
    ctx.font = '10px Orbitron'
    ctx.fillText(`(${(pt.x).toFixed(1)},${(pt.y).toFixed(1)})`, pt.x + 10, pt.y + 4)

    // 恢复透明度
    if (pt.relLayer === -1) {
      ctx.globalAlpha = 1.0
    }
  })

  ctx.restore()
}

/**
 * 绘制坐标系（网格、轴线、刻度）
 * 坐标系约定：中心为 (0,0)，1px=1坐标单位，缩放1.0
 */
function drawCoordinateSystem(ctx: CanvasRenderingContext2D) {
  // 注意：ctx 此时已经在世界坐标系（原点在画布中心）下绘制

  // 绘制网格（每 20 世界单位一格）
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)'
  ctx.lineWidth = 1
  const gridSize = 20
  const gridRange = HALF_WORLD // 绘制范围
  for (let x = -gridRange; x <= gridRange; x += gridSize) {
    ctx.beginPath()
    ctx.moveTo(x, -gridRange)
    ctx.lineTo(x, gridRange)
    ctx.stroke()
  }
  for (let y = -gridRange; y <= gridRange; y += gridSize) {
    ctx.beginPath()
    ctx.moveTo(-gridRange, y)
    ctx.lineTo(gridRange, y)
    ctx.stroke()
  }

  // 绘制主轴线（X、Y）
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.4)'
  ctx.lineWidth = 2
  const axisRange = HALF_WORLD
  // X 轴
  ctx.beginPath()
  ctx.moveTo(-axisRange, 0)
  ctx.lineTo(axisRange, 0)
  ctx.stroke()
  // Y 轴
  ctx.beginPath()
  ctx.moveTo(0, -axisRange)
  ctx.lineTo(0, axisRange)
  ctx.stroke()

  // 绘制刻度与数值（每 50 世界单位）
  ctx.fillStyle = 'rgba(255, 255, 255, 0.7)'
  ctx.font = '10px Orbitron'
  ctx.textAlign = 'center'
  ctx.textBaseline = 'top'

  const tickInterval = 50
  const tickRange = HALF_WORLD

  // X 轴刻度
  for (let x = -tickRange; x <= tickRange; x += tickInterval) {
    if (x === 0) continue
    ctx.fillText(String(x), x, 4)
    // 刻度线
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.6)'
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(x, -4)
    ctx.lineTo(x, 4)
    ctx.stroke()
  }

  // Y 轴刻度
  ctx.textAlign = 'left'
  ctx.textBaseline = 'middle'
  for (let y = -tickRange; y <= tickRange; y += tickInterval) {
    if (y === 0) continue
    ctx.fillText(String(y), 4, y)
    // 刻度线
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.6)'
    ctx.lineWidth = 1
    ctx.beginPath()
    ctx.moveTo(-4, y)
    ctx.lineTo(4, y)
    ctx.stroke()
  }

  // 绘制原点标记
  ctx.fillStyle = 'rgba(255, 255, 0, 0.8)'
  ctx.beginPath()
  ctx.arc(0, 0, 4, 0, Math.PI * 2)
  ctx.fill()
  ctx.fillStyle = 'rgba(255, 255, 255, 0.9)'
  ctx.font = '12px Orbitron'
  ctx.textAlign = 'left'
  ctx.textBaseline = 'bottom'
  ctx.fillText('(0,0)', 8, -8)
}

function onCanvasMouseDown(event: MouseEvent) {
  const canvas = canvasRef.value
  if (!canvas || !selectedTexture.value) return

  const rect = canvas.getBoundingClientRect()
  const screenX = event.clientX - rect.left
  const screenY = event.clientY - rect.top

  // 屏幕坐标 -> 世界坐标
  const { x: worldX, y: worldY } = screenToWorld(screenX, screenY, canvas)

  // 检查是否点击了某个挂载点
  const clickedPoint = mountPoints.value.find(pt => {
    const dist = Math.hypot(worldX - pt.x, worldY - pt.y)
    return dist <= 8 * zoom.value
  })

  if (clickedPoint) {
    isDragging.value = clickedPoint
    selectedMountPoint.value = clickedPoint
  } else {
    // 创建新挂载点
    const newPoint: MountPoint = {
      x: worldX,
      y: worldY,
      relLayer: 0,
      type: 'engineMount' // 默认类型，可在右侧修改
    }
    mountPoints.value.push(newPoint)
    selectedMountPoint.value = newPoint
    syncMountPointsToModule()
    requestRedraw()
  }
}

function onCanvasMouseMove(event: MouseEvent) {
  const canvas = canvasRef.value
  if (!canvas) return

  const rect = canvas.getBoundingClientRect()
  const screenX = event.clientX - rect.left
  const screenY = event.clientY - rect.top

  // 屏幕坐标 -> 世界坐标
  const { x: worldX, y: worldY } = screenToWorld(screenX, screenY, canvas)

  // 更新右上角浮窗的鼠标世界坐标（精度 0.1）
  mouseWorldPos.value = {
    x: snapToCoord(worldX),
    y: snapToCoord(worldY)
  }

  if (isDragging.value) {
    isDragging.value.x = worldX
    isDragging.value.y = worldY
    syncMountPointsToModule()
    requestRedraw()
  }

  if (isPanning.value) {
    const dx = screenX - panStart.value.x
    const dy = screenY - panStart.value.y
    cameraOffset.value.x -= dx * zoom.value
    cameraOffset.value.y -= dy * zoom.value
    clampCamera()
    panStart.value = { x: screenX, y: screenY }
    drawCanvas()
  }
}

function onCanvasMouseUp() {
  isDragging.value = null
  isPanning.value = false
}

function onCanvasWheel(event: WheelEvent) {
  event.preventDefault()
  const delta = event.deltaY
  const scaleFactor = 1.1
  // 数值越小画面越大（镜头越近）：滚轮向上（delta<0）放大（减小 zoom），向下缩小（增大 zoom）
  const newZoom = Math.max(0.1, Math.min(10, zoom.value * (delta > 0 ? scaleFactor : 1 / scaleFactor)))
  zoom.value = newZoom
  drawCanvas()
}

function clampCamera() {
  cameraOffset.value.x = Math.max(-HALF_WORLD, Math.min(HALF_WORLD, cameraOffset.value.x))
  cameraOffset.value.y = Math.max(-HALF_WORLD, Math.min(HALF_WORLD, cameraOffset.value.y))
}

// WASD 键盘监听（按住连续移动，按 dt 计算）
const keyboardState = ref({
  w: false,
  a: false,
  s: false,
  d: false
})
const animationFrameId = ref<number | null>(null)
let lastTimestamp = 0
const isAnyInputFocused = ref(false)

// 坐标精度统一为 0.1
const COORD_PRECISION = 0.1

// 键盘移动速度参数
const BASE_SPEED = 10.0 // zoom = 1 时，10 像素/秒（世界单位）
const SPEED_EXPONENT = 2 // 指数级增长：speed = BASE_SPEED * zoom^SPEED_EXPONENT

// 挂载点颜色映射
const MOUNT_COLORS = {
  engineMount: '#00ff00',
  fireMount: '#ff0000',
  turretCenter: '#ffff00',
  center: '#cccccc'
} as const

// 工具函数：屏幕坐标 -> 世界坐标
function screenToWorld(screenX: number, screenY: number, canvas: HTMLCanvasElement) {
  const centerX = canvas.width / 2
  const centerY = canvas.height / 2
  const worldX = (screenX - centerX) * zoom.value + cameraOffset.value.x
  const worldY = (screenY - centerY) * zoom.value + cameraOffset.value.y
  return { x: worldX, y: worldY }
}

// 工具函数：按精度对齐坐标
function snapToCoord(value: number) {
  return Math.round(value / COORD_PRECISION) * COORD_PRECISION
}

// 工具函数：更新挂载点数据到模块
function syncMountPointsToModule() {
  const newMountPoints: ModuleData['mountPoints'] = {}
  mountPoints.value.forEach(pt => {
    if (pt && pt.type) {
      newMountPoints[pt.type] = { x: pt.x, y: pt.y, relLayer: pt.relLayer ?? 0, type: pt.type }
    }
  })
  moduleData.value.mountPoints = newMountPoints
}

// 工具函数：触发重绘
function requestRedraw() {
  requestAnimationFrame(drawCanvas)
}

function checkInputFocused() {
  const active = document.activeElement
  const isInput = active?.tagName === 'INPUT' || active?.tagName === 'SELECT' || active?.tagName === 'TEXTAREA'
  isAnyInputFocused.value = !!isInput
}

function anyKeyboardPressed() {
  return Object.values(keyboardState.value).some(v => v)
}

function onGlobalKeyDown(event: KeyboardEvent) {
  checkInputFocused()
  if (isAnyInputFocused.value) return

  const key = event.key.toLowerCase()
  if (key === 'w' || key === 'a' || key === 's' || key === 'd') {
    event.preventDefault()
    if (!keyboardState.value[key as keyof typeof keyboardState.value]) {
      keyboardState.value[key as keyof typeof keyboardState.value] = true
      // 启动连续移动循环
      if (!animationFrameId.value) {
        lastTimestamp = performance.now()
        animationFrameId.value = requestAnimationFrame(movementLoop)
      }
    }
  }
}

function onGlobalKeyUp(event: KeyboardEvent) {
  const key = event.key.toLowerCase()
  if (key === 'w' || key === 'a' || key === 's' || key === 'd') {
    keyboardState.value[key as keyof typeof keyboardState.value] = false
    if (!anyKeyboardPressed()) {
      stopMovementLoop()
    }
  }
}

function stopMovementLoop() {
  if (animationFrameId.value) {
    cancelAnimationFrame(animationFrameId.value)
    animationFrameId.value = null
  }
}

function movementLoop(timestamp: number) {
  const dtSeconds = Math.min(0.05, Math.max(0, (timestamp - lastTimestamp) / 1000))
  lastTimestamp = timestamp

  if (!anyKeyboardPressed()) {
    stopMovementLoop()
    return
  }

  moveByKeyboard(dtSeconds)
  animationFrameId.value = requestAnimationFrame(movementLoop)
}

function moveByKeyboard(dtSeconds: number) {
  // 指数速度模型：zoom 越大，速度越快（适合远景快速移动）
  const speed = BASE_SPEED * Math.pow(zoom.value, SPEED_EXPONENT) // 世界单位/秒
  const step = speed * dtSeconds

  let dx = 0
  let dy = 0

  // 镜头移动方向（镜头坐标增减）
  if (keyboardState.value.w) dy -= step // W: 镜头向上
  if (keyboardState.value.s) dy += step // S: 镜头向下
  if (keyboardState.value.a) dx -= step // A: 镜头向左
  if (keyboardState.value.d) dx += step // D: 镜头向右

  if (dx !== 0 || dy !== 0) {
    cameraOffset.value.x += dx
    cameraOffset.value.y += dy
    clampCamera()
    drawCanvas()
  }
}

function cleanupKeyboardListeners() {
  stopMovementLoop()
  document.removeEventListener('keydown', onGlobalKeyDown)
  document.removeEventListener('keyup', onGlobalKeyUp)
}



function updatePointType(index: number, type: MountPoint['type']) {
  const pt = mountPoints.value[index]
  if (!pt) return
  pt.type = type
  syncMountPointsToModule()
  drawCanvas()
}

function onTypeChange(index: number, event: Event) {
  const el = event.target as HTMLSelectElement | null
  if (!el) return
  updatePointType(index, el.value as MountPoint['type'])
}

function onLayerChange(index: number, event: Event) {
  const el = event.target as HTMLSelectElement | null
  if (!el) return

  const pt = mountPoints.value[index]
  if (!pt) return

  if (pt.type === 'center') return // 中心点层锁定 0

  const raw = Number(el.value)
  const layer = raw === -1 ? -1 : raw === 1 ? 1 : 0
  pt.relLayer = layer
  syncMountPointsToModule()
  drawCanvas()
}

function onCoordInput(index: number, axis: 'x' | 'y', event: Event) {
  const el = event.target as HTMLInputElement | null
  if (!el) return
  const rawValue = Number(el.value ?? 0)
  const value = Math.round(rawValue / COORD_PRECISION) * COORD_PRECISION
  const pt = mountPoints.value[index]
  if (!pt) return
  pt[axis] = value
  syncMountPointsToModule()
  drawCanvas()
}

function deletePoint(index: number) {
  mountPoints.value.splice(index, 1)
  if (selectedMountPoint.value === mountPoints.value[index]) {
    selectedMountPoint.value = null
  }
  syncMountPointsToModule()
  drawCanvas()
}

async function saveModule() {
  if (!moduleData.value.moduleId) {
    alert('请先选择纹理')
    return
  }

  try {
    const payload = {
      category: moduleData.value.category,
      texturePath: selectedTexture.value,
      nameKey: moduleData.value.nameKey,
      descriptionKey: moduleData.value.descriptionKey,
      slotType: moduleData.value.slotType,
      size: moduleData.value.size,
      mass: moduleData.value.mass,
      engineMount: moduleData.value.mountPoints.engineMount,
      fireMount: moduleData.value.mountPoints.fireMount,
      turretCenter: moduleData.value.mountPoints.turretCenter
    }

    const resp = await fetch(`/api/ship/modules/${moduleData.value.moduleId}/mount-points`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    const data = await resp.json()
    if (data.ok) {
      alert('保存成功')
      loadTextures() // 刷新纹理使用状态
    } else {
      alert('保存失败: ' + data.error)
    }
  } catch (e) {
    console.error('Failed to save module:', e)
    alert('保存失败')
  }
}

onMounted(() => {
  loadTextures()
  document.addEventListener('keydown', onGlobalKeyDown)
  document.addEventListener('keyup', onGlobalKeyUp)
})

onBeforeUnmount(() => {
  cleanupKeyboardListeners()
})
</script>

<template>
  <div class="ship-designer-dev-page">
    <!-- 顶部工具栏 -->
    <header class="designer-header">
      <button class="back-btn" @click="goBack">
        <span class="icon">←</span>
        <span>{{ t('shipDesigner.back') }}</span>
      </button>

      <h1 class="page-title">{{ t('shipDesigner.devMode') }}</h1>

      <div class="header-actions">
        <button class="action-btn" @click="saveModule">
          {{ t('shipDesigner.save') }}
        </button>
      </div>
    </header>

    <!-- 主体布局：左中右三栏 -->
    <main class="designer-main">
      <!-- 左侧：纹理列表 -->
      <aside class="texture-panel">
        <h2 class="panel-title">{{ t('shipDesigner.dev.textures') }}
          <button class="reload-btn" @click="loadTextures(true)">重载</button>
        </h2>
        <div class="texture-list">
          <div class="texture-item" v-for="texture in textures" :key="texture.path"
            :class="{ selected: selectedTexture === texture.path, used: texture.used }"
            @click="selectTexture(texture.path)">
            <div class="texture-icon" />
            <div class="texture-info">
              <div class="texture-path">{{ texture.path }}</div>
              <div class="texture-status">{{ texture.used ? '已使用' : '未使用' }}</div>
            </div>
          </div>
        </div>
      </aside>

      <!-- 中间：渲染界面（整个布局都是画布） -->
      <section class="canvas-panel">
        <canvas ref="canvasRef" class="canvas-full" @mousedown="onCanvasMouseDown" @mousemove="onCanvasMouseMove"
          @mouseup="onCanvasMouseUp" @mouseleave="onCanvasMouseUp" @wheel="onCanvasWheel" />
        <div v-if="!selectedTexture" class="canvas-overlay">
          <p>{{ t('shipDesigner.dev.selectTextureHint') }}</p>
        </div>

        <!-- 右上角浮窗：鼠标坐标、缩放、选中挂载点坐标 -->
        <div class="floating-overlay">
          <div class="floating-panel">
            <div class="float-item">
              <span class="float-label">鼠标</span>
              <span class="float-value">{{ mouseWorldLabel }}</span>
            </div>
            <div class="float-item">
              <span class="float-label">缩放</span>
              <span class="float-value">{{ zoomLabel }}</span>
            </div>
            <div class="float-item">
              <span class="float-label">选中</span>
              <span class="float-value">{{ selectedMountPointLabel }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 右侧：数据面板 -->
      <aside class="data-panel">
        <h2 class="panel-title">{{ t('shipDesigner.dev.data') }}</h2>
        <div class="module-data">
          <!-- 模块元数据 -->
          <div class="data-section">
            <h3>{{ t('shipDesigner.dev.moduleMeta') }}</h3>
            <div class="data-item">
              <span class="data-label">Module ID</span>
              <input class="data-input" v-model="moduleData.moduleId" readonly />
            </div>
            <div class="data-item">
              <span class="data-label">{{ t('shipDesigner.dev.category') }}</span>
              <select class="data-select" v-model="moduleData.category">
                <option v-for="cat in moduleCategories" :key="cat.value" :value="cat.value">
                  {{ cat.label }}
                </option>
              </select>
            </div>
            <div class="data-item">
              <span class="data-label">{{ t('shipDesigner.dev.nameKey') }}</span>
              <input class="data-input" v-model="moduleData.nameKey" />
            </div>
            <div class="data-item">
              <span class="data-label">{{ t('shipDesigner.dev.descriptionKey') }}</span>
              <input class="data-input" v-model="moduleData.descriptionKey" />
            </div>
            <div class="data-item">
              <span class="data-label">{{ t('shipDesigner.dev.slotType') }}</span>
              <input class="data-input" v-model="moduleData.slotType" />
            </div>
            <div class="data-item">
              <span class="data-label">{{ t('shipDesigner.dev.size') }}</span>
              <input class="data-input" type="number" v-model.number="moduleData.size" />
            </div>
            <div class="data-item">
              <span class="data-label">{{ t('shipDesigner.dev.mass') }}</span>
              <input class="data-input" type="number" v-model.number="moduleData.mass" />
            </div>
          </div>

          <!-- 挂载点列表 -->
          <div class="data-section">
            <h3>{{ t('shipDesigner.dev.mountPoints') }}</h3>
            <div class="mount-points-list">
              <div v-for="(pt, index) in mountPoints" :key="index" class="mount-point-item">
                <div class="mount-point-header">
                  <select class="mount-type-select" :value="pt.type" @change="e => onTypeChange(index, e)"
                    :disabled="pt.type === 'center'">
                    <option v-if="pt.type === 'center'" value="center">中心点 (只读)</option>
                    <option value="engineMount">引擎挂载点</option>
                    <option value="fireMount">开火挂载点</option>
                    <option value="turretCenter">炮塔中心点</option>
                  </select>
                  <select class="mount-layer-select" :value="pt.relLayer ?? 0" @change="e => onLayerChange(index, e)"
                    :disabled="pt.type === 'center'">
                    <option value="1">上层 (+1)</option>
                    <option value="0">中层 (0)</option>
                    <option value="-1">下层 (-1)</option>
                  </select>
                  <button class="delete-btn" @click="deletePoint(index)" :disabled="pt.type === 'center'">删除</button>
                </div>
                <div class="mount-point-coords">
                  <span class="coord-label">X:</span>
                  <input class="coord-input" type="number" step="0.1" :value="pt.x.toFixed(1)"
                    @input="e => onCoordInput(index, 'x', e)" />
                  <span class="coord-label">Y:</span>
                  <input class="coord-input" type="number" step="0.1" :value="pt.y.toFixed(1)"
                    @input="e => onCoordInput(index, 'y', e)" />
                </div>
              </div>
            </div>
            <div v-if="mountPoints.length === 0" class="no-points">
              点击画布创建挂载点
            </div>
          </div>
        </div>
      </aside>
    </main>
  </div>
</template>

<style scoped>
.ship-designer-dev-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
  background-color: var(--background-color);
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  overflow: hidden;
}

/* 顶部工具栏 */
.designer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 2rem;
  background: color-mix(in srgb, var(--glow-color) 8%, rgba(255, 255, 255, 0.02));
  border-bottom: 1px solid color-mix(in srgb, var(--glow-color) 20%, transparent);
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  background: transparent;
  border: none;
  color: var(--text-color);
  font-size: 1rem;
  cursor: pointer;
  transition: color 0.3s ease;
}

.back-btn:hover {
  color: var(--text-color-hover);
}

.page-title {
  font-size: 1.8rem;
  font-weight: 700;
  color: var(--text-color-hover);
  text-shadow: 0 0 4px var(--glow-color);
  margin: 0;
}

.header-actions {
  display: flex;
  gap: 1rem;
}

.action-btn {
  padding: 0.5rem 1rem;
  background: color-mix(in srgb, var(--glow-color) 12%, rgba(255, 255, 255, 0.05));
  border: 1px solid color-mix(in srgb, var(--glow-color) 30%, transparent);
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  font-size: 0.9rem;
  cursor: pointer;
  transition: all 0.3s ease;
  border-radius: 4px;
}

.action-btn:hover {
  background: color-mix(in srgb, var(--glow-color) 20%, rgba(255, 255, 255, 0.08));
  border-color: var(--glow-color);
  color: var(--text-color-hover);
}

/* 主体布局 */
.designer-main {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 左中右面板通用样式 */
.texture-panel,
.canvas-panel,
.data-panel {
  display: flex;
  flex-direction: column;
  padding: 1.5rem;
  border-right: 1px solid color-mix(in srgb, var(--glow-color) 12%, transparent);
}

.data-panel {
  border-right: none;
  border-left: 1px solid color-mix(in srgb, var(--glow-color) 12%, transparent);
}

.panel-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 1.2rem;
  font-weight: 600;
  color: var(--text-color-hover);
  margin: 0 0 1rem 0;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.reload-btn {
  padding: 0.2rem 0.6rem;
  font-size: 0.8rem;
  background: transparent;
  border: 1px solid color-mix(in srgb, var(--glow-color) 40%, transparent);
  color: var(--text-color);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.reload-btn:hover {
  background: color-mix(in srgb, var(--glow-color) 20%, transparent);
  border-color: var(--glow-color);
  color: var(--text-color-hover);
}

/* 左侧：纹理列表 */
.texture-panel {
  width: 280px;
  flex-shrink: 0;
}

.texture-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.texture-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.75rem;
  background: color-mix(in srgb, var(--glow-color) 6%, rgba(255, 255, 255, 0.02));
  border: 1px solid color-mix(in srgb, var(--glow-color) 15%, transparent);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.texture-item:hover {
  background: color-mix(in srgb, var(--glow-color) 12%, rgba(255, 255, 255, 0.05));
  border-color: var(--glow-color);
}

.texture-item.selected {
  background: color-mix(in srgb, var(--glow-color) 20%, rgba(255, 255, 255, 0.08));
  border-color: var(--glow-color);
}

.texture-item.used {
  opacity: 0.6;
}

.texture-icon {
  width: 32px;
  height: 32px;
  background: color-mix(in srgb, var(--glow-color) 20%, rgba(255, 255, 255, 0.1));
  border-radius: 4px;
  flex-shrink: 0;
}

.texture-info {
  flex: 1;
  min-width: 0;
}

.texture-path {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--text-color);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.texture-status {
  font-size: 0.7rem;
  color: var(--glow-color);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* 中间：画布（整块） */
.canvas-panel {
  flex: 1;
  min-width: 0;
  position: relative;
  padding: 0;
}

.canvas-full {
  width: 100%;
  height: 100%;
  display: block;
  cursor: crosshair;
  background: #000;
}

.canvas-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  pointer-events: none;
  color: var(--text-color);
  opacity: 0.6;
  font-size: 1.2rem;
}

.floating-overlay {
  position: absolute;
  top: 12px;
  right: 12px;
  pointer-events: none;
}

.floating-panel {
  background: color-mix(in srgb, var(--glow-color) 10%, rgba(0, 0, 0, 0.45));
  border: 1px solid color-mix(in srgb, var(--glow-color) 25%, transparent);
  border-radius: 6px;
  padding: 8px 10px;
  min-width: 160px;
  backdrop-filter: blur(6px);
}

.float-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  font-size: 12px;
  line-height: 18px;
  color: var(--text-color);
}

.float-label {
  opacity: 0.75;
}

.float-value {
  color: var(--text-color-hover);
  font-variant-numeric: tabular-nums;
}

/* 右侧：数据面板 */
.data-panel {
  width: 320px;
  flex-shrink: 0;
  overflow: hidden;
}

.module-data {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.module-data {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.data-section h3 {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-color-hover);
  margin: 0 0 1rem 0;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.data-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.75rem;
}

.data-label {
  font-size: 0.8rem;
  color: var(--text-color);
  min-width: 100px;
  flex-shrink: 0;
}

.data-input,
.data-select {
  flex: 1;
  padding: 0.4rem 0.6rem;
  background: color-mix(in srgb, var(--glow-color) 8%, rgba(255, 255, 255, 0.02));
  border: 1px solid color-mix(in srgb, var(--glow-color) 20%, transparent);
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  font-size: 0.8rem;
  border-radius: 3px;
  transition: all 0.3s ease;
}

.data-input:focus,
.data-select:focus {
  outline: none;
  border-color: var(--glow-color);
  background: color-mix(in srgb, var(--glow-color) 12%, rgba(255, 255, 255, 0.04));
}

.mount-points-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.mount-point-item {
  background: color-mix(in srgb, var(--glow-color) 6%, rgba(255, 255, 255, 0.02));
  border: 1px solid color-mix(in srgb, var(--glow-color) 15%, transparent);
  border-radius: 4px;
  padding: 0.75rem;
}

.mount-point-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.5rem;
}

.mount-type-select {
  flex: 1;
  padding: 0.3rem 0.5rem;
  background: color-mix(in srgb, var(--glow-color) 8%, rgba(255, 255, 255, 0.02));
  border: 1px solid color-mix(in srgb, var(--glow-color) 20%, transparent);
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  font-size: 0.8rem;
  border-radius: 3px;
}

.delete-btn {
  padding: 0.3rem 0.6rem;
  background: color-mix(in srgb, #ff4444 20%, rgba(255, 255, 255, 0.05));
  border: 1px solid color-mix(in srgb, #ff4444 30%, transparent);
  color: #ff6666;
  font-family: 'Orbitron', sans-serif;
  font-size: 0.7rem;
  cursor: pointer;
  border-radius: 3px;
  transition: all 0.3s ease;
}

.delete-btn:hover {
  background: color-mix(in srgb, #ff4444 30%, rgba(255, 255, 255, 0.08));
  border-color: #ff6666;
  color: #ff8888;
}

.mount-point-coords {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.coord-label {
  font-size: 0.8rem;
  color: var(--text-color);
  min-width: 20px;
}

.coord-input {
  width: 60px;
  padding: 0.3rem 0.5rem;
  background: color-mix(in srgb, var(--glow-color) 8%, rgba(255, 255, 255, 0.02));
  border: 1px solid color-mix(in srgb, var(--glow-color) 20%, transparent);
  color: var(--text-color);
  font-family: 'Orbitron', sans-serif;
  font-size: 0.8rem;
  border-radius: 3px;
  text-align: center;
}

.coord-input:focus {
  outline: none;
  border-color: var(--glow-color);
  background: color-mix(in srgb, var(--glow-color) 12%, rgba(255, 255, 255, 0.04));
}

.no-points {
  text-align: center;
  color: var(--text-color);
  opacity: 0.6;
  font-size: 0.9rem;
  padding: 2rem;
}
</style>
