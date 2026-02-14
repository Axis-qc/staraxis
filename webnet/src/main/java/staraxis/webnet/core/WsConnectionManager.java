package staraxis.webnet.core;

import io.undertow.websockets.core.WebSocketChannel;
import io.undertow.websockets.core.WebSockets;
import staraxis.webnet.ai.WebAiAutoStarter;
import staraxis.game.world.hex.SectorCoord;

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

    private final ConcurrentHashMap<WebSocketChannel, Set<SectorCoord>> visibleSectorsByChannel = new ConcurrentHashMap<>();

    // playerId -> Channel (实现一号一连) 喵
    private final ConcurrentHashMap<String, WebSocketChannel> playerSessions = new ConcurrentHashMap<>();
    // Channel -> playerId (方便断开时快速查找) 喵
    private final ConcurrentHashMap<WebSocketChannel, String> channelToPlayerId = new ConcurrentHashMap<>();

    /**
     * 获取通道对应的玩家ID喵。
     *
     * @param channel WebSocket通道
     * @return playerId，如果未绑定则返回 null
     */
    public String getPlayerIdByChannel(WebSocketChannel channel) {
        return channelToPlayerId.get(channel);
    }

    // playerId -> nationId（玩家所属国家）喵
    private final ConcurrentHashMap<String, String> playerToNationId = new ConcurrentHashMap<>();
    // Channel -> nationId（方便快速查找）喵
    private final ConcurrentHashMap<WebSocketChannel, String> channelToNationId = new ConcurrentHashMap<>();

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
            WebNetLog.logThrottled("conn_kick_" + playerId,
                    "ConnMgr: Player " + playerId + " reconnected, kicking old session.");
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

        playerConnectionCount.incrementAndGet();
        WebAiAutoStarter.ensureAiStartedIfNeeded();
    }

    /**
     * 注销玩家连接喵。
     */
    public void unregisterPlayer(WebSocketChannel channel) {
        if (allChannels.remove(channel)) {
            cleanupChannel(channel);
            playerConnectionCount.decrementAndGet();

            if (playerConnectionCount.get() <= 0) {
                lastDisconnectAtMs.set(System.currentTimeMillis());
            }
        }
    }

    private void cleanupChannel(WebSocketChannel channel) {
        snapshotSubscribers.remove(channel);
        visibleSectorsByChannel.remove(channel);
        playerLastPongMs.remove(channel);
        channelToNationId.remove(channel);
        String pid = channelToPlayerId.remove(channel);
        if (pid != null) {
            playerSessions.remove(pid, channel);
            playerToNationId.remove(pid);
        }
    }

    public void registerAi(WebSocketChannel channel) {
        allChannels.add(channel);
        aiConnectionCount.incrementAndGet();
    }

    public void unregisterAi(WebSocketChannel channel) {
        if (allChannels.remove(channel)) {
            aiConnectionCount.decrementAndGet();
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

    public void updateVisibleSectors(WebSocketChannel channel, Set<SectorCoord> visibleSectors) {
        if (channel == null) {
            return;
        }

        if (visibleSectors == null || visibleSectors.isEmpty()) {
            visibleSectorsByChannel.remove(channel);
        } else {
            visibleSectorsByChannel.put(channel, visibleSectors);
        }
    }

    public Set<SectorCoord> getVisibleSectors(WebSocketChannel channel) {
        return visibleSectorsByChannel.get(channel);
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

    /**
     * 设置玩家所属国家ID喵。
     *
     * @param playerId 玩家ID
     * @param nationId 国家ID
     */
    public void setPlayerNationId(String playerId, String nationId) {
        if (playerId == null || nationId == null)
            return;
        playerToNationId.put(playerId, nationId);
        WebSocketChannel channel = playerSessions.get(playerId);
        if (channel != null) {
            channelToNationId.put(channel, nationId);
        }
    }

    /**
     * 获取玩家所属国家ID喵。
     *
     * @param playerId 玩家ID
     * @return 国家ID，如果未设置则返回 null
     */
    public String getPlayerNationId(String playerId) {
        return playerToNationId.get(playerId);
    }

    /**
     * 获取通道对应的国家ID喵。
     *
     * @param channel WebSocket通道
     * @return 国家ID，如果未设置则返回 null
     */
    public String getNationIdByChannel(WebSocketChannel channel) {
        return channelToNationId.get(channel);
    }

    /**
     * 清除玩家的国家ID关联喵（玩家断开连接时调用）喵。
     *
     * @param playerId 玩家ID
     */
    public void clearPlayerNationId(String playerId) {
        playerToNationId.remove(playerId);
        WebSocketChannel channel = playerSessions.get(playerId);
        if (channel != null) {
            channelToNationId.remove(channel);
        }
    }
}
