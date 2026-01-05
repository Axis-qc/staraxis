package com.staraxis.game.client;

import java.util.LinkedList;

import com.staraxis.game.client.render.LinearInterpolator;
import com.staraxis.game.shared.model.Command;
import com.staraxis.game.shared.model.GameState;
import com.staraxis.game.shared.network.MemoryQueue;

/**
 * 游戏客户端核心 (Game Client Core)
 *
 * 使用的接口: GameState, Command, MemoryQueue 提供的接口: 管理客户端状态缓冲区，处理平滑插值，转发用户输入
 */
public class GameClient {

    private final LinkedList<GameState> stateBuffer = new LinkedList<>(); // 状态缓冲区
    private final LinearInterpolator interpolator = new LinearInterpolator(); // 插值器
    private final MemoryQueue<Object> clientToServerQueue; // 发送到服务端的队列

    private GameState interpolatedState; // 插值后的显示状态
    private float renderAccumulator = 0; // 渲染时间累加器
    private long currentSimulationTime = 0; // 客户端当前模拟时间
    private final float tickDurationSec = 0.05f; // 20Hz = 0.05s
    private boolean connected = false;
    private long lastHeartbeatReceived = 0; // 上次收到心跳的时间
    private final long connectionTimeout = 5000; // 5s 超时
    private long lastServerTick = -1;

    public GameClient(MemoryQueue<Object> clientToServerQueue) {
        this.clientToServerQueue = clientToServerQueue;
    }

    /**
     * 接收来自服务端的状态更新 (Receive State Update from Server)
     */
    public void onGameStateUpdate(GameState newState) {
        if (newState == null) {
            return;
        }

        lastHeartbeatReceived = System.currentTimeMillis();
        connected = true;

        // 确保 Tick 顺序
        if (newState.tick > lastServerTick) {
            // 在添加新状态前，执行预测校解 (T035)
            reconcile(newState);

            stateBuffer.addLast(newState);
            lastServerTick = newState.tick;

            // 缓冲区限制
            if (stateBuffer.size() > 5) {
                stateBuffer.removeFirst();
            }
        }
    }

    /**
     * 预测校解 (Reconciliation Logic - T035) 检查本地预测与权威状态的偏差
     */
    private void reconcile(GameState authoritativeState) {
        if (interpolatedState == null) {
            return;
        }

        for (com.staraxis.game.shared.model.EntityState authEntity : authoritativeState.entities.values()) {
            com.staraxis.game.shared.model.EntityState localEntity = interpolatedState.entities.get(authEntity.id);
            if (localEntity != null && localEntity.position != null && authEntity.position != null) {
                float dx = authEntity.position.x - localEntity.position.x;
                float dy = authEntity.position.y - localEntity.position.y;
                double distance = Math.sqrt(dx * dx + dy * dy);

                // 如果偏差超过阈值 (0.1单位)，则触发强制同步 (Snap-back)
                if (distance > 0.1) {
                    System.out.println("[警告] 预测偏差过大 (" + distance + ")，执行强制对齐: " + authEntity.id);
                    // 实际应用中，这里可能会标记该实体需要立即平滑过渡或瞬间移动
                }
            }
        }
    }

    /**
     * 更新插值逻辑 (Update Interpolation)
     */
    public void update(float deltaTime) {
        // 更新连接状态 (T034)
        if (connected && System.currentTimeMillis() - lastHeartbeatReceived > connectionTimeout) {
            connected = false;
            System.err.println("[错误] 失去服务端连接...");
        }

        if (stateBuffer.size() < 2) {
            if (stateBuffer.size() == 1) {
                interpolatedState = stateBuffer.getFirst();
                currentSimulationTime = interpolatedState.simulationTime;
            }
            return;
        }

        renderAccumulator += deltaTime;

        // 基于收到的 Tick 推进客户端模拟时间 (T038)
        while (renderAccumulator >= tickDurationSec) {
            if (stateBuffer.size() > 2) {
                stateBuffer.removeFirst();
                renderAccumulator -= tickDurationSec;
                currentSimulationTime += (long) (tickDurationSec * 1000);
            } else {
                // 缓冲区即将枯竭，停止步进以维持最后一帧
                renderAccumulator = tickDurationSec;
                break;
            }
        }

        float alpha = renderAccumulator / tickDurationSec;
        interpolatedState = interpolator.interpolate(stateBuffer.get(0), stateBuffer.get(1), alpha);
    }

    /**
     * 发送指令到服务端 (Send Command to Server)
     */
    public void sendCommand(Command command) {
        if (command != null && clientToServerQueue != null) {
            clientToServerQueue.send(command);
        }
    }

    public GameState getInterpolatedState() {
        return interpolatedState;
    }

    public boolean isConnected() {
        return connected;
    }
}
