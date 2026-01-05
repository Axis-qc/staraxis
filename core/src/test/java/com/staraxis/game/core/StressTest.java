package com.staraxis.game.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.staraxis.game.core.engine.GameServer;
import com.staraxis.game.shared.model.EntityState;
import com.staraxis.game.shared.model.GameState;
import com.staraxis.game.shared.model.Vector2;

/**
 * 压力测试 (Stress Tests)
 */
public class StressTest {

    @Test
    public void test100EntitiesPerformance /* 测试100个实体的性能 */() throws InterruptedException {
        GameServer server = new GameServer();
        GameState state = server.getCurrentState();

        // 1. 初始化 100 个实体
        for (long i = 1; i <= 100; i++) {
            EntityState entity = new EntityState();
            entity.id = i;
            entity.type = "SHIP";
            entity.position = new Vector2(i, i);
            entity.rotation = 0;
            entity.health = 100;
            state.entities.put(i, entity);
        }

        // 2. 启动模拟并观察耗时
        server.start();

        // 运行 1 秒 (约 20 个 Tick)
        Thread.sleep(1000);

        server.stop();

        assertTrue(server.getCurrentState().tick >= 15, "1秒内应该至少执行 15 个 Tick");
        assertEquals(100, server.getCurrentState().entities.size(), "实体数量应维持在 100");
    }
}
