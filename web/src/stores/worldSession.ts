import { defineStore } from 'pinia'

export type PlayerWorldState = 'NOT_JOINED' | 'SPAWN_PENDING' | 'SPAWNED'

export const useWorldSessionStore = defineStore('world-session', {
  state: () => ({
    selectedWorldId: '',
    selectedWorldName: '',
    playerWorldState: 'NOT_JOINED' as PlayerWorldState,
    joinedAtEpochMs: 0,
  }),
  actions: {
    setSelectedWorld(worldId: string, worldName: string) {
      this.selectedWorldId = worldId || ''
      this.selectedWorldName = worldName || ''
    },
    setPlayerWorldState(state: PlayerWorldState) {
      this.playerWorldState = state
    },
    markJoinedNow() {
      this.joinedAtEpochMs = Date.now()
    },
    clear() {
      this.selectedWorldId = ''
      this.selectedWorldName = ''
      this.playerWorldState = 'NOT_JOINED'
      this.joinedAtEpochMs = 0
    },
  },
})
