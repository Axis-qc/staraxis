import { SECTOR_SIZE_GU } from '../../../rendering/hexSectorGeometry'

export type SectorCoord = { q: number; r: number }

export function sectorCenterWorld2D_GU(s: SectorCoord): { x: number; y: number } {
    const size = SECTOR_SIZE_GU
    const x = size * (Math.sqrt(3) * s.q + (Math.sqrt(3) / 2) * s.r)
    const y = size * ((3 / 2) * s.r)
    return { x, y }
}

export function hexDistance(a: SectorCoord, b: SectorCoord): number {
    const ax = a.q
    const az = a.r
    const ay = -ax - az

    const bx = b.q
    const bz = b.r
    const by = -bx - bz

    return (Math.abs(ax - bx) + Math.abs(ay - by) + Math.abs(az - bz)) / 2
}
