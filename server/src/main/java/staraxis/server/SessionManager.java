package staraxis.server;

import io.netty.channel.Channel;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程安全的 Session 管理器。
 */
public class SessionManager {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public String createSession(Channel controlChannel) {
        String clientId = UUID.randomUUID().toString();
        Session session = new Session(clientId, controlChannel);
        sessions.put(clientId, session);
        return clientId;
    }

    public Session getSession(String clientId) {
        return sessions.get(clientId);
    }

    public void bindDataChannel(String clientId, Channel dataChannel) {
        Session s = sessions.get(clientId);
        if (s != null) {
            s.setDataChannel(dataChannel);
        }
    }

    public Collection<Session> allSessions() {
        return sessions.values();
    }
}
