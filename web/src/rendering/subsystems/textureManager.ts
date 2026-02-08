/**
 * @file textureManager.ts
 *
 * @description
 * 纹理器（Texture Manager）。
 *
 * 作用：
 * - 统一加载、缓存和管理 Three.js 纹理资源。
 * - 提供纹理复用机制，避免重复加载。
 * - 支持异步加载与错误处理。
 *
 * @usage
 * - 在子系统初始化时通过 context.getTexture(path) 获取纹理。
 * - 管理器负责缓存生命周期，子系统无需关心缓存逻辑。
 *
 * @provides
 * - **纹理加载**：支持异步加载和缓存。
 * - **纹理缓存**：避免重复加载相同资源。
 * - **错误处理**：加载失败时返回默认纹理或抛出错误。
 *
 * @api
 * - getTexture(path: string): Promise<THREE.Texture>
 * - dispose(): void
 *
 * @important_notes
 * - 使用 Map 缓存纹理，避免内存泄漏。
 * - 纹理器在管理器销毁时统一释放所有纹理。
 */
import * as THREE from 'three'

export type TextureManager = {
    getTexture: (path: string) => Promise<THREE.Texture>
    dispose: () => void
}

export function createTextureManager(): TextureManager {
    const cache = new Map<string, THREE.Texture>()
    const loader = new THREE.TextureLoader()

    const getTexture = async (path: string): Promise<THREE.Texture> => {

        if (!path || path.trim() === '') {
            console.error(`TextureManager: invalid path: "${path}"`)
            return Promise.reject(new Error(`Invalid texture path: "${path}"`))
        }

        if (cache.has(path)) {
            return cache.get(path)!
        }

        const fullPath = `/assets/${path}`

        return new Promise((resolve, reject) => {
            loader.load(
                fullPath,
                (texture) => {
                    texture.anisotropy = 16 // 默认各向异性
                    cache.set(path, texture)
                    resolve(texture)
                },
                undefined, // onProgress callback (optional)
                (error) => {
                    console.error(`TextureManager: failed to load texture: "${path}"`, error)
                    reject(error)
                }
            )
        })
    }

    const dispose = () => {
        for (const texture of cache.values()) {
            texture.dispose()
        }
        cache.clear()
    }

    return { getTexture, dispose }
}