package com.staraxis.game.core.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.staraxis.game.core.network.ClientSession;
import com.staraxis.game.shared.model.Command;
import com.staraxis.game.shared.model.CommandType;
import com.staraxis.game.shared.model.GameState;
import com.staraxis.game.shared.model.WorldMetadata;
import com.staraxis.game.shared.network.ConnectionRequest;
import com.staraxis.game.shared.network.ConnectionResponse;
import com.staraxis.game.shared.network.GameStateUpdate;
import com.staraxis.game.shared.network.PlayerCommandMessage;

/**
 * 游戏服务端核心 (Game Server Core)
 *
 * 使用的接口: GameState, WorldMetadata 提供的接口: 驱动 20Hz 模拟循环，维护权威游戏状态
 */
public class GameServer {

    private final GameState currentState;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final long tickDuration = 50; // 20Hz = 50ms per tick

    private final Map<Long, ClientSession> sessions = new ConcurrentHashMap<>();
    private final AtomicLong nextPlayerId = new AtomicLong(1);
    private long nextMessageSequence /* 下一个消息序列号 */ = 1;

    public GameServer() {
        currentState = new GameState();
        currentState.tick = 0;
        currentState.entities = new HashMap<>();
        currentState.worldData = new WorldMetadata();
        currentState.worldData.galaxySize = 1000;
    }

    /**
     * 启动模拟循环 (Start Simulation Loop)
     */
    public void start() {
        if (running.getAndSet(true)) {
            return;
        }

        Thread loopThread = new Thread(() -> {
            while (running.get()) {
                long startTime = System.currentTimeMillis();

                tick();

                long endTime = System.currentTimeMillis();
                long elapsed = endTime - startTime;

                // 性能预算检查 (FR-006: 10ms budget)
                if (elapsed > 10) {
                    System.err.println("[警告] Tick 耗时超标: " + elapsed + "ms");
                }

                long sleepTime = tickDuration - elapsed;
                if (sleepTime > 0) {
                    try {
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }, "StarAxis-SimLoop");
        loopThread.setDaemon(true);
        loopThread.start();
    }

    /**
     * 停止模拟循环 (Stop Simulation Loop)
     */
    public void stop() {
        running.set(false);
    }

    /**
     * 处理来自客户端的消息包 (Handle Message from Client)
     */
    public void handleMessage(Object message) {
        if (message instanceof ConnectionRequest) {
            handleConnection((ConnectionRequest) message);
        } else if (message instanceof PlayerCommandMessage) {
            handlePlayerCommand(((PlayerCommandMessage) message).command);
        }
    }

    private void broadcastState /* 广播状态更新 */() {
        if (sessions.isEmpty()) {
            return;
        }

        GameStateUpdate update = new GameStateUpdate();
        update.state = currentState; // 实际应为快照或增量包

        for (ClientSession session : sessions.values()) {
            if (session.connected) {
                // 模拟发送数据包 (Simulate sending packet)
                // 在 MemoryQueue 模式下，我们会将消息放入 session 对应的队列
            }
        }
    }

    private void handlePlayerCommand(Command command) {
        if (command == null) {
            return;
        }

        ClientSession session = sessions.get(command.playerId);
        if (session != null) {
            session.updateHeartbeat();

            if (command.commandType != CommandType.HEARTBEAT) {
                // TODO: 将非心跳指令加入处理队列 (Add to command queue)
            }
        }
    }

    /**
     * 处理连接请求 (Handle Connection Request)
     */
    public ConnectionResponse handleConnection(ConnectionRequest request) {
        if (request == null) {
            return null;
        }

        // TODO: 版本校验 (Version check)
        long playerId = nextPlayerId.getAndIncrement();
        ClientSession session = new ClientSession(playerId, request.playerName);
        sessions.put(playerId, session);

        ConnectionResponse response = new ConnectionResponse();
        response.success = true;
        response.assignedPlayerId = playerId;
        response.message = "Welcome to StarAxis, " + request.playerName;

        return response;
    }

    /**
     * 执行单次步进 (Execute Single Tick)
     */
    public void tick() {
        long tickStartTime = System.nanoTime();

        currentState.tick++;
        currentState.timestamp = System.currentTimeMillis();
        currentState.simulationTime += tickDuration;

        String tickPrefix = "[TICK-" + currentState.tick + "] ";
        // System.out.println(tickPrefix + "开始逻辑步进...");

        updateSessions();
        // TODO: 调用各系统处理器 (Call processors)

        broadcastState();

        // 帧漂移监控 (Tick Drift Monitor - T039)
        long tickEndTime = System.nanoTime();
        double actualDurationMs = (tickEndTime - tickStartTime) / 1_000_000.0;

        // 性能预算检查 (FR-006: 10ms budget) (T045)
        if (actualDurationMs > 10) {
            System.err.println(tickPrefix + "[警告] 逻辑处理耗时超标: " + actualDurationMs + "ms");
        }

        if (Math.abs(actualDurationMs - tickDuration) > 2.0) {
            // System.out.println(tickPrefix + "[调试] 帧执行时间偏移: " + actualDurationMs + "ms");
        }
    }

    private void updateSessions() {
        long now = System.currentTimeMillis();
        for (ClientSession session : sessions.values()) {
            if (session.connected) {
                // 心跳超时检查 (FR-004: 5s timeout)
                if (now - session.lastHeartbeat > 5000) {
                    session.connected = false;
                    session.disconnectTime = now;
                    System.out.println("[信息] 玩家 " + session.playerId + " 心跳超时，进入断线保留状态");
                }
            } else {
                // 实体保留超时检查 (30s retention)
                if (now - session.disconnectTime > 30000) {
                    sessions.remove(session.playerId);
                    System.out.println("[信息] 玩家 " + session.playerId + " 保留超时，移除会话");
                }
            }
        }
    }

    public GameState getCurrentState() {
        return currentState;
    }

    public boolean isRunning() {
        return running.get();
    }
}
