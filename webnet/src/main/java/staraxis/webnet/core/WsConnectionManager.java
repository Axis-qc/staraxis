package staraxis.webnet.core;

import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import staraxis.webnet.ai.WebAiAutoStarter;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WsConnectionManager
 *
 * 作用：
 * - 统一管理所有 WebSocket 连接（玩家与 AI）的生命周期。
 * - 实现“挤号下线”：一个 playerId 同时只能有一个活跃连接。
 * - 负责应用层心跳（Ping/Pong）检测与僵尸连接清理。
 */
public class WsConnectionManager {

    private final Set<WebSocketChannel> allChannels = ConcurrentHashMap.newKeySet();
    private final Set<WebSocketChannel> snapshotSubscribers = ConcurrentHashMap.newKeySet();

    // playerId -> Channel (实现一号一连) 喵
    private final ConcurrentHashMap<String, WebSocketChannel> playerSessions = new ConcurrentHashMap<>();
    // Channel -> playerId (方便断开时快速查找) 喵
    private final ConcurrentHashMap<WebSocketChannel, String> channelToPlayerId = new ConcurrentHashMap<>();

    private final AtomicInteger playerConnectionCount = new AtomicInteger(0);
    private final AtomicInteger aiConnectionCount = new AtomicInteger(0);
    private final AtomicLong lastDisconnectAtMs = new AtomicLong(0);

    // 玩家心跳追踪喵
    private final ConcurrentHashMap<WebSocketChannel, Long> playerLastPongMs = new ConcurrentHashMap<>();

    public WsConnectionManager() {
    }

    /**
     * 注册玩家连接，支持挤号逻辑喵。
     * 
     * @param playerId 玩家唯一标识（来自 Auth）
     * @param channel  新连接的通道
     */
    public void registerPlayer(String playerId, WebSocketChannel channel) {
        if (playerId == null || playerId.isBlank()) {
            return;
        }

        // 1. 挤号逻辑：如果该 ID 已有连接，强制关闭旧的喵
        WebSocketChannel oldChannel = playerSessions.get(playerId);
        if (oldChannel != null && oldChannel != channel) {
            System.out.println("ConnMgr: Player " + playerId + " reconnected, kicking old session.");
            try {
                WebSockets.sendText("{\"type\":\"kick\",\"reason\":\"new_login\"}", oldChannel, null);
                oldChannel.close();
            } catch (Exception ignored) {
            }
            // 立即从集合移除，不等待 onClose 回调喵
            cleanupChannel(oldChannel);
        }

        // 2. 绑定新连接喵
        playerSessions.put(playerId, channel);
        channelToPlayerId.put(channel, playerId);
        allChannels.add(channel);
        playerLastPongMs.put(channel, System.currentTimeMillis());

        int count = playerConnectionCount.incrementAndGet();
        System.out.println("ConnMgr: Player [" + playerId + "] connected, total=" + count);

        WebAiAutoStarter.ensureAiStartedIfNeeded();
    }

    /**
     * 注销玩家连接喵。
     */
    public void unregisterPlayer(WebSocketChannel channel) {
        if (allChannels.remove(channel)) {
            cleanupChannel(channel);
            int left = playerConnectionCount.decrementAndGet();
            System.out.println("ConnMgr: Player disconnected, remaining=" + left);

            if (left <= 0) {
                lastDisconnectAtMs.set(System.currentTimeMillis());
            }
        }
    }

    private void cleanupChannel(WebSocketChannel channel) {
        snapshotSubscribers.remove(channel);
        playerLastPongMs.remove(channel);
        String pid = channelToPlayerId.remove(channel);
        if (pid != null) {
            playerSessions.remove(pid, channel);
        }
    }

    public void registerAi(WebSocketChannel channel) {
        allChannels.add(channel);
        int count = aiConnectionCount.incrementAndGet();
        System.out.println("ConnMgr: AI connected, total=" + count);
    }

    public void unregisterAi(WebSocketChannel channel) {
        if (allChannels.remove(channel)) {
            int left = aiConnectionCount.decrementAndGet();
            System.out.println("ConnMgr: AI disconnected, remaining=" + left);
        }
    }

    public void onPlayerPong(WebSocketChannel channel) {
        playerLastPongMs.put(channel, System.currentTimeMillis());
    }

    public void subscribeSnapshot(WebSocketChannel channel) {
        snapshotSubscribers.add(channel);
    }

    public void unsubscribeSnapshot(WebSocketChannel channel) {
        snapshotSubscribers.remove(channel);
    }

    /**
     * 获取所有活跃的 WebSocket 通道喵。
     */
    public Set<WebSocketChannel> getAllChannels() {
        return allChannels;
    }

    public Set<WebSocketChannel> getSnapshotSubscribers() {
        return snapshotSubscribers;
    }

    /**
     * 检查超时并发送 Ping 喵。
     */
    public void sweepAndPing() {
        long now = System.currentTimeMillis();

        if (playerConnectionCount.get() > 0) {
            WebAiAutoStarter.reportActivity();
        }

        for (WebSocketChannel ch : allChannels) {
            // 只对玩家通道做心跳，AI 通道由 WebAiWebSocketHandler 自行管理（如有需要）喵
            if (!channelToPlayerId.containsKey(ch))
                continue;

            Long lastPong = playerLastPongMs.get(ch);
            if (lastPong != null && (now - lastPong > 60_000L)) {
                String pid = channelToPlayerId.get(ch);
                System.out.println("ConnMgr: Heartbeat timeout for player [" + pid + "], closing.");
                forceCloseAndCleanup(ch);
                continue;
            }

            if (ch.isOpen()) {
                try {
                    WebSockets.sendText("{\"type\":\"ping\"}", ch, null);
                } catch (Exception e) {
                    forceCloseAndCleanup(ch);
                }
            } else {
                unregisterPlayer(ch);
            }
        }
    }

    private void forceCloseAndCleanup(WebSocketChannel ch) {
        try {
            ch.close();
        } catch (Exception ignored) {
        }
        unregisterPlayer(ch);
    }

    public int getPlayerCount() {
        return playerConnectionCount.get();
    }

    public int getAiCount() {
        return aiConnectionCount.get();
    }

    public long getLastDisconnectAtMs() {
        return lastDisconnectAtMs.get();
    }

    // 供 AdminApi 直接绑定的原子引用喵
    public AtomicInteger getPlayerCountRef() {
        return playerConnectionCount;
    }

    public AtomicInteger getAiCountRef() {
        return aiConnectionCount;
    }

    public AtomicLong getLastDisconnectAtMsRef() {
        return lastDisconnectAtMs;
    }
}
