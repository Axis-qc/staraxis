package staraxis.webnet;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class AuthStore {

    private static final int SALT_BYTES = 16;
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int PBKDF2_KEY_BITS = 256;

    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom;

    private final ConcurrentHashMap<String, Session> sessionsByToken = new ConcurrentHashMap<>();

    public AuthStore(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.secureRandom = new SecureRandom();
    }

    public Account loadAccount(String username) {
        if (!isSafeUsername(username)) {
            return null;
        }
        try {
            var p = AccountFiles.accountPath(username);
            if (!p.toFile().isFile()) {
                return null;
            }
            return objectMapper.readValue(p.toFile(), Account.class);
        } catch (Exception e) {
            return null;
        }
    }

    public Account register(String username, String password) throws Exception {
        if (!isSafeUsername(username)) {
            throw new IllegalArgumentException("invalid username");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("invalid password");
        }

        var existing = loadAccount(username);
        if (existing != null) {
            throw new IllegalStateException("username already exists");
        }

        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt);

        Account a = new Account();
        a.username = username;
        a.playerId = "p_" + java.util.UUID.randomUUID();
        a.passwordSaltB64 = Base64.getEncoder().encodeToString(salt);
        a.passwordHashB64 = Base64.getEncoder().encodeToString(hash);
        a.createdAtMs = System.currentTimeMillis();

        AccountFiles.ensureDir();
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(AccountFiles.accountPath(username).toFile(), a);
        return a;
    }

    public Session login(String username, String password) throws Exception {
        Account a = loadAccount(username);
        if (a == null) {
            throw new IllegalArgumentException("account not found");
        }
        if (password == null) {
            throw new IllegalArgumentException("invalid password");
        }

        byte[] salt = Base64.getDecoder().decode(a.passwordSaltB64);
        byte[] expected = Base64.getDecoder().decode(a.passwordHashB64);
        byte[] actual = pbkdf2(password, salt);

        if (!MessageDigest.isEqual(expected, actual)) {
            throw new IllegalArgumentException("wrong password");
        }

        String token = newToken();
        Session s = new Session(token, a.playerId, a.username, System.currentTimeMillis());
        sessionsByToken.put(token, s);
        return s;
    }

    public Session getSessionFromAuthorizationHeader(String authorization) {
        if (authorization == null) {
            return null;
        }
        String prefix = "Bearer ";
        if (!authorization.startsWith(prefix)) {
            return null;
        }
        String token = authorization.substring(prefix.length()).trim();
        if (token.isEmpty()) {
            return null;
        }
        return sessionsByToken.get(token);
    }

    public Session getSessionByToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return sessionsByToken.get(token.trim());
    }

    public void logout(String token) {
        if (token == null) {
            return;
        }
        sessionsByToken.remove(token);
    }

    public void setGameId(String playerId, String gameId) throws Exception {
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("invalid playerId");
        }
        if (gameId == null) {
            gameId = "";
        }
        String trimmed = gameId.trim();
        if (trimmed.length() > 64) {
            throw new IllegalArgumentException("gameId too long");
        }

        AccountFiles.ensureDir();

        // 读全量账户并匹配 playerId（简单实现：数量少时足够；后续可加索引文件）
        for (var p : AccountFiles.listAccountFiles()) {
            try {
                Account a = objectMapper.readValue(p.toFile(), Account.class);
                if (a != null && playerId.equals(a.playerId)) {
                    a.gameId = trimmed;
                    objectMapper.writerWithDefaultPrettyPrinter().writeValue(p.toFile(), a);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        throw new IllegalArgumentException("account not found by playerId");
    }

    private String newToken() {
        byte[] b = new byte[32];
        secureRandom.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static byte[] pbkdf2(String password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS);
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        return skf.generateSecret(spec).getEncoded();
    }

    private static boolean isSafeUsername(String username) {
        if (username == null)
            return false;
        String u = username.trim();
        if (u.isEmpty() || u.length() > 32)
            return false;
        for (int i = 0; i < u.length(); i++) {
            char c = u.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_'
                    || c == '-';
            if (!ok)
                return false;
        }
        return true;
    }

    public static class Session {
        public final String token;
        public final String playerId;
        public final String username;
        public final long loginAtMs;

        public Session(String token, String playerId, String username, long loginAtMs) {
            this.token = token;
            this.playerId = playerId;
            this.username = username;
            this.loginAtMs = loginAtMs;
        }
    }

    public static class Account {
        public String username;
        public String playerId;
        public String passwordSaltB64;
        public String passwordHashB64;
        public long createdAtMs;
        public String gameId;
    }
}
