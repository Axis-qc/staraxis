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
 * - 实现“挤号下线”：一个 playerId 同时只能有一个活跃玩家连接 + 一个活跃 AI 连接喵。
 * - 负责应用层心跳（Ping/Pong）检测与僵尸连接清理喵。
 */
public class WsConnectionManager {

    private final Set<WebSocketChannel> allChannels = ConcurrentHashMap.newKeySet();
    private final Set<WebSocketChannel> snapshotSubscribers = ConcurrentHashMap.newKeySet();

    private final ConcurrentHashMap<WebSocketChannel, Set<SectorCoord>> visibleSectorsByChannel = new ConcurrentHashMap<>();

    // playerId -> Player Channel (实现一号一连) 喵
    private final ConcurrentHashMap<String, WebSocketChannel> playerSessions = new ConcurrentHashMap<>();
    // playerId -> AI Channel (实现一号一 AI) 喵
    private final ConcurrentHashMap<String, WebSocketChannel> aiSessions = new ConcurrentHashMap<>();

    // Channel -> playerId (方便断开时快速查找) 喵
    private final ConcurrentHashMap<WebSocketChannel, String> channelToPlayerId = new ConcurrentHashMap<>();

    // Channel -> connectionId (本次物理连接的唯一临时 ID) 喵
    private final ConcurrentHashMap<WebSocketChannel, String> channelToConnectionId = new ConcurrentHashMap<>();

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
     * 生成并获取新的连接 ID 喵。
     */
    public String generateConnectionId() {
        return "conn_" + System.currentTimeMillis() + "_" + (int) (Math.random() * 1000);
    }

    /**
     * 获取通道对应的玩家ID喵。
     *
     * @param channel WebSocket通道
     * @return playerId，如果未绑定则返回 null
     */
    public String getPlayerIdByChannel(WebSocketChannel channel) {
        return channelToPlayerId.get(channel);
    }

    /**
     * 获取通道对应的连接ID喵。
     */
    public String getConnectionIdByChannel(WebSocketChannel channel) {
        return channelToConnectionId.get(channel);
    }

    /**
     * 注册玩家连接，支持挤号逻辑喵。
     *
     * @param playerId 玩家唯一标识（来自 Auth）喵。
     * @param channel  新连接的通道喵。
     * @return 分配的 connectionId 喵。
     */
    public String registerPlayer(String playerId, WebSocketChannel channel) {
        if (playerId == null || playerId.isBlank() || channel == null) {
            return null;
        }

        String connectionId = generateConnectionId();

        // 1. 挤号逻辑：如果该 playerId 已有玩家连接，强制关闭旧的喵
        WebSocketChannel oldChannel = playerSessions.get(playerId);
        if (oldChannel != null && oldChannel != channel) {
            String oldConnId = channelToConnectionId.get(oldChannel);
            WebNetLog.logThrottled("conn_kick_" + playerId,
                    "ConnMgr: Player " + playerId + " reconnected. New=" + connectionId + ", kicking old=" + oldConnId);
            try {
                WebSockets.sendText(
                        "{\"type\":\"kick\",\"reason\":\"new_login\",\"connectionId\":\"" + connectionId + "\"}",
                        oldChannel, null);
                oldChannel.close();
            } catch (Exception ignored) {
            }
            // 立即从集合移除并扣减计数，防止累加喵
            unregisterPlayer(oldChannel);
        }

        // 2. 绑定新连接喵
        playerSessions.put(playerId, channel);
        channelToPlayerId.put(channel, playerId);
        channelToConnectionId.put(channel, connectionId);
        allChannels.add(channel);
        playerLastPongMs.put(channel, System.currentTimeMillis());

        playerConnectionCount.incrementAndGet();
        WebAiAutoStarter.ensureAiStartedIfNeeded();

        return connectionId;
    }

    /**
     * 注册 AI 连接，并绑定到 playerId，支持挤号逻辑喵。
     *
     * @param playerId AI 代表的玩家ID喵。
     * @param channel  AI 的 WebSocket 通道喵。
     * @return 分配的 connectionId 喵。
     */
    public String registerAiForPlayer(String playerId, WebSocketChannel channel) {
        if (playerId == null || playerId.isBlank() || channel == null) {
            return null;
        }

        String connectionId = generateConnectionId();

        // 1. 挤号：同一 playerId 只能有一个 AI 连接喵
        WebSocketChannel oldAi = aiSessions.get(playerId);
        if (oldAi != null && oldAi != channel) {
            String oldConnId = channelToConnectionId.get(oldAi);
            WebNetLog.logThrottled("ai_conn_kick_" + playerId,
                    "ConnMgr: AI for player " + playerId + " reconnected. New=" + connectionId + ", kicking old="
                            + oldConnId);
            try {
                WebSockets.sendText(
                        "{\"type\":\"kick\",\"reason\":\"new_login\",\"connectionId\":\"" + connectionId + "\"}", oldAi,
                        null);
                oldAi.close();
            } catch (Exception ignored) {
            }
            unregisterAi(oldAi);
        }

        aiSessions.put(playerId, channel);
        channelToPlayerId.put(channel, playerId);
        channelToConnectionId.put(channel, connectionId);
        allChannels.add(channel);
        aiConnectionCount.incrementAndGet();

        WebAiAutoStarter.ensureAiStartedIfNeeded();

        return connectionId;
    }

    /**
     * 注销玩家连接喵。
     */
    public void unregisterPlayer(WebSocketChannel channel) {
        if (channel == null) {
            return;
        }

        if (allChannels.remove(channel)) {
            cleanupChannel(channel);
            playerConnectionCount.decrementAndGet();

            if (playerConnectionCount.get() <= 0) {
                lastDisconnectAtMs.set(System.currentTimeMillis());
            }
        }
    }

    /**
     * 注销 AI 连接喵。
     */
    public void unregisterAi(WebSocketChannel channel) {
        if (channel == null) {
            return;
        }

        if (allChannels.remove(channel)) {
            cleanupChannel(channel);
            aiConnectionCount.decrementAndGet();
        }
    }

    private void cleanupChannel(WebSocketChannel channel) {
        snapshotSubscribers.remove(channel);
        visibleSectorsByChannel.remove(channel);
        playerLastPongMs.remove(channel);
        channelToNationId.remove(channel);
        channelToConnectionId.remove(channel);

        String pid = channelToPlayerId.remove(channel);
        if (pid != null) {
            // 如果这是玩家通道，解除 playerSessions 绑定，并联动关闭该玩家的 AI 通道喵
            if (playerSessions.remove(pid, channel)) {
                WebSocketChannel associatedAi = aiSessions.remove(pid);
                if (associatedAi != null && associatedAi.isOpen()) {
                    WebNetLog.logThrottled("ai_link_close_" + pid,
                            "ConnMgr: Player " + pid + " disconnected, closing associated AI session喵.");
                    try {
                        WebSockets.sendText("{\"type\":\"kick\",\"reason\":\"player_disconnected\"}", associatedAi,
                                null);
                        associatedAi.close();
                    } catch (Exception ignored) {
                    }
                }
            }

            // 如果这是 AI 通道，解除 aiSessions 绑定喵
            aiSessions.remove(pid, channel);

            playerToNationId.remove(pid);
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
     * 每分钟打印当前连接详情到 webnet.log 喵。
     */
    public void logActiveConnections() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("ConnMgr: ActiveConnections players=").append(playerConnectionCount.get())
                    .append(" ai=").append(aiConnectionCount.get()).append(" all=").append(allChannels.size())
                    .append("\n");

            sb.append("Players:\n");
            for (var e : playerSessions.entrySet()) {
                String pid = e.getKey();
                WebSocketChannel ch = e.getValue();
                String cid = ch == null ? null : channelToConnectionId.get(ch);
                boolean open = ch != null && ch.isOpen();
                sb.append("  - playerId=").append(pid)
                        .append(" connectionId=").append(cid)
                        .append(" open=").append(open)
                        .append("\n");
            }

            sb.append("AIs:\n");
            for (var e : aiSessions.entrySet()) {
                String pid = e.getKey();
                WebSocketChannel ch = e.getValue();
                String cid = ch == null ? null : channelToConnectionId.get(ch);
                boolean open = ch != null && ch.isOpen();
                sb.append("  - playerId=").append(pid)
                        .append(" connectionId=").append(cid)
                        .append(" open=").append(open)
                        .append("\n");
            }

            WebNetLog.log(sb.toString());
        } catch (Exception ignored) {
        }
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
            // 这里判断：通道存在 playerLastPong 才认为是玩家通道喵
            if (!playerLastPongMs.containsKey(ch)) {
                continue;
            }

            Long lastPong = playerLastPongMs.get(ch);
            // 心跳容错：允许短暂抖动与后台挂起，超过 130s 未收到 pong 才断开喵
            if (lastPong != null && (now - lastPong > 130_000L)) {
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
