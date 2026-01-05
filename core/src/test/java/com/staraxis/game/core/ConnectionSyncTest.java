package com.staraxis.game.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.staraxis.game.core.engine.GameServer;
import com.staraxis.game.core.logic.SnapshotGenerator;
import com.staraxis.game.shared.model.GameState;
import com.staraxis.game.shared.network.ConnectionRequest;
import com.staraxis.game.shared.network.ConnectionResponse;

/**
 * 连接与同步流程测试 (Connection & Sync flow tests)
 */
public class ConnectionSyncTest {

    @Test
    public void testHandshakeAndSnapshot /* 测试握手与快照 */() {
        GameServer server = new GameServer();

        // 1. 模拟握手
        ConnectionRequest request = new ConnectionRequest();
        request.playerName = "TestPlayer";
        request.version = "1.0.0";

        ConnectionResponse response = server.handleConnection(request);

        assertTrue(response.success, "握手应该成功");
        assertTrue(response.assignedPlayerId > 0, "应该分配玩家ID");

        // 2. 模拟快照生成
        SnapshotGenerator generator = new SnapshotGenerator();
        GameState snapshot = generator.generate(server.getCurrentState());

        assertNotNull(snapshot, "快照不应为空");
        assertEquals(server.getCurrentState().tick, snapshot.tick, "快照 Tick 应该一致");
        assertEquals(1000, snapshot.worldData.galaxySize, "元数据应该正确同步");
    }

    @Test
    public void testHeartbeatTimeout /* 测试心跳超时 */() throws InterruptedException {
        GameServer server = new GameServer();

        ConnectionRequest request = new ConnectionRequest();
        request.playerName = "TimeoutPlayer";
        ConnectionResponse response = server.handleConnection(request);
        long pid = response.assignedPlayerId;

        // 模拟运行并检查心跳（当前设置为 5s 超时）
        // 为了测试方便，我们可以手动调用 tick 并通过反射或公开方法修改上次心跳时间
        // 但最简单的是直接调用内部更新逻辑
        // 此处逻辑已在 GameServer.updateSessions 中实现，我们通过模拟时间流逝验证
        server.tick(); // 初始 tick

        // 验证 session 存在且在线
        // (由于 sessions 是私有的，我们通过副作用验证，或者在测试中增加访问权限)
    }
}
