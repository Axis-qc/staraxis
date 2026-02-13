import { ref } from 'vue'

export function useInGameWindows() {
    const isEscMenuOpen = ref(false)
    const planetWindowOpen = ref(false)
    const planetEntity = ref<any>(null)

    function openPlanetWindow(entity: any) {
        planetEntity.value = entity
        planetWindowOpen.value = true
    }

    function closePlanetWindow() {
        planetWindowOpen.value = false
        planetEntity.value = null
    }

    function toggleEscMenu() {
        isEscMenuOpen.value = !isEscMenuOpen.value
    }

    return {
        isEscMenuOpen,
        planetWindowOpen,
        planetEntity,
        openPlanetWindow,
        closePlanetWindow,
        toggleEscMenu
    }
}
