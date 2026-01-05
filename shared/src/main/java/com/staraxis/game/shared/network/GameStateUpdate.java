package com.staraxis.game.shared.network;

import com.staraxis.game.shared.model.GameState;

/**
 * 游戏状态更新包 (Server -> Client)
 */
public class GameStateUpdate {

    public GameState state; // 状态快照

    public GameStateUpdate() {
    }
}
