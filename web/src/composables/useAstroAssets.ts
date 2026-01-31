import { ref, onMounted } from 'vue'

export type AstroAsset<T> = {
    typeId: string
    spriteCandidates: string[]
} & T

const starTypes = ref<AstroAsset<{}>[]>([])
const planetTypes = ref<AstroAsset<{}>[]>([])

async function loadAstroAssets() {
    try {
        const starResponse = await fetch('/assets/star/star-types.json')
        starTypes.value = await starResponse.json()

        const planetResponse = await fetch('/assets/planet/planet-types.json')
        planetTypes.value = await planetResponse.json()
    } catch (error) {
        console.error('Failed to load astro assets:', error)
    }
}

export function useAstroAssets() {
    onMounted(loadAstroAssets)

    function getSpritePath(typeId: string): string | undefined {
        const star = starTypes.value.find(t => t.typeId === typeId)
        if (star && star.spriteCandidates.length > 0) {
            return star.spriteCandidates[0] // Return the first candidate for now
        }

        const planet = planetTypes.value.find(t => t.typeId === typeId)
        if (planet && planet.spriteCandidates.length > 0) {
            return planet.spriteCandidates[0] // Return the first candidate for now
        }

        return undefined
    }

    return { getSpritePath }
}
