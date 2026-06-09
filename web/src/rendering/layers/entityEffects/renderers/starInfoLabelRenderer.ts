/**
 * @file starInfoLabelRenderer.ts
 * @description 恒星信息标签渲染器 - 在选中的恒星旁边显示信息面板喵
 * @important_notes
 * - 使用 Canvas 纹理渲染信息标签，与 Three.js 场景完美融合喵
 * - 只在恒星被选中时显示喵
 * - 跟随恒星移动，支持缩放喵
 */

import * as THREE from 'three'
import type { WorldRenderContext, WorldFrameState } from '../../../worldRenderManager'
import type { EntitySnapshot, StarDetails } from '../../../../net/snapshotWs'

// 标签配置喵
// 样式与现有 UI 风格对齐（参考 ShipPanel.vue）喵
const LABEL_CONFIG = {
    // 标签尺寸（像素）喵
    width: 240,
    height: 160,
    // 标签偏移（像素）喵
    offsetX: 30,
    offsetY: -30,
    // 背景色 - 使用与 ShipPanel 相同的半透明风格喵
    backgroundColor: 'rgba(13, 17, 23, 0.75)',
    // 边框色 - 使用 glow-color 风格（青色）喵
    borderColor: 'rgba(86, 215, 255, 0.35)',
    borderRadius: 12,
    borderWidth: 1,
    // 左侧装饰条颜色喵
    accentColor: 'rgba(86, 215, 255, 0.9)',
    // 文字样式喵
    titleFont: 'bold 13px "Microsoft YaHei", sans-serif',
    infoFont: '11px "Microsoft YaHei", sans-serif',
    labelFont: '10px "Microsoft YaHei", sans-serif',
    // 文字颜色 - 与现有 UI 变量对齐喵
    titleColor: 'rgba(225, 248, 255, 0.95)', // --text-color-hover 喵
    infoColor: 'rgba(200, 220, 235, 0.85)',   // --text-color 喵
    labelColor: 'rgba(200, 220, 235, 0.55)',  // --text-color 55% 喵
    // 分隔线颜色喵
    dividerColor: 'rgba(86, 215, 255, 0.12)',
    // 对象池大小喵
    poolSize: 5,
} as const

// 恒星类型名称映射喵
const STAR_TYPE_NAMES: Record<string, string> = {
    'G_MAIN_SEQUENCE': 'G 型主序星',
    'K_MAIN_SEQUENCE': 'K 型主序星',
    'M_MAIN_SEQUENCE': 'M 型主序星',
    'F_MAIN_SEQUENCE': 'F 型主序星',
    'A_MAIN_SEQUENCE': 'A 型主序星',
    'B_MAIN_SEQUENCE': 'B 型主序星',
    'O_MAIN_SEQUENCE': 'O 型主序星',
    'RED_GIANT': '红巨星',
    'WHITE_DWARF': '白矮星',
    'NEUTRON_STAR': '中子星',
}

/**
 * 恒星信息标签渲染器喵
 */
export class StarInfoLabelRenderer {
    private readonly parentGroup: THREE.Group
    private readonly labelPool: THREE.Sprite[] = []
    private readonly activeLabelsByEntityId = new Map<number, THREE.Sprite>()
    private readonly labelTextureCache = new Map<string, THREE.CanvasTexture>()

    constructor(parentGroup: THREE.Group) {
        this.parentGroup = parentGroup
    }

    init(_ctx: WorldRenderContext): void {
        // 预热对象池喵
        for (let i = 0; i < LABEL_CONFIG.poolSize; i++) {
            const label = this.createLabelSprite()
            label.visible = false
            this.parentGroup.add(label)
            this.labelPool.push(label)
        }
    }

    update(ctx: WorldRenderContext, frame: WorldFrameState): void {
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
            const labelWorldWidth = LABEL_CONFIG.width * ctx.zoom.value
            const labelWorldHeight = LABEL_CONFIG.height * ctx.zoom.value

            label.scale.set(labelWorldWidth, labelWorldHeight, 1)

            // 计算标签位置（在恒星右上方）喵
            const rp = ctx.toRenderPos(entityPos)
            const offsetWorldX = LABEL_CONFIG.offsetX * ctx.zoom.value
            const offsetWorldY = LABEL_CONFIG.offsetY * ctx.zoom.value

            label.position.set(
                rp.x + offsetWorldX + labelWorldWidth / 2,
                rp.y + offsetWorldY - labelWorldHeight / 2,
                0.5 // 在选中环上方喵
            )
            label.visible = true
        }

        // 释放不可见的标签喵
        this.releaseInactiveLabels(visibleIds)
    }

    dispose(): void {
        // 释放所有精灵和纹理喵
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
     * 释放不可见的标签喵
     */
    private releaseInactiveLabels(visibleIds: Set<number>): void {
        for (const [entityId, label] of this.activeLabelsByEntityId.entries()) {
            if (!visibleIds.has(entityId)) {
                this.activeLabelsByEntityId.delete(entityId)
                this.releaseLabel(label)
            }
        }
    }

    /**
     * 获取或创建纹理喵
     */
    private getOrCreateTexture(entity: EntitySnapshot, details: StarDetails): THREE.CanvasTexture {
        // 使用实体ID和恒星类型作为缓存键喵
        const cacheKey = `${entity.entityId}_${details.starTypeId}_${details.temperatureK}`

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
        canvas.width = Math.round(LABEL_CONFIG.width * devicePixelRatio)
        canvas.height = Math.round(LABEL_CONFIG.height * devicePixelRatio)

        const ctx = canvas.getContext('2d')
        if (!ctx) {
            throw new Error('Failed to create 2D context for star info label texture')
        }

        ctx.scale(devicePixelRatio, devicePixelRatio)
        ctx.clearRect(0, 0, LABEL_CONFIG.width, LABEL_CONFIG.height)

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
     * 使用与 ShipPanel 相同的毛玻璃风格喵
     */
    private drawBackground(ctx: CanvasRenderingContext2D): void {
        const { width, height, backgroundColor, borderColor, borderRadius, borderWidth } = LABEL_CONFIG

        // 绘制圆角矩形背景喵
        ctx.fillStyle = backgroundColor
        ctx.strokeStyle = borderColor
        ctx.lineWidth = borderWidth

        ctx.beginPath()
        ctx.roundRect(0, 0, width, height, borderRadius)
        ctx.fill()
        ctx.stroke()

        // 绘制左侧装饰条（使用强调色）喵
        ctx.fillStyle = LABEL_CONFIG.accentColor
        ctx.beginPath()
        ctx.roundRect(0, borderRadius, 3, height - borderRadius * 2, [0, 2, 2, 0])
        ctx.fill()

        // 绘制标题区域背景（略微高亮）喵
        ctx.fillStyle = 'rgba(86, 215, 255, 0.08)'
        ctx.beginPath()
        ctx.roundRect(borderWidth, borderWidth, width - borderWidth * 2, 44, [borderRadius - 1, borderRadius - 1, 0, 0])
        ctx.fill()

        // 绘制分隔线喵
        ctx.strokeStyle = LABEL_CONFIG.dividerColor
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
        const { titleFont, titleColor } = LABEL_CONFIG

        ctx.font = titleFont
        ctx.fillStyle = titleColor
        ctx.textBaseline = 'top'

        // 恒星类型名称喵
        const starTypeName = STAR_TYPE_NAMES[details.starTypeId] || details.starTypeId
        ctx.fillText(`☀️ ${starTypeName}`, 14, 14)

        // 恒星ID喵
        ctx.font = LABEL_CONFIG.labelFont
        ctx.fillStyle = LABEL_CONFIG.labelColor
        ctx.fillText(`#${entity.entityId}`, 14, 32)
    }

    /**
     * 绘制信息喵
     * 使用卡片式布局，与现有 UI 风格对齐喵
     */
    private drawInfo(ctx: CanvasRenderingContext2D, details: StarDetails): void {
        const { width, infoFont, infoColor, labelFont, labelColor } = LABEL_CONFIG
        const startY = 54
        const cardPadding = 10
        const cardGap = 6

        // 定义信息卡片喵
        const cards = [
            { label: '温度', value: `${details.temperatureK.toLocaleString()} K`, icon: '🌡️' },
            { label: '质量', value: `${details.massSolar.toFixed(2)} M☉`, icon: '⚖️' },
            { label: '半径', value: `${details.radiusGU.toLocaleString()} GU`, icon: '📏' },
        ]

        // 计算卡片尺寸喵
        const cardWidth = (width - 12 * 2 - cardGap * (cards.length - 1)) / cards.length
        const cardHeight = 40

        cards.forEach((card, index) => {
            const x = 12 + index * (cardWidth + cardGap)
            const y = startY

            // 绘制卡片背景喵
            ctx.fillStyle = 'rgba(86, 215, 255, 0.05)'
            ctx.strokeStyle = 'rgba(86, 215, 255, 0.1)'
            ctx.lineWidth = 1
            ctx.beginPath()
            ctx.roundRect(x, y, cardWidth, cardHeight, 6)
            ctx.fill()
            ctx.stroke()

            // 绘制标签喵
            ctx.font = labelFont
            ctx.fillStyle = labelColor
            ctx.textBaseline = 'top'
            ctx.fillText(`${card.icon} ${card.label}`, x + cardPadding, y + 8)

            // 绘制值喵
            ctx.font = infoFont
            ctx.fillStyle = infoColor
            ctx.fillText(card.value, x + cardPadding, y + 24)
        })

        // 描述（如果有）喵
        if (details.description) {
            const descY = startY + cardHeight + 10
            ctx.font = labelFont
            ctx.fillStyle = labelColor
            ctx.textBaseline = 'top'
            const descText = details.description.length > 30
                ? details.description.substring(0, 30) + '...'
                : details.description
            ctx.fillText(`"${descText}"`, 12, descY)
        }
    }
}
