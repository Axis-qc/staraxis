package com.staraxis.game.core;

import com.staraxis.game.core.engine.GameServer;
import com.staraxis.game.shared.model.GameState;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 服务端核心测试 (Game Server Tests)
 */
public class GameServerTest {

    @Test
    public void testTickProgression /* 测试帧进阶 */() throws InterruptedException {
        GameServer server = new GameServer();
        server.start();

        // 等待约 3 个 Tick (150ms)
        Thread.sleep(200);

        server.stop();

        GameState state = server.getCurrentState();
        assertTrue(state.tick >= 3, "Tick 应该至少增加到 3，实际为: " + state.tick);
        assertTrue(state.timestamp > 0, "时间戳应该被设置");
    }

    @Test
    public void testHeadlessConstraints /* 测试无界面约束 */() {
        // 这个测试主要用于确保核心逻辑不依赖图形库
        // 如果引入了 com.badlogic.gdx.graphics，编译检查任务 checkNoGraphicsDependencies 会报错
        GameServer server = new GameServer();
        assertNotNull(server.getCurrentState());
    }
}
