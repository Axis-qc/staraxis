/**
 * @file starInfoLabelRenderer.ts
 * @description 恒星信息标签渲染器 - 在选中的恒星旁边显示信息面板喵
 * @important_notes
 * - 使用 Canvas 纹理渲染信息标签，与 Three.js 场景完美融合喵
 * - 只在恒星被选中时显示喵
 * - 跟随恒星移动，支持缩放喵
 * - 颜色从 CSS 主题变量（--sa-*）动态读取，跟随主题切换喵
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'
import type { EntitySnapshot, StarDetails } from '../../../../net/snapshotWs'
import type { Interactable } from '../../../subsystems/mouseInteractionManager'
import { i18n } from '../../../../i18n'

// ── 主题颜色读取 ────────────────────────────────────────────喵

/** 解析 CSS 颜色为 [r, g, b]，支持 rgba() 和 #hex 喵 */
function parseColorRgb(color: string): [number, number, number] {
    const m = color.match(/rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/)
    if (m) return [+m[1]!, +m[2]!, +m[3]!]
    const h = color.match(/^#([0-9a-f]{6})$/i)
    if (h) {
        const s = h[1]!
        return [parseInt(s.slice(0, 2), 16), parseInt(s.slice(2, 4), 16), parseInt(s.slice(4, 6), 16)]
    }
    return [0, 191, 255] // 回退青色喵
}

/** 从 CSS 变量读取当前主题的 RGB 值喵 */
interface ThemeRgb {
    accent: [number, number, number]   // --sa-accent 主题强调色喵
    bg0: string                        // --sa-bg0 背景色（原始字符串）喵
    stroke: [number, number, number]   // --sa-stroke 边框色喵
    text: [number, number, number]     // --sa-text 文字色喵
    muted: [number, number, number]    // --sa-muted 柔和文字色喵
}

function readThemeRgb(): ThemeRgb {
    const cs = getComputedStyle(document.documentElement)
    const get = (v: string, fb: string) => cs.getPropertyValue(v).trim() || fb
    return {
        accent: parseColorRgb(get('--sa-accent', 'rgba(0, 191, 255, 0.92)')),
        bg0: get('--sa-bg0', '#050a12'),
        stroke: parseColorRgb(get('--sa-stroke', 'rgba(0, 191, 255, 0.18)')),
        text: parseColorRgb(get('--sa-text', 'rgba(236, 254, 255, 0.92)')),
        muted: parseColorRgb(get('--sa-muted', 'rgba(203, 251, 255, 0.70)')),
    }
}

/** 将 [r,g,b] + alpha 转为 rgba 字符串喵 */
function rgba(rgb: [number, number, number], a: number): string {
    return `rgba(${rgb[0]}, ${rgb[1]}, ${rgb[2]}, ${a})`
}

/** 从 ThemeRgb 派生标签所需的全部颜色喵 */
function deriveLabelColors(t: ThemeRgb) {
    const [ar, ag, ab] = t.accent
    const [tr, tg, tb] = t.text
    return {
        // 面板背景 - 深色半透明喵
        backgroundColor: `rgba(${Math.round(ar * 0.06)}, ${Math.round(ag * 0.06)}, ${Math.round(ab * 0.06)}, 0.70)`,
        // 边框色 - accent 30% 喵
        borderColor: rgba(t.accent, 0.30),
        // 标题区域背景 - accent 10% 喵
        headerBg: rgba(t.accent, 0.10),
        // 标题区域底边 - accent 15% 喵
        headerBorder: rgba(t.accent, 0.15),
        // 分隔线 - accent 12% 喵
        dividerColor: rgba(t.accent, 0.12),
        // 卡片背景 - accent 5% 喵
        cardBg: rgba(t.accent, 0.05),
        // 卡片边框 - accent 8% 喵
        cardBorder: rgba(t.accent, 0.08),
        // 标题色 - text 全亮喵
        titleColor: `rgb(${tr}, ${tg}, ${tb})`,
        // 信息值色 - text 全亮喵
        infoColor: `rgb(${tr}, ${tg}, ${tb})`,
        // 标签色 - text 55% 喵
        labelColor: rgba(t.text, 0.55),
        // 副标题色 - muted 65% 喵
        subtitleColor: rgba(t.muted, 0.65),
    }
}

// ── 不随主题变化的静态配置 ──────────────────────────────────喵

const LABEL_STATIC = {
    width: 240,
    height: 160,
    offsetX: 30,
    offsetY: -30,
    borderRadius: 14,
    borderWidth: 1,
    titleFont: '600 14px "Microsoft YaHei", sans-serif',
    infoFont: '500 12px "Microsoft YaHei", sans-serif',
    labelFont: '10px "Microsoft YaHei", sans-serif',
    poolSize: 5,
} as const

/** 翻译辅助函数：获取 i18n 文本喵 */
function t(key: string): string {
    return i18n.global.t(key)
}

/**
 * 恒星信息标签渲染器喵
 * 实现 Interactable 接口，由 MouseInteractionManager 分发事件喵
 */
export class StarInfoLabelRenderer implements Interactable {
    private readonly parentGroup: THREE.Group
    private readonly labelPool: THREE.Sprite[] = []
    private readonly activeLabelsByEntityId = new Map<number, THREE.Sprite>()
    private readonly labelTextureCache = new Map<string, THREE.CanvasTexture>()

    // 主题监听喵
    private themeObserver: MutationObserver | null = null
    private themeVersion = 0
    private cachedThemeRgb: ThemeRgb | null = null

    // 语言变化追踪（语言切换时清除纹理缓存）喵
    private lastLocale = ''

    // 拖动状态喵
    private readonly labelUserOffsetPx = new Map<number, { dx: number; dy: number }>()
    private dragEntityId: number | null = null

    // 标签屏幕矩形缓存（update 中计算，hitTest 中使用）喵
    private readonly labelScreenRects = new Map<number, { l: number; t: number; r: number; b: number }>()

    constructor(parentGroup: THREE.Group) {
        this.parentGroup = parentGroup
    }

    init(_ctx: WorldRenderContext): void {
        // 监听主题变化喵
        this.themeObserver = new MutationObserver(() => {
            this.themeVersion++
            this.cachedThemeRgb = null
            for (const tex of this.labelTextureCache.values()) tex.dispose()
            this.labelTextureCache.clear()
        })
        this.themeObserver.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['style'],
        })

        // 预热对象池喵
        for (let i = 0; i < LABEL_STATIC.poolSize; i++) {
            const label = this.createLabelSprite()
            label.visible = false
            this.parentGroup.add(label)
            this.labelPool.push(label)
        }
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
        // 检测语言变化，清除纹理缓存喵
        const currentLocale = i18n.global.locale.value
        if (this.lastLocale && this.lastLocale !== currentLocale) {
            for (const tex of this.labelTextureCache.values()) tex.dispose()
            this.labelTextureCache.clear()
        }
        this.lastLocale = currentLocale

        this.labelScreenRects.clear()
        const canvasEl = ctx.renderer.domElement as HTMLCanvasElement
        const canvasRect = canvasEl.getBoundingClientRect()
        const canvasW = canvasRect.width
        const canvasH = canvasRect.height
        const zoom = ctx.zoom.value

        // 只处理选中的恒星喵
        const visibleIds = new Set<number>()

        for (const entityId of frame.selectedIds) {
            const entity = frame.entitiesById.get(entityId)
            if (!entity || entity.entityType !== 'STAR') continue

            const details = entity.details as StarDetails | null
            if (!details) continue

            const entityPos = ctx.getEntityWorldPosGU(entityId) ?? entity.posWorldGU
            if (!entityPos) continue

            visibleIds.add(entityId)

            let label = this.activeLabelsByEntityId.get(entityId)
            if (!label) {
                label = this.acquireLabel()
                this.activeLabelsByEntityId.set(entityId, label)
            }

            // 获取或创建纹理喵
            const texture = this.getOrCreateTexture(entity, details)
            const material = label.material as THREE.SpriteMaterial

            if (material.map !== texture) {
                material.map = texture
                material.needsUpdate = true
            }

            // 计算标签尺寸（世界单位）喵
            const labelWorldWidth = LABEL_STATIC.width * zoom
            const labelWorldHeight = LABEL_STATIC.height * zoom

            label.scale.set(labelWorldWidth, labelWorldHeight, 1)

            // 默认偏移：基于恒星半径，防止遮挡实体喵
            const radiusOffset = Math.max(details.radiusGU, 10 * zoom)
            const defaultOffsetX = radiusOffset + LABEL_STATIC.offsetX * zoom
            const defaultOffsetY = -radiusOffset + LABEL_STATIC.offsetY * zoom

            // 用户拖动偏移（屏幕像素 → 世界单位）喵
            const userOffset = this.labelUserOffsetPx.get(entityId) ?? { dx: 0, dy: 0 }
            const userOffsetWorldX = userOffset.dx * zoom
            const userOffsetWorldY = -userOffset.dy * zoom // 屏幕 Y 轴翻转喵

            // 标签渲染坐标喵
            const rp = ctx.toRenderPos(entityPos)
            const renderX = rp.x + defaultOffsetX + userOffsetWorldX + labelWorldWidth / 2
            const renderY = rp.y + defaultOffsetY + userOffsetWorldY - labelWorldHeight / 2

            label.position.set(renderX, renderY, 0.5)
            label.visible = true

            // 计算标签屏幕矩形（canvas 像素坐标）喵
            const screenCX = renderX / zoom + canvasW / 2
            const screenCY = -renderY / zoom + canvasH / 2
            const halfW = LABEL_STATIC.width / 2
            const halfH = LABEL_STATIC.height / 2
            this.labelScreenRects.set(entityId, {
                l: screenCX - halfW,
                t: screenCY - halfH,
                r: screenCX + halfW,
                b: screenCY + halfH,
            })
        }

        // 释放不可见的标签喵
        this.releaseInactiveLabels(visibleIds)
    }

    /**
     * 释放所有资源喵
     */
    dispose(): void {
        this.themeObserver?.disconnect()
        this.themeObserver = null
        this.cachedThemeRgb = null
        this.dragEntityId = null
        this.labelUserOffsetPx.clear()
        this.labelScreenRects.clear()

        const allLabels = new Set([...this.labelPool, ...this.activeLabelsByEntityId.values()])
        for (const label of allLabels) {
            const material = label.material as THREE.SpriteMaterial
            material.map = null
            material.dispose()
            label.parent?.remove(label)
        }

        this.labelPool.length = 0
        this.activeLabelsByEntityId.clear()

        for (const texture of this.labelTextureCache.values()) {
            texture.dispose()
        }
        this.labelTextureCache.clear()
    }

    // ── Interactable 接口（由 MouseInteractionManager 调用）─────喵

    hitTest(canvasX: number, canvasY: number): boolean {
        for (const [entityId, rect] of this.labelScreenRects.entries()) {
            if (canvasX >= rect.l && canvasX <= rect.r && canvasY >= rect.t && canvasY <= rect.b) {
                return true
            }
        }
        return false
    }

    onPointerDown(canvasX: number, canvasY: number): void {
        for (const [entityId, rect] of this.labelScreenRects.entries()) {
            if (canvasX >= rect.l && canvasX <= rect.r && canvasY >= rect.t && canvasY <= rect.b) {
                this.dragEntityId = entityId
                if (!this.labelUserOffsetPx.has(entityId)) {
                    this.labelUserOffsetPx.set(entityId, { dx: 0, dy: 0 })
                }
                return
            }
        }
    }

    onPointerMove(_canvasX: number, _canvasY: number, dx: number, dy: number): void {
        if (this.dragEntityId === null) return
        const offset = this.labelUserOffsetPx.get(this.dragEntityId)
        if (offset) {
            offset.dx += dx
            offset.dy += dy
        }
    }

    onPointerUp(): void {
        this.dragEntityId = null
    }

    /**
     * 创建标签精灵喵
     */
    private createLabelSprite(): THREE.Sprite {
        const material = new THREE.SpriteMaterial({
            color: 0xffffff,
            transparent: true,
            opacity: 0.95,
            depthWrite: false,
            depthTest: false,
            sizeAttenuation: true,
        })
        const sprite = new THREE.Sprite(material)
        sprite.frustumCulled = false
        return sprite
    }

    /**
     * 从池中获取标签喵
     */
    private acquireLabel(): THREE.Sprite {
        const label = this.labelPool.pop()
        if (label) {
            label.visible = true
            return label
        }

        const newLabel = this.createLabelSprite()
        this.parentGroup.add(newLabel)
        return newLabel
    }

    /**
     * 释放标签到池中喵
     */
    private releaseLabel(label: THREE.Sprite): void {
        label.visible = false
        this.labelPool.push(label)
    }

    /**
     * 释放不可见的标签，并清理其拖动偏移喵
     */
    private releaseInactiveLabels(visibleIds: Set<number>): void {
        for (const [entityId, label] of this.activeLabelsByEntityId.entries()) {
            if (!visibleIds.has(entityId)) {
                this.activeLabelsByEntityId.delete(entityId)
                this.labelUserOffsetPx.delete(entityId)
                this.releaseLabel(label)
            }
        }
    }

    /**
     * 获取当前主题颜色（带缓存）喵
     */
    private getThemeRgb(): ThemeRgb {
        if (!this.cachedThemeRgb) {
            this.cachedThemeRgb = readThemeRgb()
        }
        return this.cachedThemeRgb
    }

    /**
     * 获取或创建纹理喵
     */
    private getOrCreateTexture(entity: EntitySnapshot, details: StarDetails): THREE.CanvasTexture {
        // 缓存键包含主题版本和语言，主题/语言变化时自动失效喵
        const cacheKey = `${entity.entityId}_${details.starTypeId}_${details.temperatureK}_t${this.themeVersion}_l${this.lastLocale}`

        const cached = this.labelTextureCache.get(cacheKey)
        if (cached) {
            return cached
        }

        const texture = this.createLabelTexture(entity, details)
        this.labelTextureCache.set(cacheKey, texture)
        return texture
    }

    /**
     * 创建标签纹理喵
     */
    private createLabelTexture(entity: EntitySnapshot, details: StarDetails): THREE.CanvasTexture {
        const devicePixelRatio = Math.min(window.devicePixelRatio || 1, 2)
        const canvas = document.createElement('canvas')
        canvas.width = Math.round(LABEL_STATIC.width * devicePixelRatio)
        canvas.height = Math.round(LABEL_STATIC.height * devicePixelRatio)

        const ctx = canvas.getContext('2d')
        if (!ctx) {
            throw new Error('Failed to create 2D context for star info label texture')
        }

        ctx.scale(devicePixelRatio, devicePixelRatio)
        ctx.clearRect(0, 0, LABEL_STATIC.width, LABEL_STATIC.height)

        // 绘制背景喵
        this.drawBackground(ctx)

        // 绘制标题喵
        this.drawTitle(ctx, entity, details)

        // 绘制信息喵
        this.drawInfo(ctx, details)

        const texture = new THREE.CanvasTexture(canvas)
        texture.needsUpdate = true
        return texture
    }

    /**
     * 绘制背景喵
     * 使用与 ShipPanel.vue 相同的毛玻璃风格喵
     */
    private drawBackground(ctx: CanvasRenderingContext2D): void {
        const { width, height, borderRadius, borderWidth } = LABEL_STATIC
        const colors = deriveLabelColors(this.getThemeRgb())

        // 绘制圆角矩形背景喵
        ctx.fillStyle = colors.backgroundColor
        ctx.strokeStyle = colors.borderColor
        ctx.lineWidth = borderWidth

        ctx.beginPath()
        ctx.roundRect(0, 0, width, height, borderRadius)
        ctx.fill()
        ctx.stroke()

        // 绘制标题区域背景（accent 10%）喵
        ctx.fillStyle = colors.headerBg
        ctx.beginPath()
        ctx.roundRect(borderWidth, borderWidth, width - borderWidth * 2, 44, [borderRadius - 1, borderRadius - 1, 0, 0])
        ctx.fill()

        // 绘制标题区域底边（accent 15%）喵
        ctx.strokeStyle = colors.headerBorder
        ctx.lineWidth = 1
        ctx.beginPath()
        ctx.moveTo(0, 45)
        ctx.lineTo(width, 45)
        ctx.stroke()

        // 绘制分隔线（accent 12%）喵
        ctx.strokeStyle = colors.dividerColor
        ctx.lineWidth = 1
        ctx.beginPath()
        ctx.moveTo(12, 46)
        ctx.lineTo(width - 12, 46)
        ctx.stroke()
    }

    /**
     * 绘制标题喵
     */
    private drawTitle(ctx: CanvasRenderingContext2D, entity: EntitySnapshot, details: StarDetails): void {
        const colors = deriveLabelColors(this.getThemeRgb())

        ctx.font = LABEL_STATIC.titleFont
        ctx.fillStyle = colors.titleColor
        ctx.textBaseline = 'top'

        // 恒星类型名称（国际化）喵
        const starTypeName = t(`star.type.${details.starTypeId}`) || details.starTypeId
        ctx.fillText(starTypeName, 14, 14)

        // 恒星ID - 使用副标题样式（.ship-type）喵
        ctx.font = LABEL_STATIC.labelFont
        ctx.fillStyle = colors.subtitleColor
        ctx.fillText(`#${entity.entityId}`, 14, 32)
    }

    /**
     * 绘制信息喵
     * 使用卡片式布局，与现有 UI 风格对齐喵
     */
    private drawInfo(ctx: CanvasRenderingContext2D, details: StarDetails): void {
        const { width, infoFont, labelFont } = LABEL_STATIC
        const colors = deriveLabelColors(this.getThemeRgb())
        const startY = 54
        const cardPadding = 10
        const cardGap = 6

        // 定义信息卡片（国际化）喵
        const cards = [
            { label: t('star.label.temperature'), value: `${details.temperatureK.toLocaleString()} K` },
            { label: t('star.label.mass'), value: `${details.massSolar.toFixed(2)} M☉` },
            { label: t('star.label.radius'), value: `${details.radiusGU.toLocaleString()} GU` },
        ]

        // 计算卡片尺寸喵
        const cardWidth = (width - 12 * 2 - cardGap * (cards.length - 1)) / cards.length
        const cardHeight = 40

        cards.forEach((card, index) => {
            const x = 12 + index * (cardWidth + cardGap)
            const y = startY

            // 绘制卡片背景（accent 5%/8%）喵
            ctx.fillStyle = colors.cardBg
            ctx.strokeStyle = colors.cardBorder
            ctx.lineWidth = 1
            ctx.beginPath()
            ctx.roundRect(x, y, cardWidth, cardHeight, 6)
            ctx.fill()
            ctx.stroke()

            // 绘制标签（text 55%）喵
            ctx.font = labelFont
            ctx.fillStyle = colors.labelColor
            ctx.textBaseline = 'top'
            ctx.fillText(card.label, x + cardPadding, y + 8)

            // 绘制值（text 全亮）喵
            ctx.font = infoFont
            ctx.fillStyle = colors.infoColor
            ctx.fillText(card.value, x + cardPadding, y + 24)
        })

        // 描述（如果有）喵
        if (details.description) {
            const descY = startY + cardHeight + 10
            ctx.font = labelFont
            ctx.fillStyle = colors.labelColor
            ctx.textBaseline = 'top'
            const descText = details.description.length > 30
                ? details.description.substring(0, 30) + '...'
                : details.description
            ctx.fillText(`"${descText}"`, 12, descY)
        }
    }
}
