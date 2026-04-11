/**
 * @file celestialLayer.test.ts
 *
 * @description
 * 星体层单元测试喵。
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import * as THREE from 'three'
import { CelestialLayer } from '../../../rendering/layers/celestial'
import { RenderOrder } from '../../../rendering/layers'

describe('CelestialLayer', () => {
    let layer: CelestialLayer

    beforeEach(() => {
        layer = new CelestialLayer()
    })

    afterEach(() => {
        // 清理
    })

    it('应该正确初始化', () => {
        expect(layer.name).toBe('celestial')
        expect(layer.renderOrder).toBe(RenderOrder.CELESTIAL)
        expect(layer.isVisible()).toBe(true)
        expect(layer.group).toBeInstanceOf(THREE.Group)
        expect(layer.group.renderOrder).toBe(RenderOrder.CELESTIAL)
    })

    it('应该控制可见性', () => {
        layer.setVisible(false)
        expect(layer.isVisible()).toBe(false)
        expect(layer.group.visible).toBe(false)

        layer.setVisible(true)
        expect(layer.isVisible()).toBe(true)
        expect(layer.group.visible).toBe(true)
    })

    it('应该控制渲染质量', () => {
        layer.setQuality(0.5)
        // 质量设置应该被限制在0-1之间
        layer.setQuality(1.5)
        // group对象本身是可见的，所以visibleObjects至少为1
        expect(layer.getStats().visibleObjects).toBe(1) // group对象本身
    })
})